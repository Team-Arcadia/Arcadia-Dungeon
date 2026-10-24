package com.vyrriox.arcadiadungeon.config;

import java.util.ArrayList;
import java.util.List;

public class WaveConfig {
    public int waveNumber = 1;
    public String name = "";
    public int delayBeforeSeconds = 0;
    public List<MobSpawnConfig> mobs = new ArrayList<>();
    public String startMessage = "";
    public boolean glowingAfterDelay = true;
    public int glowingDelaySeconds = 60;

    public WaveConfig() {}

    public WaveConfig(int waveNumber) {
        this.waveNumber = waveNumber;
        this.name = "Vague " + waveNumber;
    }
}
