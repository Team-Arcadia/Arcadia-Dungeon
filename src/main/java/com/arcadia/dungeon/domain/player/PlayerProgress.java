package com.arcadia.dungeon.domain.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agrégat domain — progression d'un joueur.
 *
 * <p>Version MINIMALE MVP : currency + PB par donjon uniquement.
 * Pas de XP/level/rank/milestones (à ajouter en v1.1+).
 *
 * @see <a href="../../../../../../../../_bmad-output/planning-artifacts/architecture-v1.md">architecture-v1 §4.4</a>
 */
public final class PlayerProgress {

    private final UUID playerId;
    private String playerName;
    private long currency;
    private final Map<String, DungeonProgress> dungeons;

    public PlayerProgress(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.currency = 0L;
        this.dungeons = new HashMap<>();
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public long currency() { return currency; }
    public Map<String, DungeonProgress> dungeons() { return Map.copyOf(dungeons); }

    public void setPlayerName(String name) { this.playerName = name; }
    public void addCurrency(long amount) { this.currency += amount; }

    public void recordRunCompletion(String dungeonId, long timeSeconds) {
        DungeonProgress dp = dungeons.computeIfAbsent(dungeonId, k -> new DungeonProgress());
        dp.completions++;
        dp.lastCompletionMs = System.currentTimeMillis();
        if (dp.bestTimeSeconds == 0 || timeSeconds < dp.bestTimeSeconds) {
            dp.bestTimeSeconds = timeSeconds;
        }
    }

    /**
     * Progression du joueur sur un donjon spécifique.
     */
    public static final class DungeonProgress {
        public int completions = 0;
        public long bestTimeSeconds = 0L;   // 0 = pas de PB
        public long lastCompletionMs = 0L;
    }
}
