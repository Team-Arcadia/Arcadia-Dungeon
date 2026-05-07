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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Charge tous les configs JSON donjons depuis {@code config/arcadia/dungeon/}.
 *
 * <p>Story S1.2.
 *
 * <p><b>Validation best-effort MVP</b> : champs obligatoires checked, types non
 * tous validés. Schema validator strict en v1.1 (cf. architecture-v1.md §14
 * dette technique).
 *
 * <p>I/O sur Virtual Thread Java 21 (cf. architecture-v1.md §8) — pas SGT.
 */
public final class DungeonConfigLoader {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    /** Récupère les `_doc` champs par exemple — Gson lenient les ignore par défaut. */

    private final Path configDir;
    private final Map<String, DungeonConfig> loaded = new LinkedHashMap<>();

    public DungeonConfigLoader() {
        this(FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon"));
    }

    /** Constructeur pour tests (path custom). */
    public DungeonConfigLoader(Path configDir) {
        this.configDir = configDir;
    }

    /**
     * Charge tous les `*.json` du dossier config et retourne la map des configs valides.
     * Les configs invalides sont loggés et skippés (les autres restent chargées).
     */
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

    /**
     * Recharge un donjon spécifique par id. Utilisé par {@code /arcadia reload <id>}.
     * Retourne le nouveau config si succès, empty si invalide ou absent.
     */
    public Optional<DungeonConfig> reload(String dungeonId) {
        Path file = configDir.resolve(sanitizeFileName(dungeonId) + ".json");
        if (!Files.exists(file)) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][CONFIG] reload_miss dungeonId={} path={}", dungeonId, file);
            loaded.remove(dungeonId);
            return Optional.empty();
        }
        Optional<DungeonConfig> cfg = tryLoadOne(file);
        cfg.ifPresent(c -> loaded.put(c.id(), c));
        return cfg;
    }

    /**
     * Reload all (utilisé par /arcadia reload sans argument).
     */
    public Map<String, DungeonConfig> reloadAll() {
        return loadAll();
    }

    /** Donjons chargés à un instant donné (read-only). */
    public Map<String, DungeonConfig> loaded() {
        return Map.copyOf(loaded);
    }

    // ============================================================
    // Internals
    // ============================================================

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

    /**
     * Validation best-effort des champs obligatoires.
     * Retourne null si OK, message d'erreur sinon.
     *
     * <p>TODO[DEBT] : remplacer par schema validator strict en v1.1.
     * Raison MVP : Gson + checks manuels suffisent pour validation basique.
     * Sortie : v1.1 quand on aura une suite de donjons configurés et que les
     * erreurs JSON deviennent fréquentes.
     */
    private String validate(DungeonConfig cfg, String fileName) {
        if (cfg == null) return "config null after parse";
        if (cfg.schemaVersion() == 0) return "missing or zero schemaVersion (must be " + DungeonConfig.CURRENT_SCHEMA_VERSION + ")";
        if (cfg.schemaVersion() > DungeonConfig.CURRENT_SCHEMA_VERSION) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][CONFIG] schema_version_future file={} schemaVersion={} current={} attempt=best-effort",
                fileName, cfg.schemaVersion(), DungeonConfig.CURRENT_SCHEMA_VERSION);
            // Best-effort : on essaie de charger quand même
        }
        if (cfg.id() == null || cfg.id().isBlank()) return "missing id";
        if (cfg.nameKey() == null || cfg.nameKey().isBlank()) return "missing nameKey";
        if (cfg.lives() < 0) return "lives must be >= 0";
        if (cfg.rooms() == null || cfg.rooms().isEmpty()) return "rooms must contain >= 1 entry";
        if (cfg.boss() == null) return "missing boss";
        if (cfg.boss().hp() <= 0) return "boss.hp must be > 0";
        if (cfg.rewards() == null) return "missing rewards";
        return null;
    }

    private static String sanitizeFileName(String s) {
        return s.replaceAll("[^a-zA-Z0-9_:-]", "_").replace(":", "_");
    }
}
