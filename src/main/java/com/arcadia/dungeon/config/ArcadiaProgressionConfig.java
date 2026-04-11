package com.arcadia.dungeon.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ArcadiaProgressionConfig {
    public List<LevelThreshold> levels = new ArrayList<>();
    public List<RankThreshold> ranks = new ArrayList<>();
    public List<MilestoneReward> milestoneRewards = new ArrayList<>();
    public List<StreakBonus> streakBonuses = new ArrayList<>();
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
        public String color = "&7";

        public RankThreshold() {}

        public RankThreshold(int minLevel, String rankName, String color) {
            this.minLevel = minLevel;
            this.rankName = rankName;
            this.color = color;
        }
    }

    public static class MilestoneReward {
        public int level;
        public List<RewardConfig> rewards = new ArrayList<>();
        public String message = "";

        public MilestoneReward() {}

        public MilestoneReward(int level, String message) {
            this.level = level;
            this.message = message;
        }
    }

    public static class StreakBonus {
        public int weeks;
        public long xpBonus;
        public String message = "";

        public StreakBonus() {}

        public StreakBonus(int weeks, long xpBonus, String message) {
            this.weeks = weeks;
            this.xpBonus = xpBonus;
            this.message = message;
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
        config.ranks.add(new RankThreshold(1, "Novice", "&7"));
        config.ranks.add(new RankThreshold(5, "Aventurier", "&a"));
        config.ranks.add(new RankThreshold(10, "Chasseur", "&9"));
        config.ranks.add(new RankThreshold(20, "Legende", "&6"));

        config.milestoneRewards.clear();
        config.milestoneRewards.add(new MilestoneReward(5, "&6[Arcadia] &ePalier niveau 5 atteint!"));
        config.milestoneRewards.add(new MilestoneReward(10, "&6[Arcadia] &ePalier niveau 10 atteint!"));
        config.milestoneRewards.add(new MilestoneReward(25, "&6[Arcadia] &ePalier niveau 25 atteint!"));

        config.streakBonuses.clear();
        config.streakBonuses.add(new StreakBonus(3, 50, "&6[Arcadia] &eStreak 3 semaines! &a+50 XP Arcadia"));
        config.streakBonuses.add(new StreakBonus(7, 150, "&6[Arcadia] &eStreak 7 semaines! &a+150 XP Arcadia"));
        config.streakBonuses.add(new StreakBonus(14, 400, "&6[Arcadia] &eStreak 14 semaines! &a+400 XP Arcadia"));

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
        if (milestoneRewards == null) {
            milestoneRewards = new ArrayList<>();
        }
        if (streakBonuses == null) {
            streakBonuses = createDefault().streakBonuses;
        }
        if (defaultDungeonXp <= 0) {
            defaultDungeonXp = 100;
        }

        levels.removeIf(level -> level == null || level.level < 1 || level.xpRequired < 0);
        ranks.removeIf(rank -> rank == null || rank.minLevel < 1 || rank.rankName == null || rank.rankName.isBlank());
        milestoneRewards.removeIf(milestone -> milestone == null || milestone.level < 1);
        streakBonuses.removeIf(streak -> streak == null || streak.weeks < 1 || streak.xpBonus < 0);

        if (levels.isEmpty()) {
            levels = createDefault().levels;
        }
        if (ranks.isEmpty()) {
            ranks = createDefault().ranks;
        }

        for (RankThreshold rank : ranks) {
            if (rank.color == null || rank.color.isBlank()) rank.color = "&7";
        }
        for (MilestoneReward milestone : milestoneRewards) {
            if (milestone.rewards == null) milestone.rewards = new ArrayList<>();
            if (milestone.message == null) milestone.message = "";
            for (RewardConfig reward : milestone.rewards) {
                if (reward != null) reward.normalize();
            }
        }
        for (StreakBonus streak : streakBonuses) {
            if (streak.message == null) streak.message = "";
        }

        levels.sort(Comparator.comparingInt(level -> level.level));
        ranks.sort(Comparator.comparingInt(rank -> rank.minLevel));
        milestoneRewards.sort(Comparator.comparingInt(milestone -> milestone.level));
        streakBonuses.sort(Comparator.comparingInt(streak -> streak.weeks));
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

    public String getRankColorForLevel(int level) {
        normalize();
        String resolvedColor = "&7";
        for (RankThreshold threshold : ranks) {
            if (level >= threshold.minLevel) {
                resolvedColor = threshold.color;
            } else {
                break;
            }
        }
        return resolvedColor;
    }

    public long getXpRequiredForNextLevel(int currentLevel, long currentXp) {
        normalize();
        for (LevelThreshold threshold : levels) {
            if (threshold.level > currentLevel) {
                return Math.max(0, threshold.xpRequired - currentXp);
            }
        }
        return 0;
    }

    public List<MilestoneReward> getMilestonesBetween(int oldLevel, int newLevel, Set<Integer> claimedLevels) {
        normalize();
        List<MilestoneReward> result = new ArrayList<>();
        for (MilestoneReward milestone : milestoneRewards) {
            if (milestone.level > oldLevel && milestone.level <= newLevel
                    && (claimedLevels == null || !claimedLevels.contains(milestone.level))) {
                result.add(milestone);
            }
        }
        return result;
    }

    public StreakBonus getStreakBonus(int weeklyStreak) {
        normalize();
        for (StreakBonus bonus : streakBonuses) {
            if (bonus.weeks == weeklyStreak) {
                return bonus;
            }
        }
        return null;
    }
}
