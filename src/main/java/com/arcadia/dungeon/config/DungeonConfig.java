package com.arcadia.dungeon.config;

import java.util.ArrayList;
import java.util.List;

public class DungeonConfig {
    public String id = "";
    public String name = "";
    public int cooldownSeconds = 3600;
    public boolean announceStart = true;
    public boolean announceCompletion = true;
    public String startMessage = "&6[Mazmorra] &e%player% &7está iniciando &e%dungeon%&7! ¡Únete con &f/arcadia_dungeon join %id%&7!";
    public String completionMessage = "&6[Mazmorra] &a%jugador% &7ha derrotado a &e%mazmorra%&7!";
    public String failMessage = "&6[Mazmorra] &c%player% &7a fracasa en &e%dungeon%&7!";
    public boolean teleportBackOnComplete = true;
    public int arcadiaXp = 0;
    public double difficultyMultiplier = 1.0;
    public int requiredArcadiaLevel = 0;
    public int speedrunBonusSeconds = 0;
    public int speedrunBonusXp = 0;
    public SpawnPointConfig spawnPoint = new SpawnPointConfig();
    public List<BossConfig> bosses = new ArrayList<>();
    public List<RewardConfig> completionRewards = new ArrayList<>();
    public DungeonSettings settings = new DungeonSettings();
    public boolean enabled = true;
    public int availableEverySeconds = 0;
    public boolean announceAvailability = true;
    public int announceIntervalMinutes = 0;
    public String availabilityMessage = "&6[Mazmorra] &e%dungeon% &7vuelve a estar disponible! &fIníciala con &e/arcadia_dungeon start %id%";
    public String requiredPermission = "";
    public int order = 0;
    public String requiredDungeon = "";
    public boolean debugMode = false;
    public List<WaveConfig> waves = new ArrayList<>();

    // Recruitment / group system
    public int recruitmentDurationSeconds = 90;
    public String recruitmentMessage = "&6[Mazmorra] &e%player% &7está abriendo &e%dungeon%&7! &fÚnete en %time%s &7con &f/arcadia_dungeon join %id%";

    // Dungeon area (cuboid) - containment + anti-parasites
    public AreaPos areaPos1 = null;
    public AreaPos areaPos2 = null;
    public List<ScriptedWallConfig> scriptedWalls = new ArrayList<>();

    public static class AreaPos {
        public String dimension = "minecraft:overworld";
        public int x = 0;
        public int y = 0;
        public int z = 0;

        public AreaPos() {}

        public AreaPos(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public boolean hasArea() {
        return areaPos1 != null && areaPos2 != null;
    }

    public static class ScriptedWallConfig {
        public String id = "";
        public List<AreaPos> blocks = new ArrayList<>();
        // TODO: condition system for activating/deactivating scripted walls.
        public String activationCondition = "";
        public String action = "TOGGLE";
    }

    public boolean isInArea(String dimension, double px, double py, double pz) {
        return isInsideArea(areaPos1, areaPos2, dimension, px, py, pz);
    }

    public static boolean isInsideArea(AreaPos areaPos1, AreaPos areaPos2, String dimension, double px, double py, double pz) {
        if (areaPos1 == null || areaPos2 == null) return false;
        if (!areaPos1.dimension.equals(dimension)) return false;

        int minX = Math.min(areaPos1.x, areaPos2.x);
        int maxX = Math.max(areaPos1.x, areaPos2.x);
        int minY = Math.min(areaPos1.y, areaPos2.y);
        int maxY = Math.max(areaPos1.y, areaPos2.y);
        int minZ = Math.min(areaPos1.z, areaPos2.z);
        int maxZ = Math.max(areaPos1.z, areaPos2.z);

        return px >= minX && px <= maxX + 1
                && py >= minY && py <= maxY + 1
                && pz >= minZ && pz <= maxZ + 1;
    }

    public static double clampInsideX(AreaPos areaPos1, AreaPos areaPos2, double x) {
        int minX = Math.min(areaPos1.x, areaPos2.x);
        int maxX = Math.max(areaPos1.x, areaPos2.x);
        return Math.max(minX + 0.5, Math.min(maxX + 0.5, x));
    }

    public static double clampInsideY(AreaPos areaPos1, AreaPos areaPos2, double y) {
        int minY = Math.min(areaPos1.y, areaPos2.y);
        int maxY = Math.max(areaPos1.y, areaPos2.y);
        return Math.max(minY, Math.min(maxY, y));
    }

    public static double clampInsideZ(AreaPos areaPos1, AreaPos areaPos2, double z) {
        int minZ = Math.min(areaPos1.z, areaPos2.z);
        int maxZ = Math.max(areaPos1.z, areaPos2.z);
        return Math.max(minZ + 0.5, Math.min(maxZ + 0.5, z));
    }

    public DungeonConfig() {}

    public DungeonConfig(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
