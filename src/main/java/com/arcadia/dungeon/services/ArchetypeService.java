package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service archétypes MVP : kit d'items de départ + strip/restore inventaire (Story S6.6).
 *
 * <p>Backup en mémoire (Map UUID → ListTag). Si le serveur redémarre pendant
 * une run, le backup est perdu — comportement acceptable en MVP (les donjons
 * ne survivent pas aux redémarrages non plus).
 *
 * <p>Toutes les mutations d'inventaire Minecraft doivent se faire sur le SGT.
 */
public final class ArchetypeService {

    private final DungeonRegistry dungeonRegistry;
    private final Map<UUID, ListTag> inventoryBackups = new ConcurrentHashMap<>();

    public ArchetypeService(DungeonRegistry dungeonRegistry) {
        this.dungeonRegistry = dungeonRegistry;
    }

    /**
     * Sauvegarde l'inventaire du joueur, le vide, puis donne le kit de l'archétype.
     * Doit être appelé sur le SGT.
     */
    public void preparePlayer(ServerPlayer player, String dungeonId, String archetypeId) {
        stripAndSaveInventory(player);
        giveKit(player, dungeonId, archetypeId);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=kit_given playerId={} dungeon={} archetype={}",
            player.getUUID(), dungeonId, archetypeId);
    }

    /**
     * Restaure l'inventaire sauvegardé pour tous les joueurs de la run.
     * Joueurs déconnectés : backup supprimé, items perdus (MVP acceptable).
     * Doit être appelé sur le SGT.
     */
    public void restoreAll(Run run, MinecraftServer server) {
        for (UUID playerId : run.playerIds()) {
            restoreInventory(playerId, server);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void stripAndSaveInventory(ServerPlayer player) {
        ListTag saved = new ListTag();
        player.getInventory().save(saved);
        inventoryBackups.put(player.getUUID(), saved);
        player.getInventory().clearContent();
    }

    private void giveKit(ServerPlayer player, String dungeonId, String archetypeId) {
        DungeonConfig config = dungeonRegistry.get(dungeonId).orElse(null);
        if (config == null || config.archetypes() == null) return;

        config.archetypes().stream()
            .filter(a -> a.id().equals(archetypeId))
            .findFirst()
            .ifPresent(archetype -> {
                for (String itemId : archetype.items()) {
                    ResourceLocation rl = ResourceLocation.tryParse(itemId);
                    if (rl == null) {
                        ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] item invalide: {}", itemId);
                        continue;
                    }
                    var item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                    if (item == null) {
                        ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] item inconnu: {}", itemId);
                        continue;
                    }
                    ItemStack stack = new ItemStack(item, 1);
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            });
    }

    private void restoreInventory(UUID playerId, MinecraftServer server) {
        ListTag saved = inventoryBackups.remove(playerId);
        if (saved == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][ARCHETYPE] player absent — backup discarded playerId={}", playerId);
            return;
        }

        player.getInventory().clearContent();
        player.getInventory().load(saved);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ARCHETYPE] event=inventory_restored playerId={}", playerId);
    }
}
