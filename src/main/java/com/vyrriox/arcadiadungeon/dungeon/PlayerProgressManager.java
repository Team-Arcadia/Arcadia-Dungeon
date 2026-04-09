package com.vyrriox.arcadiadungeon.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProgressManager {
    private static final PlayerProgressManager INSTANCE = new PlayerProgressManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dataDir;
    private final Map<String, PlayerProgress> playerData = new ConcurrentHashMap<>();

    private PlayerProgressManager() {
        this.dataDir = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon").resolve("playerdata");
    }

    public static PlayerProgressManager getInstance() {
        return INSTANCE;
    }

    public void loadAll() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to create playerdata directory", e);
            return;
        }

        playerData.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file);
                    PlayerProgress progress = GSON.fromJson(json, PlayerProgress.class);
                    if (progress != null && progress.uuid != null && !progress.uuid.isEmpty()) {
                        playerData.put(progress.uuid, progress);
                    }
                } catch (Exception e) {
                    ArcadiaDungeon.LOGGER.error("Failed to load player progress: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to read playerdata directory", e);
        }

        ArcadiaDungeon.LOGGER.info("Loaded {} player progress files", playerData.size());
    }

    public void save(PlayerProgress progress) {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve(progress.uuid + ".json");
            String json = GSON.toJson(progress);
            Files.writeString(file, json);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("Failed to save player progress: {}", progress.uuid, e);
        }
    }

    public PlayerProgress getOrCreate(String uuid, String playerName) {
        PlayerProgress progress = playerData.get(uuid);
        if (progress == null) {
            progress = new PlayerProgress(uuid, playerName);
            playerData.put(uuid, progress);
        }
        if (playerName != null && !playerName.isEmpty()) {
            progress.playerName = playerName;
        }
        return progress;
    }

    public PlayerProgress get(String uuid) {
        return playerData.get(uuid);
    }

    private final Set<String> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public void recordCompletion(String uuid, String playerName, String dungeonId, long timeSeconds) {
        PlayerProgress progress = getOrCreate(uuid, playerName);
        progress.recordCompletion(dungeonId, timeSeconds);
        dirtyPlayers.add(uuid); // Fix #8: mark dirty instead of saving immediately
    }

    public void recordCompletionAndSave(String uuid, String playerName, String dungeonId, long timeSeconds) {
        PlayerProgress progress = getOrCreate(uuid, playerName);
        progress.recordCompletion(dungeonId, timeSeconds);
        save(progress);
        dirtyPlayers.remove(uuid);
    }

    public void saveNow(String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        PlayerProgress progress = playerData.get(uuid);
        if (progress != null) {
            save(progress);
            dirtyPlayers.remove(uuid);
        }
    }

    public void flushDirty() {
        if (dirtyPlayers.isEmpty()) return;
        for (String uuid : new ArrayList<>(dirtyPlayers)) {
            PlayerProgress progress = playerData.get(uuid);
            if (progress != null) save(progress);
        }
        dirtyPlayers.clear();
    }

    public boolean hasCompleted(String uuid, String dungeonId) {
        PlayerProgress progress = playerData.get(uuid);
        return progress != null && progress.hasCompleted(dungeonId);
    }

    public List<PlayerProgress> getTopPlayers(int limit) {
        List<PlayerProgress> all = new ArrayList<>(playerData.values());
        all.sort((a, b) -> Integer.compare(b.getTotalCompletions(), a.getTotalCompletions()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<Map.Entry<PlayerProgress, PlayerProgress.DungeonProgress>> getTopForDungeon(String dungeonId, int limit) {
        List<Map.Entry<PlayerProgress, PlayerProgress.DungeonProgress>> entries = new ArrayList<>();
        for (PlayerProgress progress : playerData.values()) {
            PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeonId);
            if (dp != null && dp.completions > 0) {
                entries.add(Map.entry(progress, dp));
            }
        }
        // Sort by best time (fastest first), then by completions
        entries.sort((a, b) -> {
            long timeA = a.getValue().bestTimeSeconds;
            long timeB = b.getValue().bestTimeSeconds;
            if (timeA != timeB) return Long.compare(timeA, timeB);
            return Integer.compare(b.getValue().completions, a.getValue().completions);
        });
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    public PlayerProgress findByName(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        for (PlayerProgress progress : playerData.values()) {
            if (progress.playerName != null && progress.playerName.equalsIgnoreCase(playerName)) {
                return progress;
            }
        }
        return null;
    }

    public Collection<PlayerProgress> getAll() {
        return Collections.unmodifiableCollection(playerData.values());
    }

    public void resetPlayer(String uuid) {
        PlayerProgress progress = playerData.get(uuid);
        if (progress != null) {
            progress.completedDungeons.clear();
            save(progress);
        }
    }

    public void resetPlayerDungeon(String uuid, String dungeonId) {
        PlayerProgress progress = playerData.get(uuid);
        if (progress != null) {
            progress.completedDungeons.remove(dungeonId);
            save(progress);
        }
    }

    public void setPlayerDungeon(String uuid, String playerName, String dungeonId, int completions, long bestTime) {
        PlayerProgress progress = getOrCreate(uuid, playerName);
        PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeonId);
        if (dp == null) {
            dp = new PlayerProgress.DungeonProgress();
            progress.completedDungeons.put(dungeonId, dp);
        }
        dp.completions = completions;
        dp.bestTimeSeconds = bestTime;
        dp.lastCompletionTimestamp = System.currentTimeMillis();
        save(progress);
    }
}
