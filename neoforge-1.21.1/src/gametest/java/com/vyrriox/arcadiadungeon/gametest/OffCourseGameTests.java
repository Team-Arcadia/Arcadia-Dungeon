package com.vyrriox.arcadiadungeon.gametest;

import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.DungeonSettings;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.dungeon.CourseHoles;
import com.vyrriox.arcadiadungeon.dungeon.RunItemLock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A player thrown off the course: the chorus totem that did it, and the holes it threw them into.
 *
 * <p>The chorus totem of Artifacts is not in the test world. What these tests hold is the lock it
 * gives way to: Artifacts skips a totem whose item is cooling down, and so does the game for an item
 * used by hand, which is checked here with the chorus fruit.</p>
 *
 * <p>The holes are checked on a small building in the sky: a room with the dungeon spawn, a second
 * room behind a door, two closed cavities in the ceiling, one of them holding an armor stand, and
 * open air all around inside the area.</p>
 *
 * @author vyrriox
 */
@GameTestHolder("arcadia_dungeon")
@PrefixGameTestTemplate(false)
public class OffCourseGameTests {

    private static final String T = "floor";
    private static final int SITE_Y = 200;
    private static final AtomicInteger NEXT_SITE = new AtomicInteger(1);

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    // === Locked items ===

    /** A chorus fruit cannot be eaten during a run, and can again as soon as the run lets go. */
    @GameTest(template = T)
    public static void aLockedItemCannotBeUsedDuringARun(GameTestHelper h) {
        ServerPlayer player = RunTestKit.fakePlayer(h, "lock-test");
        player.setGameMode(GameType.SURVIVAL);
        try {
            List<Item> items = RunItemLock.resolve(new DungeonSettings().lockedItems);
            RunItemLock.lock(player, items);
            check(h, player.getCooldowns().isOnCooldown(Items.CHORUS_FRUIT),
                    "a chorus fruit held during a run is not cooling down");

            ItemStack fruit = new ItemStack(Items.CHORUS_FRUIT);
            player.setItemInHand(InteractionHand.MAIN_HAND, fruit);
            InteractionResult locked = player.gameMode.useItem(player, h.getLevel(), fruit, InteractionHand.MAIN_HAND);
            check(h, !player.isUsingItem(), "a locked chorus fruit could still be eaten: " + locked);

            RunItemLock.release(player);
            check(h, !player.getCooldowns().isOnCooldown(Items.CHORUS_FRUIT),
                    "the chorus fruit is still locked after the run let go of the player");
            player.gameMode.useItem(player, h.getLevel(), fruit, InteractionHand.MAIN_HAND);
            check(h, player.isUsingItem(), "a chorus fruit could not be eaten once the run let go");
        } finally {
            RunItemLock.release(player);
            player.discard();
        }
        h.succeed();
    }

    /** The totem is locked by default, and an item of a mod that is not installed is skipped quietly. */
    @GameTest(template = T)
    public static void theChorusTotemIsLockedByDefault(GameTestHelper h) {
        List<String> defaults = new DungeonSettings().lockedItems;
        check(h, defaults.contains("artifacts:chorus_totem"), "the chorus totem is not locked by default: " + defaults);
        check(h, defaults.contains("minecraft:chorus_fruit"), "the chorus fruit is not locked by default: " + defaults);

        List<Item> items = RunItemLock.resolve(List.of("artifacts:chorus_totem", "minecraft:chorus_fruit", "Not An Id!", ""));
        check(h, items.equals(List.of(Items.CHORUS_FRUIT)),
                "only the chorus fruit exists in this world, yet the lock resolved " + items);
        h.succeed();
    }

    // === Holes ===

    private record Site(BlockPos center, DungeonConfig config) {}

