package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Where a dungeon puts the mobs it spawns.
 *
 * <p>The configured point comes first: somebody stood there and chose it. Only when it cannot hold
 * the mob is another spot looked for, and that search walks outward from the point through open
 * space, so it cannot come out on the far side of a wall or on the roof of the room. It used to look
 * at every block in a box around the point and to refuse carpet, slabs and paths as floors, so in a
 * carpeted room the nearest place it accepted was the roof, where nobody could reach the mob.</p>
 */
public final class SpawnSafety {

    /** How far a point in the air may be above what it lands on. */
    private static final int MAX_DROP = 3;
    /** Shapes up to this height are stepped over, as a mob does: carpet, slabs, paths. */
    private static final double STEP_HEIGHT = 0.6D;

    private SpawnSafety() {}

    public static Vec3 findSafeSpawn(LivingEntity entity, double originX, double originY, double originZ, int horizontalRadius, int verticalRadius) {
        loadAround(entity, originX, originZ, horizontalRadius);

        Vec3 exact = standAt(entity, originX, originY, originZ);
        if (exact != null) return exact;

        BlockPos origin = BlockPos.containing(originX, originY, originZ);
        Vec3 walked = walkFrom(entity, origin, horizontalRadius, verticalRadius);
        if (walked != null) return walked;

        // The point is inside a block, or walled in. There is no way to walk out of a wall, so this
        // takes the nearest open spot and accepts that it may be on another side of something.
        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                        BlockPos cell = origin.offset(dx, dy, dz);
                        if (blocksWalking(entity.level(), cell)) continue;
                        Vec3 spot = standOn(entity, cell);
                        if (spot != null) return spot;
                    }
                }
            }
        }

        return new Vec3(originX, originY, originZ);
    }

    public static Vec3 findSafeSpawnInArea(LivingEntity entity, DungeonConfig.AreaPos areaPos1, DungeonConfig.AreaPos areaPos2, double preferredX, double preferredY, double preferredZ, int horizontalRadius) {
        double clampedX = DungeonConfig.clampInsideX(areaPos1, areaPos2, preferredX);
        double clampedY = DungeonConfig.clampInsideY(areaPos1, areaPos2, preferredY);
        double clampedZ = DungeonConfig.clampInsideZ(areaPos1, areaPos2, preferredZ);
        return findSafeSpawn(entity, clampedX, clampedY, clampedZ, horizontalRadius, 4);
    }

    /**
     * Puts a mob at the preferred spot if it fits there, otherwise near the spawn point itself. A
     * wave spreads its mobs around the point, and one that lands in a wall has to start over from
     * the point: searching from inside the wall could bring it out on the other side.
     */
    public static void placeAtSafeSpawn(LivingEntity entity, SpawnPointConfig spawnPoint, double preferredX, double preferredY, double preferredZ, int horizontalRadius) {
        loadAround(entity, preferredX, preferredZ, horizontalRadius);
        Vec3 safePos = standAt(entity, preferredX, preferredY, preferredZ);
        if (safePos == null) {
            safePos = findSafeSpawn(entity, spawnPoint.x, spawnPoint.y, spawnPoint.z, horizontalRadius, 4);
        }
        entity.setPos(safePos);
        configureManagedMob(entity);
    }

    public static void stabilizeEntityPosition(LivingEntity entity, double fallbackX, double fallbackY, double fallbackZ) {
        if (!entity.isAlive()) return;
        if (isFree(entity, entity.position())) {
            configureManagedMob(entity);
            return;
        }

        Vec3 safePos = findSafeSpawn(entity, fallbackX, fallbackY, fallbackZ, 6, 4);
        entity.teleportTo(safePos.x, safePos.y, safePos.z);
        configureManagedMob(entity);
    }

    public static void configureManagedMob(LivingEntity entity) {
        if (entity.getType() == EntityType.VEX) {
            entity.noPhysics = false;
            entity.setNoGravity(false);
        }
    }

    /**
     * Holds and loads the chunks a search is going to read. In a chunk that is not loaded a block
     * collision check simply passes, so a search there could call a wall open. Holding comes first,
     * so the chunk is still there when the mob arrives instead of unloading a few ticks later.
     */
    private static void loadAround(LivingEntity entity, double x, double z, int horizontalRadius) {
        Level level = entity.level();
        int reach = horizontalRadius + Mth.ceil(entity.getBbWidth());
        int minX = Mth.floor(x - reach) >> 4;
        int maxX = Mth.floor(x + reach) >> 4;
        int minZ = Mth.floor(z - reach) >> 4;
        int maxZ = Mth.floor(z + reach) >> 4;
        if (level instanceof ServerLevel serverLevel) {
            ManagedEntities.hold(serverLevel, new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4));
        }
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    /** The configured coordinates themselves, dropped to what is under them, if the mob fits there. */
    private static Vec3 standAt(LivingEntity entity, double x, double y, double z) {
        Level level = entity.level();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(y);
        for (int blockY = startY; blockY >= startY - MAX_DROP; blockY--) {
            BlockPos pos = new BlockPos(blockX, blockY, blockZ);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = blockY + shape.max(Direction.Axis.Y);
            // A block reaching well above the point means the point is inside it.
            if (top > y + STEP_HEIGHT) return null;
            Vec3 spot = new Vec3(x, top, z);
            return isFree(entity, spot) ? spot : null;
        }
        return null;
    }

    /** The nearest spot with room around it reachable through open space, or a cramped one if none. */
    private static Vec3 walkFrom(LivingEntity entity, BlockPos origin, int horizontalRadius, int verticalRadius) {
        Level level = entity.level();
        if (blocksWalking(level, origin)) return null;

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(origin);
        seen.add(origin);
        Vec3 cramped = null;

        while (!open.isEmpty()) {
            BlockPos cell = open.poll();
            Vec3 spot = standOn(entity, cell);
            if (spot != null) {
                if (hasEnoughOpenAccess(level, cell)) return spot;
                if (cramped == null) cramped = spot;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = cell.relative(direction);
                if (Math.abs(next.getX() - origin.getX()) > horizontalRadius) continue;
                if (Math.abs(next.getZ() - origin.getZ()) > horizontalRadius) continue;
                if (Math.abs(next.getY() - origin.getY()) > verticalRadius) continue;
                if (blocksWalking(level, next) || !seen.add(next)) continue;
                open.add(next);
            }
        }
        return cramped;
    }

    /** The center of this block, at the height of what is underfoot there, if the mob fits. */
    private static Vec3 standOn(LivingEntity entity, BlockPos cell) {
        Double ground = groundHeight(entity.level(), cell);
        if (ground == null) return null;
        Vec3 spot = new Vec3(cell.getX() + 0.5D, ground, cell.getZ() + 0.5D);
        return isFree(entity, spot) ? spot : null;
    }

    private static Double groundHeight(Level level, BlockPos cell) {
        VoxelShape here = level.getBlockState(cell).getCollisionShape(level, cell);
        if (!here.isEmpty()) return cell.getY() + here.max(Direction.Axis.Y);
        BlockPos below = cell.below();
        VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
        return under.isEmpty() ? null : below.getY() + under.max(Direction.Axis.Y);
    }

    private static boolean blocksWalking(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > STEP_HEIGHT;
    }

    private static boolean isFree(LivingEntity entity, Vec3 pos) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos.x, pos.y, pos.z);
        Level level = entity.level();
        return level.noCollision(entity, box) && !level.containsAnyLiquid(box);
    }

    /** At least two sides a mob could walk off to, so it does not start the fight in a niche. */
    private static boolean hasEnoughOpenAccess(Level level, BlockPos feet) {
        int openSides = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            if (!blocksWalking(level, side) && !blocksWalking(level, side.above())
                    && groundHeight(level, side) != null) {
                openSides++;
            }
        }
        return openSides >= 2;
    }
}
