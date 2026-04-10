package com.vyrriox.arcadiadungeon.dungeon;

public class LevelUpResult {
    public final boolean leveledUp;
    public final int oldLevel;
    public final int newLevel;
    public final boolean rankChanged;
    public final String oldRank;
    public final String newRank;

    public LevelUpResult(boolean leveledUp, int oldLevel, int newLevel, boolean rankChanged, String oldRank, String newRank) {
        this.leveledUp = leveledUp;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.rankChanged = rankChanged;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }
}
