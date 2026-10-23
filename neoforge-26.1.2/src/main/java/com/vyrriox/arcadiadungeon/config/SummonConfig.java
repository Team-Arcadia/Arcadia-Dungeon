package com.vyrriox.arcadiadungeon.config;

import java.util.HashMap;
import java.util.Map;

public class SummonConfig {
    public String entityType = "minecraft:zombie";
    public int count = 1;
    public String customName = "";
    public double health = 20;
    public double damage = 3;

    // Equipment
    public String mainHand = "";
    public String offHand = "";
    public String helmet = "";
    public String chestplate = "";
    public String leggings = "";
    public String boots = "";

    // Custom attributes (e.g. "minecraft:generic.armor" -> 10.0)
    public Map<String, Double> customAttributes = new HashMap<>();

    public SummonConfig() {}

    public SummonConfig(String entityType, int count) {
        this.entityType = entityType;
        this.count = count;
    }
}
