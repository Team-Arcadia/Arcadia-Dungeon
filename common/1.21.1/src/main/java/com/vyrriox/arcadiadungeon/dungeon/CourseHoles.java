package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Finds the holes of a dungeon: the empty spaces of its area that no one can walk to from the
 * course, and that are closed off from the outside too. A player who lands in one, thrown there by
 * a chorus totem or a chorus fruit, has no way out.
 *
 * <p>The course is everything a player can reach from the points of the dungeon (its spawn, the
 * spawn of every wave mob and every boss) without going through a full block. Doors, slabs, fences,
 * panes and the scripted walls count as open, so the course is if anything too large, and a room
 * behind them is never taken for a hole. What the course does not reach and that opens onto the
 * outside of the area is the outside: the sky, the hub around the dungeon. It is reported but
 * never touched. What remains is closed on every side: those are the holes.</p>
 *
 * <p>Filling puts invisible barriers in the air of the holes, a few thousand blocks per tick, and
 * writes every position first to an undo file, so a fill cut short by a crash can still be undone.
 * The undo only takes away barriers it placed and that are still there.</p>
 *
 * @author vyrriox
 */
public final class CourseHoles {

    /** About 250 x 256 x 250 blocks. The scan keeps a few bits per block and reads each once. */
    public static final long MAX_CELLS = 16_000_000L;
    /** Blocks changed per tick by a fill or an undo, so a big one never freezes the server. */
    static final int BLOCKS_PER_TICK = 20_000;
    /** Shown in the chat, biggest first. */
    public static final int POCKETS_SHOWN = 5;

    /** Tell the clients, and leave the neighbours alone: the blocks around a hole stay as built. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int FILE_MAGIC = 0x41444831; // "ADH1"

    private static Job job;

    private CourseHoles() {}

    /** Why a scan could not run. */
    public static final class Refused extends Exception {
        public final String reason;

        Refused(String reason) {
            super(reason);
            this.reason = reason;
        }
    }

    /** One hole: where it starts, how many blocks it has, and how many of them are air. */
    public record Pocket(BlockPos first, int cells, int air) {}

    /** What a scan found. */
    public static final class Scan {
        public final ServerLevel level;
        public final int sizeX, sizeY, sizeZ;
        public final int seeds;
        public final int courseCells;
        public final int outsideCells;
        public final List<Pocket> pockets;
        public final int fillable;
        final long[] fillPositions;

        Scan(ServerLevel level, int sizeX, int sizeY, int sizeZ, int seeds, int courseCells, int outsideCells,
             List<Pocket> pockets, long[] fillPositions) {
            this.level = level;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.seeds = seeds;
            this.courseCells = courseCells;
            this.outsideCells = outsideCells;
            this.pockets = pockets;
            this.fillPositions = fillPositions;
            this.fillable = fillPositions.length;
        }
    }

    /** The cuboid of the area, as cells. */
    private record Box(int minX, int minY, int minZ, int sx, int sy, int sz) {
        int index(int x, int y, int z) {
            return (y * sz + z) * sx + x;
        }

        int x(int i) { return i % sx; }
        int z(int i) { return (i / sx) % sz; }
        int y(int i) { return i / (sx * sz); }

        boolean contains(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
        }
    }

    // === Scan ===

