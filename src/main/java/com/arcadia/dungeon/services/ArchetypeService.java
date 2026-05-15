package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.player.PlayerProgress;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import com.arcadia.dungeon.persistence.GlobalClassRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Arcadia run kits while keeping the player's normal inventory separate.
 *
 * <p>The original inventory is backed up in memory and SQLite before the run
 * inventory is applied. The persistent backup is deleted only after a successful
 * restore, so disconnects or restarts do not silently discard player items.
 */
public final class ArchetypeService {

    private final DungeonRegistry dungeonRegistry;
    private final GlobalClassRegistry globalClassRegistry;
    private final ArcadiaDatabaseService databaseService;
    private final Map<UUID, ListTag> inventoryBackups = new ConcurrentHashMap<>();
    private final Set<UUID> debugInventoryPreviews = ConcurrentHashMap.newKeySet();

    public ArchetypeService(DungeonRegistry dungeonRegistry,
                            GlobalClassRegistry globalClassRegistry,
                            ArcadiaDatabaseService databaseService) {
        this.dungeonRegistry = dungeonRegistry;
        this.globalClassRegistry = globalClassRegistry;
        this.databaseService = databaseService;
    }

    /**
     * Saves the current inventory, clears it, then gives the selected run kit.
     * Must be called on the server game thread.
     */
    public void preparePlayer(ServerPlayer player, RunId runId, String dungeonId, String archetypeId) {
        preparePlayer(player, runId.toString(), dungeonId, archetypeId);
    }

