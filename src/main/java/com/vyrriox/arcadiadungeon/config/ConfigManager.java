package com.vyrriox.arcadiadungeon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private final Path documentationDir;
    private final Path examplesDir;
    private final Path schemaPath;
    private final Map<String, DungeonConfig> dungeonConfigs = new ConcurrentHashMap<>();

    private ConfigManager() {
        this.configDir = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon");
        this.dungeonsDir = configDir.resolve("dungeons");
        this.documentationDir = Path.of("dungeon-configs");
        this.examplesDir = documentationDir.resolve("examples");
        this.schemaPath = documentationDir.resolve("dungeon-schema.json");
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    public synchronized void loadAll() {
        try {
            ensureDirectoryLayout();
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to create config directories", e);
            return;
        }

        Map<String, DungeonConfig> previousConfigs = Map.copyOf(dungeonConfigs);
        Map<String, DungeonConfig> loadedConfigs = new ConcurrentHashMap<>();
        boolean foundAnyConfig = false;

        // Load all dungeon configs
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dungeonsDir, "*.json")) {
            for (Path file : stream) {
                foundAnyConfig = true;
                loadConfigFile(file, previousConfigs, loadedConfigs);
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read dungeon configs directory", e);
        }

        // Create example config if none exist
        if (!foundAnyConfig && previousConfigs.isEmpty()) {
            DungeonConfig example = buildExampleConfig();
            try {
                writeAnnotatedExampleConfig(example, dungeonsDir.resolve(example.id + ".json"));
                loadedConfigs.put(example.id, example);
                ArcadiaDungeon.LOGGER.info("Created example dungeon config");
            } catch (IOException e) {
                ArcadiaDungeon.LOGGER.error("Failed to create example dungeon config", e);
            }
        }

        dungeonConfigs.clear();
        dungeonConfigs.putAll(loadedConfigs);

        writeDocumentationArtifacts();
    }

    public void saveDungeon(DungeonConfig config) {
        try {
            ensureDirectoryLayout();
            Path file = dungeonsDir.resolve(config.id + ".json");
            String json = GSON.toJson(config);
            Files.writeString(file, json);
            dungeonConfigs.put(config.id, config);
            ArcadiaDungeon.LOGGER.info("Saved dungeon config: {}", config.id);
            writeDocumentationArtifacts();
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
        if (config.requiredDungeon == null) config.requiredDungeon = "";
        for (BossConfig boss : config.bosses) {
            if (boss.spawnPoint == null) boss.spawnPoint = new SpawnPointConfig();
            if (boss.phases == null) boss.phases = new java.util.ArrayList<>();
            if (boss.rewards == null) boss.rewards = new java.util.ArrayList<>();
            for (PhaseConfig phase : boss.phases) {
                if (phase.summonMobs == null) phase.summonMobs = new java.util.ArrayList<>();
                if (phase.playerEffects == null) phase.playerEffects = new java.util.ArrayList<>();
                if (phase.phaseCommands == null) phase.phaseCommands = new java.util.ArrayList<>();
            }
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

    private void ensureDirectoryLayout() throws IOException {
        Files.createDirectories(dungeonsDir);
        Files.createDirectories(examplesDir);
    }

    private void loadConfigFile(Path file, Map<String, DungeonConfig> previousConfigs, Map<String, DungeonConfig> loadedConfigs) {
        String fileName = file.getFileName().toString();
        String fallbackId = stripJsonExtension(fileName);
        try {
            String json = Files.readString(file);
            DungeonConfig config = GSON.fromJson(json, DungeonConfig.class);
            if (config == null || config.id == null || config.id.isEmpty()) {
                ArcadiaDungeon.LOGGER.warn("Skipped dungeon config {}: missing non-empty id", fileName);
                return;
            }

            validateConfig(config);
            loadedConfigs.put(config.id, config);
            ArcadiaDungeon.LOGGER.info("Loaded dungeon config: {}", config.id);
        } catch (Exception e) {
            DungeonConfig previous = previousConfigs.get(fallbackId);
            if (previous != null) {
                loadedConfigs.put(fallbackId, previous);
                ArcadiaDungeon.LOGGER.warn("Failed to reload dungeon config: {}. Keeping previous valid version.", fileName, e);
            } else {
                ArcadiaDungeon.LOGGER.warn("Failed to load dungeon config: {}. Skipping file.", fileName, e);
            }
        }
    }

    private String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private DungeonConfig buildExampleConfig() {
        DungeonConfig example = new DungeonConfig("example_dungeon", "Donjon Exemple");
        example.cooldownSeconds = 3600;
        example.announceStart = true;
        example.announceCompletion = true;
        example.announceIntervalMinutes = 30;
        example.teleportBackOnComplete = true;
        example.spawnPoint = new SpawnPointConfig("minecraft:overworld", 100, 64, 100, 0, 0);

        BossConfig boss = new BossConfig("guardian", "minecraft:wither_skeleton", 200, 15);
        boss.customName = "Gardien des Tenebres";
        boss.showBossBar = true;
        boss.adaptivePower = true;
        boss.spawnPoint = new SpawnPointConfig("minecraft:overworld", 110, 64, 110, 0, 0);
        boss.attackRange = 4.5;
        boss.attackCooldownMs = 800;
        boss.aggroRange = 24.0;
        boss.dodgeChance = 0.1;
        boss.dodgeCooldownMs = 3000;
        boss.dodgeMessage = "&6[Boss] &eLe Gardien esquive l'attaque!";

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
        phase2.immunityDuration = 3.0;
        SummonConfig summon = new SummonConfig("minecraft:zombie", 3);
        summon.customName = "Sbire du Gardien";
        summon.spawnPoint = new SpawnPointConfig("minecraft:overworld", 112, 64, 110, 0, 0);
        summon.attackRange = 3.5;
        summon.dodgeChance = 0.05;
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
        zombie1.attackRange = 3.5;
        wave1.mobs.add(zombie1);

        WaveConfig wave2 = new WaveConfig(2);
        wave2.name = "Deuxieme vague";
        wave2.startMessage = "&e[Donjon] &7Des squelettes arrivent!";
        MobSpawnConfig skel = new MobSpawnConfig("minecraft:skeleton", 4,
                new SpawnPointConfig("minecraft:overworld", 108, 64, 108, 0, 0));
        skel.customName = "Squelette du Donjon";
        skel.health = 25;
        skel.damage = 5;
        skel.projectileCooldownMs = 1200;
        skel.aggroRange = 20.0;
        skel.mainHand = "minecraft:iron_sword";
        skel.helmet = "minecraft:iron_helmet";
        wave2.mobs.add(skel);
        MobSpawnConfig spider = new MobSpawnConfig("minecraft:spider", 2,
                new SpawnPointConfig("minecraft:overworld", 103, 64, 108, 0, 0));
        wave2.mobs.add(spider);

        example.waves.add(wave1);
        example.waves.add(wave2);

        DungeonConfig.ScriptedWallConfig wall = new DungeonConfig.ScriptedWallConfig();
        wall.id = "boss_gate";
        wall.activationCondition = "WAVE_COMPLETE:2";
        wall.action = "REMOVE";
        wall.blocks.add(new DungeonConfig.AreaPos("minecraft:overworld", 109, 64, 109));
        wall.blocks.add(new DungeonConfig.AreaPos("minecraft:overworld", 109, 65, 109));
        example.scriptedWalls.add(wall);

        example.settings.maxPlayers = 4;
        example.settings.timeLimitSeconds = 1800;
        example.settings.maxDeaths = 3;
        example.debugMode = false;

        return example;
    }

    private void writeDocumentationArtifacts() {
        DungeonConfig example = dungeonConfigs.getOrDefault("example_dungeon", buildExampleConfig());
        try {
            ensureDirectoryLayout();
            Files.writeString(schemaPath, GSON.toJson(buildDungeonSchema()));
            writeAnnotatedExampleConfig(example, examplesDir.resolve("example_dungeon.json"));
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to generate dungeon documentation artifacts", e);
        }
    }

    private void writeAnnotatedExampleConfig(DungeonConfig example, Path target) throws IOException {
        JsonObject root = GSON.toJsonTree(example).getAsJsonObject();
        root.addProperty("_comment", "Example generated by Arcadia Dungeon. Unknown _comment fields are ignored by the loader.");
        root.addProperty("_comment_placeholders", "%player%, %dungeon% and %id% are available in message fields.");

        JsonObject settings = root.getAsJsonObject("settings");
        if (settings != null) {
            settings.addProperty("_comment_timerWarnings", "Seconds before timeout when warning messages are sent.");
        }

        JsonArray waves = root.getAsJsonArray("waves");
        if (waves != null && !waves.isEmpty()) {
            waves.get(0).getAsJsonObject().addProperty("_comment", "Waves run in order and must be fully cleared before the next one starts.");
        }

        JsonArray bosses = root.getAsJsonArray("bosses");
        if (bosses != null && !bosses.isEmpty()) {
            bosses.get(0).getAsJsonObject().addProperty("_comment", "Bosses can spawn at start, after a wave, or after all waves depending on their flags.");
        }

        Files.writeString(target, GSON.toJson(root));
    }

    private JsonObject buildDungeonSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.addProperty("title", "Arcadia Dungeon Config");
        schema.addProperty("type", "object");
        schema.add("properties", buildDungeonConfigProperties());
        schema.add("required", arrayOf("id", "name", "spawnPoint"));
        schema.add("additionalProperties", primitive(true));

        JsonObject defs = new JsonObject();
        defs.add("spawnPoint", objectSchema(buildSpawnPointProperties(), "Spawn point for dungeon, boss, or mob."));
        defs.add("reward", objectSchema(buildRewardProperties(), "Reward definition."));
        defs.add("phaseEffect", objectSchema(buildPhaseEffectProperties(), "Potion effect applied during a phase transition."));
        defs.add("summon", objectSchema(buildSummonProperties(), "Summoned mob during a boss phase."));
        defs.add("phase", objectSchema(buildPhaseProperties(), "Boss phase definition."));
        defs.add("boss", objectSchema(buildBossProperties(), "Boss definition."));
        defs.add("mobSpawn", objectSchema(buildMobSpawnProperties(), "Wave mob definition."));
        defs.add("wave", objectSchema(buildWaveProperties(), "Wave definition."));
        defs.add("areaPos", objectSchema(buildAreaPosProperties(), "Area corner position."));
        defs.add("scriptedWall", objectSchema(buildScriptedWallProperties(), "Scripted wall definition."));
        defs.add("settings", objectSchema(buildSettingsProperties(), "Dungeon runtime settings."));
        schema.add("$defs", defs);
        return schema;
    }

    private JsonObject buildDungeonConfigProperties() {
        JsonObject props = new JsonObject();
        props.add("id", stringSchema("example_dungeon", "Unique dungeon identifier."));
        props.add("name", stringSchema("Donjon Exemple", "Display name."));
        props.add("cooldownSeconds", intSchema(3600, "Cooldown after completion in seconds."));
        props.add("announceStart", boolSchema(true, "Broadcast the start message."));
        props.add("announceCompletion", boolSchema(true, "Broadcast completion/fail messages."));
        props.add("startMessage", stringSchema("&6[Donjon] ...", "Global start announcement."));
        props.add("completionMessage", stringSchema("&6[Donjon] ...", "Global completion announcement."));
        props.add("failMessage", stringSchema("&6[Donjon] ...", "Global fail announcement."));
        props.add("teleportBackOnComplete", boolSchema(true, "Teleport players back on completion/fail."));
        props.add("spawnPoint", refSchema("spawnPoint"));
        props.add("bosses", arraySchema(refSchema("boss"), "Configured bosses."));
        props.add("completionRewards", arraySchema(refSchema("reward"), "Rewards granted on completion."));
        props.add("settings", refSchema("settings"));
        props.add("enabled", boolSchema(true, "Whether the dungeon can be started or joined."));
        props.add("availableEverySeconds", intSchema(0, "Availability timer after a run."));
        props.add("announceAvailability", boolSchema(true, "Broadcast availability announcements."));
        props.add("announceIntervalMinutes", intSchema(0, "Periodic idle availability announcement interval in minutes."));
        props.add("availabilityMessage", stringSchema("&6[Donjon] ...", "Availability announcement."));
        props.add("requiredPermission", stringSchema("", "Optional permission node."));
        props.add("order", intSchema(0, "Display order."));
        props.add("requiredDungeon", stringSchema("", "Required dungeon id to unlock this dungeon."));
        props.add("debugMode", boolSchema(false, "Enable verbose DEBUG logs for this dungeon."));
        props.add("waves", arraySchema(refSchema("wave"), "Configured waves."));
        props.add("recruitmentDurationSeconds", intSchema(90, "Recruitment duration before the run starts."));
        props.add("recruitmentMessage", stringSchema("&6[Donjon] ...", "Recruitment announcement."));
        props.add("areaPos1", nullableRefSchema("areaPos"));
        props.add("areaPos2", nullableRefSchema("areaPos"));
        props.add("scriptedWalls", arraySchema(refSchema("scriptedWall"), "Scripted walls in the dungeon area."));
        return props;
    }

    private JsonObject buildBossProperties() {
        JsonObject props = new JsonObject();
        props.add("id", stringSchema("guardian", "Boss identifier."));
        props.add("entityType", stringSchema("minecraft:zombie", "Minecraft entity id."));
        props.add("customName", stringSchema("Boss", "Displayed custom name."));
        props.add("baseHealth", numberSchema(100, "Base health."));
        props.add("baseDamage", numberSchema(10, "Base damage."));
        props.add("adaptivePower", boolSchema(true, "Scale boss with player count."));
        props.add("healthMultiplierPerPlayer", numberSchema(0.5, "Extra health per additional player."));
        props.add("damageMultiplierPerPlayer", numberSchema(0.1, "Extra damage per additional player."));
        props.add("spawnPoint", refSchema("spawnPoint"));
        props.add("areaPos1", nullableRefSchema("areaPos"));
        props.add("areaPos2", nullableRefSchema("areaPos"));
        props.add("phases", arraySchema(refSchema("phase"), "Boss phases."));
        props.add("rewards", arraySchema(refSchema("reward"), "Boss rewards."));
        props.add("showBossBar", boolSchema(true, "Display the boss bar."));
        props.add("bossBarColor", stringSchema("RED", "Boss bar color."));
        props.add("optional", boolSchema(false, "Whether the boss may be skipped."));
        props.add("spawnChance", numberSchema(1.0, "Chance for optional bosses."));
        props.add("spawnMessage", stringSchema("", "Message when the boss spawns."));
        props.add("skipMessage", stringSchema("", "Message when an optional boss is skipped."));
        props.add("spawnAfterWave", intSchema(0, "0 = after all waves, N = after wave N."));
        props.add("spawnAtStart", boolSchema(false, "Spawn when the dungeon starts."));
        props.add("requiredKill", boolSchema(true, "Must be killed to complete the dungeon."));
        props.add("mainHand", stringSchema("", "Main hand item id."));
        props.add("offHand", stringSchema("", "Off hand item id."));
        props.add("helmet", stringSchema("", "Helmet item id."));
        props.add("chestplate", stringSchema("", "Chestplate item id."));
        props.add("leggings", stringSchema("", "Leggings item id."));
        props.add("boots", stringSchema("", "Boots item id."));
        props.add("customAttributes", mapSchema("Custom attributes map."));
        props.add("attackRange", numberSchema(0, "Extended melee range stored in ArcadiaCombat."));
        props.add("attackCooldownMs", intSchema(0, "Melee attack cooldown in milliseconds."));
        props.add("aggroRange", numberSchema(0, "Follow range override stored in ArcadiaCombat."));
        props.add("projectileCooldownMs", intSchema(0, "Projectile cooldown in milliseconds."));
        props.add("dodgeChance", numberSchema(0, "Dodge chance between 0.0 and 1.0."));
        props.add("dodgeCooldownMs", intSchema(0, "Minimum delay between dodges in milliseconds."));
        props.add("dodgeProjectilesOnly", boolSchema(false, "If true, dodge only against projectiles."));
        props.add("dodgeMessage", stringSchema("", "Broadcast message when a dodge succeeds."));
        return props;
    }

    private JsonObject buildWaveProperties() {
        JsonObject props = new JsonObject();
        props.add("waveNumber", intSchema(1, "Wave index."));
        props.add("name", stringSchema("Vague 1", "Wave display name."));
        props.add("delayBeforeSeconds", intSchema(0, "Delay before the wave spawns."));
        props.add("mobs", arraySchema(refSchema("mobSpawn"), "Mob spawn entries."));
        props.add("startMessage", stringSchema("", "Message when the wave starts."));
        props.add("glowingAfterDelay", boolSchema(true, "Enable delayed glowing."));
        props.add("glowingDelaySeconds", intSchema(60, "Delay before glowing is applied."));
        return props;
    }

    private JsonObject buildMobSpawnProperties() {
        JsonObject props = new JsonObject();
        props.add("entityType", stringSchema("minecraft:zombie", "Minecraft entity id."));
        props.add("count", intSchema(1, "Base mob count."));
        props.add("customName", stringSchema("", "Displayed custom name."));
        props.add("health", numberSchema(20, "Base health."));
        props.add("damage", numberSchema(3, "Base damage."));
        props.add("speed", numberSchema(0, "Movement speed override."));
        props.add("spawnPoint", refSchema("spawnPoint"));
        props.add("areaPos1", nullableRefSchema("areaPos"));
        props.add("areaPos2", nullableRefSchema("areaPos"));
        props.add("mainHand", stringSchema("", "Main hand item id."));
        props.add("offHand", stringSchema("", "Off hand item id."));
        props.add("helmet", stringSchema("", "Helmet item id."));
        props.add("chestplate", stringSchema("", "Chestplate item id."));
        props.add("leggings", stringSchema("", "Leggings item id."));
        props.add("boots", stringSchema("", "Boots item id."));
        props.add("customAttributes", mapSchema("Custom attributes map."));
        props.add("attackRange", numberSchema(0, "Extended melee range stored in ArcadiaCombat."));
        props.add("attackCooldownMs", intSchema(0, "Melee attack cooldown in milliseconds."));
        props.add("aggroRange", numberSchema(0, "Follow range override stored in ArcadiaCombat."));
        props.add("projectileCooldownMs", intSchema(0, "Projectile cooldown in milliseconds."));
        props.add("dodgeChance", numberSchema(0, "Dodge chance between 0.0 and 1.0."));
        props.add("dodgeCooldownMs", intSchema(0, "Minimum delay between dodges in milliseconds."));
        props.add("dodgeProjectilesOnly", boolSchema(false, "If true, dodge only against projectiles."));
        props.add("dodgeMessage", stringSchema("", "Broadcast message when a dodge succeeds."));
        return props;
    }

    private JsonObject buildPhaseProperties() {
        JsonObject props = new JsonObject();
        props.add("phase", intSchema(1, "Phase index."));
        props.add("healthThreshold", numberSchema(1.0, "Threshold ratio triggering the phase."));
        props.add("damageMultiplier", numberSchema(1.0, "Damage multiplier for this phase."));
        props.add("speedMultiplier", numberSchema(1.0, "Speed multiplier for this phase."));
        props.add("description", stringSchema("", "Description for admins."));
        props.add("summonMobs", arraySchema(refSchema("summon"), "Summoned mobs during the phase."));
        props.add("requiredAction", stringSchema("NONE", "Special action required to continue."));
        props.add("phaseStartMessage", stringSchema("", "Message sent when the phase starts."));
        props.add("invulnerableDuringTransition", boolSchema(false, "Make the boss invulnerable during transition."));
        props.add("transitionDurationSeconds", numberSchema(2.0, "Transition duration."));
        props.add("immunityDuration", numberSchema(0.0, "Alias for transition invulnerability duration in seconds."));
        props.add("playerEffects", arraySchema(refSchema("phaseEffect"), "Effects applied to players."));
        props.add("phaseCommands", arraySchema(stringSchema("", "Command"), "Commands executed when the phase starts."));
        return props;
    }

    private JsonObject buildSummonProperties() {
        JsonObject props = new JsonObject();
        props.add("entityType", stringSchema("minecraft:zombie", "Minecraft entity id."));
        props.add("count", intSchema(1, "Summoned mob count."));
        props.add("customName", stringSchema("", "Displayed custom name."));
        props.add("health", numberSchema(20, "Base health."));
        props.add("damage", numberSchema(3, "Base damage."));
        props.add("spawnPoint", nullableRefSchema("spawnPoint"));
        props.add("mainHand", stringSchema("", "Main hand item id."));
        props.add("offHand", stringSchema("", "Off hand item id."));
        props.add("helmet", stringSchema("", "Helmet item id."));
        props.add("chestplate", stringSchema("", "Chestplate item id."));
        props.add("leggings", stringSchema("", "Leggings item id."));
        props.add("boots", stringSchema("", "Boots item id."));
        props.add("customAttributes", mapSchema("Custom attributes map."));
        props.add("attackRange", numberSchema(0, "Extended melee range stored in ArcadiaCombat."));
        props.add("attackCooldownMs", intSchema(0, "Melee attack cooldown in milliseconds."));
        props.add("aggroRange", numberSchema(0, "Follow range override stored in ArcadiaCombat."));
        props.add("projectileCooldownMs", intSchema(0, "Projectile cooldown in milliseconds."));
        props.add("dodgeChance", numberSchema(0, "Dodge chance between 0.0 and 1.0."));
        props.add("dodgeCooldownMs", intSchema(0, "Minimum delay between dodges in milliseconds."));
        props.add("dodgeProjectilesOnly", boolSchema(false, "If true, dodge only against projectiles."));
        props.add("dodgeMessage", stringSchema("", "Broadcast message when a dodge succeeds."));
        return props;
    }

    private JsonObject buildPhaseEffectProperties() {
        JsonObject props = new JsonObject();
        props.add("effect", stringSchema("minecraft:slowness", "Effect id."));
        props.add("durationSeconds", intSchema(10, "Duration in seconds."));
        props.add("amplifier", intSchema(0, "Amplifier."));
        return props;
    }

    private JsonObject buildRewardProperties() {
        JsonObject props = new JsonObject();
        props.add("item", stringSchema("minecraft:diamond", "Reward item id."));
        props.add("count", intSchema(1, "Item count."));
        props.add("chance", numberSchema(1.0, "Reward drop chance."));
        props.add("command", stringSchema("", "Command reward."));
        props.add("experience", intSchema(0, "Experience reward."));
        return props;
    }

    private JsonObject buildSettingsProperties() {
        JsonObject props = new JsonObject();
        props.add("maxPlayers", intSchema(4, "Maximum players."));
        props.add("minPlayers", intSchema(1, "Minimum players."));
        props.add("timeLimitSeconds", intSchema(1800, "Time limit in seconds."));
        props.add("pvp", boolSchema(false, "Allow PVP."));
        props.add("difficultyScaling", boolSchema(true, "Enable adaptive scaling."));
        props.add("waveHealthMultiplierPerPlayer", numberSchema(0.3, "Health scaling per extra player."));
        props.add("waveDamageMultiplierPerPlayer", numberSchema(0.1, "Damage scaling per extra player."));
        props.add("waveCountMultiplierPerPlayer", numberSchema(0.2, "Mob count scaling per extra player."));
        props.add("maxDeaths", intSchema(3, "Maximum deaths before ejection."));
        props.add("antiMonopole", boolSchema(true, "Enable anti-monopoly join rules."));
        props.add("antiMonopoleThreshold", intSchema(5, "Weekly completion threshold."));
        props.add("timerWarnings", arraySchema(intSchema(300, "Warning seconds"), "Timer warning milestones."));
        props.add("blockTeleportCommands", boolSchema(true, "Block teleport commands in dungeon."));
        props.add("blockedCommands", arraySchema(stringSchema("tpa", "Blocked command root"), "Blocked command list."));
        return props;
    }

    private JsonObject buildSpawnPointProperties() {
        JsonObject props = new JsonObject();
        props.add("dimension", stringSchema("minecraft:overworld", "Dimension id."));
        props.add("x", numberSchema(0, "X coordinate."));
        props.add("y", numberSchema(64, "Y coordinate."));
        props.add("z", numberSchema(0, "Z coordinate."));
        props.add("yaw", numberSchema(0, "Yaw."));
        props.add("pitch", numberSchema(0, "Pitch."));
        return props;
    }

    private JsonObject buildAreaPosProperties() {
        JsonObject props = new JsonObject();
        props.add("dimension", stringSchema("minecraft:overworld", "Dimension id."));
        props.add("x", intSchema(0, "Block X."));
        props.add("y", intSchema(0, "Block Y."));
        props.add("z", intSchema(0, "Block Z."));
        return props;
    }

    private JsonObject buildScriptedWallProperties() {
        JsonObject props = new JsonObject();
        props.add("id", stringSchema("", "Wall identifier."));
        props.add("blocks", arraySchema(refSchema("areaPos"), "Wall blocks."));
        props.add("activationCondition", stringSchema("", "Supported conditions: DUNGEON_START, WAVE_START:N, WAVE_COMPLETE:N, PHASE_START:N."));
        props.add("action", stringSchema("TOGGLE", "Action type. TOGGLE/PLACE places barrier blocks, REMOVE deletes them."));
        return props;
    }

    private JsonObject objectSchema(JsonObject properties, String description) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "object");
        object.addProperty("description", description);
        object.add("properties", properties);
        object.add("additionalProperties", primitive(true));
        return object;
    }

    private JsonObject stringSchema(String defaultValue, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("default", defaultValue);
        property.addProperty("description", description);
        return property;
    }

    private JsonObject boolSchema(boolean defaultValue, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "boolean");
        property.addProperty("default", defaultValue);
        property.addProperty("description", description);
        return property;
    }

    private JsonObject intSchema(int defaultValue, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "integer");
        property.addProperty("default", defaultValue);
        property.addProperty("description", description);
        return property;
    }

    private JsonObject numberSchema(double defaultValue, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "number");
        property.addProperty("default", defaultValue);
        property.addProperty("description", description);
        return property;
    }

    private JsonObject refSchema(String refName) {
        JsonObject property = new JsonObject();
        property.addProperty("$ref", "#/$defs/" + refName);
        return property;
    }

    private JsonObject nullableRefSchema(String refName) {
        JsonObject property = new JsonObject();
        JsonObject ref = refSchema(refName);
        property.add("anyOf", arrayOf(ref, primitive("null")));
        return property;
    }

    private JsonObject arraySchema(JsonElement items, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "array");
        property.add("items", items);
        property.addProperty("description", description);
        return property;
    }

    private JsonObject mapSchema(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "object");
        property.addProperty("description", description);
        JsonObject valueSchema = new JsonObject();
        valueSchema.addProperty("type", "number");
        property.add("additionalProperties", valueSchema);
        return property;
    }

    private JsonArray arrayOf(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private JsonArray arrayOf(JsonElement... values) {
        JsonArray array = new JsonArray();
        for (JsonElement value : values) {
            array.add(value);
        }
        return array;
    }

    private JsonElement primitive(boolean value) {
        return GSON.toJsonTree(value);
    }

    private JsonElement primitive(String value) {
        return GSON.toJsonTree(value);
    }
}
