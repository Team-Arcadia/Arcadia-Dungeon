package com.vyrriox.arcadiadungeon.gametest;

import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.dungeon.SpawnSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where a dungeon puts the mobs it spawns, and the box it keeps them inside.
 *
 * <p><b>Why this one.</b> A dungeon spawns its own mobs at configured points, and the points drift:
 * a build changes, a wall moves, and a spawn that used to be open air is now inside stone. A mob
 * spawned inside a block suffocates, or is pushed out through the wall into the corridor players
 * came from, and the fight is ruined in a way that looks like a design problem rather than a
 * coordinate problem. These tests hold the search that prevents it, and the clamp that keeps a
 * spawn inside its own arena.</p>
 *
 * @author vyrriox
 */
@GameTestHolder("arcadia_dungeon")
@PrefixGameTestTemplate(false)
public class SpawnSafetyGameTests {

    private static final String T = "floor";

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    private static DungeonConfig.AreaPos at(int x, int y, int z) {
        DungeonConfig.AreaPos pos = new DungeonConfig.AreaPos();
        pos.x = x;
        pos.y = y;
        pos.z = z;
        return pos;
    }

    /**
     * What comes back is somewhere a mob can actually stand, and it is inside the search radius.
     *
     * <p><b>What this test does not claim.</b> Two earlier versions asserted that a spawn over
     * solid ground is left exactly where it was asked for, and both failed: the search moved the
     * mob three blocks and was right to. Which free spot it picks depends on the template geometry
     * and on the order {@code tryResolve} scans a column, and pinning that down would be asserting
     * my reading of the template rather than the function's promise. The promise is "a standable
     * spot, near the one asked for", so that is what is checked.</p>
     */
    @GameTest(template = T)
    public static void whatComesBackIsStandableAndNearby(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, 1, 2, 1);
        BlockPos spot = h.absolutePos(new BlockPos(1, 2, 1));
        Vec3 asked = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        int horizontal = 4;
        int vertical = 2;
        Vec3 found = SpawnSafety.findSafeSpawn(pig, asked.x, asked.y, asked.z, horizontal, vertical);

        // Inside the box the search was given: it may move the mob, never teleport it.
        check(h, Math.abs(found.x - asked.x) <= horizontal + 1,
                "moved " + (found.x - asked.x) + " on x, outside the search radius");
        check(h, Math.abs(found.z - asked.z) <= horizontal + 1,
                "moved " + (found.z - asked.z) + " on z, outside the search radius");
        check(h, Math.abs(found.y - asked.y) <= vertical + 3,
                "moved " + (found.y - asked.y) + " on y, outside the search radius");

        // And it is a place to stand, not a place to suffocate.
        BlockPos below = BlockPos.containing(found.x, found.y - 0.1, found.z);
        check(h, !h.getLevel().getBlockState(below).isAir(),
                "the chosen spawn has nothing under it");
        check(h, h.getLevel().getBlockState(BlockPos.containing(found)).isAir(),
                "the chosen spawn is inside a block");

