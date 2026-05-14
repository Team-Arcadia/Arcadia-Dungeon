package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Runtime NBT instances used by active runs. Admin preview slots stay separate. */
public final class DungeonInstanceService {

    private final DungeonRegistry dungeonRegistry;
    private final StructurePlacementScheduler scheduler;
    private final Map<RunId, Instance> activeInstances = new ConcurrentHashMap<>();
    private final Set<RunId> pendingRuns = ConcurrentHashMap.newKeySet();
    private final Set<Integer> pendingSlots = ConcurrentHashMap.newKeySet();

    public DungeonInstanceService(DungeonRegistry dungeonRegistry, StructurePlacementScheduler scheduler) {
        this.dungeonRegistry = dungeonRegistry;
        this.scheduler = scheduler;
    }

    public boolean requiresRuntimeInstance(DungeonConfig config) {
        return config != null
            && config.structureRef() != null
            && !config.structureRef().isBlank()
            && !"custom".equalsIgnoreCase(config.generationMode());
    }

    public void prepareRunInstance(MinecraftServer server,
                                   Run run,
                                   DungeonConfig config,
                                   Consumer<PreparedInstance> onReady,
                                   Consumer<Component> onError) {
        ResourceLocation structureRef;
        try {
            structureRef = ResourceLocation.parse(config.structureRef());
        } catch (Exception ex) {
            onError.accept(Component.literal("Structure invalide : " + config.structureRef()));
            return;
        }

        String dimensionId = config.dimension() != null && !config.dimension().isBlank()
            ? config.dimension()
            : ArcadiaDungeon.DUNGEON_DIMENSION_ID;
        ResourceLocation dimensionLocation = ResourceLocation.tryParse(dimensionId);
        if (dimensionLocation == null) {
            onError.accept(Component.literal("Dimension invalide : " + dimensionId));
            return;
        }

        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        ServerLevel level = server.getLevel(levelKey);
        if (level == null) {
            onError.accept(Component.literal("Dimension introuvable : " + dimensionId));
            return;
        }

        Optional<Integer> slotOpt = allocateSlot(dimensionId);
        if (slotOpt.isEmpty()) {
            onError.accept(Component.literal("Aucun slot d'instance disponible"));
            return;
        }

        int slot = slotOpt.get();
        int y = config.placementY() != null ? config.placementY() : DungeonPlacementSlots.DEFAULT_Y;
        BlockPos origin = DungeonPlacementSlots.originFor(slot, y);
        String label = "run-instance:" + run.id();
        pendingRuns.add(run.id());

        boolean queued = scheduler.enqueueTemplate(level, structureRef, origin, null, label, result -> {
            pendingRuns.remove(run.id());
            pendingSlots.remove(slot);
            Instance instance = new Instance(run.id(), config.id(), level, dimensionId, slot, origin, result.size(), result.spawnPos());
            activeInstances.put(run.id(), instance);
            onReady.accept(new PreparedInstance(level, result.spawnPos(), origin, result.size(), slot));
        }, message -> {
            pendingRuns.remove(run.id());
            pendingSlots.remove(slot);
            onError.accept(message);
        });

        if (queued) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][INSTANCE] event=queued runId={} dungeon={} slot={} origin={}",
                run.id(), config.id(), slot, origin);
        } else {
            pendingRuns.remove(run.id());
            pendingSlots.remove(slot);
        }
    }

    public void cleanupRun(Run run) {
        Instance instance = activeInstances.remove(run.id());
        if (instance == null) return;

        String label = "run-cleanup:" + run.id();
        scheduler.enqueueClear(new StructurePlacementScheduler.ClearArea(instance.level(), instance.origin(), instance.size()),
            label,
            () -> {
                ArcadiaDungeon.LOGGER.info("[Arcadia][INSTANCE] event=cleaned runId={} dungeon={} slot={}",
                    run.id(), instance.dungeonId(), instance.slot());
            },
            message -> ArcadiaDungeon.LOGGER.warn("[Arcadia][INSTANCE] event=cleanup_failed runId={} reason={}",
                run.id(), message.getString()));
    }

    public Optional<PreparedInstance> activeInstance(Run run) {
        Instance instance = activeInstances.get(run.id());
        if (instance == null) return Optional.empty();
        return Optional.of(new PreparedInstance(instance.level(), instance.spawnPos(), instance.origin(), instance.size(), instance.slot()));
    }

    public boolean isPreparing(Run run) {
        return run != null && pendingRuns.contains(run.id());
    }

    public void shutdown() {
        activeInstances.clear();
        pendingRuns.clear();
        pendingSlots.clear();
    }

    private Optional<Integer> allocateSlot(String dimensionId) {
        Set<Integer> occupied = new HashSet<>(pendingSlots);
        for (Instance instance : activeInstances.values()) {
            if (dimensionId.equals(instance.dimensionId())) occupied.add(instance.slot());
        }
        for (DungeonConfig dungeon : dungeonRegistry.dungeons().values()) {
            if (dungeon.generatedSlot() == null || dungeon.generatedOrigin() == null) continue;
            if (dimensionId.equals(dungeon.generatedOrigin().dimension())) occupied.add(dungeon.generatedSlot());
        }

        for (int slot = DungeonPlacementSlots.RUNTIME_MIN_SLOT; slot <= DungeonPlacementSlots.RUNTIME_MAX_SLOT; slot++) {
            if (occupied.contains(slot)) continue;
            if (pendingSlots.add(slot)) return Optional.of(slot);
        }
        return Optional.empty();
    }

    public record PreparedInstance(ServerLevel level, Vec3 spawnPos, BlockPos origin, BlockPos size, int slot) {}

    private record Instance(RunId runId,
                            String dungeonId,
                            ServerLevel level,
                            String dimensionId,
                            int slot,
                            BlockPos origin,
                            BlockPos size,
                            Vec3 spawnPos) {}
}
