package com.arcadia.dungeon.dungeon;

import com.arcadia.dungeon.config.SpawnPointConfig;
import com.arcadia.dungeon.config.DungeonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpawnSafety {

    private SpawnSafety() {}

    public static Vec3 findSafeSpawn(LivingEntity entity, double originX, double originY, double originZ, int horizontalRadius, int verticalRadius) {
        BlockPos origin = BlockPos.containing(originX, originY, originZ);
        Vec3 best = tryResolve(entity, origin);
        if (best != null) return best;

        for (int radius = 1; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                        best = tryResolve(entity, origin.offset(dx, dy, dz));
                        if (best != null) return best;
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

    public static void placeAtSafeSpawn(LivingEntity entity, SpawnPointConfig spawnPoint, double preferredX, double preferredY, double preferredZ, int horizontalRadius) {
        Vec3 safePos = findSafeSpawn(entity, preferredX, preferredY, preferredZ, horizontalRadius, 4);
        entity.setPos(safePos);
        configureManagedMob(entity);
    }

    public static void stabilizeEntityPosition(LivingEntity entity, double fallbackX, double fallbackY, double fallbackZ) {
        if (!entity.isAlive()) return;
        if (isSafePosition(entity, entity.position())) {
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

    private static Vec3 tryResolve(LivingEntity entity, BlockPos candidate) {
        for (int y = candidate.getY() + 3; y >= candidate.getY(); y--) {
            BlockPos feet = new BlockPos(candidate.getX(), y, candidate.getZ());
            Vec3 spawnPos = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (isSafePosition(entity, spawnPos)) {
                return spawnPos;
            }
        }
        return null;
    }

    private static boolean isSafePosition(LivingEntity entity, Vec3 pos) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos.x, pos.y, pos.z);
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.1, pos.z);
        var level = entity.level();
        return level.noCollision(entity, box)
                && !level.containsAnyLiquid(box)
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && hasEnoughOpenAccess(level, pos);
    }

    private static boolean hasEnoughOpenAccess(net.minecraft.world.level.Level level, Vec3 pos) {
        BlockPos feet = BlockPos.containing(pos.x, pos.y, pos.z);
        int openSides = 0;

        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos sideFeet = feet.relative(direction);
            BlockPos sideHead = sideFeet.above();
            BlockPos sideFloor = sideFeet.below();

            boolean sideOpen = level.getBlockState(sideFeet).isAir()
                    && level.getBlockState(sideHead).isAir()
                    && level.getBlockState(sideFloor).isFaceSturdy(level, sideFloor, Direction.UP);
            if (sideOpen) {
                openSides++;
            }
        }

        return openSides >= 2;
    }
}
