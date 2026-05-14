package com.arcadia.dungeon.command;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.persistence.PlacementRegistry;
import com.arcadia.dungeon.services.DungeonPlacementSlots;
import com.arcadia.dungeon.services.StructurePlacementScheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;

/** Admin setup commands for dungeon placement and spawn registration. */
public final class ArcadiaSetupCommand {

    private final StructurePlacementScheduler structurePlacementScheduler;
    private final PlacementRegistry placementRegistry;

    public ArcadiaSetupCommand(PlacementRegistry placementRegistry,
                               StructurePlacementScheduler structurePlacementScheduler) {
        this.placementRegistry = placementRegistry;
        this.structurePlacementScheduler = structurePlacementScheduler;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcadia")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("setup")
                .then(Commands.argument("dungeonId", StringArgumentType.string())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ArcadiaDungeon.dungeonRegistry().dungeons().keySet(), builder))
                    .then(Commands.argument("slot", IntegerArgumentType.integer(
                            DungeonPlacementSlots.MIN_SLOT, DungeonPlacementSlots.MAX_SLOT))
                        .executes(ctx -> execute(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "dungeonId"),
                            IntegerArgumentType.getInteger(ctx, "slot"))))
                    .executes(ctx -> execute(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "dungeonId"),
                        null))))
            .then(Commands.literal("setspawn")
                .then(Commands.argument("dungeonId", StringArgumentType.string())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ArcadiaDungeon.dungeonRegistry().dungeons().keySet(), builder))
                    .executes(ctx -> setSpawn(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "dungeonId"))))));
    }

    private int execute(CommandSourceStack src, String dungeonId, Integer requestedSlot) {
        String resolvedId = resolveDungeonId(dungeonId);
        DungeonConfig config = ArcadiaDungeon.dungeonRegistry().get(resolvedId).orElse(null);
        if (config == null) {
            src.sendFailure(Component.literal("[Arcadia] Donjon inconnu : " + dungeonId));
            return 0;
        }

        ServerLevel level = resolveLevel(src, config);
        if (level == null) {
            src.sendFailure(Component.literal("[Arcadia] Dimension introuvable : " + config.dimension()));
            return 0;
        }

        String dimensionId = level.dimension().location().toString();
        Vec3 spawnPos;
        String ref = config.structureRef();
        if (ref != null && !ref.isBlank()) {
            ResourceLocation structure = ResourceLocation.tryParse(ref);
            if (structure == null) {
                src.sendFailure(Component.literal("[Arcadia] structureRef invalide : " + ref));
                return 0;
            }

            int slot = resolveSlot(src, resolvedId, dimensionId, config, requestedSlot);
            if (slot < 0) return 0;

            int originY = config.placementY() != null ? config.placementY() : DungeonPlacementSlots.DEFAULT_Y;
            BlockPos origin = DungeonPlacementSlots.originFor(slot, originY);

            StructurePlacementScheduler.ClearArea clearArea = previousGeneratedArea(src, config);
            boolean queued = structurePlacementScheduler.enqueueTemplate(level, structure, origin, clearArea,
                "setup:" + resolvedId,
                result -> {
                    Vec3 generatedSpawn = result.spawnPos();
                    BlockPos size = result.size();
                    DungeonConfig.AreaPos area1 = new DungeonConfig.AreaPos(dimensionId, origin.getX(), origin.getY(), origin.getZ());
                    DungeonConfig.AreaPos area2 = new DungeonConfig.AreaPos(dimensionId,
                        origin.getX() + Math.max(0, size.getX() - 1),
                        origin.getY() + Math.max(0, size.getY() - 1),
                        origin.getZ() + Math.max(0, size.getZ() - 1));
                    DungeonConfig updated = config.withGeneration(structure.toString(), dimensionId, origin.getY(), area1, area2,
                        area1, new DungeonConfig.GeneratedSize(size.getX(), size.getY(), size.getZ()), slot);
                    ArcadiaDungeon.dungeonRegistry().save(updated);
                    placementRegistry.setSpawn(resolvedId, generatedSpawn, dimensionId);
                    src.sendSuccess(() -> Component.literal(
                        "[Arcadia] Structure generee dans " + dimensionId + " slot " + slot +
                            ". Spawn temporaire au centre : " + formatPos(generatedSpawn) +
                            " - utilise /arcadia setspawn " + resolvedId + " pour le preciser."), true);
                    if (src.getPlayer() instanceof ServerPlayer player) {
                        player.teleportTo(level, generatedSpawn.x, generatedSpawn.y, generatedSpawn.z, player.getYRot(), player.getXRot());
                    }
                    ArcadiaDungeon.LOGGER.info("[Arcadia][SETUP] event=setup_done dungeonId={} dim={} spawn={}",
                        resolvedId, dimensionId, formatPos(generatedSpawn));
                },
                message -> src.sendFailure(Component.literal("[Arcadia] " + message.getString())));
            if (!queued) {
                return 0;
            }
            src.sendSuccess(() -> Component.literal(
                "[Arcadia] Generation planifiee dans " + dimensionId + " slot " + slot + ". Le spawn sera enregistre a la fin."), true);
            return 1;
        } else {
            spawnPos = src.getPosition();
            Vec3 finalSpawn = spawnPos;
            src.sendSuccess(() -> Component.literal(
                "[Arcadia] Spawn enregistre dans " + dimensionId + " a : " + formatPos(finalSpawn)), true);
        }

        placementRegistry.setSpawn(resolvedId, spawnPos, dimensionId);

        if (src.getPlayer() instanceof ServerPlayer player) {
            player.teleportTo(level, spawnPos.x, spawnPos.y, spawnPos.z, player.getYRot(), player.getXRot());
        }

        ArcadiaDungeon.LOGGER.info("[Arcadia][SETUP] event=setup_done dungeonId={} dim={} spawn={}",
            resolvedId, dimensionId, formatPos(spawnPos));
        return 1;
    }

    private int setSpawn(CommandSourceStack src, String dungeonId) {
        String resolvedId = resolveDungeonId(dungeonId);
        if (ArcadiaDungeon.dungeonRegistry().get(resolvedId).isEmpty()) {
            src.sendFailure(Component.literal("[Arcadia] Donjon inconnu : " + dungeonId));
            return 0;
        }

        Vec3 pos = src.getPosition();
        String dimensionId = src.getLevel().dimension().location().toString();
        placementRegistry.setSpawn(resolvedId, pos, dimensionId);
        src.sendSuccess(() -> Component.literal(
            "[Arcadia] Spawn du donjon " + resolvedId + " mis a jour : " + formatPos(pos) +
                " (dim: " + dimensionId + ")"), true);

        ArcadiaDungeon.LOGGER.info("[Arcadia][SETUP] event=setspawn dungeonId={} dim={} pos={}",
            resolvedId, dimensionId, formatPos(pos));
        return 1;
    }

    private int resolveSlot(CommandSourceStack src,
                            String dungeonId,
                            String dimensionId,
                            DungeonConfig config,
                            Integer requestedSlot) {
        int slot;
        if (requestedSlot != null) {
            slot = DungeonPlacementSlots.clampSlot(requestedSlot);
        } else if (config.generatedSlot() != null) {
            slot = DungeonPlacementSlots.clampSlot(config.generatedSlot());
        } else {
            OptionalInt firstFree = DungeonPlacementSlots.firstAvailable(
                ArcadiaDungeon.dungeonRegistry().dungeons().values(), dungeonId, dimensionId);
            if (firstFree.isEmpty()) {
                src.sendFailure(Component.literal("[Arcadia] Aucun slot de placement libre dans " + dimensionId));
                return -1;
            }
            slot = firstFree.getAsInt();
        }

        if (DungeonPlacementSlots.isOccupied(ArcadiaDungeon.dungeonRegistry().dungeons().values(),
            dungeonId, dimensionId, slot)) {
            src.sendFailure(Component.literal("[Arcadia] Slot de placement deja utilise : " + slot));
            return -1;
        }
        return slot;
    }

    private StructurePlacementScheduler.ClearArea previousGeneratedArea(CommandSourceStack src, DungeonConfig config) {
        if (config.generatedOrigin() == null || config.generatedSize() == null) return null;

        DungeonConfig.AreaPos origin = config.generatedOrigin();
        ResourceLocation dimension = ResourceLocation.tryParse(origin.dimension());
        if (dimension == null) return null;
        ServerLevel oldLevel = src.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (oldLevel == null) return null;

        DungeonConfig.GeneratedSize size = config.generatedSize();
        return new StructurePlacementScheduler.ClearArea(oldLevel,
            new BlockPos(origin.x(), origin.y(), origin.z()),
            new BlockPos(size.x(), size.y(), size.z()));
    }

    private static String resolveDungeonId(String input) {
        String id = input != null ? input.trim() : "";
        if (ArcadiaDungeon.dungeonRegistry().get(id).isPresent()) return id;
        if (!id.contains(":")) {
            String namespaced = ArcadiaDungeon.MODID + ":" + id;
            if (ArcadiaDungeon.dungeonRegistry().get(namespaced).isPresent()) return namespaced;
        }
        return id;
    }

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
