package com.arcadia.dungeon.config;

public class RewardConfig {
    public String item = "minecraft:diamond";
    public int count = 1;
    public double chance = 1.0;
    public String command = "";
    public int experience = 0;

    public RewardConfig() {}

    public RewardConfig(String item, int count, double chance) {
        this.item = item;
        this.count = count;
        this.chance = chance;
    }

    public void normalize() {
        if (item == null) item = "";
        if (command == null) command = "";
        if (count < 1) count = 1;
        if (!Double.isFinite(chance)) chance = 1.0;
        chance = Math.max(0.0, Math.min(1.0, chance));
        if (experience < 0) experience = 0;
    }
}
