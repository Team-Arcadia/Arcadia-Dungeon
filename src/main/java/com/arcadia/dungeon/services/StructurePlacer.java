package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Place une structure NBT dans le monde via {@code StructureTemplateManager} (S8.setup).
 *
 * <p>Les structures sont chargées depuis {@code data/<namespace>/structure/<path>.nbt}
 * — dans le JAR ou dans le dossier world/datapacks.
 * Le placement s'effectue sur le SGT (appelé depuis une commande ou enqueueWork).
 */
public final class StructurePlacer {

    /**
     * Place la structure {@code ref} avec son coin NW en {@code origin}.
     *
     * @return spawn point calculé (centre de la structure, Y+1) — vide si structure introuvable
     */
    public Optional<Vec3> place(ServerLevel level, ResourceLocation ref, BlockPos origin) {
        var manager = level.getServer().getStructureManager();
        // getOrCreate() charge depuis le ResourceManager (mod JAR / datapack) si absent du cache.
        // get() ne retourne que le cache mémoire — toujours vide pour les structures mod non pré-chargées.
        StructureTemplate template = manager.getOrCreate(ref);
        var size = template.getSize();

        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][STRUCT] Structure introuvable ou vide : {}", ref);
            return Optional.empty();
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false)
            .setFinalizeEntities(true);

        // TODO[DEBT]: placement synchrone sur le SGT — freeze serveur proportionnel à la taille de la structure.
        // Acceptable pour /arcadia setup (one-shot admin, pas de joueurs en run).
        // INTERDIT pour placement per-run (v1.1) : implémenter time-sliced via ServerTickEvent,
        // 256 blocs/tick (archi §8.2). Budget cible : ≤ 2ms/tick.
        // Date : 2026-05-07
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_ALL);

        if (!placed) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][STRUCT] placeInWorld a retourné false pour {}", ref);
        }

        Vec3 spawnPos = new Vec3(
            origin.getX() + size.getX() / 2.0,
            origin.getY() + 1.0,
            origin.getZ() + size.getZ() / 2.0
        );

        ArcadiaDungeon.LOGGER.info("[Arcadia][STRUCT] event=placed ref={} origin={} size={}x{}x{} spawn={}",
            ref, origin, size.getX(), size.getY(), size.getZ(), spawnPos);

        return Optional.of(spawnPos);
    }
}