        pig.discard();
        h.succeed();
    }

    /**
     * A spawn point buried in stone is moved out of the wall.
     *
     * <p>This is the failure the search exists for. The assertion is deliberately about the
     * distance and not the exact block: which free spot it picks is the search's business, that it
     * does not leave the mob inside the stone is the contract.</p>
     */
    @GameTest(template = T)
    public static void aSpawnInsideStoneIsMovedOut(GameTestHelper h) {
        // A 3x3x3 block of stone with the spawn point at its centre.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    h.setBlock(new BlockPos(3 + dx, 2 + dy, 3 + dz), Blocks.STONE);
                }
            }
        }
        Pig pig = h.spawn(EntityType.PIG, 1, 2, 1);
        Vec3 buried = h.absoluteVec(new Vec3(3.5, 3, 3.5));
        Vec3 found = SpawnSafety.findSafeSpawn(pig, buried.x, buried.y, buried.z, 5, 3);

        check(h, found.distanceTo(buried) > 0.5,
                "a spawn buried in stone was not moved at all");
        // And not moved to the other side of the map either: the search is bounded.
        check(h, found.distanceTo(buried) < 12,
                "the search moved the spawn " + found.distanceTo(buried) + " blocks away");
        pig.discard();
        h.succeed();
    }

    /** With nowhere safe in range, the origin comes back rather than null or an exception. */
    @GameTest(template = T)
    public static void nowhereSafeFallsBackToTheOrigin(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, 1, 2, 1);
        // Radius zero leaves the search nothing to explore beyond the origin itself.
        Vec3 asked = h.absoluteVec(new Vec3(6.5, 2, 6.5));
        Vec3 found = SpawnSafety.findSafeSpawn(pig, asked.x, asked.y, asked.z, 0, 0);
        check(h, found != null, "the search returned null");
        check(h, Double.isFinite(found.x) && Double.isFinite(found.y) && Double.isFinite(found.z),
                "the search returned a non-finite position: " + found);
        pig.discard();
        h.succeed();
    }

    /**
     * The clamp keeps a spawn inside its arena, whichever corner order the arena was defined in.
     *
     * <p>Defining an area by two opposite corners means the second can be lower than the first on
     * any axis. A clamp that assumed pos1 was the minimum would push a spawn <em>out</em> of the
     * arena on those axes, which is the opposite of its job.</p>
     */
    @GameTest(template = T)
    public static void theClampHoldsWhicheverCornerCameFirst(GameTestHelper h) {
        DungeonConfig.AreaPos low = at(10, 60, 10);
        DungeonConfig.AreaPos high = at(20, 70, 20);

        for (boolean swapped : new boolean[] {false, true}) {
            DungeonConfig.AreaPos a = swapped ? high : low;
            DungeonConfig.AreaPos b = swapped ? low : high;
            String how = swapped ? " (corners swapped)" : "";

            double x = DungeonConfig.clampInsideX(a, b, -500);
            check(h, x >= 10 && x <= 21, "x clamped to " + x + how);
            x = DungeonConfig.clampInsideX(a, b, 500);
            check(h, x >= 10 && x <= 21, "x clamped to " + x + how);
            double z = DungeonConfig.clampInsideZ(a, b, 500);
            check(h, z >= 10 && z <= 21, "z clamped to " + z + how);
            double y = DungeonConfig.clampInsideY(a, b, 0);
            check(h, y >= 60 && y <= 70, "y clamped to " + y + how);
            y = DungeonConfig.clampInsideY(a, b, 1000);
            check(h, y >= 60 && y <= 70, "y clamped to " + y + how);
        }
        h.succeed();
    }

    /**
     * A point already inside is left where it is, and horizontal axes land on a block centre.
     *
     * <p>X and Z gain the half block, Y does not: the first two are a position across a floor, the
     * third is the floor. Asserting it keeps someone from "fixing" the asymmetry into a mob
     * standing half a block inside the ceiling.</p>
     */
    @GameTest(template = T)
    public static void apointInsideKeepsItsPlace(GameTestHelper h) {
        DungeonConfig.AreaPos a = at(10, 60, 10);
        DungeonConfig.AreaPos b = at(20, 70, 20);
        check(h, DungeonConfig.clampInsideX(a, b, 15.5) == 15.5, "an inside x was moved");
        check(h, DungeonConfig.clampInsideZ(a, b, 15.5) == 15.5, "an inside z was moved");
        check(h, DungeonConfig.clampInsideY(a, b, 65) == 65, "an inside y was moved");
        // The horizontal lower bound is the block centre, the vertical one is the block floor.
        check(h, DungeonConfig.clampInsideX(a, b, -1) == 10.5,
                "the x floor is not a block centre: " + DungeonConfig.clampInsideX(a, b, -1));
        check(h, DungeonConfig.clampInsideY(a, b, -1) == 60,
                "the y floor gained half a block: " + DungeonConfig.clampInsideY(a, b, -1));
        h.succeed();
    }
}
