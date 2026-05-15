package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.player.PlayerProgress;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Player progress service: currency, global loadout selection and per-dungeon PBs.
 */
public final class PlayerProgressService {

    private final ArcadiaDatabaseService databaseService;
    private final Map<UUID, PlayerProgress> progressMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "arcadia-progress-save");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean savePending = false;

    public PlayerProgressService(ArcadiaDatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public PlayerProgress getOrCreate(UUID playerId, String playerName) {
        return progressMap.compute(playerId, (id, current) -> {
            if (current == null) return new PlayerProgress(id, playerName);
            current.setPlayerName(playerName);
            return current;
        });
    }

    public Optional<PlayerProgress> get(UUID playerId) {
        return Optional.ofNullable(progressMap.get(playerId));
    }

    public boolean selectClass(UUID playerId, String playerName, String classId) {
        if (classId == null || classId.isBlank()) return false;
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.selectClass(classId.trim());
        saveAsync();
        return true;
    }

    public boolean saveCustomLoadout(UUID playerId, String playerName,
                                     String mainItem, String offItem, String utilityItem) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        if (!p.customLoadoutUnlocked()) return false;
        p.saveCustomLoadout(mainItem, offItem, utilityItem);
        saveAsync();
        return true;
    }

    public void addCurrency(UUID playerId, String playerName, long amount) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.addCurrency(amount);
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=currency_add playerId={} amount={} total={}",
            playerId, amount, p.currency());
        saveAsync();
    }

    public void setCurrency(UUID playerId, String playerName, long amount) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.setCurrency(amount);
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=currency_set playerId={} total={}",
            playerId, p.currency());
        saveAsync();
    }

    public void addLoadoutPoints(UUID playerId, String playerName, int amount) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.addLoadoutPoints(amount);
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=loadout_points_add playerId={} amount={} total={}",
            playerId, amount, p.loadoutPoints());
        saveAsync();
    }

    public void unlockCustomLoadout(UUID playerId, String playerName) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.unlockCustomLoadout();
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=custom_loadout_unlock playerId={}", playerId);
        saveAsync();
    }

    public void resetProgress(UUID playerId, String playerName) {
        progressMap.put(playerId, new PlayerProgress(playerId, playerName));
        databaseService.deleteDungeonInventory(playerId);
        ArcadiaDungeon.LOGGER.warn("[Arcadia][PROGRESS] event=reset playerId={}", playerId);
        saveAsync();
    }

    /**
     * Records a completion. Returns true when this completion produces a new PB.
     */
    public boolean recordRunCompletion(UUID playerId, String playerName,
                                       String dungeonId, long timeSeconds) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        PlayerProgress.DungeonProgress before = p.dungeons().get(dungeonId);
        long oldBest = before != null ? before.bestTimeSeconds : 0L;

        p.recordRunCompletion(dungeonId, timeSeconds);

        PlayerProgress.DungeonProgress after = p.dungeons().get(dungeonId);
        boolean isNewPb = after != null && after.bestTimeSeconds > 0 && after.bestTimeSeconds != oldBest;

        saveAsync();
        return isNewPb;
    }

    public Map<UUID, PlayerProgress> snapshot() {
        return Map.copyOf(progressMap);
    }

    public void load() {
        progressMap.clear();
        progressMap.putAll(databaseService.loadPlayerProgress());
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=loaded source=sqlite count={}", progressMap.size());
    }

    public void save() {
        try {
            databaseService.saveAllPlayerProgress(progressMap.values());
        } catch (RuntimeException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][PROGRESS] sqlite_save_failed: {}", e.getMessage());
        }
    }

    private void saveAsync() {
        if (savePending) return;
        savePending = true;
        saveExecutor.execute(() -> {
            savePending = false;
            save();
        });
    }

    public void shutdown() {
        saveExecutor.shutdownNow();
    }
}
