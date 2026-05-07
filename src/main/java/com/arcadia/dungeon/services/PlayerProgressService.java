package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.player.PlayerProgress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Service progression joueur — currency + PB par donjon (Stories S5.1, S4.3).
 *
 * <p>Persistance JSON dans {@code config/arcadia/player_progress.json}.
 * Chargé au {@code ServerStartingEvent}, sauvegardé après chaque mise à jour PB/currency.
 */
public final class PlayerProgressService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/arcadia/player_progress.json";

    private final Map<UUID, PlayerProgress> progressMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "arcadia-progress-save");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean savePending = false;

    public PlayerProgress getOrCreate(UUID playerId, String playerName) {
        return progressMap.computeIfAbsent(playerId, id -> new PlayerProgress(id, playerName));
    }

    public Optional<PlayerProgress> get(UUID playerId) {
        return Optional.ofNullable(progressMap.get(playerId));
    }

    public void addCurrency(UUID playerId, String playerName, long amount) {
        PlayerProgress p = getOrCreate(playerId, playerName);
        p.addCurrency(amount);
        ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=currency_add playerId={} amount={} total={}",
            playerId, amount, p.currency());
        saveAsync();
    }

    /**
     * Enregistre une complétion. Retourne {@code true} si c'est un nouveau PB.
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

    // ── Persistence ────────────────────────────────────────────────────────

    /** Chargé au ServerStartingEvent. */
    public void load() {
        Path path = FMLPaths.GAMEDIR.get().resolve(DATA_FILE);
        if (!Files.exists(path)) return;
        try (Reader r = Files.newBufferedReader(path)) {
            Type type = new com.google.gson.reflect.TypeToken<Map<String, ProgressData>>() {}.getType();
            Map<String, ProgressData> raw = GSON.fromJson(r, type);
            if (raw == null) return;
            raw.forEach((uuidStr, data) -> {
                try {
                    UUID id = UUID.fromString(uuidStr);
                    PlayerProgress p = new PlayerProgress(id, data.playerName != null ? data.playerName : "unknown");
                    p.addCurrency(data.currency);
                    if (data.dungeons != null) {
                        data.dungeons.forEach((dungeonId, dp) -> {
                            if (dp.bestTimeSeconds > 0) {
                                p.recordRunCompletion(dungeonId, dp.bestTimeSeconds);
                            }
                        });
                    }
                    progressMap.put(id, p);
                } catch (IllegalArgumentException ignored) {}
            });
            ArcadiaDungeon.LOGGER.info("[Arcadia][PROGRESS] event=loaded count={}", progressMap.size());
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][PROGRESS] load_failed: {}", e.getMessage());
        }
    }

    public void save() {
        Path path = FMLPaths.GAMEDIR.get().resolve(DATA_FILE);
        try {
            Files.createDirectories(path.getParent());
            Map<String, ProgressData> raw = new HashMap<>();
            progressMap.forEach((id, p) -> {
                ProgressData data = new ProgressData();
                data.playerName = p.playerName();
                data.currency = p.currency();
                p.dungeons().forEach((dungeonId, dp) -> {
                    DungeonData dd = new DungeonData();
                    dd.completions = dp.completions;
                    dd.bestTimeSeconds = dp.bestTimeSeconds;
                    dd.lastCompletionMs = dp.lastCompletionMs;
                    data.dungeons.put(dungeonId, dd);
                });
                raw.put(id.toString(), data);
            });
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, w);
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][PROGRESS] save_failed: {}", e.getMessage());
        }
    }

    private void saveAsync() {
        if (savePending) return;
        savePending = true;
        saveExecutor.execute(() -> { savePending = false; save(); });
    }

    /** Appelé depuis ArcadiaDungeon.onServerStopping pour terminer le thread proprement. */
    public void shutdown() {
        saveExecutor.shutdownNow();
    }

    // ── DTOs sérialisables ─────────────────────────────────────────────────

    private static class ProgressData {
        String playerName = "";
        long currency = 0L;
        Map<String, DungeonData> dungeons = new HashMap<>();
    }

    private static class DungeonData {
        int completions = 0;
        long bestTimeSeconds = 0L;
        long lastCompletionMs = 0L;
    }
}
