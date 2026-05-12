package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registre central des configs donjons chargés.
 *
 * <p>Orchestre le boot serveur :
 * <ol>
 *   <li>{@link DungeonExampleSeeder#seedIfEmpty()} — copie l'exemple si dossier vide (Story S1.3)</li>
 *   <li>{@link DungeonConfigLoader#loadAll()} — charge tous les JSON valides (Story S1.2)</li>
 *   <li><b>Fail-safe (Story S1.6)</b> : si AUCUN donjon valide chargé, fallback sur l'exemple in-memory depuis le JAR</li>
 * </ol>
 *
 * <p>Le résultat est immutable après boot (jusqu'à hot-reload via {@link #reload()}).
 */
public final class DungeonRegistry {

    private final DungeonExampleSeeder seeder;
    private final DungeonConfigLoader loader;
    private final Map<String, DungeonConfig> dungeons = new LinkedHashMap<>();
    private boolean fallbackActive = false;

    public DungeonRegistry() {
        this(new DungeonExampleSeeder(), new DungeonConfigLoader());
    }

    /** Constructeur pour tests (DI). */
    public DungeonRegistry(DungeonExampleSeeder seeder, DungeonConfigLoader loader) {
        this.seeder = seeder;
        this.loader = loader;
    }

    /**
     * Boot orchestration : seed → load → fail-safe.
     */
    public void bootstrap() {
        seeder.seedIfEmpty();
        Map<String, DungeonConfig> loaded = loader.loadAll();
        applyLoaded(loaded);
    }

    /**
     * Reload all configs (utilisé par /arcadia reload — Story S1.4).
     * Respecte le fail-safe : si aucun valide après reload, fallback exemple.
     */
    public Map<String, DungeonConfig> reload() {
        Map<String, DungeonConfig> loaded = loader.reloadAll();
        applyLoaded(loaded);
        return Map.copyOf(dungeons);
    }

    /** Donjons actuellement disponibles (read-only snapshot). */
    public Map<String, DungeonConfig> dungeons() {
        return Map.copyOf(dungeons);
    }

    public Optional<DungeonConfig> get(String dungeonId) {
        return Optional.ofNullable(dungeons.get(dungeonId));
    }

    /**
     * Persiste {@code cfg} sur disque et l'ajoute au registre en mémoire.
     * Utilisé par le handler admin {@code handleCreateDungeon}.
     */
    public void save(DungeonConfig cfg) {
        loader.save(cfg);
        dungeons.put(cfg.id(), cfg);
        fallbackActive = false;
    }

    /**
     * Supprime le donjon {@code id} du disque et du registre en mémoire.
     *
     * @return {@code true} si le donjon existait et a été supprimé
     */
    public boolean delete(String id) {
        boolean deleted = loader.delete(id);
        if (deleted) dungeons.remove(id);
        return deleted;
    }

    /** True si le mod tourne en mode fail-safe (donjon exemple uniquement, depuis JAR). */
    public boolean isFallbackActive() {
        return fallbackActive;
    }

    // ============================================================
    // Internals
    // ============================================================

    private void applyLoaded(Map<String, DungeonConfig> loaded) {
        dungeons.clear();
        if (loaded.isEmpty()) {
            applyFallback();
        } else {
            dungeons.putAll(loaded);
            fallbackActive = false;
            ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] registry_ready count={} fallback=false",
                dungeons.size());
        }
    }

    /**
     * Story S1.6 — fail-safe : aucun donjon valide → on charge l'exemple depuis le JAR.
     */
    private void applyFallback() {
        Optional<DungeonConfig> fallback = DungeonExampleSeeder.loadFromJarFallback();
        if (fallback.isPresent()) {
            dungeons.put(fallback.get().id(), fallback.get());
            fallbackActive = true;
            ArcadiaDungeon.LOGGER.warn("⚠ [Arcadia] Aucun donjon valide chargé. Fallback sur l'exemple JAR. " +
                "Vérifiez vos JSON dans config/arcadia/dungeon/");
        } else {
            fallbackActive = false;
            ArcadiaDungeon.LOGGER.error(
                "[Arcadia][CONFIG] registry_empty count=0 fallback_failed=true reason=jar_resource_missing");
        }
    }
}
