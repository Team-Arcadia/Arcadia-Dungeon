package com.vyrriox.arcadiadungeon.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArcadiaProgressionConfig {
    public List<LevelThreshold> levels = new ArrayList<>();
    public List<RankThreshold> ranks = new ArrayList<>();
    public int defaultDungeonXp = 100;

    public static class LevelThreshold {
        public int level;
        public long xpRequired;

        public LevelThreshold() {}

        public LevelThreshold(int level, long xpRequired) {
            this.level = level;
            this.xpRequired = xpRequired;
        }
    }

    public static class RankThreshold {
        public int minLevel;
        public String rankName;

        public RankThreshold() {}

        public RankThreshold(int minLevel, String rankName) {
            this.minLevel = minLevel;
            this.rankName = rankName;
        }
    }

    public static ArcadiaProgressionConfig createDefault() {
        ArcadiaProgressionConfig config = new ArcadiaProgressionConfig();
        config.levels.clear();
        for (int level = 1; level <= 50; level++) {
            long xpRequired = level <= 1 ? 0 : (long) level * level * 50L;
            config.levels.add(new LevelThreshold(level, xpRequired));
        }

        config.ranks.clear();
        config.ranks.add(new RankThreshold(1, "Novice"));
        config.ranks.add(new RankThreshold(5, "Aventurier"));
        config.ranks.add(new RankThreshold(10, "Chasseur"));
        config.ranks.add(new RankThreshold(20, "Légende"));
        config.defaultDungeonXp = 100;
        return config;
    }

    public void normalize() {
        if (levels == null || levels.isEmpty()) {
            levels = createDefault().levels;
        }
        if (ranks == null || ranks.isEmpty()) {
            ranks = createDefault().ranks;
        }
        if (defaultDungeonXp <= 0) {
            defaultDungeonXp = 100;
        }
        levels.removeIf(level -> level == null || level.level < 1 || level.xpRequired < 0);
        ranks.removeIf(rank -> rank == null || rank.minLevel < 1 || rank.rankName == null || rank.rankName.isBlank());
        if (levels.isEmpty()) {
            levels = createDefault().levels;
        }
        if (ranks.isEmpty()) {
            ranks = createDefault().ranks;
        }
        levels.sort(Comparator.comparingInt(level -> level.level));
        ranks.sort(Comparator.comparingInt(rank -> rank.minLevel));
    }

    public int getLevelForXp(long xp) {
        normalize();
        int resolvedLevel = 1;
        for (LevelThreshold threshold : levels) {
            if (xp >= threshold.xpRequired) {
                resolvedLevel = Math.max(resolvedLevel, threshold.level);
            } else {
                break;
            }
        }
        return resolvedLevel;
    }

    public String getRankForLevel(int level) {
        normalize();
        String resolvedRank = "Novice";
        for (RankThreshold threshold : ranks) {
            if (level >= threshold.minLevel) {
                resolvedRank = threshold.rankName;
            } else {
                break;
            }
        }
        return resolvedRank;
    }
}
