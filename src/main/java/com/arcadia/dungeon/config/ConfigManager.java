package com.arcadia.dungeon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.dungeon.CombatTuning;
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
    private final Path progressionConfigPath;
    private final Path documentationDir;
    private final Path examplesDir;
    private final Path schemaPath;
    private final Map<String, DungeonConfig> dungeonConfigs = new ConcurrentHashMap<>();
    private volatile ArcadiaProgressionConfig progressionConfig = ArcadiaProgressionConfig.createDefault();

    private ConfigManager() {
        this.configDir = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon");
        this.dungeonsDir = configDir.resolve("dungeons");
        this.progressionConfigPath = configDir.resolve("arcadia-progression.json");
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

        loadProgressionConfig();

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
                ArcadiaDungeon.LOGGER.info("Configuración de mazmorra de ejemplo creada");
            } catch (IOException e) {
                ArcadiaDungeon.LOGGER.error("No se ha podido crear la configuración de la mazmorra de ejemplo", e);
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
            ArcadiaDungeon.LOGGER.info("Configuración de mazmorra guardada: {}", config.id);
            writeDocumentationArtifacts();
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("No se ha podido guardar la configuración de la mazmorra: {}", config.id, e);
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
            ArcadiaDungeon.LOGGER.error("No se ha podido eliminar la configuración de la mazmorra: {}", id, e);
        }
        return false;
    }

    public DungeonConfig getDungeon(String id) {
        return dungeonConfigs.get(id);
    }

    public Map<String, DungeonConfig> getDungeonConfigs() {
        return Collections.unmodifiableMap(dungeonConfigs);
    }

    public synchronized void loadProgressionConfig() {
        try {
            Files.createDirectories(configDir);
            if (Files.notExists(progressionConfigPath)) {
                progressionConfig = ArcadiaProgressionConfig.createDefault();
                Files.writeString(progressionConfigPath, GSON.toJson(progressionConfig));
                ArcadiaDungeon.LOGGER.info("Archivo «arcadia-progression.json» generado por defecto");
                return;
            }

            String json = Files.readString(progressionConfigPath);
            JsonObject rawObject = GSON.fromJson(json, JsonObject.class);
            ArcadiaProgressionConfig loadedConfig = GSON.fromJson(json, ArcadiaProgressionConfig.class);
            if (loadedConfig == null) {
                ArcadiaDungeon.LOGGER.warn("El archivo arcadia-progression.json está vacío. Se utilizará la configuración de progresión predeterminada..");
                progressionConfig = ArcadiaProgressionConfig.createDefault();
            } else {
                ArcadiaProgressionConfig defaults = ArcadiaProgressionConfig.createDefault();
                if (rawObject == null || !rawObject.has("milestoneRewards")) {
                    loadedConfig.milestoneRewards = defaults.milestoneRewards;
                }
                if (rawObject == null || !rawObject.has("streakBonuses")) {
                    loadedConfig.streakBonuses = defaults.streakBonuses;
                }
                loadedConfig.normalize();
                progressionConfig = loadedConfig;
            }
            ArcadiaDungeon.LOGGER.info("Configuración de progresión de Arcadia cargada: {} umbrales de nivel, {} umbrales de rango",
                    progressionConfig.levels.size(), progressionConfig.ranks.size());
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Error de E/S al cargar arcadia-progression.json. Se ha conservado la configuración anterior..", e);
            if (progressionConfig == null) {
                progressionConfig = ArcadiaProgressionConfig.createDefault();
            }
        } catch (JsonParseException e) {
            ArcadiaDungeon.LOGGER.error("El archivo arcadia-progression.json está dañado (JSON no válido). Se ha conservado la configuración anterior..", e);
            if (progressionConfig == null) {
                progressionConfig = ArcadiaProgressionConfig.createDefault();
            }
        }
    }

    public ArcadiaProgressionConfig getProgressionConfig() {
        return progressionConfig;
    }

    public synchronized void saveProgressionConfig() {
        try {
            Files.createDirectories(configDir);
            if (progressionConfig == null) {
                progressionConfig = ArcadiaProgressionConfig.createDefault();
            }
            progressionConfig.normalize();
            Files.writeString(progressionConfigPath, GSON.toJson(progressionConfig));
            ArcadiaDungeon.LOGGER.info("Configuración de progresión guardada de Arcadia");
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to save arcadia-progression.json", e);
        }
    }

    private void validateConfig(DungeonConfig config) {
        if (config.spawnPoint == null) config.spawnPoint = new SpawnPointConfig();
        if (config.settings == null) config.settings = new DungeonSettings();
        if (config.bosses == null) config.bosses = new java.util.ArrayList<>();
        if (config.waves == null) config.waves = new java.util.ArrayList<>();
        if (config.completionRewards == null) config.completionRewards = new java.util.ArrayList<>();
        if (config.scriptedWalls == null) config.scriptedWalls = new java.util.ArrayList<>();
        if (config.requiredDungeon == null) config.requiredDungeon = "";
        if (config.arcadiaXp < 0) config.arcadiaXp = 0;
        if (!Double.isFinite(config.difficultyMultiplier) || config.difficultyMultiplier < 0) {
            config.difficultyMultiplier = 1.0;
        }
        if (config.requiredArcadiaLevel < 0) config.requiredArcadiaLevel = 0;
        if (config.speedrunBonusSeconds < 0) config.speedrunBonusSeconds = 0;
        if (config.speedrunBonusXp < 0) config.speedrunBonusXp = 0;
        for (RewardConfig reward : config.completionRewards) {
            if (reward != null) reward.normalize();
        }
        for (BossConfig boss : config.bosses) {
            if (boss.spawnPoint == null) boss.spawnPoint = new SpawnPointConfig();
            if (boss.phases == null) boss.phases = new java.util.ArrayList<>();
            if (boss.rewards == null) boss.rewards = new java.util.ArrayList<>();
            migrateLegacyCombatAttributes(boss.customAttributes,
                    value -> boss.attackRange = Math.max(0.0D, value),
                    value -> boss.attackCooldownMs = Math.max(0, (int) Math.round(value)),
                    value -> boss.aggroRange = Math.max(0.0D, value),
                    value -> boss.projectileCooldownMs = Math.max(0, (int) Math.round(value)),
                    value -> boss.dodgeChance = Math.max(0.0D, Math.min(1.0D, value)),
                    value -> boss.dodgeCooldownMs = Math.max(0, (int) Math.round(value)),
                    value -> boss.dodgeProjectilesOnly = value >= 0.5D,
                    boss.attackRange,
                    boss.attackCooldownMs,
                    boss.aggroRange,
                    boss.projectileCooldownMs,
                    boss.dodgeChance,
                    boss.dodgeCooldownMs,
                    boss.dodgeProjectilesOnly);
            for (RewardConfig reward : boss.rewards) {
                if (reward != null) reward.normalize();
            }
            for (PhaseConfig phase : boss.phases) {
                if (phase.summonMobs == null) phase.summonMobs = new java.util.ArrayList<>();
                if (phase.playerEffects == null) phase.playerEffects = new java.util.ArrayList<>();
                if (phase.phaseCommands == null) phase.phaseCommands = new java.util.ArrayList<>();
                for (SummonConfig summon : phase.summonMobs) {
                    migrateLegacyCombatAttributes(summon.customAttributes,
                            value -> summon.attackRange = Math.max(0.0D, value),
                            value -> summon.attackCooldownMs = Math.max(0, (int) Math.round(value)),
                            value -> summon.aggroRange = Math.max(0.0D, value),
                            value -> summon.projectileCooldownMs = Math.max(0, (int) Math.round(value)),
                            value -> summon.dodgeChance = Math.max(0.0D, Math.min(1.0D, value)),
                            value -> summon.dodgeCooldownMs = Math.max(0, (int) Math.round(value)),
                            value -> summon.dodgeProjectilesOnly = value >= 0.5D,
                            summon.attackRange,
                            summon.attackCooldownMs,
                            summon.aggroRange,
                            summon.projectileCooldownMs,
                            summon.dodgeChance,
                            summon.dodgeCooldownMs,
                            summon.dodgeProjectilesOnly);
                }
            }
        }
        for (WaveConfig wave : config.waves) {
            if (wave.mobs == null) wave.mobs = new java.util.ArrayList<>();
            for (MobSpawnConfig mob : wave.mobs) {
                if (mob.spawnPoint == null) mob.spawnPoint = new SpawnPointConfig();
                migrateLegacyCombatAttributes(mob.customAttributes,
                        value -> mob.attackRange = Math.max(0.0D, value),
                        value -> mob.attackCooldownMs = Math.max(0, (int) Math.round(value)),
                        value -> mob.aggroRange = Math.max(0.0D, value),
                        value -> mob.projectileCooldownMs = Math.max(0, (int) Math.round(value)),
                        value -> mob.dodgeChance = Math.max(0.0D, Math.min(1.0D, value)),
                        value -> mob.dodgeCooldownMs = Math.max(0, (int) Math.round(value)),
                        value -> mob.dodgeProjectilesOnly = value >= 0.5D,
                        mob.attackRange,
                        mob.attackCooldownMs,
                        mob.aggroRange,
                        mob.projectileCooldownMs,
                        mob.dodgeChance,
                        mob.dodgeCooldownMs,
                        mob.dodgeProjectilesOnly);
            }
        }
    }

    private void migrateLegacyCombatAttributes(Map<String, Double> attributes,
                                               java.util.function.DoubleConsumer attackRangeSetter,
                                               java.util.function.DoubleConsumer attackCooldownSetter,
                                               java.util.function.DoubleConsumer aggroRangeSetter,
                                               java.util.function.DoubleConsumer projectileCooldownSetter,
                                               java.util.function.DoubleConsumer dodgeChanceSetter,
                                               java.util.function.DoubleConsumer dodgeCooldownSetter,
                                               java.util.function.DoubleConsumer dodgeProjectilesOnlySetter,
                                               double attackRangeCurrent,
                                               int attackCooldownCurrent,
                                               double aggroRangeCurrent,
                                               int projectileCooldownCurrent,
                                               double dodgeChanceCurrent,
                                               int dodgeCooldownCurrent,
                                               boolean dodgeProjectilesOnlyCurrent) {
        if (attributes == null || attributes.isEmpty()) return;

        migrateLegacyCombatValue(attributes, CombatTuning.KEY_ATTACK_RANGE, attackRangeCurrent == 0.0D, attackRangeSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_ATTACK_COOLDOWN_MS, attackCooldownCurrent == 0, attackCooldownSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_AGGRO_RANGE, aggroRangeCurrent == 0.0D, aggroRangeSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, projectileCooldownCurrent == 0, projectileCooldownSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_DODGE_CHANCE, dodgeChanceCurrent == 0.0D, dodgeChanceSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_DODGE_COOLDOWN_MS, dodgeCooldownCurrent == 0, dodgeCooldownSetter);
        migrateLegacyCombatValue(attributes, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, !dodgeProjectilesOnlyCurrent, dodgeProjectilesOnlySetter);
    }

    private void migrateLegacyCombatValue(Map<String, Double> attributes, String key, boolean shouldApply, java.util.function.DoubleConsumer setter) {
        if (!attributes.containsKey(key)) return;
        double value = attributes.getOrDefault(key, 0.0D);
        if (shouldApply && setter != null) {
            setter.accept(value);
        }
        attributes.remove(key);
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
        } catch (IOException e) {
            DungeonConfig previous = previousConfigs.get(fallbackId);
            if (previous != null) {
                loadedConfigs.put(fallbackId, previous);
                ArcadiaDungeon.LOGGER.warn("Erreur I/O lors du rechargement de {}. Version précédente conservée.", fileName, e);
            } else {
                ArcadiaDungeon.LOGGER.warn("Erreur I/O lors du chargement de {}. Fichier ignoré.", fileName, e);
            }
        } catch (JsonParseException e) {
            DungeonConfig previous = previousConfigs.get(fallbackId);
            if (previous != null) {
                loadedConfigs.put(fallbackId, previous);
                ArcadiaDungeon.LOGGER.warn("Config {} corrompue (JSON invalide). Version précédente conservée.", fileName, e);
            } else {
                ArcadiaDungeon.LOGGER.warn("Config {} corrompue (JSON invalide). Fichier ignoré.", fileName, e);
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
        example.arcadiaXp = 100;
        example.difficultyMultiplier = 1.0;
        example.requiredArcadiaLevel = 0;
        example.speedrunBonusSeconds = 300;
        example.speedrunBonusXp = 50;
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
        wave1.name = "Primera oleada";
        wave1.startMessage = "&e[Mazmorra] &7¡Se acercan enemigos!";
        MobSpawnConfig zombie1 = new MobSpawnConfig("minecraft:zombie", 3,
                new SpawnPointConfig("minecraft:overworld", 105, 64, 105, 0, 0));
        zombie1.customName = "Zombi de la mazmorra";
        zombie1.health = 30;
        zombie1.attackRange = 3.5;
        wave1.mobs.add(zombie1);

        WaveConfig wave2 = new WaveConfig(2);
        wave2.name = "Segunda ola";
        wave2.startMessage = "&e[Mazmorra] &7Llegan los esqueletos!";
        MobSpawnConfig skel = new MobSpawnConfig("minecraft:skeleton", 4,
                new SpawnPointConfig("minecraft:overworld", 108, 64, 108, 0, 0));
        skel.customName = "Esqueleto de la mazmorra";
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
        root.addProperty("_comment", "Ejemplo generado por Arcadia Dungeon. El cargador ignora los campos de comentario desconocidos.");
        root.addProperty("_comment_placeholders", "%player%, %dungeon% y %id% están disponibles en los campos de mensaje.");

        JsonObject settings = root.getAsJsonObject("settings");
        if (settings != null) {
            settings.addProperty("_comment_timerWarnings", "Segundos antes de que se agote el tiempo, cuando se envían los mensajes de advertencia».");
        }

        JsonArray waves = root.getAsJsonArray("waves");
        if (waves != null && !waves.isEmpty()) {
            waves.get(0).getAsJsonObject().addProperty("_comment", "Las series se suceden en orden y deben completarse por completo antes de que comience la siguiente.");
        }

        JsonArray bosses = root.getAsJsonArray("bosses");
        if (bosses != null && !bosses.isEmpty()) {
            bosses.get(0).getAsJsonObject().addProperty("_comment", "Los jefes pueden aparecer al inicio, tras una oleada o tras todas las oleadas, dependiendo de sus indicadores.");
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
        props.add("arcadiaXp", intSchema(0, "Arcadia XP granted to each present player on successful completion. 0 uses the global default."));
        props.add("difficultyMultiplier", numberSchema(1.0, "Multiplier applied to Arcadia XP rewards for this dungeon."));
        props.add("requiredArcadiaLevel", intSchema(0, "Minimum Arcadia level required to start or join this dungeon. 0 disables the requirement."));
        props.add("speedrunBonusSeconds", intSchema(0, "Completion time threshold in seconds for granting the speedrun XP bonus. 0 disables the bonus."));
        props.add("speedrunBonusXp", intSchema(0, "Additional Arcadia XP granted when the dungeon is completed within speedrunBonusSeconds."));
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
        props.add("completionDelaySeconds", intSchema(10, "Seconds to wait before teleporting players back after dungeon completion. 0 = instant."));
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
