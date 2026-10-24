package com.vyrriox.arcadiadungeon.config;

public class SpawnPointConfig {
    public String dimension = "minecraft:overworld";
    public double x = 0;
    public double y = 64;
    public double z = 0;
    public float yaw = 0;
    public float pitch = 0;

    public SpawnPointConfig() {}

    public SpawnPointConfig(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
