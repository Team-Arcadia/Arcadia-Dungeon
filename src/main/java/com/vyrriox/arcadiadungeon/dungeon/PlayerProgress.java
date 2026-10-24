package com.vyrriox.arcadiadungeon.dungeon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProgress {
    public String uuid = "";
    public String playerName = "";
    public Map<String, DungeonProgress> completedDungeons = new ConcurrentHashMap<>();

    public static class DungeonProgress {
        public int completions = 0;
        public long bestTimeSeconds = 0;
        public long lastCompletionTimestamp = 0;

        public DungeonProgress() {}

        public DungeonProgress(long timeSeconds) {
            this.completions = 1;
            this.bestTimeSeconds = timeSeconds;
            this.lastCompletionTimestamp = System.currentTimeMillis();
        }
    }

    public PlayerProgress() {}

    public PlayerProgress(String uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
    }

    public int getTotalCompletions() {
        int total = 0;
        for (DungeonProgress dp : completedDungeons.values()) {
            total += dp.completions;
        }
        return total;
    }

    public boolean hasCompleted(String dungeonId) {
        DungeonProgress dp = completedDungeons.get(dungeonId);
        return dp != null && dp.completions > 0;
    }

    public void recordCompletion(String dungeonId, long timeSeconds) {
        DungeonProgress dp = completedDungeons.get(dungeonId);
        if (dp == null) {
            dp = new DungeonProgress(timeSeconds);
            completedDungeons.put(dungeonId, dp);
        } else {
            dp.completions++;
            if (dp.bestTimeSeconds == 0 || timeSeconds < dp.bestTimeSeconds) {
                dp.bestTimeSeconds = timeSeconds;
            }
            dp.lastCompletionTimestamp = System.currentTimeMillis();
        }
    }
}