    private void preparePlayer(ServerPlayer player, String backupId, String dungeonId, String archetypeId) {
        ListTag dungeonInventory = databaseService.loadDungeonInventory(player.getUUID()).orElse(null);
        stripAndSaveInventory(player, backupId);
        try {
            applyDungeonInventoryOrKit(player, dungeonId, archetypeId, dungeonInventory);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][ARCHETYPE] giveKit failed - restoring inventory for {}",
                player.getUUID(), e);
            restoreInventory(player.getUUID(), player.getServer(), false);
            throw e;
        }
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=kit_given playerId={} dungeon={} archetype={}",
            player.getUUID(), dungeonId, archetypeId);
    }

    public void preparePlayer(ServerPlayer player, String dungeonId, String archetypeId) {
        preparePlayer(player, RunId.generate(), dungeonId, archetypeId);
    }

    public boolean prepareDebugInventory(ServerPlayer player, String dungeonId) {
        if (ArcadiaDungeon.runLifecycleService().findActiveRunForPlayer(player.getUUID()).isPresent()
            || hasBackup(player.getUUID())) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] debug inventory refused playerId={} reason=active_run_or_backup",
                player.getUUID());
            return false;
        }
        String archetypeId = ArcadiaDungeon.playerProgressService()
            .get(player.getUUID())
            .map(PlayerProgress::selectedClassId)
            .filter(id -> id != null && !id.isBlank())
            .orElseGet(this::fallbackArchetypeId);
        debugInventoryPreviews.add(player.getUUID());
        try {
            preparePlayer(player, "debug:" + UUID.randomUUID(), dungeonId, archetypeId);
        } catch (RuntimeException e) {
            debugInventoryPreviews.remove(player.getUUID());
            throw e;
        }
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=debug_inventory_prepared playerId={} dungeon={} archetype={}",
            player.getUUID(), dungeonId, archetypeId);
        return true;
    }

    public boolean restoreDebugInventory(ServerPlayer player) {
        boolean debugPreview = debugInventoryPreviews.remove(player.getUUID()) || hasDebugBackup(player.getUUID());
        if (!debugPreview) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] debug inventory restore ignored playerId={} reason=no_debug_backup",
                player.getUUID());
            return false;
        }
        restoreInventory(player.getUUID(), player.getServer(), false);
        return true;
    }

    /**
     * Restores saved inventory for every player in the run.
     * Offline players keep their disk backup and are restored on next login.
     */
    public void restoreAll(Run run, MinecraftServer server) {
        for (UUID playerId : run.playerIds()) {
            restoreInventory(playerId, server, true);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean inActiveRun = ArcadiaDungeon.runLifecycleService()
            .findActiveRunForPlayer(player.getUUID())
            .isPresent();
        if (inActiveRun) return;
        if (hasBackup(player.getUUID())) {
            boolean debugBackup = debugInventoryPreviews.remove(player.getUUID()) || hasDebugBackup(player.getUUID());
            boolean saveDungeonInventory = !debugBackup;
            restoreInventory(player.getUUID(), player.getServer(), saveDungeonInventory);
        }
    }

    private void stripAndSaveInventory(ServerPlayer player, String backupId) {
        UUID playerId = player.getUUID();
        if (hasBackup(playerId)) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] existing inventory backup kept playerId={}", playerId);
            player.getInventory().clearContent();
            return;
        }

        ListTag saved = new ListTag();
        player.getInventory().save(saved);
        inventoryBackups.put(playerId, saved.copy());
        persistInventoryBackup(playerId, backupId, saved);
        player.getInventory().clearContent();
    }

    private void giveKit(ServerPlayer player, String dungeonId, String archetypeId) {
        DungeonConfig config = dungeonRegistry.get(dungeonId).orElse(null);
        List<String> items = resolveKit(player, config, archetypeId);
        for (int i = 0; i < items.size(); i++) {
            String itemId = items.get(i);
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] item invalid: {}", itemId);
                continue;
            }
            var item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item == null) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] item unknown: {}", itemId);
                continue;
            }
            ItemStack stack = new ItemStack(item, 1);
            if (i < 3) {
                player.getInventory().setItem(i, stack);
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        syncInventory(player);
    }

    private void applyDungeonInventoryOrKit(ServerPlayer player,
                                            String dungeonId,
                                            String archetypeId,
                                            ListTag dungeonInventory) {
        if (dungeonInventory != null) {
            player.getInventory().clearContent();
            player.getInventory().load(dungeonInventory);
            syncInventory(player);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=dungeon_inventory_loaded playerId={}",
                player.getUUID());
            return;
        }
        giveKit(player, dungeonId, archetypeId);
    }

    private List<String> resolveKit(ServerPlayer player, DungeonConfig config, String archetypeId) {
        if (PlayerProgress.CUSTOM_LOADOUT_ID.equals(archetypeId)) {
            return ArcadiaDungeon.playerProgressService()
                .get(player.getUUID())
                .filter(PlayerProgress::customLoadoutUnlocked)
                .map(progress -> List.of(
                    progress.customMainItem(),
                    progress.customOffItem(),
                    progress.customUtilityItem()))
                .orElse(List.of(
                    PlayerProgress.DEFAULT_CUSTOM_MAIN,
                    PlayerProgress.DEFAULT_CUSTOM_OFF,
                    PlayerProgress.DEFAULT_CUSTOM_UTILITY));
        }
        List<String> localKit = resolveLocalKit(config, archetypeId);
        if (!localKit.isEmpty()) {
            return localKit;
        }
        for (DungeonConfig dungeon : dungeonRegistry.dungeons().values()) {
            List<String> kit = resolveLocalKit(dungeon, archetypeId);
            if (!kit.isEmpty()) {
                return kit;
            }
        }
        return globalClassRegistry.itemsFor(archetypeId);
    }

    private static List<String> resolveLocalKit(DungeonConfig config, String archetypeId) {
        if (config == null || config.archetypes() == null) {
            return List.of();
        }
        return config.archetypes().stream()
            .filter(a -> a.id().equals(archetypeId))
            .findFirst()
            .map(DungeonConfig.ArchetypeDefinition::items)
            .orElse(List.of());
    }

    private void restoreInventory(UUID playerId, MinecraftServer server, boolean saveDungeonInventory) {
        ListTag saved = inventoryBackups.get(playerId);
        if (saved == null) {
            saved = loadInventoryBackup(playerId);
        }
        if (saved == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] player absent - backup kept playerId={}", playerId);
            return;
        }

        if (saveDungeonInventory) {
            saveDungeonInventory(player);
        }
        player.getInventory().clearContent();
        player.getInventory().load(saved);
        syncInventory(player);
        inventoryBackups.remove(playerId);
        deleteInventoryBackup(playerId);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=inventory_restored playerId={}", playerId);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean inActiveRun = ArcadiaDungeon.runLifecycleService()
            .findActiveRunForPlayer(player.getUUID())
            .isPresent();
        if (inActiveRun) {
            saveDungeonInventory(player);
        }
    }

    private void saveDungeonInventory(ServerPlayer player) {
        ListTag current = new ListTag();
        player.getInventory().save(current);
        databaseService.saveDungeonInventory(player.getUUID(), current);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=dungeon_inventory_saved playerId={}",
            player.getUUID());
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private boolean hasBackup(UUID playerId) {
        return inventoryBackups.containsKey(playerId)
            || databaseService.hasNormalInventoryBackup(playerId);
    }

    private boolean hasDebugBackup(UUID playerId) {
        return databaseService.loadNormalInventoryBackupRunId(playerId)
            .map(id -> id.startsWith("debug:"))
            .orElse(false);
    }

    private void persistInventoryBackup(UUID playerId, String backupId, ListTag inventory) {
        try {
            databaseService.saveNormalInventoryBackup(playerId, backupId, inventory);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=inventory_backup_saved playerId={} store=sqlite",
                playerId);
        } catch (RuntimeException e) {
            inventoryBackups.remove(playerId);
            throw new IllegalStateException("Unable to persist Arcadia inventory backup for " + playerId, e);
        }
    }

    private ListTag loadInventoryBackup(UUID playerId) {
        ListTag sqliteBackup = databaseService.loadNormalInventoryBackup(playerId).orElse(null);
        if (sqliteBackup != null) {
            inventoryBackups.put(playerId, sqliteBackup.copy());
            return sqliteBackup;
        }
        return null;
    }

    private void deleteInventoryBackup(UUID playerId) {
        databaseService.deleteNormalInventoryBackup(playerId);
    }

    private String fallbackArchetypeId() {
        return globalClassRegistry.classes().stream()
            .findFirst()
            .map(DungeonConfig.ArchetypeDefinition::id)
            .orElse("warrior");
    }
}
