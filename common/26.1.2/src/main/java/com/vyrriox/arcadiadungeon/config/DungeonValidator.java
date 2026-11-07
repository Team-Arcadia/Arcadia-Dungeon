package com.vyrriox.arcadiadungeon.config;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.dungeon.CombatTuning;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Says what is wrong with a dungeon's config before a run finds out.
 *
 * <p>A mistyped mob id used to show up as a wave that clears itself the moment it starts, and a
 * spawn point outside the area as a mob nobody can reach. Nothing here refuses a dungeon: every
 * problem is a warning in the log at start, and {@code /arcadia_dungeon admin debug validate} lists
 * them in game.</p>
 *
 * @author vyrriox
 */
public final class DungeonValidator {

    private DungeonValidator() {}

    public static void logProblems(MinecraftServer server) {
        for (DungeonConfig config : ConfigManager.getInstance().getDungeonConfigs().values()) {
            for (String problem : check(config, server)) {
                ArcadiaDungeon.LOGGER.warn("Dungeon {}: {}", config.id, problem);
            }
        }
    }

    public static List<String> check(DungeonConfig config, MinecraftServer server) {
        List<String> problems = new ArrayList<>();

        if (config.bosses.isEmpty() && config.waves.isEmpty()) {
            problems.add("it has no boss and no wave, so it refuses to start");
        }
        if (config.requiredDungeon != null && !config.requiredDungeon.isEmpty()
                && ConfigManager.getInstance().getDungeon(config.requiredDungeon) == null) {
            problems.add("it requires dungeon '" + config.requiredDungeon + "', which does not exist, so nobody can ever start it");
        }
        if (config.requiredPermission != null && !config.requiredPermission.isEmpty()) {
            problems.add("requiredPermission is set, but no version of the mod reads it: anyone can start this dungeon");
        }
        checkPoint(problems, "the spawn point", config.spawnPoint, config, server);
        if (config.hasArea() && !dimensionExists(config.areaPos1.dimension, server)) {
            problems.add("its area is in dimension '" + config.areaPos1.dimension + "', which does not exist");
        }

        Set<Integer> waveNumbers = new HashSet<>();
        for (WaveConfig wave : config.waves) {
            String name = "wave " + wave.waveNumber;
            if (!waveNumbers.add(wave.waveNumber)) problems.add(name + " is numbered twice");
            if (wave.mobs.isEmpty()) problems.add(name + " has no mobs");
            for (MobSpawnConfig mob : wave.mobs) {
                checkEntity(problems, name, mob.entityType);
                checkPoint(problems, name + ", " + mob.entityType, mob.spawnPoint, config, server);
                checkEquipment(problems, name, mob.mainHand, mob.offHand, mob.helmet, mob.chestplate, mob.leggings, mob.boots);
                checkAttributes(problems, name, mob.customAttributes);
            }
        }

        Set<String> bossIds = new HashSet<>();
        for (BossConfig boss : config.bosses) {
            String name = "boss " + boss.id;
            if (!bossIds.add(boss.id)) problems.add(name + " exists twice, only one of them can be followed");
            checkEntity(problems, name, boss.entityType);
            checkPoint(problems, name, boss.spawnPoint, config, server);
            checkEquipment(problems, name, boss.mainHand, boss.offHand, boss.helmet, boss.chestplate, boss.leggings, boss.boots);
            checkAttributes(problems, name, boss.customAttributes);
            if (boss.spawnAfterWave > 0 && !waveNumbers.contains(boss.spawnAfterWave)) {
                problems.add(name + " spawns after wave " + boss.spawnAfterWave + ", which does not exist, so it never comes");
            }
            for (PhaseConfig phase : boss.phases) {
                String phaseName = name + ", phase " + phase.phase;
                if (phase.healthThreshold <= 0.0 || phase.healthThreshold > 1.0) {
                    problems.add(phaseName + " has a health threshold of " + phase.healthThreshold + ", expected more than 0 and up to 1");
                }
                if (phase.requiredAction != null && !phase.requiredAction.equalsIgnoreCase("NONE")
                        && !phase.requiredAction.equalsIgnoreCase("KILL_SUMMONS")) {
                    problems.add(phaseName + " asks for action " + phase.requiredAction + ", which has no effect: only KILL_SUMMONS does anything");
                }
                for (SummonConfig summon : phase.summonMobs) {
                    checkEntity(problems, phaseName, summon.entityType);
                    checkEquipment(problems, phaseName, summon.mainHand, summon.offHand, summon.helmet, summon.chestplate, summon.leggings, summon.boots);
                    checkAttributes(problems, phaseName, summon.customAttributes);
                }
                for (PhaseConfig.PhaseEffect effect : phase.playerEffects) {
                    Identifier id = Identifier.tryParse(effect.effect);
                    if (id == null || BuiltInRegistries.MOB_EFFECT.getOptional(id).isEmpty()) {
                        problems.add(phaseName + " applies unknown effect '" + effect.effect + "'");
                    }
                }
            }
        }
        return problems;
    }

    private static void checkEntity(List<String> problems, String where, String entityType) {
        if (EntityType.byString(entityType).isEmpty()) {
            problems.add(where + " uses unknown mob '" + entityType + "': it will not spawn");
        }
    }

    private static void checkPoint(List<String> problems, String where, SpawnPointConfig point, DungeonConfig config, MinecraftServer server) {
        if (!dimensionExists(point.dimension, server)) {
            problems.add(where + " is in dimension '" + point.dimension + "', which does not exist");
        } else if (config.hasArea() && !config.isInArea(point.dimension, point.x, point.y, point.z)) {
            problems.add(where + " is outside the dungeon's area (" + (int) point.x + ", " + (int) point.y + ", " + (int) point.z + ")");
        }
    }

    private static boolean dimensionExists(String dimension, MinecraftServer server) {
        Identifier id = Identifier.tryParse(dimension);
        return id != null && server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) != null;
    }

    private static void checkEquipment(List<String> problems, String where, String... items) {
        for (String item : items) {
            if (item == null || item.isEmpty()) continue;
            Identifier id = Identifier.tryParse(item);
            if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
                problems.add(where + " is equipped with unknown item '" + item + "'");
            }
        }
    }

    private static void checkAttributes(List<String> problems, String where, Map<String, Double> attributes) {
        if (attributes == null) return;
        for (String key : attributes.keySet()) {
            if (CombatTuning.SPECIAL_KEYS.contains(key)) continue;
            if (BossInstance.findAttribute(key).isEmpty()) {
                problems.add(where + " sets unknown attribute '" + key + "'");
            }
        }
    }
}
