package com.vyrriox.arcadiadungeon.gametest;

import com.google.gson.Gson;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.DungeonValidator;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import com.vyrriox.arcadiadungeon.config.SummonConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonState;
import com.vyrriox.arcadiadungeon.dungeon.WaveInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plays a whole dungeon from its first wave to its completion, and checks it happened in order.
 *
 * <p><b>Why this one.</b> A fix to how runs follow their mobs is only worth having if the dungeons
 * already built still play the way they were written. So this runs real configs through the real
 * manager: it starts the run, hits every mob through the damage path a player's sword takes, goes
 * through every phase of every boss, kills the summons that protect them, and checks the waves and
 * the bosses came in the order the config declares.</p>
 *
 * <p>It always plays the dungeon below. To check other configs, drop their files in
 * {@code runs/gameTestServer/test-dungeons/}: they are read from there, moved to this world's
 * overworld, and given a floor under each of their spawn points. That folder is not part of the
 * repository, so a server's own dungeons stay on that server.</p>
 *
 * @author vyrriox
 */
@GameTestHolder("arcadia_dungeon")
@PrefixGameTestTemplate(false)
public class FullRunGameTests {

    private static final String T = "floor";
    private static final int BUDGET = 60_000_000;
    private static final long PER_DUNGEON_MS = 240_000L;

