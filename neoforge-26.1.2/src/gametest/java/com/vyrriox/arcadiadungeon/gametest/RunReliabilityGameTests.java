package com.vyrriox.arcadiadungeon.gametest;

import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.boss.BossManager;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.DungeonValidator;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.config.RewardConfig;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.config.SummonConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.MobRescue;
import com.vyrriox.arcadiadungeon.dungeon.SpawnSafety;
import com.vyrriox.arcadiadungeon.dungeon.WaveInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A whole run on a real server: mobs spawning, chunks unloading, bosses being hit.
 *
 * <p><b>Why this one.</b> Players reported bosses that could not be hit and waves that never
 * appeared. Both come from the same place: a run followed its mobs by a Java reference, and the game
 * replaces that object whenever a chunk unloads, which happens as soon as nobody is near, typically
 * when a player dies and respawns at the entrance. These tests stand a mock player next to the
 * dungeon's entrance and put the fight thousands of blocks away, where only the dungeon itself can
 * hold the chunk.</p>
 *
 * <p><b>Time.</b> A run checks its waves on the wall clock and this server ticks as fast as it can,
 * so the budgets are generous tick counts and the waits are in milliseconds. Each test gets its own
 * stretch of the flat world so their areas never touch.</p>
 *
 * @author vyrriox
 */
public class RunReliabilityGameTests {

    private static final String T = "floor";
    private static final String BATCH = "reliability";
    private static final int BUDGET = 4_000_000;
    /**
     * What these tests fight when the mob has to stay alive for a while. The flat world has villages,
     * and their golems kill monsters, which reads exactly like the bug these tests look for.
     */
    private static final String HARMLESS = "minecraft:pig";
    /** Far past any view distance, so nothing but the dungeon holds the chunk there. */
    private static final int FAR = 2048;

    private static final AtomicInteger NEXT_SITE = new AtomicInteger(1);

    /** While above zero, a listener of the kit refuses rabbits, the way a spawn-control mod would. */
    private static final AtomicInteger REFUSING_RABBITS = new AtomicInteger();
    private static boolean listenerRegistered = false;

    private static synchronized void registerListener() {
        if (listenerRegistered) return;
        listenerRegistered = true;
        RunTestKit.refuseWhile(EntityType.RABBIT, () -> REFUSING_RABBITS.get() > 0);
    }

    // === Set up ===

    private record Site(BlockPos entrance, BlockPos fight) {}