    public static Scan scan(MinecraftServer server, DungeonConfig config) throws Refused {
        if (!config.hasArea()) throw new Refused("no_area");
        DungeonConfig.AreaPos a = config.areaPos1;
        DungeonConfig.AreaPos b = config.areaPos2;
        ServerLevel level = level(server, a.dimension);
        if (level == null) throw new Refused("no_level");

        int minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x);
        int minY = Math.max(Math.min(a.y, b.y), level.getMinBuildHeight());
        int maxY = Math.min(Math.max(a.y, b.y), level.getMaxBuildHeight() - 1);
        int minZ = Math.min(a.z, b.z), maxZ = Math.max(a.z, b.z);
        if (maxY < minY) throw new Refused("no_area");
        long cells = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (cells > MAX_CELLS) throw new Refused("too_big");
        Box box = new Box(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        BitSet passable = new BitSet((int) cells);
        BitSet air = new BitSet((int) cells);
        readBlocks(level, box, passable, air);
        for (DungeonConfig.ScriptedWallConfig wall : config.scriptedWalls) {
            if (wall == null || wall.blocks == null) continue;
            for (DungeonConfig.AreaPos pos : wall.blocks) {
                if (pos == null || !a.dimension.equals(pos.dimension)) continue;
                int x = pos.x - minX, y = pos.y - minY, z = pos.z - minZ;
                if (box.contains(x, y, z)) passable.set(box.index(x, y, z));
            }
        }

        BitSet course = new BitSet((int) cells);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        int seeds = 0;
        for (SpawnPointConfig point : coursePoints(config)) {
            if (point == null || !a.dimension.equals(point.dimension)) continue;
            int x = (int) Math.floor(point.x) - minX;
            int y = (int) Math.floor(point.y) - minY;
            int z = (int) Math.floor(point.z) - minZ;
            // A point is the feet of whoever stands there; rounded down it can fall in the floor.
            for (int dy = 0; dy <= 1; dy++) {
                if (!box.contains(x, y + dy, z)) continue;
                int i = box.index(x, y + dy, z);
                if (!passable.get(i)) continue;
                if (!course.get(i)) {
                    course.set(i);
                    queue.enqueue(i);
                    seeds++;
                }
                break;
            }
        }
        if (seeds == 0) throw new Refused("no_course_point");
        int courseCells = flood(box, passable, course, null, queue);

        // What the course does not reach but opens onto the outside of the area is not a hole.
        BitSet outside = new BitSet((int) cells);
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int[][] faces = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        for (int[] face : faces) {
            forEachFaceCell(box, face, (x, y, z) -> {
                int i = box.index(x, y, z);
                if (!passable.get(i) || course.get(i) || outside.get(i)) return;
                probe.set(minX + x + face[0], minY + y + face[1], minZ + z + face[2]);
                if (isPassable(level, probe, level.getBlockState(probe))) {
                    outside.set(i);
                    queue.enqueue(i);
                }
            });
        }
        int outsideCells = flood(box, passable, outside, course, queue);

        // Everything left is closed on every side.
        BitSet seen = new BitSet((int) cells);
        seen.or(course);
        seen.or(outside);
        List<Pocket> pockets = new ArrayList<>();
        LongArrayList fill = new LongArrayList();
        for (int start = passable.nextSetBit(0); start >= 0; start = passable.nextSetBit(start + 1)) {
            if (seen.get(start)) continue;
            IntArrayList members = new IntArrayList();
            seen.set(start);
            queue.enqueue(start);
            while (!queue.isEmpty()) {
                int i = queue.dequeueInt();
                members.add(i);
                forEachNeighbour(box, i, n -> {
                    if (passable.get(n) && !seen.get(n)) {
                        seen.set(n);
                        queue.enqueue(n);
                    }
                });
            }
            pockets.add(pocket(level, box, members, air, fill));
        }
        pockets.sort(Comparator.comparingInt(Pocket::cells).reversed());

        return new Scan(level, box.sx, box.sy, box.sz, seeds, courseCells, outsideCells, pockets, fill.toLongArray());
    }

