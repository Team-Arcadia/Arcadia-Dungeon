package com.vyrriox.arcadiadungeon.dungeon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProgress {
    public String uuid = "";
    public String playerName = "";
    public Map<String, DungeonProgress> completedDungeons = new ConcurrentHashMap<>();
    public ArcadiaProgress arcadiaProgress = new ArcadiaProgress();

    public static class ArcadiaProgress {
        public long arcadiaXp = 0;
        public int arcadiaLevel = 1;
        public String arcadiaRank = "Novice";
        public int weeklyStreak = 0;
        public String lastWeekId = "";
    }

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

    public boolean normalize() {
        boolean changed = false;
        if (completedDungeons == null) {
            completedDungeons = new ConcurrentHashMap<>();
            changed = true;
        }
        if (arcadiaProgress == null) {
            arcadiaProgress = new ArcadiaProgress();
            changed = true;
        }
        if (arcadiaProgress.arcadiaXp < 0) {
            arcadiaProgress.arcadiaXp = 0;
            changed = true;
        }
        if (arcadiaProgress.arcadiaLevel < 1) {
            arcadiaProgress.arcadiaLevel = 1;
            changed = true;
        }
        if (arcadiaProgress.arcadiaRank == null || arcadiaProgress.arcadiaRank.isBlank()) {
            arcadiaProgress.arcadiaRank = "Novice";
            changed = true;
        }
        if (arcadiaProgress.weeklyStreak < 0) {
            arcadiaProgress.weeklyStreak = 0;
            changed = true;
        }
        if (arcadiaProgress.lastWeekId == null) {
            arcadiaProgress.lastWeekId = "";
            changed = true;
        }
        return changed;
    }

    public int getTotalCompletions() {
        normalize();
        int total = 0;
        for (DungeonProgress dp : completedDungeons.values()) {
            total += dp.completions;
        }
        return total;
    }

    public boolean hasCompleted(String dungeonId) {
        normalize();
        DungeonProgress dp = completedDungeons.get(dungeonId);
        return dp != null && dp.completions > 0;
    }

    public void recordCompletion(String dungeonId, long timeSeconds) {
        normalize();
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
