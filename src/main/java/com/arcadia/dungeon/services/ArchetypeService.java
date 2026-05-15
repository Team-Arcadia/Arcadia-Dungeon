package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
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
        ListTag dungeonInventory = databaseService.loadDungeonInventory(player.getUUID()).orElse(null);
        stripAndSaveInventory(player, runId);
        try {
            applyDungeonInventoryOrKit(player, dungeonId, archetypeId, dungeonInventory);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][ARCHETYPE] giveKit failed - restoring inventory for {}",
                player.getUUID(), e);
            restoreInventory(player.getUUID(), player.getServer());
            throw e;
        }
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=kit_given playerId={} dungeon={} archetype={}",
            player.getUUID(), dungeonId, archetypeId);
    }

    public void preparePlayer(ServerPlayer player, String dungeonId, String archetypeId) {
        preparePlayer(player, RunId.generate(), dungeonId, archetypeId);
    }

    /**
     * Restores saved inventory for every player in the run.
     * Offline players keep their disk backup and are restored on next login.
     */
    public void restoreAll(Run run, MinecraftServer server) {
        for (UUID playerId : run.playerIds()) {
            restoreInventory(playerId, server);
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
            restoreInventory(player.getUUID(), player.getServer());
        }
    }

    private void stripAndSaveInventory(ServerPlayer player, RunId runId) {
        UUID playerId = player.getUUID();
        if (hasBackup(playerId)) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] existing inventory backup kept playerId={}", playerId);
            player.getInventory().clearContent();
            return;
        }

        ListTag saved = new ListTag();
        player.getInventory().save(saved);
        inventoryBackups.put(playerId, saved.copy());
        persistInventoryBackup(playerId, runId, saved);
        player.getInventory().clearContent();
    }

    private void giveKit(ServerPlayer player, String dungeonId, String archetypeId) {
        DungeonConfig config = dungeonRegistry.get(dungeonId).orElse(null);
        List<String> items = resolveKit(config, archetypeId);
        for (String itemId : items) {
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
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private void applyDungeonInventoryOrKit(ServerPlayer player,
                                            String dungeonId,
                                            String archetypeId,
                                            ListTag dungeonInventory) {
        if (dungeonInventory != null) {
            player.getInventory().clearContent();
            player.getInventory().load(dungeonInventory);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=dungeon_inventory_loaded playerId={}",
                player.getUUID());
            return;
        }
        giveKit(player, dungeonId, archetypeId);
    }

    private List<String> resolveKit(DungeonConfig config, String archetypeId) {
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

    private void restoreInventory(UUID playerId, MinecraftServer server) {
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

        saveDungeonInventory(player);
        player.getInventory().clearContent();
        player.getInventory().load(saved);
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

    private boolean hasBackup(UUID playerId) {
        return inventoryBackups.containsKey(playerId)
            || databaseService.hasNormalInventoryBackup(playerId);
    }

    private void persistInventoryBackup(UUID playerId, RunId runId, ListTag inventory) {
        try {
            databaseService.saveNormalInventoryBackup(playerId, runId.toString(), inventory);
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
}
