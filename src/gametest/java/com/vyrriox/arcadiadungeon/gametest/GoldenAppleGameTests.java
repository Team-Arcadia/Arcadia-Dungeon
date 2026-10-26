package com.vyrriox.arcadiadungeon.gametest;

import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * A golden apple eaten inside a dungeon, and the permission it opens for its own effects.
 *
 * <p><b>Why this one.</b> A dungeon refuses beneficial effects a player did not earn, and a golden
 * apple is the one food allowed through. The permission used to be written when eating
 * <em>finished</em>, which NeoForge reports after the apple has already applied its effects, so
 * the effects were refused first and permitted afterwards: eating a golden apple in a dungeon did
 * nothing. The same code also kept the permission open for the length of the effect, up to two
 * minutes for Absorption, so a cancelled bite let the same buff in from anywhere else. These tests
 * eat a real apple through the real use path and hold both halves of the fix.</p>
 *
 * <p><b>How a player is put in a dungeon.</b> Entering one for real needs a configured dungeon,
 * a spawn point and a teleport. The handler only asks {@code getPlayerDungeon}, so the tests
 * register an empty instance, held in recruitment, directly in the manager's maps and remove it
 * when they are done.</p>
 *
 * @author vyrriox
 */
@GameTestHolder("arcadia_dungeon")
@PrefixGameTestTemplate(false)
public class GoldenAppleGameTests {

    private static final String T = "floor";

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<Object, V> managerMap(String name) {
        try {
            Field f = DungeonManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return (Map<Object, V>) f.get(DungeonManager.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("DungeonManager." + name + " is not reachable", e);
        }
    }

    /** Puts the player in a fresh, empty dungeon instance and returns its id for cleanup. */
    private static String enterDungeon(GameTestHelper h, ServerPlayer player) {
        String id = "gametest_apple_" + UUID.randomUUID();
        DungeonConfig config = new DungeonConfig(id, id);
        // An empty dungeon that has started has no boss left to beat and completes on the next
        // tick, which clears every permission. Held in a ten minute recruitment, it stays open.
        config.recruitmentDurationSeconds = 600;
        DungeonInstance instance = new DungeonInstance(config, h.getLevel().getServer());
        instance.startRecruitment();
        instance.addPlayer(player);
        GoldenAppleGameTests.<DungeonInstance>managerMap("activeInstances").put(id, instance);
        GoldenAppleGameTests.<String>managerMap("playerToDungeon").put(player.getUUID(), id);
        check(h, DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) != null,
                "the test could not put the player in a dungeon");
        return id;
    }

    private static void leave(ServerPlayer player, String dungeonId) {
        if (dungeonId != null) {
            managerMap("activeInstances").remove(dungeonId);
            managerMap("playerToDungeon").remove(player.getUUID());
        }
        DungeonManager.getInstance().clearAllowedBeneficialEffects(player.getUUID());
        player.discard();
    }

    private static ServerPlayer player(GameTestHelper h) {
        // A FakePlayer rather than the mock player: the mock one joins the player list, and
        // arcadia_lib then sends it a payload its test channel never negotiated.
        ServerPlayer player = new FakePlayer(h.getLevel(),
                new GameProfile(UUID.randomUUID(), "apple_tester"));
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        return player;
    }

