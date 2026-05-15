package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.player.PlayerProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArcadiaDatabaseService implements AutoCloseable {

    private static final long MAX_INVENTORY_NBT_BYTES = 4L * 1024L * 1024L;
    private static final String DATA_FILE = "config/arcadia/arcadia.db";

    private Connection connection;

    public synchronized void bootstrap() {
        if (connection != null) return;
        Path path = FMLPaths.GAMEDIR.get().resolve(DATA_FILE);
        try {
            Files.createDirectories(path.getParent());
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_dungeon_inventory (
                        player_id TEXT PRIMARY KEY,
                        inventory_nbt BLOB NOT NULL,
                        updated_at_ms INTEGER NOT NULL
                    )
                    """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_inventory_backup (
                        player_id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        inventory_nbt BLOB NOT NULL,
                        saved_at_ms INTEGER NOT NULL
                    )
                    """);
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_profile (
                        player_id TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        currency INTEGER NOT NULL DEFAULT 0,
                        selected_class_id TEXT NOT NULL DEFAULT '',
                        custom_loadout_unlocked INTEGER NOT NULL DEFAULT 0,
                        loadout_points INTEGER NOT NULL DEFAULT 0,
                        custom_main_item TEXT NOT NULL DEFAULT 'minecraft:iron_sword',
                        custom_off_item TEXT NOT NULL DEFAULT 'minecraft:shield',
                        custom_utility_item TEXT NOT NULL DEFAULT 'minecraft:bread',
                        updated_at_ms INTEGER NOT NULL
                    )
                    """);
                ensureColumn("player_profile", "custom_main_item", "TEXT NOT NULL DEFAULT 'minecraft:iron_sword'");
                ensureColumn("player_profile", "custom_off_item", "TEXT NOT NULL DEFAULT 'minecraft:shield'");
                ensureColumn("player_profile", "custom_utility_item", "TEXT NOT NULL DEFAULT 'minecraft:bread'");
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_dungeon_stats (
                        player_id TEXT NOT NULL,
                        dungeon_id TEXT NOT NULL,
                        completions INTEGER NOT NULL DEFAULT 0,
                        best_time_seconds INTEGER NOT NULL DEFAULT 0,
                        last_completion_ms INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(player_id, dungeon_id)
                    )
                    """);
            }
            ArcadiaDungeon.LOGGER.info("[Arcadia][DB] event=ready path={}", path);
        } catch (ClassNotFoundException | SQLException | IOException e) {
            throw new IllegalStateException("Unable to initialize Arcadia SQLite database", e);
        }
    }

    public synchronized Optional<ListTag> loadDungeonInventory(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT inventory_nbt FROM player_dungeon_inventory WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(decodeInventory(result.getBytes(1)));
            }
        } catch (SQLException | IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] dungeon_inventory_load_failed playerId={} error={}",
                playerId, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized void saveDungeonInventory(UUID playerId, ListTag inventory) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_dungeon_inventory(player_id, inventory_nbt, updated_at_ms)
            VALUES(?, ?, ?)
            ON CONFLICT(player_id) DO UPDATE SET
                inventory_nbt = excluded.inventory_nbt,
                updated_at_ms = excluded.updated_at_ms
            """)) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, encodeInventory(inventory));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Unable to save Arcadia dungeon inventory for " + playerId, e);
        }
    }

    public synchronized void deleteDungeonInventory(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM player_dungeon_inventory WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] dungeon_inventory_delete_failed playerId={} error={}",
                playerId, e.getMessage());
        }
    }

    public synchronized Optional<ListTag> loadNormalInventoryBackup(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT inventory_nbt FROM player_inventory_backup WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(decodeInventory(result.getBytes(1)));
            }
        } catch (SQLException | IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] normal_inventory_backup_load_failed playerId={} error={}",
                playerId, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized boolean hasNormalInventoryBackup(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM player_inventory_backup WHERE player_id = ? LIMIT 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] normal_inventory_backup_check_failed playerId={} error={}",
                playerId, e.getMessage());
            return false;
        }
    }

    public synchronized Optional<String> loadNormalInventoryBackupRunId(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT run_id FROM player_inventory_backup WHERE player_id = ? LIMIT 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.ofNullable(result.getString(1));
            }
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] normal_inventory_backup_run_id_failed playerId={} error={}",
                playerId, e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized void saveNormalInventoryBackup(UUID playerId, String runId, ListTag inventory) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_inventory_backup(player_id, run_id, inventory_nbt, saved_at_ms)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(player_id) DO UPDATE SET
                run_id = excluded.run_id,
                inventory_nbt = excluded.inventory_nbt,
                saved_at_ms = excluded.saved_at_ms
            """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, runId != null ? runId : "");
            statement.setBytes(3, encodeInventory(inventory));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Unable to save Arcadia normal inventory backup for " + playerId, e);
        }
    }

    public synchronized void deleteNormalInventoryBackup(UUID playerId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM player_inventory_backup WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] normal_inventory_backup_delete_failed playerId={} error={}",
                playerId, e.getMessage());
        }
    }

    public synchronized Map<UUID, PlayerProgress> loadPlayerProgress() {
        ensureOpen();
        Map<UUID, PlayerProgress> progress = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT player_id, player_name, currency, selected_class_id, custom_loadout_unlocked,
                   loadout_points, custom_main_item, custom_off_item, custom_utility_item
            FROM player_profile
            """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(result.getString("player_id"));
                } catch (IllegalArgumentException e) {
                    ArcadiaDungeon.LOGGER.warn("[Arcadia][DB] invalid_player_profile_uuid value={}",
                        result.getString("player_id"));
                    continue;
                }
                PlayerProgress player = new PlayerProgress(playerId, result.getString("player_name"));
                player.addCurrency(result.getLong("currency"));
                player.restoreLoadoutState(
                    result.getString("selected_class_id"),
                    result.getInt("custom_loadout_unlocked") != 0,
                    result.getInt("loadout_points"),
                    result.getString("custom_main_item"),
                    result.getString("custom_off_item"),
                    result.getString("custom_utility_item"));
                progress.put(playerId, player);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load Arcadia player profiles", e);
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT player_id, dungeon_id, completions, best_time_seconds, last_completion_ms
            FROM player_dungeon_stats
            """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(result.getString("player_id"));
                } catch (IllegalArgumentException e) {
                    ArcadiaDungeon.LOGGER.warn("[Arcadia][DB] invalid_player_stats_uuid value={}",
                        result.getString("player_id"));
                    continue;
                }
                PlayerProgress player = progress.get(playerId);
                if (player == null) continue;
                player.restoreDungeonProgress(
                    result.getString("dungeon_id"),
                    result.getInt("completions"),
                    result.getLong("best_time_seconds"),
                    result.getLong("last_completion_ms"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load Arcadia player dungeon stats", e);
        }
        return Map.copyOf(progress);
    }

    public synchronized void saveAllPlayerProgress(Collection<PlayerProgress> progress) {
        ensureOpen();
        for (PlayerProgress playerProgress : progress) {
            savePlayerProgress(playerProgress);
        }
    }

    public synchronized void savePlayerProgress(PlayerProgress progress) {
        ensureOpen();
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            upsertPlayerProfile(progress);
            replaceDungeonStats(progress);
            connection.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException("Unable to save Arcadia player progress for " + progress.playerId(), e);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                ArcadiaDungeon.LOGGER.error("[Arcadia][DB] autocommit_restore_failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] close_failed: {}", e.getMessage());
        } finally {
            connection = null;
        }
    }

    private void upsertPlayerProfile(PlayerProgress progress) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_profile(
                player_id, player_name, currency, selected_class_id,
                custom_loadout_unlocked, loadout_points, custom_main_item,
                custom_off_item, custom_utility_item, updated_at_ms
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_id) DO UPDATE SET
                player_name = excluded.player_name,
                currency = excluded.currency,
                selected_class_id = excluded.selected_class_id,
                custom_loadout_unlocked = excluded.custom_loadout_unlocked,
                loadout_points = excluded.loadout_points,
                custom_main_item = excluded.custom_main_item,
                custom_off_item = excluded.custom_off_item,
                custom_utility_item = excluded.custom_utility_item,
                updated_at_ms = excluded.updated_at_ms
            """)) {
            statement.setString(1, progress.playerId().toString());
            statement.setString(2, progress.playerName() != null ? progress.playerName() : "unknown");
            statement.setLong(3, progress.currency());
            statement.setString(4, progress.selectedClassId());
            statement.setInt(5, progress.customLoadoutUnlocked() ? 1 : 0);
            statement.setInt(6, progress.loadoutPoints());
            statement.setString(7, progress.customMainItem());
            statement.setString(8, progress.customOffItem());
            statement.setString(9, progress.customUtilityItem());
            statement.setLong(10, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void replaceDungeonStats(PlayerProgress progress) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
            "DELETE FROM player_dungeon_stats WHERE player_id = ?")) {
            delete.setString(1, progress.playerId().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO player_dungeon_stats(
                player_id, dungeon_id, completions, best_time_seconds, last_completion_ms
            )
            VALUES(?, ?, ?, ?, ?)
            """)) {
            for (Map.Entry<String, PlayerProgress.DungeonProgress> entry : progress.dungeons().entrySet()) {
                PlayerProgress.DungeonProgress dungeonProgress = entry.getValue();
                insert.setString(1, progress.playerId().toString());
                insert.setString(2, entry.getKey());
                insert.setInt(3, dungeonProgress.completions);
                insert.setLong(4, dungeonProgress.bestTimeSeconds);
                insert.setLong(5, dungeonProgress.lastCompletionMs);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][DB] rollback_failed: {}", e.getMessage());
        }
    }

    private void ensureOpen() {
        if (connection == null) {
            throw new IllegalStateException("ArcadiaDatabaseService not initialized");
        }
    }

    private static byte[] encodeInventory(ListTag inventory) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        root.put("inventory", inventory.copy());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return out.toByteArray();
    }

    private static ListTag decodeInventory(byte[] data) throws IOException {
        CompoundTag root = NbtIo.readCompressed(
            new ByteArrayInputStream(data),
            NbtAccounter.create(MAX_INVENTORY_NBT_BYTES));
        return root.getList("inventory", Tag.TAG_COMPOUND);
    }
}