    /**
     * Notes one hole, and the air of it that can take a barrier. Not the air an entity stands in:
     * a barrier suffocates whatever is inside it, and an armor stand or a mob shut in a display case
     * is there on purpose.
     */
    private static Pocket pocket(ServerLevel level, Box box, IntArrayList members, BitSet air, LongArrayList fill) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int first = Integer.MAX_VALUE;
        int airCount = 0;
        for (int k = 0; k < members.size(); k++) {
            int i = members.getInt(k);
            int x = box.x(i), y = box.y(i), z = box.z(i);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            first = Math.min(first, i);
            if (air.get(i)) airCount++;
        }
        AABB bounds = new AABB(box.minX + minX, box.minY + minY, box.minZ + minZ,
                box.minX + maxX + 1, box.minY + maxY + 1, box.minZ + maxZ + 1);
        List<AABB> occupied = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, bounds, e -> true)) {
            occupied.add(entity.getBoundingBox());
        }
        for (int k = 0; k < members.size(); k++) {
            int i = members.getInt(k);
            if (!air.get(i)) continue;
            int x = box.minX + box.x(i), y = box.minY + box.y(i), z = box.minZ + box.z(i);
            if (!occupied.isEmpty()) {
                AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
                boolean taken = false;
                for (AABB entityBox : occupied) {
                    if (entityBox.intersects(cell)) { taken = true; break; }
                }
                if (taken) continue;
            }
            fill.add(BlockPos.asLong(x, y, z));
        }
        BlockPos firstPos = new BlockPos(box.minX + box.x(first), box.minY + box.y(first), box.minZ + box.z(first));
        return new Pocket(firstPos, members.size(), airCount);
    }

    /** Spreads {@code reached} from the queue over passable cells that are not in {@code blocked}. */
    private static int flood(Box box, BitSet passable, BitSet reached, BitSet blocked, IntArrayFIFOQueue queue) {
        int count = 0;
        while (!queue.isEmpty()) {
            int i = queue.dequeueInt();
            count++;
            forEachNeighbour(box, i, n -> {
                if (!passable.get(n) || reached.get(n)) return;
                if (blocked != null && blocked.get(n)) return;
                reached.set(n);
                queue.enqueue(n);
            });
        }
        return count;
    }

    private static void forEachNeighbour(Box box, int i, IntConsumer action) {
        int x = box.x(i), y = box.y(i), z = box.z(i);
        if (x > 0) action.accept(i - 1);
        if (x < box.sx - 1) action.accept(i + 1);
        if (z > 0) action.accept(i - box.sx);
        if (z < box.sz - 1) action.accept(i + box.sx);
        int layer = box.sx * box.sz;
        if (y > 0) action.accept(i - layer);
        if (y < box.sy - 1) action.accept(i + layer);
    }

    private interface CellAction {
        void accept(int x, int y, int z);
    }

    private static void forEachFaceCell(Box box, int[] face, CellAction action) {
        int x0 = face[0] > 0 ? box.sx - 1 : 0, x1 = face[0] < 0 ? 0 : box.sx - 1;
        int y0 = face[1] > 0 ? box.sy - 1 : 0, y1 = face[1] < 0 ? 0 : box.sy - 1;
        int z0 = face[2] > 0 ? box.sz - 1 : 0, z1 = face[2] < 0 ? 0 : box.sz - 1;
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) action.accept(x, y, z);
            }
        }
    }

    /** Reads every block of the box once, a chunk at a time. */
    private static void readBlocks(ServerLevel level, Box box, BitSet passable, BitSet air) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maxX = box.minX + box.sx - 1, maxZ = box.minZ + box.sz - 1;
        for (int cx = box.minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                int fromX = Math.max(box.minX, cx << 4), toX = Math.min(maxX, (cx << 4) + 15);
                int fromZ = Math.max(box.minZ, cz << 4), toZ = Math.min(maxZ, (cz << 4) + 15);
                for (int y = 0; y < box.sy; y++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        for (int x = fromX; x <= toX; x++) {
                            pos.set(x, box.minY + y, z);
                            BlockState state = chunk.getBlockState(pos);
                            int i = box.index(x - box.minX, y, z - box.minZ);
                            if (state.isAir()) {
                                air.set(i);
                                passable.set(i);
                            } else if (isPassable(level, pos, state)) {
                                passable.set(i);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Anything but a full block lets a player through, or at least lets the course through. */
    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || !state.isCollisionShapeFullBlock(level, pos);
    }

    private static List<SpawnPointConfig> coursePoints(DungeonConfig config) {
        List<SpawnPointConfig> points = new ArrayList<>();
        points.add(config.spawnPoint);
        for (WaveConfig wave : config.waves) {
            if (wave == null || wave.mobs == null) continue;
            for (MobSpawnConfig mob : wave.mobs) {
                if (mob != null) points.add(mob.spawnPoint);
            }
        }
        for (BossConfig boss : config.bosses) {
            if (boss != null) points.add(boss.spawnPoint);
        }
        return points;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = dimension == null ? null : ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    // === Fill and undo ===

    /** Whether a fill or an undo is still running. */
    public static boolean busy() {
        return job != null;
    }

    /**
     * Writes the positions to the undo file, then starts putting barriers in them. Calls back with
     * the number of barriers placed once done.
     */
    public static void startFill(Scan scan, Path undoFile, IntConsumer done) throws IOException {
        if (busy()) throw new IllegalStateException("a fill is already running");
        String dimension = dimensionOf(scan.level);
        long[] previous = new long[0];
        if (Files.exists(undoFile)) {
            Undo saved = readUndo(undoFile);
            if (!saved.dimension.equals(dimension)) {
                throw new IOException("the undo file is for " + saved.dimension + ", undo it first");
            }
            previous = saved.positions;
        }
        long[] all = new long[previous.length + scan.fillPositions.length];
        System.arraycopy(previous, 0, all, 0, previous.length);
        System.arraycopy(scan.fillPositions, 0, all, previous.length, scan.fillPositions.length);
        writeUndo(undoFile, dimension, all);
        job = new Job(scan.level, scan.fillPositions, false, null, done);
    }

    /**
     * Starts taking away the barriers of the undo file. Returns false when there is nothing to undo.
     * Calls back with the number of barriers taken away once done.
     */
    public static boolean startUndo(MinecraftServer server, Path undoFile, IntConsumer done) throws IOException {
        if (busy()) throw new IllegalStateException("a fill is already running");
        if (!Files.exists(undoFile)) return false;
        Undo saved = readUndo(undoFile);
        ServerLevel level = level(server, saved.dimension);
        if (level == null) throw new IOException("unknown dimension " + saved.dimension);
        job = new Job(level, saved.positions, true, undoFile, done);
        return true;
    }

    /** Called every server tick: carries on with the running fill or undo. */
    public static void tick() {
        Job running = job;
        if (running == null) return;
        boolean finished;
        try {
            finished = running.step();
        } catch (RuntimeException e) {
            // Dropped rather than retried every tick: the undo file still holds every position.
            job = null;
            ArcadiaDungeon.LOGGER.error("A fill or undo of dungeon holes stopped after {} blocks", running.changed, e);
            return;
        }
        if (finished) {
            job = null;
            if (running.undoFile != null) {
                try {
                    Files.deleteIfExists(running.undoFile);
                } catch (IOException e) {
                    ArcadiaDungeon.LOGGER.warn("Could not delete the hole undo file {}", running.undoFile, e);
                }
            }
            ArcadiaDungeon.LOGGER.info("{} {} blocks of dungeon holes in {}", running.undo ? "Emptied" : "Filled",
                    running.changed, dimensionOf(running.level));
            running.done.accept(running.changed);
        }
    }

    private static final class Job {
        final ServerLevel level;
        final long[] positions;
        final boolean undo;
        final Path undoFile;
        final IntConsumer done;
        int next;
        int changed;

        Job(ServerLevel level, long[] positions, boolean undo, Path undoFile, IntConsumer done) {
            this.level = level;
            this.positions = positions;
            this.undo = undo;
            this.undoFile = undoFile;
            this.done = done;
        }

        /** Changes the next batch of blocks. Returns true once every position was handled. */
        boolean step() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int end = Math.min(positions.length, next + BLOCKS_PER_TICK);
            for (; next < end; next++) {
                pos.set(positions[next]);
                BlockState state = level.getBlockState(pos);
                if (undo) {
                    if (!state.is(Blocks.BARRIER)) continue;
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                } else {
                    // Someone may have built there since the scan.
                    if (!state.isAir()) continue;
                    level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), FLAGS);
                }
                changed++;
            }
            return next >= positions.length;
        }
    }

    private record Undo(String dimension, long[] positions) {}

    private static void writeUndo(Path file, String dimension, long[] positions) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(FILE_MAGIC);
            out.writeUTF(dimension);
            out.writeInt(positions.length);
            for (long position : positions) out.writeLong(position);
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Undo readUndo(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != FILE_MAGIC) throw new IOException("not an undo file of dungeon holes");
            String dimension = in.readUTF();
            int count = in.readInt();
            if (count < 0) throw new IOException("broken undo file");
            long[] positions = new long[count];
            for (int k = 0; k < count; k++) positions[k] = in.readLong();
            return new Undo(dimension, positions);
        }
    }
}
