package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.PlayerProgressPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client cache for the current player's progression.
 */
public final class PlayerProgressClient {

    private static volatile long currency = 0L;
    private static volatile int totalRuns = 0;
    private static volatile long bestTimeSeconds = 0L;
    private static volatile String selectedClassId = "";
    private static volatile boolean customLoadoutUnlocked = false;
    private static volatile int loadoutPoints = 0;
    private static volatile long version = 0L;
    private static final Map<String, PlayerProgressPayload.DungeonStat> dungeonStats = new ConcurrentHashMap<>();

    private PlayerProgressClient() {}

    public static void update(PlayerProgressPayload payload) {
        currency = payload.currency();
        totalRuns = payload.totalRuns();
        bestTimeSeconds = payload.bestTimeSeconds();
        selectedClassId = payload.selectedClassId() != null ? payload.selectedClassId() : "";
        customLoadoutUnlocked = payload.customLoadoutUnlocked();
        loadoutPoints = payload.loadoutPoints();
        dungeonStats.clear();
        for (PlayerProgressPayload.DungeonStat stat : payload.dungeons()) {
            dungeonStats.put(stat.dungeonId(), stat);
        }
        version++;
    }

    public static long currency() {
        return currency;
    }

    public static int totalRuns() {
        return totalRuns;
    }

    public static long bestTimeSeconds() {
        return bestTimeSeconds;
    }

    public static String selectedClassId() {
        return selectedClassId;
    }

    public static boolean customLoadoutUnlocked() {
        return customLoadoutUnlocked;
    }

    public static int loadoutPoints() {
        return loadoutPoints;
    }

    public static long bestTimeFor(String dungeonId) {
        PlayerProgressPayload.DungeonStat stat = dungeonStats.get(dungeonId);
        return stat != null ? stat.bestTimeSeconds() : 0L;
    }

    public static int completionsFor(String dungeonId) {
        PlayerProgressPayload.DungeonStat stat = dungeonStats.get(dungeonId);
        return stat != null ? stat.completions() : 0;
    }

    public static long version() {
        return version;
    }
}