    /** A patch of the flat world for this test alone: an entrance, and a fight far away from it. */
    private static Site newSite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int x = 8000 + NEXT_SITE.getAndIncrement() * 8192;
        int z = 8000;
        // getHeight answers the bottom of the world for a chunk that is not loaded.
        level.getChunk(x >> 4, z >> 4);
        level.getChunk((x + FAR) >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int fightY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + FAR, z);
        return new Site(new BlockPos(x, y, z), new BlockPos(x + FAR, fightY, z));
    }

    private static SpawnPointConfig point(GameTestHelper h, BlockPos pos) {
        return new SpawnPointConfig(RunTestKit.dimension(h),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
    }

    private static DungeonConfig dungeon(GameTestHelper h, Site site) {
        String id = "gametest_run_" + UUID.randomUUID().toString().substring(0, 8);
        DungeonConfig config = new DungeonConfig(id, id);
        config.recruitmentDurationSeconds = 0;
        config.cooldownSeconds = 0;
        config.announceStart = false;
        config.announceCompletion = false;
        config.teleportBackOnComplete = false;
        config.spawnPoint = point(h, site.entrance());
        config.settings.difficultyScaling = false;
        config.settings.timeLimitSeconds = 0;
        config.settings.maxDeaths = 0;
        return config;
    }

    private static WaveConfig wave(GameTestHelper h, int number, String type, int count, BlockPos at) {
        WaveConfig wave = new WaveConfig(number);
        MobSpawnConfig mob = new MobSpawnConfig(type, count, point(h, at));
        mob.health = 20;
        wave.mobs.add(mob);
        return wave;
    }

    private static BossConfig boss(GameTestHelper h, String id, String type, BlockPos at) {
        BossConfig boss = new BossConfig(id, type, 100, 1);
        boss.adaptivePower = false;
        boss.showBossBar = false;
        boss.spawnPoint = point(h, at);
        return boss;
    }

    /** A boss with two phases, the second of which summons and may ask for the summons to die. */
    private static BossConfig bossWithSummons(GameTestHelper h, String id, BlockPos at, String summonType, String action) {
        BossConfig boss = boss(h, id, HARMLESS, at);
        boss.phases.add(new PhaseConfig(1, 1.0));
        PhaseConfig second = new PhaseConfig(2, 0.5);
        second.requiredAction = action;
        second.summonMobs.add(new SummonConfig(summonType, 2));
        boss.phases.add(second);
        return boss;
    }

    private static void area(GameTestHelper h, DungeonConfig config, BlockPos center, int radius) {
        String dimension = RunTestKit.dimension(h);
        config.areaPos1 = new DungeonConfig.AreaPos(dimension,
                center.getX() - radius, center.getY() - radius, center.getZ() - radius);
        config.areaPos2 = new DungeonConfig.AreaPos(dimension,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius);
    }

    /**
     * A participant who holds no chunk: a fake player is in no player list and has no ticket, so it
     * stands for the worst case, nobody anywhere near the fight. The mock player of the game test
     * framework cannot be used, its log in sends Arcadia Lib a connection with no channels.
     */
    private static ServerPlayer playerAt(GameTestHelper h, BlockPos pos) {
        ServerPlayer player = RunTestKit.fakePlayer(h, "run-test");
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static void end(GameTestHelper h, ServerPlayer player, DungeonConfig config) {
        DungeonManager.getInstance().forceReset(config.id);
        ConfigManager.getInstance().deleteDungeon(config.id);
        player.discard();
    }

    private static void waitMillis(GameTestHelper h, long until) {
        if (System.currentTimeMillis() < until) throw RunTestKit.waiting(h, "still waiting");
    }

    private static DungeonInstance start(GameTestHelper h, ServerPlayer player, DungeonConfig config) {
        ConfigManager.getInstance().saveDungeon(config);
        DungeonInstance instance = DungeonManager.getInstance().startDungeon(config.id, player);
        if (instance == null) h.fail("the dungeon refused to start");
        return instance;
    }

    /**
     * Runs the test once the chunk at this spot is really in the world. A chunk read with getChunk
     * has its blocks but no entity in it can be looked up yet, and that takes as long as it takes
     * with a batch of tests generating chunks at the same time.
     */
    private static void whenLoaded(GameTestHelper h, BlockPos pos, Runnable test) {
        ServerLevel level = h.getLevel();
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!level.isPositionEntityTicking(pos)) throw RunTestKit.waiting(h, "the chunk is loading");
                })
                .thenExecute(test);
    }

    private static void block(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    // === Chunks unloading ===

    /**
     * A wave whose chunk nobody holds is still there, and the run waits for it.
     *
     * <p>This is the "the mobs never spawned" report. The mob was created, its chunk unloaded a few
     * ticks later, the unload removed the Java object, and the wave read that as a kill: it announced
     * itself cleared without a blow struck, then did the same with every wave after it.</p>
     */
    public static void aWaveNobodyIsNearIsNotClearedByItsChunkUnloading(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.waves.add(wave(h, 1, HARMLESS, 1, site.fight()));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        WaveInstance first = instance.getWaveInstances().get(0);
        AtomicReference<String> problem = new AtomicReference<>();
        long[] until = {0L};

        h.startSequence()
                .thenExecute(() -> until[0] = System.currentTimeMillis() + 60_000)
                .thenWaitUntil(() -> {
                    if (first.isCleared() || DungeonManager.getInstance().getInstance(config.id) == null) {
                        problem.set("the wave counts as cleared although no mob was killed");
                        return;
                    }
                    if (System.currentTimeMillis() > until[0]) {
                        problem.set("the mob never came into the world");
                        return;
                    }
                    LivingEntity mob = first.getAliveMobs().isEmpty() ? null : first.getAliveMobs().get(0);
                    if (mob == null || h.getLevel().getEntity(mob.getUUID()) == null) {
                        throw RunTestKit.waiting(h, "the mob is not in the world yet");
                    }
                })
                .thenExecute(() -> until[0] = System.currentTimeMillis() + 3000)
                .thenWaitUntil(() -> {
                    if (problem.get() != null) return;
                    if (first.isCleared() || DungeonManager.getInstance().getInstance(config.id) == null) {
                        problem.set("the wave counts as cleared although no mob was killed");
                        return;
                    }
                    waitMillis(h, until[0]);
                })
                .thenExecute(() -> {
                    String why = problem.get();
                    end(h, player, config);
                    if (why != null) h.fail(why);
                })
                .thenSucceed();
    }

    /**
     * A boss created where nobody is can be hit once the players get there.
     *
     * <p>This is the "we cannot hit the boss" report. A boss spawns invulnerable for five seconds and
     * the flag is lifted by a countdown that stopped as soon as the object was gone, so the boss went
     * to disk still flagged and came back from it invulnerable for good, with no run attached to
     * it.</p>
     */
    public static void aBossNobodyIsNearCanBeHitLater(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(boss(h, "far", HARMLESS, site.fight()));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("far");
        long[] deadline = {0L};
        AtomicReference<String> problem = new AtomicReference<>();

        h.startSequence()
                .thenExecute(() -> deadline[0] = System.currentTimeMillis() + 60_000)
                .thenWaitUntil(() -> {
                    if (boss == null || !boss.isBossAlive()) {
                        problem.set("the run lost track of the boss");
                        return;
                    }
                    if (System.currentTimeMillis() > deadline[0]) {
                        problem.set("the boss is still invulnerable");
                        return;
                    }
                    if (boss.getBossEntity().isInvulnerable()) throw RunTestKit.waiting(h, "still protected");
                })
                .thenExecute(() -> {
                    String why = problem.get();
                    if (why == null) {
                        LivingEntity entity = boss.getBossEntity();
                        float before = entity.getHealth();
                        RunTestKit.hurt(h, entity, 5);
                        if (entity.getHealth() >= before) why = "a hit took no health from the boss";
                    }
                    end(h, player, config);
                    if (why != null) h.fail(why);
                })
                .thenSucceed();
    }

    // === A spawn another mod refuses ===

    /** A wave mob another mod refused to let in cannot be killed, so it must not hold the wave. */
    public static void aWaveMobAnotherModRefusedDoesNotHoldTheWave(GameTestHelper h) {
        registerListener();
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.waves.add(wave(h, 1, "minecraft:rabbit", 2, site.entrance().offset(4, 0, 0)));
        config.waves.add(wave(h, 2, "minecraft:zombie", 1, site.entrance().offset(-4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        REFUSING_RABBITS.incrementAndGet();
        DungeonInstance instance = start(h, player, config);
        long[] deadline = {0L};
        AtomicReference<String> problem = new AtomicReference<>();

        h.startSequence()
                .thenExecute(() -> deadline[0] = System.currentTimeMillis() + 60_000)
                .thenWaitUntil(() -> {
                    if (instance.getCurrentWaveIndex() >= 1) return;
                    if (System.currentTimeMillis() > deadline[0]) {
                        problem.set("the wave is still waiting for mobs that never entered the world");
                        return;
                    }
                    throw RunTestKit.waiting(h, "still on the first wave");
                })
                .thenExecute(() -> {
                    String why = problem.get();
                    REFUSING_RABBITS.decrementAndGet();
                    end(h, player, config);
                    if (why != null) h.fail(why);
                })
                .thenSucceed();
    }

    /** A summon another mod refused cannot be killed, so it must not protect the boss for ever. */
    public static void aSummonAnotherModRefusedDoesNotProtectTheBoss(GameTestHelper h) {
        registerListener();
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(bossWithSummons(h, "refused", site.entrance().offset(4, 0, 0), "minecraft:rabbit", "KILL_SUMMONS"));
        ServerPlayer player = playerAt(h, site.entrance());
        REFUSING_RABBITS.incrementAndGet();
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("refused");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        boolean blocked = boss.shouldBlockDamageWhileSummonsAlive();
        REFUSING_RABBITS.decrementAndGet();
        end(h, player, config);
        if (blocked) h.fail("the boss is immune because of a summon that does not exist");
        h.succeed();
    }

    // === Summons ===

    /**
     * Only a phase that asks for its summons to die makes the boss immune.
     *
     * <p>{@code requiredAction} is the setting for this, and a refactor made every summon block
     * damage whatever it said, which is one more way to stand in front of a boss that takes no
     * damage.</p>
     */
    public static void summonsOnlyProtectTheBossWhenThePhaseSaysSo(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(bossWithSummons(h, "plain", site.entrance().offset(4, 0, 0), "minecraft:zombie", "NONE"));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("plain");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        boolean summoned = boss.areSummonsAlive();
        boolean blocked = boss.shouldBlockDamageWhileSummonsAlive();
        end(h, player, config);
        if (!summoned) h.fail("the phase did not summon anything");
        if (blocked) h.fail("a phase with requiredAction NONE made the boss immune");
        h.succeed();
    }

    /** And a phase that does ask for it still works, because that is the mechanic of the fight. */
    public static void killSummonsStillProtectsTheBoss(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(bossWithSummons(h, "kill", site.entrance().offset(4, 0, 0), "minecraft:zombie", "KILL_SUMMONS"));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("kill");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        boolean blockedWithSummons = boss.shouldBlockDamageWhileSummonsAlive();
        for (LivingEntity summon : List.copyOf(boss.getSummonedMobs())) RunTestKit.kill(h, summon);
        boolean blockedAfter = boss.shouldBlockDamageWhileSummonsAlive();
        end(h, player, config);
        if (!blockedWithSummons) h.fail("KILL_SUMMONS no longer protects the boss");
        if (blockedAfter) h.fail("the boss stayed immune after its summons died");
        h.succeed();
    }

    // === The order of a run ===

    /** A dungeon with bosses and no waves spawns its boss, with or without a recruitment. */
    public static void aDungeonWithoutWavesSpawnsItsBossAfterRecruitment(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.recruitmentDurationSeconds = 600;
        config.bosses.add(boss(h, "only", "minecraft:zombie", site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        DungeonManager.getInstance().finishRecruitment(config.id);
        boolean spawned = instance.getActiveBosses().containsKey("only");
        end(h, player, config);
        if (!spawned) h.fail("the recruitment ended and the boss never came");
        h.succeed();
    }

    /**
     * Killing a boss that came with the start of the run leaves the final boss where it is.
     *
     * <p>A rare boss at the start of a run is a common way to write a dungeon, and killing it brought
     * the last boss in the middle of the waves, and paused them.</p>
     */
    public static void killingAnEarlyBossDoesNotBringTheFinalBossEarly(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.recruitmentDurationSeconds = 600;
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(8, 0, 0)));
        BossConfig early = boss(h, "early", "minecraft:zombie", site.entrance().offset(4, 0, 0));
        early.spawnAtStart = true;
        early.optional = true;
        early.requiredKill = false;
        config.bosses.add(early);
        config.bosses.add(boss(h, "final", "minecraft:zombie", site.entrance().offset(-4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        DungeonManager.getInstance().finishRecruitment(config.id);
        BossInstance earlyBoss = instance.getActiveBosses().get("early");
        if (earlyBoss == null) h.fail("the boss meant for the start of the run did not spawn");
        earlyBoss.getBossEntity().setInvulnerable(false);
        RunTestKit.kill(h, earlyBoss.getBossEntity());
        boolean finalCame = instance.getActiveBosses().containsKey("final");
        boolean run = DungeonManager.getInstance().getInstance(config.id) != null;
        end(h, player, config);
        if (finalCame) h.fail("the final boss came while the waves were still running");
        if (!run) h.fail("the run ended with its waves still to fight");
        h.succeed();
    }

    /**
     * A piglin stays a piglin.
     *
     * <p>In the overworld a piglin turns into a zombified piglin after fifteen seconds. The new mob
     * is not one of the dungeon's, so it was refused at the door while the old one was removed: the
     * boss disappeared and the run waited for it until the timer ran out. Zombies in water do the
     * same in any dungeon with a flooded room.</p>
     */
    public static void aPiglinBossDoesNotConvertAwayFromItsRun(GameTestHelper h) {
        // Next to the test structure, whose chunks tick whatever the dungeon does: a piglin that
        // does not tick never zombifies, and this test would pass for the wrong reason.
        BlockPos here = h.absolutePos(new BlockPos(1, 2, 1));
        Site site = new Site(here, here);
        DungeonConfig config = dungeon(h, site);
        area(h, config, here, 24);
        config.bosses.add(boss(h, "piglin", "minecraft:piglin", here));
        ServerPlayer player = playerAt(h, here);
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("piglin");
        long startTick = h.getTick();

        h.startSequence()
                .thenWaitUntil(() -> {
                    if (h.getTick() < startTick + 450) throw RunTestKit.waiting(h, "waiting for the conversion time");
                })
                .thenExecute(() -> {
                    boolean alive = boss.isBossAlive();
                    end(h, player, config);
                    if (!alive) h.fail("the boss vanished when it should have zombified");
                })
                .thenSucceed();
    }

    // === A boss that leaves the world some other way ===

    /**
     * A boss removed as killed without a death event still ends its fight.
     *
     * <p>The ender dragon works this way: {@code tickDeath} removes it and nothing calls
     * {@code die}, and several modded bosses copy that.</p>
     */
    public static void aBossRemovedWithoutDeathEventStillCompletesTheRun(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(boss(h, "silent", "minecraft:zombie", site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        instance.getActiveBosses().get("silent").getBossEntity().remove(Entity.RemovalReason.KILLED);
        long[] deadline = {0L};
        AtomicReference<String> problem = new AtomicReference<>();

        h.startSequence()
                .thenExecute(() -> deadline[0] = System.currentTimeMillis() + 60_000)
                .thenWaitUntil(() -> {
                    if (DungeonManager.getInstance().getInstance(config.id) == null) return;
                    if (System.currentTimeMillis() > deadline[0]) {
                        problem.set("the boss is gone and the run is still waiting for it");
                        return;
                    }
                    throw RunTestKit.waiting(h, "the run is still going");
                })
                .thenExecute(() -> {
                    String why = problem.get();
                    end(h, player, config);
                    if (why != null) h.fail(why);
                })
                .thenSucceed();
    }

    /** A boss another mod simply removed comes back, at the health it had. */
    public static void aBossRemovedByAnotherModComesBack(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(boss(h, "taken", HARMLESS, site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossManager.getInstance().spawnNextBoss(instance);
        BossInstance boss = instance.getActiveBosses().get("taken");
        LivingEntity before = boss.getBossEntity();
        UUID beforeId = before.getUUID();
        before.setHealth(40);
        long[] deadline = {0L};
        AtomicReference<String> problem = new AtomicReference<>();

        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    before.discard();
                    deadline[0] = System.currentTimeMillis() + 60_000;
                })
                .thenWaitUntil(() -> {
                    if (boss.isBossAlive() && !boss.getBossEntity().getUUID().equals(beforeId)) return;
                    if (System.currentTimeMillis() > deadline[0]) {
                        problem.set("the boss was removed and the run is still waiting for it");
                        return;
                    }
                    throw RunTestKit.waiting(h, "the boss has not come back");
                })
                .thenExecute(() -> {
                    String why = problem.get();
                    if (why == null) {
                        float health = boss.getBossEntity().getHealth();
                        if (Math.abs(health - 40) > 1) why = "the boss came back at " + health + " health instead of 40";
                    }
                    end(h, player, config);
                    if (why != null) h.fail(why);
                })
                .thenSucceed();
    }

    // === What the area filter does to a world ===

    /**
     * An entity read back from a chunk is never deleted because a run is on.
     *
     * <p>Cancelling the join of an entity loaded from disk is not a refusal, it is a deletion: the
     * entity is not added, so the chunk's next save does not hold it. Armor stands and villagers in
     * a dungeon's area disappeared for good whenever their chunk happened to load during a run.</p>
     */
    public static void entitiesLoadedFromDiskSurviveARun(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 24);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(8, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        start(h, player, config);

        LivingEntity stand = RunTestKit.create(h, EntityType.ARMOR_STAND);
        stand.setPos(Vec3.atCenterOf(site.entrance().offset(2, 0, 0)));
        LivingEntity villager = RunTestKit.create(h, EntityType.VILLAGER);
        villager.setPos(Vec3.atCenterOf(site.entrance().offset(3, 0, 0)));
        boolean standDeleted = RunTestKit.joinRefused(h, stand, true);
        boolean villagerDeleted = RunTestKit.joinRefused(h, villager, true);

        // The filter itself still does its job on something spawning fresh.
        LivingEntity fresh = RunTestKit.create(h, EntityType.ZOMBIE);
        fresh.setPos(Vec3.atCenterOf(site.entrance().offset(4, 0, 0)));
        boolean freshBlocked = RunTestKit.joinRefused(h, fresh, false);

        end(h, player, config);
        if (standDeleted) h.fail("an armor stand was deleted because its chunk loaded during a run");
        if (villagerDeleted) h.fail("a villager was deleted because its chunk loaded during a run");
        if (!freshBlocked) h.fail("a mob spawning fresh in the area was let in");
        h.succeed();
    }

    /** A dungeon mob read from disk with no run to belong to is a leftover, and leaves for good. */
    public static void aLeftoverFromAnEndedRunIsRemovedWhenItLoads(GameTestHelper h) {
        LivingEntity leftover = RunTestKit.create(h, EntityType.ZOMBIE);
        leftover.addTag("arcadia_managed");
        leftover.addTag("arcadia_boss");
        leftover.setInvulnerable(true);
        boolean removed = RunTestKit.joinRefused(h, leftover, true);
        if (!removed) h.fail("an invulnerable boss left by an old run came back into the world");
        h.succeed();
    }

    // === Where mobs land ===

    /**
     * A mob stays in the room it was meant for.
     *
     * <p>A carpeted floor does not count as solid for the search, so in a carpeted room it looked for
     * somewhere else to stand, and the nearest solid top was the roof of the room, or the other side
     * of its wall. The mob stood there, out of reach, and the wave could not end.</p>
     */
    public static void aMobStaysInACarpetedRoom(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Site site = newSite(h);
        level.getChunk(site.entrance());
        BlockPos floor = site.entrance();
        int r = 3;
        int height = 3;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                for (int dy = 0; dy <= height + 1; dy++) {
                    BlockPos pos = floor.offset(dx, dy, dz);
                    if (edge || dy == height + 1) block(level, pos, Blocks.STONE);
                    else if (dy == 0) block(level, pos, Blocks.RED_CARPET);
                    else block(level, pos, Blocks.AIR);
                }
            }
        }
        LivingEntity zombie = RunTestKit.create(h, EntityType.ZOMBIE);
        Vec3 found = SpawnSafety.findSafeSpawn(zombie, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 4, 4);
        boolean inside = Math.abs(found.x - (floor.getX() + 0.5)) <= r
                && Math.abs(found.z - (floor.getZ() + 0.5)) <= r
                && found.y >= floor.getY()
                && found.y <= floor.getY() + height;
        if (!inside) {
            h.fail("the mob was put outside its room, at " + (found.x - floor.getX() - 0.5) + ", "
                    + (found.y - floor.getY()) + ", " + (found.z - floor.getZ() - 0.5));
        }
        h.succeed();
    }

    // === Mobs nobody can reach ===

    private static LivingEntity firstMobOf(GameTestHelper h, DungeonInstance instance) {
        WaveInstance wave = instance.getWaveInstances().get(0);
        wave.spawn(h.getLevel().getServer(), instance);
        if (wave.getAliveMobs().isEmpty()) h.fail("the wave has no mob");
        return wave.getAliveMobs().get(0);
    }

    /**
     * A mob outside the area comes back. Players cannot follow it, they are held inside.
     *
     * <p>Time is passed in, so the five seconds of grace and the minutes of the idle rescue are not
     * waited for in real time.</p>
     */
    public static void aMobThatLeftTheAreaIsBroughtBack(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 24);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        LivingEntity mob = firstMobOf(h, instance);
        mob.teleportTo(site.entrance().getX() + 200.5, site.entrance().getY(), site.entrance().getZ() + 0.5);

        long now = System.currentTimeMillis();
        MobRescue.check(instance, h.getLevel().getServer(), now);
        boolean movedTooSoon = mob.distanceToSqr(Vec3.atCenterOf(site.entrance())) < 100 * 100;
        MobRescue.check(instance, h.getLevel().getServer(), now + 6000);
        boolean back = mob.distanceToSqr(Vec3.atCenterOf(site.entrance())) < 30 * 30;
        end(h, player, config);
        if (movedTooSoon) h.fail("the mob was moved without any grace");
        if (!back) h.fail("the mob stayed outside the area");
        h.succeed();
    }

    /** A dungeon that turned the rescue off keeps its mobs where they went. */
    public static void theRescueCanBeTurnedOff(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 24);
        config.settings.rescueStuckMobs = false;
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        LivingEntity mob = firstMobOf(h, instance);
        mob.teleportTo(site.entrance().getX() + 200.5, site.entrance().getY(), site.entrance().getZ() + 0.5);

        long now = System.currentTimeMillis();
        MobRescue.check(instance, h.getLevel().getServer(), now);
        MobRescue.check(instance, h.getLevel().getServer(), now + 6000);
        boolean moved = mob.distanceToSqr(Vec3.atCenterOf(site.entrance())) < 100 * 100;
        end(h, player, config);
        if (moved) h.fail("the mob was moved although the dungeon turned this off");
        h.succeed();
    }

    /**
     * A wave left alone goes back to where it started, then comes to the players. A walk through a
     * maze must never pull mobs out of their room, so nothing moves in the first two minutes.
     */
    public static void aWaveNobodyTouchesIsBroughtToThePlayersInSteps(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 64);
        BlockPos home = site.entrance().offset(4, 0, 0);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, home));
        ServerPlayer player = playerAt(h, site.entrance().offset(-30, 0, 0));
        listPlayer(h, player, true);
        DungeonInstance instance = start(h, player, config);
        player.setPos(site.entrance().getX() - 29.5, site.entrance().getY(), site.entrance().getZ() + 0.5);
        LivingEntity mob = firstMobOf(h, instance);
        mob.teleportTo(home.getX() + 30.5, home.getY(), home.getZ() + 0.5);

        long now = instance.getLastProgressMillis();
        MobRescue.check(instance, h.getLevel().getServer(), now + 100_000);
        boolean movedEarly = mob.distanceToSqr(Vec3.atCenterOf(home)) < 20 * 20;
        MobRescue.check(instance, h.getLevel().getServer(), now + 125_000);
        boolean wentHome = mob.distanceToSqr(Vec3.atCenterOf(home)) < 8 * 8;
        MobRescue.check(instance, h.getLevel().getServer(), now + 245_000);
        boolean cameToPlayer = mob.distanceToSqr(player) < 8 * 8;
        listPlayer(h, player, false);
        end(h, player, config);
        if (movedEarly) h.fail("a mob was moved although the fight is not old enough");
        if (!wentHome) h.fail("the mob did not go back to where it spawned");
        if (!cameToPlayer) h.fail("the mob did not come to the player");
        h.succeed();
    }

    /** A hidden boss nobody has to kill stays hidden, however long it waits. */
    public static void anOptionalBossIsNotBroughtToThePlayers(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.recruitmentDurationSeconds = 600;
        area(h, config, site.entrance(), 64);
        BlockPos home = site.entrance().offset(30, 0, 0);
        BossConfig secret = boss(h, "secret", "minecraft:zombie", home);
        secret.spawnAtStart = true;
        secret.optional = true;
        secret.requiredKill = false;
        config.bosses.add(secret);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(-4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        listPlayer(h, player, true);
        DungeonInstance instance = start(h, player, config);
        DungeonManager.getInstance().finishRecruitment(config.id);
        LivingEntity entity = instance.getActiveBosses().get("secret").getBossEntity();

        MobRescue.check(instance, h.getLevel().getServer(), instance.getLastProgressMillis() + 300_000);
        boolean stayed = entity.distanceToSqr(Vec3.atCenterOf(home)) < 4 * 4;
        listPlayer(h, player, false);
        end(h, player, config);
        if (!stayed) h.fail("a secret boss was dragged to the players");
        h.succeed();
    }

    /**
     * A summon protecting its boss gets found: it glows, then it is brought back, and if nobody can
     * kill it the boss can be hit anyway. A blaze hovers out of reach in a tall room, and a pillager
     * wanders off through the rooms.
     */
    public static void aSummonNobodyReachesEndsUpReachableAndThenStopsProtecting(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 64);
        BossConfig withSummons = bossWithSummons(h, "guarded", site.entrance().offset(4, 0, 0), "minecraft:zombie", "KILL_SUMMONS");
        config.bosses.add(withSummons);
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossInstance boss = instance.getActiveBosses().get("guarded");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        LivingEntity summon = boss.getSummonedMobs().get(0);
        LivingEntity bossEntity = boss.getBossEntity();
        summon.teleportTo(site.entrance().getX() + 44.5, site.entrance().getY(), site.entrance().getZ() + 0.5);

        long now = System.currentTimeMillis();
        MobRescue.check(instance, h.getLevel().getServer(), now + 10_000);
        boolean glowingEarly = summon.hasGlowingTag();
        MobRescue.check(instance, h.getLevel().getServer(), now + 16_000);
        boolean glowing = summon.hasGlowingTag();
        boolean stillFar = summon.distanceTo(bossEntity) > 24;
        MobRescue.check(instance, h.getLevel().getServer(), now + 31_000);
        boolean nearBoss = summon.distanceTo(bossEntity) < 12;
        boolean blockedBefore = boss.shouldBlockDamageWhileSummonsAlive();
        MobRescue.check(instance, h.getLevel().getServer(), now + 181_000);
        boolean blockedAfter = boss.shouldBlockDamageWhileSummonsAlive();
        end(h, player, config);
        if (glowingEarly) h.fail("the summon glowed before the delay");
        if (!glowing) h.fail("the summon did not glow");
        if (!stillFar) h.fail("the summon was moved before its delay");
        if (!nearBoss) h.fail("the summon was not brought back to its boss");
        if (!blockedBefore) h.fail("the boss stopped being protected too early");
        if (blockedAfter) h.fail("the boss is still protected after the limit");
        h.succeed();
    }

    /** {@code summonLockMaxSeconds: 0} keeps the boss immune until its summons are dead. */
    public static void theSummonLimitCanBeTurnedOff(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.settings.summonLockMaxSeconds = 0;
        config.bosses.add(bossWithSummons(h, "guarded", site.entrance().offset(4, 0, 0), "minecraft:zombie", "KILL_SUMMONS"));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossInstance boss = instance.getActiveBosses().get("guarded");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        MobRescue.check(instance, h.getLevel().getServer(), System.currentTimeMillis() + 3_600_000);
        boolean blocked = boss.shouldBlockDamageWhileSummonsAlive();
        end(h, player, config);
        if (!blocked) h.fail("the boss stopped being protected although the limit is off");
        h.succeed();
    }

    // === A mob that comes back from disk ===

    /** What the game does when a chunk unloads and loads again: same id, new object, flag saved. */
    private static LivingEntity reloadFromDisk(GameTestHelper h, LivingEntity entity) {
        LivingEntity copy = (LivingEntity) RunTestKit.create(h, entity.getType());
        copy.setUUID(entity.getUUID());
        copy.setPos(entity.position());
        copy.setInvulnerable(entity.isInvulnerable());
        copy.setHealth(entity.getHealth());
        for (String tag : RunTestKit.tags(entity)) copy.addTag(tag);
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        if (!h.getLevel().addFreshEntity(copy)) h.fail("the test could not put the mob back");
        return copy;
    }

    /**
     * A boss saved during its spawn protection is hit like any other once it is back, and the run
     * knows it: the hit counts toward its phases and killing it ends the run.
     */
    public static void aBossBackFromDiskIsTheSameBoss(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(bossWithSummons(h, "back", site.entrance().offset(4, 0, 0), "minecraft:zombie", "NONE"));
        ServerPlayer player = playerAt(h, site.entrance());
        whenLoaded(h, site.entrance(), () -> {
            DungeonInstance instance = start(h, player, config);
            BossInstance boss = instance.getActiveBosses().get("back");
            LivingEntity first = boss.getBossEntity();
            if (!first.isInvulnerable()) h.fail("the boss should start protected");
            LivingEntity second = reloadFromDisk(h, first);
            AtomicReference<String> problem = new AtomicReference<>();

            h.startSequence()
                    .thenWaitUntil(() -> {
                        if (second.isInvulnerable()) throw RunTestKit.waiting(h, "waiting for the end of the spawn protection");
                    })
                    .thenExecute(() -> {
                        if (boss.getBossEntity() != second) problem.set("the run still holds the old object");
                        else if (!boss.isBossAlive()) problem.set("the run thinks the boss is gone");
                        else {
                            RunTestKit.hurt(h, second, 60);
                            if (boss.getCurrentPhase() != 1) problem.set("a hit on the boss did not reach its phases");
                            second.invulnerableTime = 0;
                            RunTestKit.kill(h, second);
                        }
                    })
                    .thenWaitUntil(() -> {
                        if (problem.get() == null && DungeonManager.getInstance().getInstance(config.id) != null) {
                            throw RunTestKit.waiting(h, "the run should end with its boss");
                        }
                    })
                    .thenExecute(() -> {
                        String why = problem.get();
                        end(h, player, config);
                        if (why != null) h.fail(why);
                    })
                    .thenSucceed();
        });
    }

    /** A wave mob back from disk still counts, and killing it clears the wave. */
    public static void aWaveMobBackFromDiskStillCounts(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        config.waves.add(wave(h, 2, "minecraft:zombie", 1, site.entrance().offset(-4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        whenLoaded(h, site.entrance(), () -> {
            DungeonInstance instance = start(h, player, config);
            LivingEntity first = firstMobOf(h, instance);
            WaveInstance wave = instance.getWaveInstances().get(0);
            LivingEntity second = reloadFromDisk(h, first);

            boolean clearedByReload = wave.isCleared();
            boolean relinked = wave.getRemainingMobs() == 1 && wave.getAliveMobs().get(0) == second;
            RunTestKit.kill(h, second);
            boolean cleared = wave.isCleared();
            end(h, player, config);
            if (clearedByReload) h.fail("the wave cleared because its mob was reloaded");
            if (!relinked) h.fail("the wave lost its mob when it was reloaded");
            if (!cleared) h.fail("killing the reloaded mob did not clear the wave");
            h.succeed();
        });
    }

    // === The rest of a run ===

    public static void rewardsWaitForADeadPlayer(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        List<RewardConfig> rewards = List.of(new RewardConfig("minecraft:diamond", 3, 1.0));

        player.setHealth(0);
        instance.giveRewards(player, rewards);
        int whileDead = player.getInventory().countItem(Items.DIAMOND);
        player.setHealth(20);
        DungeonManager.getInstance().onPlayerRespawned(player);
        int afterRespawn = player.getInventory().countItem(Items.DIAMOND);
        DungeonManager.getInstance().onPlayerRespawned(player);
        int afterSecondRespawn = player.getInventory().countItem(Items.DIAMOND);
        end(h, player, config);
        if (whileDead != 0) h.fail("the rewards went into the inventory of a dead player");
        if (afterRespawn != 3) h.fail("the player got " + afterRespawn + " of 3 items after respawning");
        if (afterSecondRespawn != 3) h.fail("the rewards were given twice");
        h.succeed();
    }

    /** Whoever opens and leaves a recruitment does not close a daily dungeon for everyone. */
    public static void leavingARecruitmentDoesNotUseUpTheDungeon(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.recruitmentDurationSeconds = 600;
        config.availableEverySeconds = 3600;
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        start(h, player, config);
        DungeonManager.getInstance().failDungeon(config.id);
        long leftBehind = DungeonManager.getInstance().getAvailabilityRemainingSeconds(config.id);

        DungeonInstance second = DungeonManager.getInstance().startDungeon(config.id, player);
        if (second == null) h.fail("the dungeon refused a second start");
        DungeonManager.getInstance().finishRecruitment(config.id);
        DungeonManager.getInstance().failDungeon(config.id);
        long afterARealRun = DungeonManager.getInstance().getAvailabilityRemainingSeconds(config.id);
        end(h, player, config);
        if (leftBehind != 0) h.fail("a recruitment nobody stayed in put the dungeon on its timer");
        if (afterARealRun <= 0) h.fail("a real run no longer puts the dungeon on its timer");
        h.succeed();
    }

    /** The countdown warnings play in every run, not only in the first one after a restart. */
    public static void warningsAreCountedPerRun(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        DungeonInstance first = new DungeonInstance(config, h.getLevel().getServer());
        DungeonInstance second = new DungeonInstance(config, h.getLevel().getServer());
        if (!first.announceOnce(-60)) h.fail("the first warning was not played");
        if (first.announceOnce(-60)) h.fail("a warning was played twice in one run");
        if (!second.announceOnce(-60)) h.fail("the next run lost its warning");
        h.succeed();
    }

    // === Mobs as configured ===

    /**
     * A pillager without a crossbow never attacks, and a projectile cooldown set on it does nothing.
     */
    public static void aPillagerGetsTheCrossbowItCannotFightWithout(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        WaveConfig armed = wave(h, 1, "minecraft:pillager", 1, site.entrance().offset(4, 0, 0));
        WaveConfig bare = wave(h, 2, "minecraft:pillager", 1, site.entrance().offset(-4, 0, 0));
        bare.mobs.get(0).mainHand = "minecraft:air";
        WaveConfig sword = wave(h, 3, "minecraft:pillager", 1, site.entrance().offset(0, 0, 4));
        sword.mobs.get(0).mainHand = "minecraft:iron_sword";
        WaveConfig zombie = wave(h, 4, "minecraft:zombie", 1, site.entrance().offset(0, 0, -4));
        config.waves.addAll(List.of(armed, bare, sword, zombie));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        List<Item> held = new java.util.ArrayList<>();
        for (WaveInstance wave : instance.getWaveInstances()) {
            wave.spawn(h.getLevel().getServer(), instance);
            held.add(wave.getAliveMobs().get(0).getMainHandItem().getItem());
        }
        end(h, player, config);
        if (held.get(0) != Items.CROSSBOW) h.fail("a pillager with no configured weapon holds " + held.get(0));
        if (held.get(1) != Items.AIR) h.fail("minecraft:air did not leave the hand empty: " + held.get(1));
        if (held.get(2) != Items.IRON_SWORD) h.fail("the configured weapon was replaced by " + held.get(2));
        if (held.get(3) != Items.AIR) h.fail("a zombie was given " + held.get(3));
        h.succeed();
    }

    /** A config keeps its armor on a game version that renamed the attribute. */
    public static void attributesAreFoundUnderEitherName(GameTestHelper h) {
        var armor = BossInstance.findAttribute("minecraft:generic.armor");
        if (armor.isEmpty()) h.fail("generic.armor was not found");
        var bare = BossInstance.findAttribute("minecraft:armor");
        if (bare.isEmpty()) h.fail("armor was not found");
        if (armor.get() != bare.get()) h.fail("the two names are not the same attribute");
        if (BossInstance.findAttribute("minecraft:generic.nothing").isPresent()) h.fail("an unknown attribute was found");
        if (BossInstance.findAttribute("othermod:generic.armor").isPresent()) h.fail("a modded id was read as a vanilla one");
        if (BossInstance.findAttribute("not an id").isPresent()) h.fail("a broken id was found");
        h.succeed();
    }

    /** The first phase is how the boss starts, and a first phase below full health waits its turn. */
    public static void theFirstPhaseOfABossIsNotSkipped(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        BossConfig strong = boss(h, "strong", "minecraft:zombie", site.entrance().offset(4, 0, 0));
        strong.spawnAtStart = true;
        PhaseConfig first = new PhaseConfig(1, 1.0);
        first.damageMultiplier = 2.0;
        first.summonMobs.add(new SummonConfig("minecraft:zombie", 1));
        strong.phases.add(first);
        config.bosses.add(strong);
        BossConfig late = boss(h, "late", "minecraft:zombie", site.entrance().offset(-4, 0, 0));
        late.spawnAtStart = true;
        late.phases.add(new PhaseConfig(1, 0.75));
        late.phases.add(new PhaseConfig(2, 0.25));
        config.bosses.add(late);
        config.recruitmentDurationSeconds = 600;
        config.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(0, 0, 4)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        DungeonManager.getInstance().finishRecruitment(config.id);
        BossInstance strongBoss = instance.getActiveBosses().get("strong");
        BossInstance lateBoss = instance.getActiveBosses().get("late");

        double damage = strongBoss.getBossEntity().getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
        boolean summoned = !strongBoss.getSummonedMobs().isEmpty();
        int lateStart = lateBoss.getCurrentPhase();
        lateBoss.checkPhaseTransition(0.7F, instance.getPlayers());
        int lateAfter = lateBoss.getCurrentPhase();
        end(h, player, config);
        if (damage != 2.0) h.fail("the first phase's damage multiplier was ignored: " + damage);
        if (!summoned) h.fail("the first phase's summons did not come");
        if (lateStart != -1) h.fail("a first phase at 75% started at full health: " + lateStart);
        if (lateAfter != 0) h.fail("a first phase at 75% did not start at 70% health: " + lateAfter);
        h.succeed();
    }

    /** A boss with custom speed keeps it through its phases: this used to fall back to the default. */
    public static void phaseSpeedMultipliesTheConfiguredSpeed(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        BossConfig fast = bossWithSummons(h, "fast", site.entrance().offset(4, 0, 0), "minecraft:zombie", "NONE");
        fast.customAttributes.put("minecraft:generic.movement_speed", 0.5);
        fast.phases.get(1).speedMultiplier = 2.0;
        config.bosses.add(fast);
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossInstance boss = instance.getActiveBosses().get("fast");
        boss.checkPhaseTransition(0.4F, instance.getPlayers());
        double speed = boss.getBossEntity().getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        end(h, player, config);
        if (Math.abs(speed - 1.0) > 1.0E-6) h.fail("phase two runs at " + speed + " instead of 1.0");
        h.succeed();
    }

    /** What a phase command summons in the area belongs to the run, and leaves with it. */
    public static void aPhaseCommandCanSummonInsideTheArea(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 24);
        BossConfig caller = bossWithSummons(h, "caller", site.entrance().offset(4, 0, 0), "minecraft:zombie", "NONE");
        BlockPos at = site.entrance().offset(-6, 0, 0);
        caller.phases.get(1).phaseCommands.add("summon minecraft:sheep " + at.getX() + " " + at.getY() + " " + at.getZ());
        config.bosses.add(caller);
        ServerPlayer player = playerAt(h, site.entrance());
        whenLoaded(h, at, () -> {
            listPlayer(h, player, true);
            DungeonInstance instance = start(h, player, config);
            BossInstance boss = instance.getActiveBosses().get("caller");
            boss.checkPhaseTransition(0.4F, instance.getPlayers());
            net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(at).inflate(4);
            List<LivingEntity> sheep = h.getLevel().getEntitiesOfClass(LivingEntity.class, around,
                    entity -> entity.getType() == EntityType.SHEEP);
            boolean summoned = !sheep.isEmpty();
            boolean adopted = summoned && RunTestKit.tags(sheep.get(0)).contains(instance.getRunTag());
            listPlayer(h, player, false);
            end(h, player, config);
            if (!summoned) h.fail("the mob of a phase command was refused at the door of the area");
            if (!adopted) h.fail("the mob of a phase command does not belong to the run");
            if (sheep.get(0).isAlive()) h.fail("the mob of a phase command outlived the run");
            h.succeed();
        });
    }

    /** Mobs of a mod the dungeon is built from may come in, others may not. */
    public static void onlyModdedBossesBringTheirOwnMobs(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig vanilla = dungeon(h, site);
        vanilla.bosses.add(boss(h, "plain", "minecraft:zombie", site.entrance()));
        DungeonInstance plainRun = new DungeonInstance(vanilla, h.getLevel().getServer());
        DungeonConfig modded = dungeon(h, site);
        modded.bosses.add(boss(h, "modded", "somemod:giant", site.entrance()));
        DungeonInstance moddedRun = new DungeonInstance(modded, h.getLevel().getServer());
        if (plainRun.bringsOwnMobs(EntityType.ZOMBIE)) h.fail("a vanilla dungeon let vanilla mobs in");
        if (moddedRun.bringsOwnMobs(EntityType.ZOMBIE)) h.fail("a modded dungeon let vanilla mobs in");
        h.succeed();
    }

    /** The validator names what a run would otherwise find out the hard way. */
    public static void theValidatorNamesWhatIsWrong(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        area(h, config, site.entrance(), 8);
        WaveConfig typo = wave(h, 1, "minecraft:zombiee", 1, site.entrance());
        typo.mobs.get(0).mainHand = "minecraft:iron_swrd";
        typo.mobs.get(0).customAttributes.put("minecraft:generic.armour", 4.0);
        config.waves.add(typo);
        config.waves.add(wave(h, 2, "minecraft:zombie", 1, site.entrance().offset(100, 0, 0)));
        BossConfig lost = boss(h, "lost", "minecraft:zombie", site.entrance());
        lost.spawnAfterWave = 9;
        PhaseConfig broken = new PhaseConfig(2, 0.0);
        broken.requiredAction = "INTERACT";
        lost.phases.add(broken);
        lost.spawnPoint.dimension = "somemod:nowhere";
        config.bosses.add(lost);
        config.requiredDungeon = "does_not_exist";

        List<String> problems = DungeonValidator.check(config, h.getLevel().getServer());
        String all = String.join(" | ", problems);
        for (String expected : List.of("zombiee", "iron_swrd", "generic.armour", "outside the dungeon's area",
                "after wave 9", "health threshold", "INTERACT", "somemod:nowhere", "does_not_exist")) {
            if (!all.contains(expected)) h.fail("the validator did not report '" + expected + "': " + all);
        }

        DungeonConfig fine = dungeon(h, site);
        area(h, fine, site.entrance(), 8);
        fine.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance()));
        List<String> none = DungeonValidator.check(fine, h.getLevel().getServer());
        if (!none.isEmpty()) h.fail("the validator complained about a good dungeon: " + none);
        h.succeed();
    }

    /** A dungeon saved by an older version reads as it did, with the new settings at their defaults. */
    public static void anOldConfigFileStillReads(GameTestHelper h) {
        String old = "{\"id\":\"old\",\"name\":\"Old\",\"settings\":{\"maxPlayers\":6},"
                + "\"bosses\":[{\"id\":\"b\",\"phases\":[{\"phase\":2,\"healthThreshold\":0.5},{\"phase\":1,\"healthThreshold\":1.0}]}],"
                + "\"waves\":[{\"waveNumber\":1}]}";
        DungeonConfig config = new com.google.gson.Gson().fromJson(old, DungeonConfig.class);
        if (config.settings.maxPlayers != 6) h.fail("a setting of the file was lost");
        if (!config.settings.rescueStuckMobs) h.fail("the rescue is off in an old file");
        if (config.settings.summonLockMaxSeconds != 180) h.fail("the summon limit is " + config.settings.summonLockMaxSeconds + " in an old file");
        if (config.waves.get(0).mobs == null || config.bosses.get(0).rewards == null) h.fail("a list was lost");
        h.succeed();
    }

    /** A player in a run cannot open another one: both would keep pulling them back to their area. */
    public static void aPlayerInARunCannotStartAnother(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig first = dungeon(h, site);
        first.recruitmentDurationSeconds = 600;
        first.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(4, 0, 0)));
        DungeonConfig second = dungeon(h, site);
        second.waves.add(wave(h, 1, "minecraft:zombie", 1, site.entrance().offset(-4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        start(h, player, first);
        ConfigManager.getInstance().saveDungeon(second);
        DungeonInstance other = DungeonManager.getInstance().startDungeon(second.id, player);
        ConfigManager.getInstance().deleteDungeon(second.id);
        boolean stillInFirst = DungeonManager.getInstance().getPlayerDungeon(player.getUUID()) != null
                && DungeonManager.getInstance().getPlayerDungeon(player.getUUID()).getConfig().id.equals(first.id);
        end(h, player, first);
        if (other != null) h.fail("a player in a run started another one");
        if (!stillInFirst) h.fail("starting another run moved the player out of their first one");
        h.succeed();
    }

    /** A dungeon with nothing in it says so, instead of leaving players in it until the timer ends. */
    public static void anEmptyDungeonRefusesToStart(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig empty = dungeon(h, site);
        ServerPlayer player = playerAt(h, site.entrance());
        ConfigManager.getInstance().saveDungeon(empty);
        DungeonInstance run = DungeonManager.getInstance().startDungeon(empty.id, player);
        end(h, player, empty);
        if (run != null) h.fail("a dungeon with no wave and no boss started");
        h.succeed();
    }

    /** A dungeon whose only boss is optional and stayed away ends at once, it has nothing left. */
    public static void aDungeonWithOnlyAnAbsentOptionalBossEnds(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        BossConfig absent = boss(h, "absent", "minecraft:zombie", site.entrance().offset(4, 0, 0));
        absent.optional = true;
        absent.requiredKill = false;
        absent.spawnChance = 0.0;
        config.bosses.add(absent);
        ServerPlayer player = playerAt(h, site.entrance());
        ConfigManager.getInstance().saveDungeon(config);
        DungeonManager.getInstance().startDungeon(config.id, player);
        boolean over = DungeonManager.getInstance().getInstance(config.id) == null;
        end(h, player, config);
        if (!over) h.fail("the run waits for a boss that rolled to stay away");
        h.succeed();
    }

    /**
     * What a chunk load may and may not bring back into a run: its own mobs stay, and a boss it
     * replaced after waiting too long for it does not come back as a second boss.
     */
    public static void aChunkLoadKeepsTheRunsMobsAndDropsWhatItReplaced(GameTestHelper h) {
        Site site = newSite(h);
        DungeonConfig config = dungeon(h, site);
        config.bosses.add(boss(h, "real", "minecraft:zombie", site.entrance().offset(4, 0, 0)));
        ServerPlayer player = playerAt(h, site.entrance());
        DungeonInstance instance = start(h, player, config);
        BossInstance boss = instance.getActiveBosses().get("real");

        LivingEntity sameBoss = RunTestKit.create(h, EntityType.ZOMBIE);
        sameBoss.setUUID(boss.getBossEntity().getUUID());
        sameBoss.addTag("arcadia_managed");
        sameBoss.addTag("arcadia_boss");
        sameBoss.addTag(instance.getRunTag());
        boolean keptBoss = !RunTestKit.joinRefused(h, sameBoss, true);

        LivingEntity replacedBoss = RunTestKit.create(h, EntityType.ZOMBIE);
        replacedBoss.addTag("arcadia_managed");
        replacedBoss.addTag("arcadia_boss");
        replacedBoss.addTag(instance.getRunTag());
        boolean droppedOther = RunTestKit.joinRefused(h, replacedBoss, true);

        LivingEntity waveMob = RunTestKit.create(h, EntityType.ZOMBIE);
        waveMob.addTag("arcadia_managed");
        waveMob.addTag("arcadia_wave_mob");
        waveMob.addTag(instance.getRunTag());
        boolean keptMob = !RunTestKit.joinRefused(h, waveMob, true);

        end(h, player, config);
        if (!keptBoss) h.fail("the run's own boss was removed when its chunk loaded");
        if (!droppedOther) h.fail("a boss the run does not follow came back into the world");
        if (!keptMob) h.fail("the run's own mob was removed when its chunk loaded");
        h.succeed();
    }

    /** Puts a fake player in the player list, for what looks players up by id, or takes it out. */
    @SuppressWarnings("unchecked")
    private static void listPlayer(GameTestHelper h, ServerPlayer player, boolean listed) {
        try {
            java.lang.reflect.Field field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            java.util.Map<UUID, ServerPlayer> players = (java.util.Map<UUID, ServerPlayer>) field.get(h.getLevel().getServer().getPlayerList());
            if (listed) players.put(player.getUUID(), player);
            else players.remove(player.getUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PlayerList.playersByUUID is not reachable", e);
        }
    }
}
