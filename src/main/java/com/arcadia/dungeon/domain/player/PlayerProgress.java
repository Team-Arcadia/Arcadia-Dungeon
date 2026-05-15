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
    private String selectedClassId;
    private boolean customLoadoutUnlocked;
    private int loadoutPoints;
    private final Map<String, DungeonProgress> dungeons;

    public PlayerProgress(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.currency = 0L;
        this.selectedClassId = "";
        this.customLoadoutUnlocked = false;
        this.loadoutPoints = 0;
        this.dungeons = new HashMap<>();
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public long currency() { return currency; }
    public String selectedClassId() { return selectedClassId; }
    public boolean customLoadoutUnlocked() { return customLoadoutUnlocked; }
    public int loadoutPoints() { return loadoutPoints; }
    public Map<String, DungeonProgress> dungeons() { return Map.copyOf(dungeons); }

    public void setPlayerName(String name) { this.playerName = name; }
    public void addCurrency(long amount) { this.currency += amount; }

    public void selectClass(String classId) {
        this.selectedClassId = classId != null ? classId : "";
    }

    public void restoreLoadoutState(String selectedClassId, boolean customLoadoutUnlocked, int loadoutPoints) {
        this.selectedClassId = selectedClassId != null ? selectedClassId : "";
        this.customLoadoutUnlocked = customLoadoutUnlocked;
        this.loadoutPoints = Math.max(0, loadoutPoints);
    }

    public void recordRunCompletion(String dungeonId, long timeSeconds) {
        DungeonProgress dp = dungeons.computeIfAbsent(dungeonId, k -> new DungeonProgress());
        dp.completions++;
        dp.lastCompletionMs = System.currentTimeMillis();
        if (dp.bestTimeSeconds == 0 || timeSeconds < dp.bestTimeSeconds) {
            dp.bestTimeSeconds = timeSeconds;
        }
        int totalCompletions = dungeons.values().stream().mapToInt(d -> d.completions).sum();
        customLoadoutUnlocked = customLoadoutUnlocked || totalCompletions >= 3;
        loadoutPoints = Math.max(loadoutPoints, totalCompletions);
    }

    public void restoreDungeonProgress(String dungeonId, int completions, long bestTimeSeconds, long lastCompletionMs) {
        if (dungeonId == null || dungeonId.isBlank()) return;
        DungeonProgress dp = dungeons.computeIfAbsent(dungeonId, k -> new DungeonProgress());
        dp.completions = Math.max(0, completions);
        dp.bestTimeSeconds = Math.max(0L, bestTimeSeconds);
        dp.lastCompletionMs = Math.max(0L, lastCompletionMs);
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