    /**
     * The building: solid stone from -8 to +8 around the center, from y-4 to y+6. Room A (-6..-1)
     * holds the spawn, an oak door at x 0 leads to room B (+1..+6), both three high. Above their
     * ceiling, two closed cavities of 3 x 3 x 3: one empty, one with an armor stand. The area goes
     * two blocks past the stone on every side, so the building stands in open air.
     */
    private static Site buildSite(GameTestHelper h, BlockPos c) {
        ServerLevel level = h.getLevel();
        fill(level, c.offset(-10, -6, -10), c.offset(10, 8, 10), Blocks.AIR);
        fill(level, c.offset(-8, -4, -8), c.offset(8, 6, 8), Blocks.STONE);
        fill(level, c.offset(-6, -2, -2), c.offset(-1, 0, 2), Blocks.AIR);
        fill(level, c.offset(1, -2, -2), c.offset(6, 0, 2), Blocks.AIR);
        level.setBlock(c.offset(0, -2, 0), Blocks.OAK_DOOR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(c.offset(0, -1, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        fill(level, c.offset(-5, 2, -1), c.offset(-3, 4, 1), Blocks.AIR);
        fill(level, c.offset(2, 2, -1), c.offset(4, 4, 1), Blocks.AIR);

        String id = "gametest_holes_" + UUID.randomUUID().toString().substring(0, 8);
        DungeonConfig config = new DungeonConfig(id, id);
        String dimension = RunTestKit.dimension(h);
        config.spawnPoint = new SpawnPointConfig(dimension, c.getX() - 4 + 0.5, c.getY() - 2, c.getZ() + 0.5, 0, 0);
        config.areaPos1 = new DungeonConfig.AreaPos(dimension, c.getX() - 10, c.getY() - 6, c.getZ() - 10);
        config.areaPos2 = new DungeonConfig.AreaPos(dimension, c.getX() + 10, c.getY() + 8, c.getZ() + 10);
        return new Site(c, config);
    }

    private static ArmorStand standIn(GameTestHelper h, BlockPos c) {
        ArmorStand stand = RunTestKit.create(h, EntityType.ARMOR_STAND);
        stand.setPos(c.getX() + 3.5, c.getY() + 2, c.getZ() + 0.5);
        h.getLevel().addFreshEntity(stand);
        return stand;
    }

    /** A spot of its own for each test, far from the others and in the middle of a chunk. */
    private static BlockPos newCenter() {
        return new BlockPos(-8008 - NEXT_SITE.getAndIncrement() * 4096, SITE_Y, -24008);
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Runs the test once the chunk of the building is in the world, entities included. */
    private static void whenLoaded(GameTestHelper h, BlockPos pos, Runnable test) {
        ServerLevel level = h.getLevel();
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!level.isPositionEntityTicking(pos)) throw RunTestKit.waiting(h, "the chunk is loading");
                })
                .thenExecute(test);
    }

    private static void release(GameTestHelper h, BlockPos pos) {
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
    }

    /**
     * The scan finds both cavities and nothing else: the room behind the door is course, the air
     * around the building is outside, and the blocks an armor stand stands in are left alone.
     */
    @GameTest(template = T, timeoutTicks = 2000)
    public static void theClosedHolesOffTheCourseAreFound(GameTestHelper h) {
        BlockPos center = newCenter();
        whenLoaded(h, center, () -> {
            Site site = buildSite(h, center);
            ArmorStand stand = standIn(h, site.center());
            try {
                CourseHoles.Scan scan = CourseHoles.scan(h.getLevel().getServer(), site.config());
                check(h, scan.seeds == 1, "the spawn should be the one course point, got " + scan.seeds);
                // Two rooms of 6 x 3 x 5 and the two blocks of the door between them.
                check(h, scan.courseCells == 182, "the course should be both rooms and the door, 182 blocks, got " + scan.courseCells);
                check(h, scan.outsideCells > 0, "the open air around the building was not taken for the outside");
                check(h, scan.pockets.size() == 2, "two closed holes were expected, got " + scan.pockets.size());
                for (CourseHoles.Pocket pocket : scan.pockets) {
                    check(h, pocket.cells() == 27 && pocket.air() == 27, "a hole should be 27 blocks of air, got " + pocket);
                }
                check(h, scan.fillable == 52, "54 blocks of holes less the 2 the armor stand stands in should be fillable, got " + scan.fillable);
            } catch (CourseHoles.Refused refused) {
                h.fail("the scan refused: " + refused.reason);
            } finally {
                stand.discard();
                release(h, center);
            }
            h.succeed();
        });
    }

    /** What the scan cannot work with is refused, not guessed. */
    @GameTest(template = T)
    public static void aScanWithoutAreaOrCoursePointIsRefused(GameTestHelper h) {
        DungeonConfig noArea = new DungeonConfig("gametest_holes_noarea", "no area");
        check(h, refusal(h, noArea).equals("no_area"), "a dungeon without area was not refused for it");

        DungeonConfig farSpawn = new DungeonConfig("gametest_holes_farspawn", "far spawn");
        String dimension = RunTestKit.dimension(h);
        farSpawn.areaPos1 = new DungeonConfig.AreaPos(dimension, -40008, SITE_Y, -40008);
        farSpawn.areaPos2 = new DungeonConfig.AreaPos(dimension, -40004, SITE_Y + 4, -40004);
        farSpawn.spawnPoint = new SpawnPointConfig(dimension, 0.5, SITE_Y, 0.5, 0, 0);
        check(h, refusal(h, farSpawn).equals("no_course_point"),
                "an area holding no point of its dungeon was not refused for it");
        h.succeed();
    }

    private static String refusal(GameTestHelper h, DungeonConfig config) {
        try {
            CourseHoles.scan(h.getLevel().getServer(), config);
            return "none";
        } catch (CourseHoles.Refused refused) {
            return refused.reason;
        }
    }

    /**
     * The commands: fill puts barriers in the holes and nowhere else, undo takes them all away and
     * deletes its file.
     */
    @GameTest(template = T, timeoutTicks = 4000)
    public static void holesAreFilledAndUndoneByTheCommands(GameTestHelper h) {
        BlockPos center = newCenter();
        ServerLevel level = h.getLevel();
        Site[] site = new Site[1];
        ArmorStand[] stand = new ArmorStand[1];
        Path[] undoFile = new Path[1];
        level.setChunkForced(center.getX() >> 4, center.getZ() >> 4, true);
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!level.isPositionEntityTicking(center)) throw RunTestKit.waiting(h, "the chunk is loading");
                })
                .thenExecute(() -> {
                    site[0] = buildSite(h, center);
                    stand[0] = standIn(h, site[0].center());
                    ConfigManager.getInstance().saveDungeon(site[0].config());
                    undoFile[0] = ConfigManager.getInstance().getConfigDir().resolve("holes").resolve(site[0].config().id + ".bin");
                    check(h, run(h, "arcadia_dungeon admin holes fill " + site[0].config().id) == 1, "the fill command failed");
                })
                .thenWaitUntil(() -> {
                    if (CourseHoles.busy()) throw RunTestKit.waiting(h, "the fill is running");
                })
                .thenExecute(() -> {
                    BlockPos c = site[0].center();
                    check(h, level.getBlockState(c.offset(-4, 3, 0)).is(Blocks.BARRIER), "the empty hole was not filled");
                    check(h, level.getBlockState(c.offset(2, 4, 1)).is(Blocks.BARRIER), "the hole of the armor stand was not filled around it");
                    check(h, level.getBlockState(c.offset(3, 2, 0)).isAir(), "a barrier was put inside the armor stand");
                    check(h, level.getBlockState(c.offset(-4, -2, 0)).isAir(), "a barrier was put on the course");
                    check(h, level.getBlockState(c.offset(4, -1, 2)).isAir(), "a barrier was put in the room behind the door");
                    check(h, level.getBlockState(c.offset(9, 0, 0)).isAir(), "a barrier was put in the open air outside");
                    check(h, Files.exists(undoFile[0]), "the fill wrote no undo file");
                    check(h, run(h, "arcadia_dungeon admin holes undo " + site[0].config().id) == 1, "the undo command failed");
                })
                .thenWaitUntil(() -> {
                    if (CourseHoles.busy()) throw RunTestKit.waiting(h, "the undo is running");
                })
                .thenExecute(() -> {
                    BlockPos c = site[0].center();
                    check(h, level.getBlockState(c.offset(-4, 3, 0)).isAir(), "the undo left a barrier in the empty hole");
                    check(h, level.getBlockState(c.offset(2, 4, 1)).isAir(), "the undo left a barrier in the hole of the armor stand");
                    check(h, !Files.exists(undoFile[0]), "the undo file is still there after the undo");
                    check(h, run(h, "arcadia_dungeon admin holes undo " + site[0].config().id) == 0, "a second undo found something to undo");
                    stand[0].discard();
                    ConfigManager.getInstance().deleteDungeon(site[0].config().id);
                    level.setChunkForced(center.getX() >> 4, center.getZ() >> 4, false);
                })
                .thenSucceed();
    }

    private static int run(GameTestHelper h, String command) {
        CommandSourceStack source = h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            h.fail("the command did not parse: " + command + ": " + e.getMessage());
            return -1;
        }
    }
}
