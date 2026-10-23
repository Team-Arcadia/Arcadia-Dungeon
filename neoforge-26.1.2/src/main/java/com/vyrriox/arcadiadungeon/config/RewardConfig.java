package com.vyrriox.arcadiadungeon.config;

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
}
