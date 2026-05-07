package com.arcadia.dungeon.command;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.persistence.PlacementRegistry;
import com.arcadia.dungeon.services.StructurePlacer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Commande {@code /arcadia setup <dungeonId>} (S8.setup).
 *
 * <p>Si {@code dimension} est défini dans la config, la structure est placée dans cette
 * dimension à la position du joueur (coordonnées transférées). Sinon, dimension courante.
 *
 * <p>Deux modes selon la config :
 * <ul>
 *   <li>Si {@code structureRef} est défini : place la structure NBT, enregistre le centre comme spawn.
 *   <li>Si {@code structureRef} est absent : enregistre la position courante comme spawn.
 * </ul>
 *
 * <p>Requiert op level 2. À lancer une seule fois par donjon sur le serveur.
 */
public final class ArcadiaSetupCommand {

    private final StructurePlacer structurePlacer;
    private final PlacementRegistry placementRegistry;

    public ArcadiaSetupCommand(StructurePlacer structurePlacer, PlacementRegistry placementRegistry) {
        this.structurePlacer = structurePlacer;
        this.placementRegistry = placementRegistry;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadia")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("setup")
                .then(Commands.argument("dungeonId", ResourceLocationArgument.id())
                    .executes(ctx -> execute(
                        ctx.getSource(),
                        ResourceLocationArgument.getId(ctx, "dungeonId").toString()))))
            .then(Commands.literal("setspawn")
                .then(Commands.argument("dungeonId", ResourceLocationArgument.id())
                    .executes(ctx -> setSpawn(
                        ctx.getSource(),
                        ResourceLocationArgument.getId(ctx, "dungeonId").toString())))));
    }

    private int execute(CommandSourceStack src, String dungeonId) {
        DungeonConfig config = ArcadiaDungeon.dungeonRegistry().get(dungeonId).orElse(null);
        if (config == null) {
            src.sendFailure(Component.literal("[Arcadia] Donjon inconnu : " + dungeonId));
            return 0;
        }

        // Résoudre la dimension cible — config prioritaire, sinon dimension courante de l'admin
        ServerLevel level = resolveLevel(src, config);
        if (level == null) {
            src.sendFailure(Component.literal("[Arcadia] Dimension introuvable : " + config.dimension()));
            return 0;
        }
        String dimensionId = level.dimension().location().toString();
        Vec3 playerPos = src.getPosition();
        Vec3 spawnPos;

        String ref = config.structureRef();
        if (ref != null && !ref.isBlank()) {
            // Mode A — placement NBT dans la dimension configurée
            ResourceLocation rl = ResourceLocation.tryParse(ref);
            if (rl == null) {
                src.sendFailure(Component.literal("[Arcadia] structureRef invalide : " + ref));
                return 0;
            }
            double originY = (config.placementY() != null) ? config.placementY() : playerPos.y;
            BlockPos origin = new BlockPos((int) playerPos.x, (int) originY, (int) playerPos.z);
            var result = structurePlacer.place(level, rl, origin);
            if (result.isEmpty()) {
                src.sendFailure(Component.literal(
                    "[Arcadia] Structure introuvable : " + rl +
                    " — vérifie que le .nbt est dans data/arcadia_dungeon/structure/"));
                return 0;
            }
            spawnPos = result.get();
            final Vec3 finalSpawn = spawnPos;
            final String finalDim = dimensionId;
            src.sendSuccess(() -> Component.literal(
                "[Arcadia] Structure placée dans " + finalDim + ". Spawn temporaire au centre : " + formatPos(finalSpawn) +
                " — utilise /arcadia setspawn " + dungeonId + " pour le préciser."), true);
        } else {
            // Mode B — donjon déjà présent, on enregistre juste le spawn
            spawnPos = playerPos;
            final String finalDim = dimensionId;
            src.sendSuccess(() -> Component.literal(
                "[Arcadia] Spawn enregistré dans " + finalDim + " à : " + formatPos(spawnPos)), true);
        }

        placementRegistry.setSpawn(dungeonId, spawnPos, dimensionId);

        // Téléporte l'admin au spawn dans la bonne dimension pour confirmation visuelle
        if (src.getPlayer() instanceof ServerPlayer player) {
            player.teleportTo(level, spawnPos.x, spawnPos.y, spawnPos.z,
                player.getYRot(), player.getXRot());
        }

        ArcadiaDungeon.LOGGER.info("[Arcadia][SETUP] event=setup_done dungeonId={} dim={} spawn={}",
            dungeonId, dimensionId, formatPos(spawnPos));
        return 1;
    }

    /**
     * Enregistre la position courante de l'admin comme spawn du donjon.
     * Peut être appelé après {@code /arcadia setup} pour corriger le spawn.
     */
    private int setSpawn(CommandSourceStack src, String dungeonId) {
        if (ArcadiaDungeon.dungeonRegistry().get(dungeonId).isEmpty()) {
            src.sendFailure(Component.literal("[Arcadia] Donjon inconnu : " + dungeonId));
            return 0;
        }

        Vec3 pos = src.getPosition();
        String dimensionId = src.getLevel().dimension().location().toString();
        placementRegistry.setSpawn(dungeonId, pos, dimensionId);
        src.sendSuccess(() -> Component.literal(
            "[Arcadia] Spawn du donjon « " + dungeonId + " » mis à jour : " + formatPos(pos) +
            " (dim: " + dimensionId + ") — les joueurs apparaîtront ici au démarrage de la run."), true);

        ArcadiaDungeon.LOGGER.info("[Arcadia][SETUP] event=setspawn dungeonId={} dim={} pos={}",
            dungeonId, dimensionId, formatPos(pos));
        return 1;
    }

    /** Retourne le {@link ServerLevel} configuré dans {@code config.dimension()}, ou la dimension courante si null. */
    private static ServerLevel resolveLevel(CommandSourceStack src, DungeonConfig config) {
        String dim = config.dimension();
        if (dim == null || dim.isBlank()) return src.getLevel();
        ResourceLocation dimRl = ResourceLocation.tryParse(dim);
        if (dimRl == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimRl);
        return src.getServer().getLevel(key);
    }

    private static String formatPos(Vec3 pos) {
        return String.format("%.1f / %.1f / %.1f", pos.x, pos.y, pos.z);
    }
}
