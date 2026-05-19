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

/** Template-backed dungeon instances used by active runs. */
public final class DungeonInstanceService {

    private final DungeonRegistry dungeonRegistry;
    private final StructurePlacementScheduler scheduler;
    private final Map<RunId, Instance> activeInstances = new ConcurrentHashMap<>();
    private final Set<RunId> pendingRuns = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingSlots = ConcurrentHashMap.newKeySet();
    private final Map<RunId, String> pendingRunSlots = new ConcurrentHashMap<>();

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
        prepareRunInstance(server, run, config, onReady, onError, progress -> {});
    }

    public void prepareRunInstance(MinecraftServer server,
                                   Run run,
                                   DungeonConfig config,
                                   Consumer<PreparedInstance> onReady,
                                   Consumer<Component> onError,
                                   Consumer<StructurePlacementScheduler.Progress> onProgress) {
        pruneInactiveInstances();
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

        PlacementTarget target = resolvePlacementTarget(config, level, dimensionId);
        if (target == null) {
            Optional<Integer> slotOpt = allocateSlot(dimensionId);
            if (slotOpt.isEmpty()) {
                onError.accept(Component.literal("Aucun slot d'instance disponible"));
                return;
            }
            int slot = slotOpt.get();
            int y = config.placementY() != null ? config.placementY() : DungeonPlacementSlots.DEFAULT_Y;
            target = new PlacementTarget(slot, DungeonPlacementSlots.originFor(slot, y), null, false);
        } else if (isSlotActive(dimensionId, target.slot()) || !reserveSlot(dimensionId, target.slot())) {
            onError.accept(Component.literal("Slot d'instance deja utilise : " + target.slot()));
            return;
        }

        PlacementTarget placement = target;
        String label = "run-instance:" + run.id();
        pendingRuns.add(run.id());
        pendingRunSlots.put(run.id(), slotKey(dimensionId, placement.slot()));

        boolean queued = scheduler.enqueueTemplate(level, structureRef, placement.origin(), placement.clearArea(), label, result -> {
            pendingRuns.remove(run.id());
            releaseRunReservation(run.id());
            Vec3 spawnPos = ArcadiaDungeon.placementRegistry()
                .getSpawn(config.id())
                .orElse(result.spawnPos());
            Instance instance = new Instance(run.id(), config.id(), level, dimensionId, placement.slot(),
                placement.origin(), result.size(), spawnPos, placement.adminConfigured());
            activeInstances.put(run.id(), instance);
            onReady.accept(new PreparedInstance(level, spawnPos, placement.origin(), result.size(), placement.slot()));
        }, message -> {
            pendingRuns.remove(run.id());
            releaseRunReservation(run.id());
            onError.accept(message);
        }, onProgress);

        if (queued) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][INSTANCE] event=queued runId={} dungeon={} slot={} origin={}",
                run.id(), config.id(), placement.slot(), placement.origin());
        } else {
            pendingRuns.remove(run.id());
            releaseRunReservation(run.id());
        }
    }

    public void cleanupRun(Run run) {
        pendingRuns.remove(run.id());
        releaseRunReservation(run.id());
        Instance instance = activeInstances.remove(run.id());
        if (instance == null) return;
        releaseSlot(instance.dimensionId(), instance.slot());
        if (instance.adminConfigured()) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][INSTANCE] event=retained runId={} dungeon={} slot={}",
                run.id(), instance.dungeonId(), instance.slot());
            return;
        }

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
        pendingRunSlots.clear();
    }

    private Optional<Integer> allocateSlot(String dimensionId) {
        Set<Integer> occupied = new HashSet<>();
        for (String pendingSlot : pendingSlots) {
            int separator = pendingSlot.lastIndexOf('#');
            if (separator <= 0 || !dimensionId.equals(pendingSlot.substring(0, separator))) continue;
            try {
                occupied.add(Integer.parseInt(pendingSlot.substring(separator + 1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed in-memory keys.
            }
        }
        for (Instance instance : activeInstances.values()) {
            if (dimensionId.equals(instance.dimensionId())) occupied.add(instance.slot());
        }
        for (DungeonConfig dungeon : dungeonRegistry.dungeons().values()) {
            if (dungeon.generatedSlot() == null || dungeon.generatedOrigin() == null) continue;
            if (dimensionId.equals(dungeon.generatedOrigin().dimension())) occupied.add(dungeon.generatedSlot());
        }

        for (int slot = DungeonPlacementSlots.RUNTIME_MIN_SLOT; slot <= DungeonPlacementSlots.RUNTIME_MAX_SLOT; slot++) {
            if (occupied.contains(slot)) continue;
            if (reserveSlot(dimensionId, slot)) return Optional.of(slot);
        }
        return Optional.empty();
    }

    private PlacementTarget resolvePlacementTarget(DungeonConfig config, ServerLevel level, String dimensionId) {
        if (config.generatedSlot() == null || config.generatedOrigin() == null || config.generatedSize() == null) {
            return null;
        }
        if (!dimensionId.equals(config.generatedOrigin().dimension())) {
            return null;
        }
        BlockPos origin = new BlockPos(config.generatedOrigin().x(), config.generatedOrigin().y(), config.generatedOrigin().z());
        BlockPos size = new BlockPos(config.generatedSize().x(), config.generatedSize().y(), config.generatedSize().z());
        StructurePlacementScheduler.ClearArea clearArea =
            new StructurePlacementScheduler.ClearArea(level, origin, size);
        return new PlacementTarget(config.generatedSlot(), origin, clearArea, true);
    }

    private boolean reserveSlot(String dimensionId, int slot) {
        return pendingSlots.add(slotKey(dimensionId, slot));
    }

    private boolean isSlotActive(String dimensionId, int slot) {
        pruneInactiveInstances();
        for (Instance instance : activeInstances.values()) {
            if (dimensionId.equals(instance.dimensionId()) && instance.slot() == slot) {
                return true;
            }
        }
        return false;
    }

    private void releaseSlot(String dimensionId, int slot) {
        pendingSlots.remove(slotKey(dimensionId, slot));
    }

    private void releaseRunReservation(RunId runId) {
        String slotKey = pendingRunSlots.remove(runId);
        if (slotKey != null) {
            pendingSlots.remove(slotKey);
        }
    }

    private static String slotKey(String dimensionId, int slot) {
        return dimensionId + "#" + slot;
    }

    private void pruneInactiveInstances() {
        try {
            RunLifecycleService lifecycle = ArcadiaDungeon.runLifecycleService();
            activeInstances.entrySet().removeIf(entry -> {
                if (lifecycle.findById(entry.getKey()).isPresent()) return false;
                Instance instance = entry.getValue();
                releaseSlot(instance.dimensionId(), instance.slot());
                ArcadiaDungeon.LOGGER.info("[Arcadia][INSTANCE] event=pruned_stale runId={} dungeon={} slot={}",
                    entry.getKey(), instance.dungeonId(), instance.slot());
                return true;
            });
        } catch (IllegalStateException ignored) {
            // Service can be queried during bootstrap/shutdown; stale entries are harmless there.
        }
    }

    public record PreparedInstance(ServerLevel level, Vec3 spawnPos, BlockPos origin, BlockPos size, int slot) {}

    private record Instance(RunId runId,
                            String dungeonId,
                            ServerLevel level,
                            String dimensionId,
                            int slot,
                            BlockPos origin,
                            BlockPos size,
                            Vec3 spawnPos,
                            boolean adminConfigured) {}

    private record PlacementTarget(int slot,
                                   BlockPos origin,
                                   StructurePlacementScheduler.ClearArea clearArea,
                                   boolean adminConfigured) {}
}
