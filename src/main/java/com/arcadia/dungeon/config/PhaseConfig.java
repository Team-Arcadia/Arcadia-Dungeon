package com.arcadia.dungeon.config;

import java.util.ArrayList;
import java.util.List;

public class PhaseConfig {
    public int phase = 1;
    public double healthThreshold = 1.0;
    public double damageMultiplier = 1.0;
    public double speedMultiplier = 1.0;
    public String description = "";
    public List<SummonConfig> summonMobs = new ArrayList<>();
    public String requiredAction = "NONE";
    public String phaseStartMessage = "";
    public boolean invulnerableDuringTransition = false;
    public double transitionDurationSeconds = 2.0;
    public double immunityDuration = 0.0;

    // Potion effects applied to players when phase starts
    public List<PhaseEffect> playerEffects = new ArrayList<>();

    // Commands executed when phase starts (%player% = each player name)
    public List<String> phaseCommands = new ArrayList<>();

    public static class PhaseEffect {
        public String effect = "minecraft:slowness";
        public int durationSeconds = 10;
        public int amplifier = 0;

        public PhaseEffect() {}

        public PhaseEffect(String effect, int durationSeconds, int amplifier) {
            this.effect = effect;
            this.durationSeconds = durationSeconds;
            this.amplifier = amplifier;
        }
    }

    public PhaseConfig() {}

    public PhaseConfig(int phase, double healthThreshold) {
        this.phase = phase;
        this.healthThreshold = healthThreshold;
    }
}
