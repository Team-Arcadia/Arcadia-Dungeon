package com.vyrriox.arcadiadungeon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path configDir;
    private final Path dungeonsDir;
    private final Map<String, DungeonConfig> dungeonConfigs = new ConcurrentHashMap<>();

    private ConfigManager() {
        this.configDir = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon");
        this.dungeonsDir = configDir.resolve("dungeons");
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public void loadAll() {
        try {
            Files.createDirectories(dungeonsDir);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to create config directories", e);
            return;
        }

        dungeonConfigs.clear();

        // Load all dungeon configs
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dungeonsDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file);
                    DungeonConfig config = GSON.fromJson(json, DungeonConfig.class);
                    if (config != null && config.id != null && !config.id.isEmpty()) {
                        validateConfig(config);
                        dungeonConfigs.put(config.id, config);
                        ArcadiaDungeon.LOGGER.info("Loaded dungeon config: {}", config.id);
                    }
                } catch (Exception e) {
                    ArcadiaDungeon.LOGGER.error("Failed to load dungeon config: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read dungeon configs directory", e);
        }

        // Create example config if none exist
        if (dungeonConfigs.isEmpty()) {
            createExampleConfig();
        }
    }

    public void saveDungeon(DungeonConfig config) {
        try {
            Files.createDirectories(dungeonsDir);
            Path file = dungeonsDir.resolve(config.id + ".json");
            String json = GSON.toJson(config);
            Files.writeString(file, json);
            dungeonConfigs.put(config.id, config);
            ArcadiaDungeon.LOGGER.info("Saved dungeon config: {}", config.id);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to save dungeon config: {}", config.id, e);
        }
    }

    public boolean deleteDungeon(String id) {
        try {
            Path file = dungeonsDir.resolve(id + ".json");
            if (Files.exists(file)) {
                Files.delete(file);
                dungeonConfigs.remove(id);
                return true;
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to delete dungeon config: {}", id, e);
        }
        return false;
    }

    public DungeonConfig getDungeon(String id) {
        return dungeonConfigs.get(id);
    }

    public Map<String, DungeonConfig> getDungeonConfigs() {
        return Collections.unmodifiableMap(dungeonConfigs);
    }

    private void validateConfig(DungeonConfig config) {
        if (config.spawnPoint == null) config.spawnPoint = new SpawnPointConfig();
        if (config.settings == null) config.settings = new DungeonSettings();
        if (config.bosses == null) config.bosses = new java.util.ArrayList<>();
        if (config.waves == null) config.waves = new java.util.ArrayList<>();
        if (config.completionRewards == null) config.completionRewards = new java.util.ArrayList<>();
        if (config.scriptedWalls == null) config.scriptedWalls = new java.util.ArrayList<>();
        for (BossConfig boss : config.bosses) {
            if (boss.spawnPoint == null) boss.spawnPoint = new SpawnPointConfig();
            if (boss.phases == null) boss.phases = new java.util.ArrayList<>();
            if (boss.rewards == null) boss.rewards = new java.util.ArrayList<>();
        }
        for (WaveConfig wave : config.waves) {
            if (wave.mobs == null) wave.mobs = new java.util.ArrayList<>();
            for (MobSpawnConfig mob : wave.mobs) {
                if (mob.spawnPoint == null) mob.spawnPoint = new SpawnPointConfig();
            }
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    private void createExampleConfig() {
        DungeonConfig example = new DungeonConfig("example_dungeon", "Donjon Exemple");
        example.cooldownSeconds = 3600;
        example.announceStart = true;
        example.announceCompletion = true;
        example.teleportBackOnComplete = true;
        example.spawnPoint = new SpawnPointConfig("minecraft:overworld", 100, 64, 100, 0, 0);

        BossConfig boss = new BossConfig("guardian", "minecraft:wither_skeleton", 200, 15);
        boss.customName = "Gardien des Tenebres";
        boss.showBossBar = true;
        boss.adaptivePower = true;
        boss.spawnPoint = new SpawnPointConfig("minecraft:overworld", 110, 64, 110, 0, 0);

        PhaseConfig phase1 = new PhaseConfig(1, 1.0);
        phase1.description = "Phase normale";
        phase1.damageMultiplier = 1.0;
        phase1.speedMultiplier = 1.0;

        PhaseConfig phase2 = new PhaseConfig(2, 0.5);
        phase2.description = "Phase enragee - Le boss invoque des sbires!";
        phase2.damageMultiplier = 1.5;
        phase2.speedMultiplier = 1.3;
        phase2.phaseStartMessage = "&c[Boss] &4Le Gardien entre en rage!";
        phase2.requiredAction = "KILL_SUMMONS";
        SummonConfig summon = new SummonConfig("minecraft:zombie", 3);
        summon.customName = "Sbire du Gardien";
        phase2.summonMobs.add(summon);

        PhaseConfig phase3 = new PhaseConfig(3, 0.2);
        phase3.description = "Phase finale - Dernier souffle!";
        phase3.damageMultiplier = 2.0;
        phase3.speedMultiplier = 1.5;
        phase3.phaseStartMessage = "&4[Boss] &cLe Gardien pousse un cri terrible!";
        phase3.invulnerableDuringTransition = true;
        phase3.transitionDurationSeconds = 2.0;

        boss.phases.add(phase1);
        boss.phases.add(phase2);
        boss.phases.add(phase3);

        boss.rewards.add(new RewardConfig("minecraft:diamond", 5, 0.8));
        boss.rewards.add(new RewardConfig("minecraft:netherite_ingot", 1, 0.1));

        example.bosses.add(boss);
        example.completionRewards.add(new RewardConfig("minecraft:experience_bottle", 10, 1.0));

        // Add example waves (intermediate mobs before boss)
        example.order = 1;

        WaveConfig wave1 = new WaveConfig(1);
        wave1.name = "Premiere vague";
        wave1.startMessage = "&e[Donjon] &7Des ennemis approchent!";
        MobSpawnConfig zombie1 = new MobSpawnConfig("minecraft:zombie", 3,
                new SpawnPointConfig("minecraft:overworld", 105, 64, 105, 0, 0));
        zombie1.customName = "Zombie du Donjon";
        zombie1.health = 30;
        wave1.mobs.add(zombie1);

        WaveConfig wave2 = new WaveConfig(2);
        wave2.name = "Deuxieme vague";
        wave2.startMessage = "&e[Donjon] &7Des squelettes arrivent!";
        MobSpawnConfig skel = new MobSpawnConfig("minecraft:skeleton", 4,
                new SpawnPointConfig("minecraft:overworld", 108, 64, 108, 0, 0));
        skel.customName = "Squelette du Donjon";
        skel.health = 25;
        skel.damage = 5;
        skel.mainHand = "minecraft:iron_sword";
        skel.helmet = "minecraft:iron_helmet";
        wave2.mobs.add(skel);
        MobSpawnConfig spider = new MobSpawnConfig("minecraft:spider", 2,
                new SpawnPointConfig("minecraft:overworld", 103, 64, 108, 0, 0));
        wave2.mobs.add(spider);

        example.waves.add(wave1);
        example.waves.add(wave2);

        example.settings.maxPlayers = 4;
        example.settings.timeLimitSeconds = 1800;
        example.settings.maxDeaths = 3;

        saveDungeon(example);
        ArcadiaDungeon.LOGGER.info("Created example dungeon config");
    }
}
