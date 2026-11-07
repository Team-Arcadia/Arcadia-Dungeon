package com.vyrriox.arcadiadungeon.config;

import java.util.ArrayList;
import java.util.List;

public class DungeonSettings {
    public int maxPlayers = 4;
    public int minPlayers = 1;
    public int timeLimitSeconds = 1800;
    public boolean pvp = false;
    public boolean difficultyScaling = true;
    public double waveHealthMultiplierPerPlayer = 0.3;
    public double waveDamageMultiplierPerPlayer = 0.1;
    public double waveCountMultiplierPerPlayer = 0.2; // +20% mobs per extra player
    public int maxDeaths = 3;
    public boolean antiMonopole = true;
    public int antiMonopoleThreshold = 5;
    public List<Integer> timerWarnings = new ArrayList<>(List.of(300, 60, 30, 10));
    public boolean blockTeleportCommands = true;
    public List<String> blockedCommands = new ArrayList<>(List.of(
            "tpa", "tpaccept", "tpahere", "tpadeny",
            "spawn", "home", "sethome", "lobby", "hub",
            "warp", "back", "rtp", "arcadiartp",
            "tpask", "tpacancel", "tptoggle",
            "tp", "teleport"
    ));

    /** Brings back mobs nobody can reach: out of the area, inside a wall, or left alone for minutes. */
    public boolean rescueStuckMobs = true;
    /** Seconds a boss stays immune while its summons stand. 0 keeps it immune until they are dead. */
    public int summonLockMaxSeconds = 180;

    public DungeonSettings() {}
}
