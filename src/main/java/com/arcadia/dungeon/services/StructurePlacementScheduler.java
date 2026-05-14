package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Time-sliced structure placement. World mutations still run on the server game thread,
 * but each tick has a small work budget to avoid long freezes during dungeon generation.
 */
public final class StructurePlacementScheduler {

    private static final int MAX_OPERATIONS_PER_TICK = 4096;
    private static final long MAX_NANOS_PER_TICK = 2_000_000L;
    private static final int FAST_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final ArrayDeque<PlacementJob> queue = new ArrayDeque<>();
    private final ArrayDeque<PreparedJob> preparedJobs = new ArrayDeque<>();
    private final ArrayDeque<Runnable> serverCallbacks = new ArrayDeque<>();
    private final Set<String> pendingLabels = new HashSet<>();
    private final Object preparationLock = new Object();
    private final ExecutorService preparationExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Arcadia-Structure-Prepare");
        thread.setDaemon(true);
        return thread;
    });
    @Nullable
    private PlacementJob active;

    public record ClearArea(ServerLevel level, BlockPos origin, BlockPos size) {}

    public record Progress(String label,
                           String stage,
                           int processed,
                           int total,
                           boolean done,
                           boolean success,
                           String message) {}

    public boolean enqueueTemplate(ServerLevel level,
                                   ResourceLocation ref,
                                   BlockPos origin,
                                   @Nullable ClearArea clearArea,
                                   String label,
                                   Consumer<StructurePlacer.PlacementResult> onComplete,
                                   Consumer<Component> onError) {
        return enqueueTemplate(level, ref, origin, clearArea, label, onComplete, onError, progress -> {});
    }

    public boolean enqueueTemplate(ServerLevel level,
                                   ResourceLocation ref,
                                   BlockPos origin,
                                   @Nullable ClearArea clearArea,
                                   String label,
                                   Consumer<StructurePlacer.PlacementResult> onComplete,
                                   Consumer<Component> onError,
                                   Consumer<Progress> onProgress) {
        StructureTemplate template = level.getServer().getStructureManager().getOrCreate(ref);
        BlockPos size = new BlockPos(template.getSize().getX(), template.getSize().getY(), template.getSize().getZ());
        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) {
            onError.accept(Component.literal("Structure introuvable ou vide : " + ref));
            return false;
        }

        synchronized (preparationLock) {
            if (hasJobLocked(label)) {
                onError.accept(Component.literal("Generation deja en cours : " + label));
                return false;
            }
            pendingLabels.add(label);
        }

        onProgress.accept(new Progress(label, "prepare", 0, 0, false, true, "Preparation du NBT"));
        preparationExecutor.execute(() -> prepareTemplate(level, ref, origin, clearArea, label, template, size, onComplete, onError, onProgress));
        ArcadiaDungeon.LOGGER.info("[Arcadia][STRUCT] event=prepare_queued label={} ref={} origin={} size={}x{}x{}",
            label, ref, origin, size.getX(), size.getY(), size.getZ());
        return true;
    }

    public boolean enqueueClear(ClearArea clearArea,
                                String label,
                                Runnable onComplete,
                                Consumer<Component> onError) {
        return enqueueClear(clearArea, label, onComplete, onError, progress -> {});
    }

    public boolean enqueueClear(ClearArea clearArea,
                                String label,
                                Runnable onComplete,
                                Consumer<Component> onError,
                                Consumer<Progress> onProgress) {
        synchronized (preparationLock) {
            if (hasJobLocked(label)) {
                onError.accept(Component.literal("Generation deja en cours : " + label));
                return false;
            }
        }

        BlockPos size = clearArea.size();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            onComplete.run();
            return true;
        }

        ResourceLocation clearRef = ResourceLocation.fromNamespaceAndPath(ArcadiaDungeon.MODID, "clear");
        queue.add(new PlacementJob(label, clearArea.level(), clearRef, clearArea.origin(), clearArea.size(), clearArea,
            List.of(), List.of(), ignored -> onComplete.run(), onError, onProgress));
        ArcadiaDungeon.LOGGER.info("[Arcadia][STRUCT] event=clear_queued label={} origin={} size={}x{}x{}",
            label, clearArea.origin(), size.getX(), size.getY(), size.getZ());
        return true;
    }

    public void shutdown() {
        preparationExecutor.shutdownNow();
        synchronized (preparationLock) {
            preparedJobs.clear();
            serverCallbacks.clear();
            pendingLabels.clear();
        }
        queue.clear();
        active = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        drainPreparedWork();
        if (active == null) active = queue.poll();
        if (active == null) return;

        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int operations = 0;
        while (operations < MAX_OPERATIONS_PER_TICK && System.nanoTime() < deadline && active != null) {
            PlacementJob.StepResult result = active.step();
            if (result == PlacementJob.StepResult.WORK_DONE) {
                operations++;
            } else if (result == PlacementJob.StepResult.COMPLETE) {
                active.finish();
                active = queue.poll();
            } else {
                PlacementJob failed = active;
                active = queue.poll();
                failed.fail(Component.literal("Generation NBT interrompue : " + failed.label()));
            }
        }
    }

    private void prepareTemplate(ServerLevel level,
                                 ResourceLocation ref,
                                 BlockPos origin,
                                 @Nullable ClearArea clearArea,
                                 String label,
                                 StructureTemplate template,
                                 BlockPos size,
                                 Consumer<StructurePlacer.PlacementResult> onComplete,
                                 Consumer<Component> onError,
                                 Consumer<Progress> onProgress) {
        try {
            TemplateData data = readTemplate(level, template);
            if (data.blocks().isEmpty() && data.entities().isEmpty()) {
                synchronized (preparationLock) {
                    pendingLabels.remove(label);
                    serverCallbacks.add(() -> onProgress.accept(new Progress(label, "error", 0, 0, true, false, "Structure vide")));
                    serverCallbacks.add(() -> onError.accept(Component.literal("Structure vide : " + ref)));
                }
                return;
            }

            synchronized (preparationLock) {
                preparedJobs.add(new PreparedJob(label, level, ref, origin, size, clearArea, data.blocks(), data.entities(), onComplete, onError, onProgress));
            }
            ArcadiaDungeon.LOGGER.info("[Arcadia][STRUCT] event=prepared label={} ref={} blocks={} entities={}",
                label, ref, data.blocks().size(), data.entities().size());
        } catch (Exception ex) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][STRUCT] event=prepare_failed label={} ref={}", label, ref, ex);
            synchronized (preparationLock) {
                pendingLabels.remove(label);
                serverCallbacks.add(() -> onProgress.accept(new Progress(label, "error", 0, 0, true, false, "Preparation impossible")));
                serverCallbacks.add(() -> onError.accept(Component.literal("Preparation NBT impossible : " + ref)));
            }
        }
    }

    private void drainPreparedWork() {
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        synchronized (preparationLock) {
            while (!preparedJobs.isEmpty()) {
                PreparedJob job = preparedJobs.poll();
                pendingLabels.remove(job.label());
                queue.add(new PlacementJob(job.label(), job.level(), job.ref(), job.origin(), job.size(), job.clearArea(),
                    job.blocks(), job.entities(), job.onComplete(), job.onError(), job.onProgress()));
            }
            while (!serverCallbacks.isEmpty()) {
                callbacks.add(serverCallbacks.poll());
            }
        }
        while (!callbacks.isEmpty()) {
            callbacks.poll().run();
        }
    }

    private boolean hasJobLocked(String label) {
        if (pendingLabels.contains(label)) return true;
        if (active != null && active.label().equals(label)) return true;
        for (PlacementJob job : queue) {
            if (job.label().equals(label)) return true;
        }
        return false;
    }

    private static TemplateData readTemplate(ServerLevel level, StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        List<BlockState> palette = readPalette(level, tag);
        List<TemplateBlock> blocks = new ArrayList<>();
        ListTag blockTags = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockTags.size(); i++) {
            CompoundTag blockTag = blockTags.getCompound(i);
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            int stateIndex = Mth.clamp(blockTag.getInt("state"), 0, Math.max(0, palette.size() - 1));
            if (palette.isEmpty()) continue;
            BlockState state = palette.get(stateIndex);
            if (state.isAir()) continue;
            CompoundTag nbt = blockTag.contains("nbt", Tag.TAG_COMPOUND) ? blockTag.getCompound("nbt").copy() : null;
            blocks.add(new TemplateBlock(new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2)), state, nbt));
        }

        List<TemplateEntity> entities = new ArrayList<>();
        ListTag entityTags = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entityTags.size(); i++) {
            CompoundTag entityTag = entityTags.getCompound(i);
            if (!entityTag.contains("nbt", Tag.TAG_COMPOUND)) continue;
            ListTag posTag = entityTag.getList("pos", Tag.TAG_DOUBLE);
            ListTag blockPosTag = entityTag.getList("blockPos", Tag.TAG_INT);
            entities.add(new TemplateEntity(
                new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2)),
                new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2)),
                entityTag.getCompound("nbt").copy()));
        }
        return new TemplateData(blocks, entities);
    }

    private static List<BlockState> readPalette(ServerLevel level, CompoundTag tag) {
        ListTag paletteTag;
        if (tag.contains("palettes", Tag.TAG_LIST)) {
            paletteTag = tag.getList("palettes", Tag.TAG_LIST).getList(0);
        } else {
            paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        }

        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), paletteTag.getCompound(i)));
        }
        return palette;
    }

    private record PreparedJob(String label,
                               ServerLevel level,
                               ResourceLocation ref,
                               BlockPos origin,
                               BlockPos size,
                               @Nullable ClearArea clearArea,
                               List<TemplateBlock> blocks,
                               List<TemplateEntity> entities,
                               Consumer<StructurePlacer.PlacementResult> onComplete,
                               Consumer<Component> onError,
                               Consumer<Progress> onProgress) {}

    private record TemplateData(List<TemplateBlock> blocks, List<TemplateEntity> entities) {}

    private record TemplateBlock(BlockPos relativePos, BlockState state, @Nullable CompoundTag nbt) {}

    private record TemplateEntity(Vec3 relativePos, BlockPos relativeBlockPos, CompoundTag nbt) {}

    private static final class PlacementJob {
        private final String label;
        private final ServerLevel level;
        private final ResourceLocation ref;
        private final BlockPos origin;
        private final BlockPos size;
        @Nullable
        private final ClearCursor clearCursor;
        private final List<TemplateBlock> blocks;
        private final List<TemplateEntity> entities;
        private final Consumer<StructurePlacer.PlacementResult> onComplete;
        private final Consumer<Component> onError;
        private final Consumer<Progress> onProgress;
        private final int totalOperations;
        private int blockIndex;
        private int entityIndex;
        private int lastPercent = -1;
        private long lastProgressAt;
        private Stage stage;

        private PlacementJob(String label,
                             ServerLevel level,
                             ResourceLocation ref,
                             BlockPos origin,
                             BlockPos size,
                             @Nullable ClearArea clearArea,
                             List<TemplateBlock> blocks,
                             List<TemplateEntity> entities,
                             Consumer<StructurePlacer.PlacementResult> onComplete,
                             Consumer<Component> onError,
                             Consumer<Progress> onProgress) {
            this.label = label;
            this.level = level;
            this.ref = ref;
            this.origin = origin;
            this.size = size;
            this.clearCursor = clearArea != null ? new ClearCursor(clearArea) : null;
            this.blocks = blocks;
            this.entities = entities;
            this.onComplete = onComplete;
            this.onError = onError;
            this.onProgress = onProgress;
            this.totalOperations = (clearCursor != null ? clearCursor.total() : 0) + blocks.size() + entities.size();
            this.stage = clearCursor != null ? Stage.CLEAR : Stage.PLACE;
            emitProgress(true, null);
        }

        private String label() {
            return label;
        }

        private StepResult step() {
            try {
                if (stage == Stage.CLEAR) {
                    if (clearCursor != null && clearCursor.clearOne()) {
                        emitProgress(false, null);
                        return StepResult.WORK_DONE;
                    }
                    stage = Stage.PLACE;
                    emitProgress(true, null);
                }

                if (stage == Stage.PLACE) {
                    if (blockIndex < blocks.size()) {
                        placeBlock(blocks.get(blockIndex++));
                        emitProgress(false, null);
                        return StepResult.WORK_DONE;
                    }
                    stage = Stage.ENTITIES;
                    emitProgress(true, null);
                }

                if (stage == Stage.ENTITIES) {
                    if (entityIndex < entities.size()) {
                        placeEntity(entities.get(entityIndex++));
                        emitProgress(false, null);
                        return StepResult.WORK_DONE;
                    }
                    stage = Stage.COMPLETE;
                }

                return StepResult.COMPLETE;
            } catch (Exception ex) {
                ArcadiaDungeon.LOGGER.error("[Arcadia][STRUCT] event=job_failed label={} ref={}", label, ref, ex);
                return StepResult.FAILED;
            }
        }

        private void placeBlock(TemplateBlock block) {
            BlockPos pos = origin.offset(block.relativePos());
            CompoundTag nbt = block.nbt() != null ? block.nbt().copy() : null;
            if (nbt != null) {
                BlockEntity oldEntity = level.getBlockEntity(pos);
                Clearable.tryClear(oldEntity);
                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), FAST_BLOCK_FLAGS);
            }

            level.setBlock(pos, block.state(), FAST_BLOCK_FLAGS);
            if (nbt != null) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) {
                    if (blockEntity instanceof RandomizableContainer) {
                        nbt.putLong("LootTableSeed", level.random.nextLong());
                    }
                    blockEntity.loadWithComponents(nbt, level.registryAccess());
                    blockEntity.setChanged();
                }
            }
        }

        private void placeEntity(TemplateEntity entityInfo) {
            CompoundTag nbt = entityInfo.nbt().copy();
            Vec3 pos = entityInfo.relativePos().add(Vec3.atLowerCornerOf(origin));
            ListTag posTag = new ListTag();
            posTag.add(DoubleTag.valueOf(pos.x));
            posTag.add(DoubleTag.valueOf(pos.y));
            posTag.add(DoubleTag.valueOf(pos.z));
            nbt.put("Pos", posTag);
            nbt.remove("UUID");
            EntityType.create(nbt, level).ifPresent(entity -> {
                entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.STRUCTURE, null);
                }
                level.addFreshEntityWithPassengers(entity);
            });
        }

        private void finish() {
            Vec3 spawnPos = new Vec3(
                origin.getX() + size.getX() / 2.0,
                origin.getY() + 1.0,
                origin.getZ() + size.getZ() / 2.0
            );
            ArcadiaDungeon.LOGGER.info("[Arcadia][STRUCT] event=placed_async label={} ref={} origin={} size={}x{}x{} blocks={} entities={}",
                label, ref, origin, size.getX(), size.getY(), size.getZ(), blocks.size(), entities.size());
            onProgress.accept(new Progress(label, "complete", totalOperations, totalOperations, true, true, "Generation terminee"));
            onComplete.accept(new StructurePlacer.PlacementResult(spawnPos, size));
        }

        private void fail(Component message) {
            onProgress.accept(new Progress(label, "error", processedOperations(), totalOperations, true, false, message.getString()));
            onError.accept(message);
        }

        private void emitProgress(boolean force, @Nullable String message) {
            int total = Math.max(1, totalOperations);
            int processed = processedOperations();
            int percent = Math.min(100, processed * 100 / total);
            long now = System.currentTimeMillis();
            if (!force && percent == lastPercent && now - lastProgressAt < 500L) return;
            if (!force && percent - lastPercent < 5 && now - lastProgressAt < 500L) return;
            lastPercent = percent;
            lastProgressAt = now;
            onProgress.accept(new Progress(label, stage.name().toLowerCase(), processed, totalOperations, false, true,
                message != null ? message : stageMessage()));
        }

        private int processedOperations() {
            return (clearCursor != null ? clearCursor.processed() : 0) + blockIndex + entityIndex;
        }

        private String stageMessage() {
            return switch (stage) {
                case CLEAR -> "Suppression ancienne zone";
                case PLACE -> "Placement blocs";
                case ENTITIES -> "Placement entites";
                case COMPLETE -> "Finalisation";
            };
        }

        private enum Stage { CLEAR, PLACE, ENTITIES, COMPLETE }

        private enum StepResult { WORK_DONE, COMPLETE, FAILED }
    }

    private static final class ClearCursor {
        private final ServerLevel level;
        private final BlockPos origin;
        private final BlockPos size;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final int total;
        private int processed;
        private int x;
        private int y;
        private int z;

        private ClearCursor(ClearArea area) {
            this.level = area.level();
            this.origin = area.origin();
            this.size = area.size();
            this.total = Math.max(0, size.getX()) * Math.max(0, size.getY()) * Math.max(0, size.getZ());
            discardEntities();
        }

        private boolean clearOne() {
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) return false;
            if (x >= size.getX()) return false;

            cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!level.getBlockState(cursor).isAir()) {
                Clearable.tryClear(level.getBlockEntity(cursor));
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FAST_BLOCK_FLAGS);
            }
            advance();
            processed++;
            return true;
        }

        private int total() {
            return total;
        }

        private int processed() {
            return processed;
        }

        private void advance() {
            z++;
            if (z < size.getZ()) return;
            z = 0;
            y++;
            if (y < size.getY()) return;
            y = 0;
            x++;
        }

        private void discardEntities() {
            AABB bounds = new AABB(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
            for (Entity entity : level.getEntities((Entity) null, bounds, entity -> !(entity instanceof Player))) {
                entity.discard();
            }
        }
    }
}
