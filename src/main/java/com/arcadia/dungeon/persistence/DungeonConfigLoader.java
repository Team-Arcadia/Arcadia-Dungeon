package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads dungeon JSON configs from {@code config/arcadia/dungeon/}.
 *
 * <p>Development schema validation is intentionally strict: configs must be
 * rewritten when the JSON format changes.
 */
public final class DungeonConfigLoader {

    private static final Gson GSON = new GsonBuilder().create();

    private final Path configDir;
    private final Map<String, DungeonConfig> loaded = new LinkedHashMap<>();

    public DungeonConfigLoader() {
        this(FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon"));
    }

    /** Test constructor with a custom config path. */
    public DungeonConfigLoader(Path configDir) {
        this.configDir = configDir;
    }

    public Map<String, DungeonConfig> loadAll() {
        loaded.clear();
        if (!Files.exists(configDir)) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] dir_missing path={} count=0", configDir);
            return Map.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
            for (Path file : stream) {
                tryLoadOne(file).ifPresent(cfg -> loaded.put(cfg.id(), cfg));
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] dir_read_failed path={} error={}", configDir, e.getMessage());
        }

        ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] loaded count={} from={}", loaded.size(), configDir);
        return Map.copyOf(loaded);
    }

    public Optional<DungeonConfig> reload(String dungeonId) {
        Path file = findFileByConfigId(dungeonId)
            .orElse(configDir.resolve(sanitizeFileName(dungeonId) + ".json"));
        if (!Files.exists(file)) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][CONFIG] reload_miss dungeonId={} path={}", dungeonId, file);
            loaded.remove(dungeonId);
            return Optional.empty();
        }
        Optional<DungeonConfig> cfg = tryLoadOne(file);
        cfg.ifPresent(c -> loaded.put(c.id(), c));
        return cfg;
    }

    public Map<String, DungeonConfig> reloadAll() {
        return loadAll();
    }

    public void save(DungeonConfig cfg) {
        if (cfg == null || cfg.id() == null || cfg.id().isBlank()) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] save_rejected reason=null_or_blank_id");
            return;
        }
        Path file = findFileByConfigId(cfg.id())
            .orElse(configDir.resolve(sanitizeFileName(cfg.id()) + ".json"));
        try {
            Files.createDirectories(configDir);
            Gson pretty = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file, pretty.toJson(cfg));
            loaded.put(cfg.id(), cfg);
            ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] saved dungeonId={} path={}", cfg.id(), file);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] save_failed dungeonId={} error={}", cfg.id(), e.getMessage());
        }
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) return false;
        Path file = findFileByConfigId(id)
            .orElse(configDir.resolve(sanitizeFileName(id) + ".json"));
        try {
            boolean existed = Files.deleteIfExists(file);
            if (existed) {
                loaded.remove(id);
                ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] deleted dungeonId={} path={}", id, file);
            }
            return existed;
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] delete_failed dungeonId={} error={}", id, e.getMessage());
            return false;
        }
    }

    public Map<String, DungeonConfig> loaded() {
        return Map.copyOf(loaded);
    }

    private Optional<DungeonConfig> tryLoadOne(Path file) {
        String fileName = file.getFileName().toString();
        try {
            String json = Files.readString(file);
            DungeonConfig cfg = GSON.fromJson(json, DungeonConfig.class);
            String validationError = validate(cfg, fileName);
            if (validationError != null) {
                ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] invalid file={} error={}", fileName, validationError);
                return Optional.empty();
            }
            return Optional.of(cfg);
        } catch (JsonSyntaxException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] json_syntax_error file={} error={}", fileName, e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] read_failed file={} error={}", fileName, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> findFileByConfigId(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank() || !Files.exists(configDir)) {
            return Optional.empty();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
            for (Path file : stream) {
                try {
                    DungeonConfig cfg = GSON.fromJson(Files.readString(file), DungeonConfig.class);
                    if (cfg != null && dungeonId.equals(cfg.id())) {
                        return Optional.of(file);
                    }
                } catch (JsonSyntaxException | IOException ignored) {
                    // Invalid files are reported by loadAll(); lookup just skips unreadable files.
                }
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] file_lookup_failed dungeonId={} error={}", dungeonId, e.getMessage());
        }
        return Optional.empty();
    }

    private String validate(DungeonConfig cfg, String fileName) {
        if (cfg == null) return "config null after parse";
        if (cfg.schemaVersion() != DungeonConfig.CURRENT_SCHEMA_VERSION) {
            return "schemaVersion must be " + DungeonConfig.CURRENT_SCHEMA_VERSION + " (got " + cfg.schemaVersion() + ")";
        }
        if (cfg.id() == null || cfg.id().isBlank()) return "missing id";
        if (cfg.nameKey() == null || cfg.nameKey().isBlank()) return "missing nameKey";
        if (cfg.lives() <= 0) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][CONFIG] dungeonId={} lives={} invalid - must be > 0, skipped", cfg.id(), cfg.lives());
            return "lives must be > 0 (got " + cfg.lives() + ")";
        }

        var configuredBosses = cfg.configuredBosses();
        if (configuredBosses.isEmpty()) return "bosses must contain >= 1 entry";
        int bossIndex = 0;
        for (DungeonConfig.BossDefinition boss : configuredBosses) {
            if (boss.type() == null || boss.type().isBlank()) return "bosses[" + bossIndex + "].type missing";
            if (boss.hp() <= 0) return "bosses[" + bossIndex + "].hp must be > 0";
            bossIndex++;
        }

        if (cfg.waves() == null) return "missing top-level waves array";
        int waveIndex = 0;
        for (DungeonConfig.Wave wave : cfg.waves()) {
            if (wave == null) return "waves[" + waveIndex + "] null";
            String triggerMode = wave.triggerMode();
            if (!"ordered".equals(triggerMode) && !"ticks".equals(triggerMode)) {
                return "waves[" + waveIndex + "].triggerMode must be ordered or ticks";
            }
            if (wave.delayTicks() < 0) return "waves[" + waveIndex + "].delayTicks must be >= 0";
            if (wave.mobs() == null || wave.mobs().isEmpty()) {
                return "waves[" + waveIndex + "].mobs must contain >= 1 entry";
            }
            int mobIndex = 0;
            for (DungeonConfig.MobSpawn mob : wave.mobs()) {
                if (mob.mobType() == null || mob.mobType().isBlank()) {
                    return "waves[" + waveIndex + "].mobs[" + mobIndex + "].mobType missing";
                }
                if (mob.count() <= 0) return "waves[" + waveIndex + "].mobs[" + mobIndex + "].count must be > 0";
                mobIndex++;
            }
            waveIndex++;
        }

        if (cfg.rewards() == null) return "missing rewards";
        return null;
    }

    private static String sanitizeFileName(String s) {
        return s.replaceAll("[^a-zA-Z0-9_:-]", "_").replace(":", "_");
    }
}