    /**
     * Eats a golden apple through the vanilla path: {@code startUsingItem} posts the Start event,
     * {@code completeUsingItem} applies the food and its effects, then posts the Finish event. The
     * second one is protected, and the fake player is not ticked by a connection, so it is called
     * directly instead of waiting thirty two ticks for an update that never comes.
     */
    private static void eatGoldenApple(ServerPlayer player) {
        player.getFoodData().setFoodLevel(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        try {
            Method complete = LivingEntity.class.getDeclaredMethod("completeUsingItem");
            complete.setAccessible(true);
            complete.invoke(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not finish eating", e);
        }
    }

    /**
     * Assertion 1: a player in a dungeon who eats a golden apple gets Regeneration II and
     * Absorption. Against the old {@code Finish} handler both are refused and this fails.
     */
    @GameTest(template = T)
    public static void aGoldenAppleInADungeonGivesItsEffects(GameTestHelper h) {
        ServerPlayer player = player(h);
        String dungeon = enterDungeon(h, player);
        try {
            eatGoldenApple(player);
            MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
            MobEffectInstance absorption = player.getEffect(MobEffects.ABSORPTION);
            check(h, regen != null && regen.getAmplifier() == 1,
                    "Regeneration II did not land on a player who ate a golden apple in a dungeon: " + regen);
            check(h, absorption != null && absorption.getAmplifier() == 0,
                    "Absorption did not land on a player who ate a golden apple in a dungeon: " + absorption);
        } finally {
            leave(player, dungeon);
        }
        h.succeed();
    }

    /**
     * Assertion 2: the permission lasts the eating window, not the effect. Ten seconds after a
     * bite, the same Absorption arriving from somewhere else is refused. The permission is held in
     * wall clock time (sixty ticks plus a three second margin, so six seconds) and the test waits
     * ten seconds of wall clock, while the old behaviour kept it open two minutes: the margin is
     * wide on both sides.
     */
    @GameTest(template = T, timeoutTicks = 1_000_000)
    public static void thePermissionClosesWithTheEatingWindow(GameTestHelper h) {
        ServerPlayer player = player(h);
        String dungeon = enterDungeon(h, player);
        long bite = System.currentTimeMillis();
        player.getFoodData().setFoodLevel(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
        // Only the start of the bite: the player stops eating, as a cancelled bite does.
        player.startUsingItem(InteractionHand.MAIN_HAND);
        player.stopUsingItem();
        MobEffectInstance probe = new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0);
        if (!DungeonManager.getInstance().isBeneficialEffectAllowed(player, probe)) {
            leave(player, dungeon);
            h.fail("starting to eat a golden apple opened no permission at all");
            return;
        }
        afterWallClock(h, bite + 10_000L, () -> {
            try {
                long waited = System.currentTimeMillis() - bite;
                // If the dungeon ended during the wait, its end cleared every permission and the
                // refusal below would prove nothing, so that case fails instead of passing.
                check(h, DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) != null,
                        "the player left the dungeon during the wait, so the window was not measured");
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
                check(h, player.getEffect(MobEffects.ABSORPTION) == null,
                        "Absorption from another source still landed " + waited
                                + " ms after a cancelled bite: the permission outlived the eating window");
            } finally {
                leave(player, dungeon);
            }
            h.succeed();
        });
    }

    /**
     * Runs the action on the first tick after a wall clock instant. The test server does not pace
     * its ticks (a first version waited two hundred ticks and measured 87 ms), and the permission
     * is held in milliseconds, so the wait has to be measured in milliseconds too.
     */
    private static void afterWallClock(GameTestHelper h, long instant, Runnable action) {
        h.runAfterDelay(1, () -> {
            if (System.currentTimeMillis() >= instant) action.run();
            else afterWallClock(h, instant, action);
        });
    }

    /**
     * Assertion 3: outside a dungeon nothing of this applies. The apple works, an unrelated buff
     * works, and no permission is recorded for the player.
     */
    @GameTest(template = T)
    public static void outsideADungeonNothingChanges(GameTestHelper h) {
        ServerPlayer player = player(h);
        try {
            check(h, DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) == null,
                    "a fresh player is already in a dungeon");
            eatGoldenApple(player);
            check(h, player.getEffect(MobEffects.REGENERATION) != null,
                    "Regeneration from a golden apple did not land outside a dungeon");
            check(h, player.getEffect(MobEffects.ABSORPTION) != null,
                    "Absorption from a golden apple did not land outside a dungeon");
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            check(h, player.getEffect(MobEffects.MOVEMENT_SPEED) != null,
                    "an unrelated buff was refused outside a dungeon");
            check(h, !managerMap("allowedBeneficialEffects").containsKey(player.getUUID()),
                    "a permission was recorded for a player who is not in a dungeon");
        } finally {
            leave(player, null);
        }
        h.succeed();
    }
}