    @GameTest(template = T, batch = "fullrun", timeoutTicks = BUDGET)
    public static void everyDungeonPlaysFromStartToFinish(GameTestHelper h) {
        Driver driver = new Driver(h, dungeonsToPlay(h));
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (!driver.tick()) throw RunTestKit.waiting(h, "still playing");
                })
                .thenExecute(() -> {
                    if (driver.failure != null) h.fail(driver.failure);
                })
                .thenSucceed();
    }

    private static List<DungeonConfig> dungeonsToPlay(GameTestHelper h) {
        List<DungeonConfig> dungeons = new ArrayList<>();
        dungeons.add(sample());
        Path folder = RunTestKit.gameDirectory().resolve("test-dungeons");
        if (Files.isDirectory(folder)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
                Gson gson = new Gson();
                for (Path file : files) {
                    dungeons.add(gson.fromJson(Files.readString(file), DungeonConfig.class));
                }
            } catch (IOException e) {
                throw new IllegalStateException("could not read " + folder, e);
            }
        }
        return dungeons;
    }

    /** A dungeon with every kind of boss: at the start and optional, between waves, and at the end. */
    private static DungeonConfig sample() {
        DungeonConfig config = new DungeonConfig("full_run_sample", "Full run sample");
        config.spawnPoint = new SpawnPointConfig("minecraft:overworld", 20000.5, 80, 20000.5, 0, 0);
        config.recruitmentDurationSeconds = 90;
        config.areaPos1 = new DungeonConfig.AreaPos("minecraft:overworld", 19950, 60, 19950);
        config.areaPos2 = new DungeonConfig.AreaPos("minecraft:overworld", 20060, 110, 20060);

        for (int number = 1; number <= 3; number++) {
            WaveConfig wave = new WaveConfig(number);
            MobSpawnConfig mob = new MobSpawnConfig("minecraft:zombie", 2,
                    new SpawnPointConfig("minecraft:overworld", 20000.5 + number * 10, 80, 20000.5, 0, 0));
            wave.mobs.add(mob);
            config.waves.add(wave);
        }

        BossConfig secret = boss("secret", "minecraft:wither_skeleton", 20010.5);
        secret.spawnAtStart = true;
        secret.optional = true;
        secret.requiredKill = false;
        config.bosses.add(secret);

        BossConfig middle = boss("middle", "minecraft:vindicator", 20020.5);
        middle.spawnAfterWave = 2;
        config.bosses.add(middle);

        BossConfig last = boss("last", "minecraft:evoker", 20040.5);
        config.bosses.add(last);
        return config;
    }

    private static BossConfig boss(String id, String type, double x) {
        BossConfig boss = new BossConfig(id, type, 200, 5);
        boss.spawnPoint = new SpawnPointConfig("minecraft:overworld", x, 80, 20010.5, 0, 0);
        boss.phases.add(new PhaseConfig(1, 1.0));
        PhaseConfig second = new PhaseConfig(2, 0.5);
        second.requiredAction = "KILL_SUMMONS";
        second.summonMobs.add(new SummonConfig("minecraft:zombie", 2));
        boss.phases.add(second);
        return boss;
    }

    /** Plays the dungeons one after the other, a little on every tick. */
    private static final class Driver {
        private final GameTestHelper h;
        private final ServerLevel level;
        private final Deque<DungeonConfig> queue;
        private final ServerPlayer player;
        String failure = null;

        private DungeonConfig current = null;
        private String label = null;
        private long deadline = 0L;
        private List<String> played = new ArrayList<>();
        private Set<Integer> wavesSeen = new HashSet<>();
        private Set<String> bossesSeen = new HashSet<>();
        private Map<String, Integer> lastPhase = new HashMap<>();
        private Map<String, Integer> summonsSeen = new HashMap<>();

        Driver(GameTestHelper h, List<DungeonConfig> dungeons) {
            this.h = h;
            this.level = h.getLevel();
            this.queue = new ArrayDeque<>(dungeons);
            this.player = RunTestKit.fakePlayer(h, "full-run");
        }

        /** True when everything was played, or something failed. */
        boolean tick() {
            if (failure != null) return true;
            if (current == null) {
                if (queue.isEmpty()) return true;
                startNext();
                return failure != null;
            }
            DungeonInstance run = DungeonManager.getInstance().getInstance(current.id);
            if (run == null) {
                finishCurrent();
                return failure != null;
            }
            if (System.currentTimeMillis() > deadline) {
                failure = label + " did not finish in time. Played: " + played + ". State: " + run.getState()
                        + ", wave index " + run.getCurrentWaveIndex() + ", bosses " + run.getActiveBosses().keySet();
                cleanUp();
                return true;
            }
            observe(run);
            fight(run);
            return false;
        }

        private void startNext() {
            DungeonConfig original = queue.poll();
            label = original.id;
            current = playableCopy(original);
            for (String problem : DungeonValidator.check(current, level.getServer())) {
                ArcadiaDungeon.LOGGER.info("FULLRUN {} config: {}", label, problem);
            }
            buildFloors(current);
            played = new ArrayList<>();
            wavesSeen = new HashSet<>();
            bossesSeen = new HashSet<>();
            lastPhase = new HashMap<>();
            summonsSeen = new HashMap<>();
            deadline = System.currentTimeMillis() + PER_DUNGEON_MS;
            player.setPos(current.spawnPoint.x, current.spawnPoint.y, current.spawnPoint.z);

            ConfigManager.getInstance().saveDungeon(current);
            DungeonInstance run = DungeonManager.getInstance().startDungeon(current.id, player);
            if (run == null) {
                failure = label + " refused to start";
                cleanUp();
                return;
            }
            if (run.getState() == DungeonState.RECRUITING) {
                DungeonManager.getInstance().finishRecruitment(current.id);
            }
            ArcadiaDungeon.LOGGER.info("FULLRUN {} started", label);
        }

        /**
         * A copy this world can hold. The id is new on every run: a finished dungeon goes on an
         * availability timer that survives on disk, and the next test run would be refused.
         */
        private DungeonConfig playableCopy(DungeonConfig original) {
            Gson gson = new Gson();
            DungeonConfig copy = gson.fromJson(gson.toJson(original), DungeonConfig.class);
            copy.id = "fullrun_" + UUID.randomUUID().toString().substring(0, 8);
            copy.requiredDungeon = "";
            copy.settings.timeLimitSeconds = 0;
            String dimension = RunTestKit.dimension(h);
            copy.spawnPoint.dimension = dimension;
            if (copy.hasArea()) {
                copy.areaPos1.dimension = dimension;
                copy.areaPos2.dimension = dimension;
            }
            for (WaveConfig wave : copy.waves) {
                for (MobSpawnConfig mob : wave.mobs) mob.spawnPoint.dimension = dimension;
            }
            for (BossConfig boss : copy.bosses) {
                boss.spawnPoint.dimension = dimension;
                // A chance would make the order of the run differ from one test to the next.
                if (boss.optional) boss.spawnChance = 1.0;
            }
            return copy;
        }

        private void buildFloors(DungeonConfig config) {
            List<SpawnPointConfig> points = new ArrayList<>();
            points.add(config.spawnPoint);
            for (WaveConfig wave : config.waves) {
                for (MobSpawnConfig mob : wave.mobs) points.add(mob.spawnPoint);
            }
            for (BossConfig boss : config.bosses) points.add(boss.spawnPoint);
            for (SpawnPointConfig point : points) {
                BlockPos center = BlockPos.containing(point.x, point.y - 1, point.z);
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }

        private void observe(DungeonInstance run) {
            for (Map.Entry<String, BossInstance> entry : run.getActiveBosses().entrySet()) {
                if (bossesSeen.add(entry.getKey())) played.add("boss " + entry.getKey());
                lastPhase.merge(entry.getKey(), entry.getValue().getCurrentPhase(), Math::max);
                summonsSeen.merge(entry.getKey(), entry.getValue().getSummonedMobs().size(), Math::max);
            }
            WaveInstance wave = run.getCurrentWave();
            if (wave != null && wave.isSpawned() && wavesSeen.add(wave.getConfig().waveNumber)) {
                played.add("wave " + wave.getConfig().waveNumber);
                int expected = 0;
                for (MobSpawnConfig mob : wave.getConfig().mobs) expected += mob.count;
                int spawned = wave.getRemainingMobs();
                if (spawned < expected) {
                    failure = label + ": wave " + wave.getConfig().waveNumber + " spawned " + spawned + " of " + expected + " mobs";
                    cleanUp();
                }
            }
        }

        /** Hits everything through the damage path a player's weapon goes through. */
        private void fight(DungeonInstance run) {
            for (BossInstance boss : List.copyOf(run.getActiveBosses().values())) {
                for (LivingEntity summon : boss.getSummonedMobs()) hit(summon);
                LivingEntity entity = boss.getBossEntity();
                if (entity != null && !entity.isRemoved()) hit(entity);
            }
            WaveInstance wave = run.getCurrentWave();
            if (wave != null) {
                for (LivingEntity mob : wave.getAliveMobs()) hit(mob);
            }
        }

        private void hit(LivingEntity entity) {
            if (!entity.isAlive()) return;
            entity.invulnerableTime = 0;
            RunTestKit.hitAs(h, player, entity, 100000F);
        }

        private void finishCurrent() {
            List<String> expected = new ArrayList<>();
            for (BossConfig boss : current.bosses) {
                if (boss.spawnAtStart) expected.add("boss " + boss.id);
            }
            for (WaveConfig wave : current.waves) {
                expected.add("wave " + wave.waveNumber);
                for (BossConfig boss : current.bosses) {
                    if (boss.spawnAfterWave == wave.waveNumber) expected.add("boss " + boss.id);
                }
            }
            for (BossConfig boss : current.bosses) {
                if (boss.spawnAfterWave == 0 && !boss.spawnAtStart) expected.add("boss " + boss.id);
            }

            ArcadiaDungeon.LOGGER.info("FULLRUN {} finished: {}", label, played);
            if (!played.equals(expected)) {
                failure = label + " played out of order.\n  expected " + expected + "\n  played   " + played;
            }
            for (BossConfig boss : current.bosses) {
                if (failure != null) break;
                int phases = boss.phases.size();
                if (phases > 0 && lastPhase.getOrDefault(boss.id, -2) != phases - 1) {
                    failure = label + ": boss " + boss.id + " ended at phase index "
                            + lastPhase.get(boss.id) + " of " + phases;
                }
                boolean summons = boss.phases.stream().map(p -> p.summonMobs).anyMatch(s -> !s.isEmpty());
                if (summons && summonsSeen.getOrDefault(boss.id, 0) == 0) {
                    failure = label + ": boss " + boss.id + " never had its summons";
                }
            }
            cleanUp();
        }

        private void cleanUp() {
            if (current != null) {
                DungeonManager.getInstance().forceReset(current.id);
                ConfigManager.getInstance().deleteDungeon(current.id);
            }
            current = null;
        }
    }
}
