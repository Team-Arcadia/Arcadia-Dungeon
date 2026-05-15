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

    public static final String CUSTOM_LOADOUT_ID = "__custom__";
    public static final String DEFAULT_CUSTOM_MAIN = "minecraft:iron_sword";
    public static final String DEFAULT_CUSTOM_OFF = "minecraft:shield";
    public static final String DEFAULT_CUSTOM_UTILITY = "minecraft:bread";

    private final UUID playerId;
    private String playerName;
    private long currency;
    private String selectedClassId;
    private boolean customLoadoutUnlocked;
    private int loadoutPoints;
    private String customMainItem;
    private String customOffItem;
    private String customUtilityItem;
    private final Map<String, DungeonProgress> dungeons;

    public PlayerProgress(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.currency = 0L;
        this.selectedClassId = "";
        this.customLoadoutUnlocked = false;
        this.loadoutPoints = 0;
        this.customMainItem = DEFAULT_CUSTOM_MAIN;
        this.customOffItem = DEFAULT_CUSTOM_OFF;
        this.customUtilityItem = DEFAULT_CUSTOM_UTILITY;
        this.dungeons = new HashMap<>();
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public long currency() { return currency; }
    public String selectedClassId() { return selectedClassId; }
    public boolean customLoadoutUnlocked() { return customLoadoutUnlocked; }
    public int loadoutPoints() { return loadoutPoints; }
    public String customMainItem() { return customMainItem; }
    public String customOffItem() { return customOffItem; }
    public String customUtilityItem() { return customUtilityItem; }
    public Map<String, DungeonProgress> dungeons() { return Map.copyOf(dungeons); }

    public void setPlayerName(String name) { this.playerName = name; }
    public void addCurrency(long amount) { this.currency = Math.max(0L, this.currency + amount); }
    public void setCurrency(long amount) { this.currency = Math.max(0L, amount); }
    public void addLoadoutPoints(int amount) { this.loadoutPoints = Math.max(0, this.loadoutPoints + amount); }
    public void unlockCustomLoadout() { this.customLoadoutUnlocked = true; }

    public void selectClass(String classId) {
        this.selectedClassId = classId != null ? classId : "";
    }

    public void restoreLoadoutState(String selectedClassId, boolean customLoadoutUnlocked, int loadoutPoints) {
        restoreLoadoutState(selectedClassId, customLoadoutUnlocked, loadoutPoints,
            DEFAULT_CUSTOM_MAIN, DEFAULT_CUSTOM_OFF, DEFAULT_CUSTOM_UTILITY);
    }

    public void restoreLoadoutState(String selectedClassId, boolean customLoadoutUnlocked, int loadoutPoints,
                                    String customMainItem, String customOffItem, String customUtilityItem) {
        this.selectedClassId = selectedClassId != null ? selectedClassId : "";
        this.customLoadoutUnlocked = customLoadoutUnlocked;
        this.loadoutPoints = Math.max(0, loadoutPoints);
        this.customMainItem = safeItem(customMainItem, DEFAULT_CUSTOM_MAIN);
        this.customOffItem = safeItem(customOffItem, DEFAULT_CUSTOM_OFF);
        this.customUtilityItem = safeItem(customUtilityItem, DEFAULT_CUSTOM_UTILITY);
    }

    public void saveCustomLoadout(String mainItem, String offItem, String utilityItem) {
        this.customMainItem = safeItem(mainItem, DEFAULT_CUSTOM_MAIN);
        this.customOffItem = safeItem(offItem, DEFAULT_CUSTOM_OFF);
        this.customUtilityItem = safeItem(utilityItem, DEFAULT_CUSTOM_UTILITY);
        this.selectedClassId = CUSTOM_LOADOUT_ID;
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

    private static String safeItem(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
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
