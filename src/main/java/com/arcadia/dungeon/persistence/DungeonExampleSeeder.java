package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Seede le donjon exemple au premier boot serveur si {@code config/arcadia/dungeon/}
 * est vide.
 *
 * <p>Story S1.3.
 *
 * <p>Source : {@code arcadia_dungeon/example/example_dungeon.json} dans le JAR.
 * Destination : {@code config/arcadia/dungeon/example_dungeon.json}.
 *
 * <p>Idempotent : si le dossier contient déjà des fichiers, ne fait rien.
 */
public final class DungeonExampleSeeder {

    private static final String JAR_RESOURCE_PATH = "/arcadia_dungeon/example/example_dungeon.json";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private final Path configDir;

    public DungeonExampleSeeder() {
        this(FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon"));
    }

    /** Constructeur pour tests (path custom). */
    public DungeonExampleSeeder(Path configDir) {
        this.configDir = configDir;
    }

    /**
     * Si {@code configDir} n'existe pas ou est vide, copie le donjon exemple depuis le JAR.
     *
     * @return true si seed effectué, false si dossier non-vide (idempotent)
     */
    public boolean seedIfEmpty() {
        try {
            if (Files.exists(configDir) && !isEmpty(configDir)) {
                return false;
            }
            Files.createDirectories(configDir);
            Path target = configDir.resolve("example_dungeon.json");
            try (InputStream in = DungeonExampleSeeder.class.getResourceAsStream(JAR_RESOURCE_PATH)) {
                if (in == null) {
                    ArcadiaDungeon.LOGGER.error(
                        "[Arcadia][CONFIG] seed_failed reason=resource_not_found path={}",
                        JAR_RESOURCE_PATH);
                    return false;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            ArcadiaDungeon.LOGGER.info("[Arcadia][CONFIG] event=seed_example target={}", target);
            return true;
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] seed_failed error={}", e.getMessage());
            return false;
        }
    }

    /**
     * Charge le donjon exemple **directement depuis le JAR** (sans passer par filesystem).
     * Utilisé par le fail-safe (Story S1.6) quand AUCUN donjon valide n'a été chargé.
     */
    public static Optional<DungeonConfig> loadFromJarFallback() {
        try (InputStream in = DungeonExampleSeeder.class.getResourceAsStream(JAR_RESOURCE_PATH)) {
            if (in == null) {
                ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] fallback_failed reason=resource_not_found path={}",
                    JAR_RESOURCE_PATH);
                return Optional.empty();
            }
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DungeonConfig cfg = GSON.fromJson(json, DungeonConfig.class);
            ArcadiaDungeon.LOGGER.warn(
                "[Arcadia][CONFIG] event=fallback_to_example reason=no_valid_dungeon_loaded id={}",
                cfg != null ? cfg.id() : "?");
            return Optional.ofNullable(cfg);
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][CONFIG] fallback_failed error={}", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (var stream = Files.newDirectoryStream(dir, "*.json")) {
            return !stream.iterator().hasNext();
        }
    }
}
