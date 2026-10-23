package com.vyrriox.arcadiadungeon.config;

import java.util.HashMap;
import java.util.Map;

public class MobSpawnConfig {
    public String entityType = "minecraft:zombie";
    public int count = 1;
    public String customName = "";
    public double health = 20;
    public double damage = 3;
    public double speed = 0;
    public SpawnPointConfig spawnPoint = new SpawnPointConfig();
    public DungeonConfig.AreaPos areaPos1 = null;
    public DungeonConfig.AreaPos areaPos2 = null;

    // Equipment (item IDs, empty = no equipment)
    public String mainHand = "";
    public String offHand = "";
    public String helmet = "";
    public String chestplate = "";
    public String leggings = "";
    public String boots = "";

    // Custom attributes (e.g. "minecraft:generic.armor" -> 10.0)
    public Map<String, Double> customAttributes = new HashMap<>();

    public MobSpawnConfig() {}

    public MobSpawnConfig(String entityType, int count, SpawnPointConfig spawnPoint) {
        this.entityType = entityType;
        this.count = count;
        this.spawnPoint = spawnPoint;
    }

    public boolean hasArea() {
        return areaPos1 != null && areaPos2 != null;
    }

    public boolean isInArea(String dimension, double px, double py, double pz) {
        return DungeonConfig.isInsideArea(areaPos1, areaPos2, dimension, px, py, pz);
    }
}
