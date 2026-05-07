package com.arcadia.dungeon.persistence;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.phys.Vec3;
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

/**
 * Persiste les coordonnées de spawn + dimension par donjon (S8.setup).
 *
 * <p>Fichier : {@code config/arcadia_dungeon/placements.json}.
 * Format : {@code { "dungeonId": { "x": 0.0, "y": 64.0, "z": 0.0, "dimension": "minecraft:overworld" } }}.
 */
public final class PlacementRegistry {

    private static final Gson GSON = new Gson();
    private static final String FILE = "placements.json";

    /** Entrée persistée pour un donjon. */
    private static final class SpawnEntry {
        double x, y, z;
        String dimension; // ex: "minecraft:overworld"
    }

    private final Path filePath;
    private final Map<String, SpawnEntry> data = new HashMap<>();

    public PlacementRegistry() {
        this.filePath = FMLPaths.CONFIGDIR.get()
            .resolve("arcadia_dungeon")
            .resolve(FILE);
    }

    public void load() {
        if (!Files.exists(filePath)) return;
        try (Reader r = Files.newBufferedReader(filePath)) {
            Type type = new TypeToken<Map<String, SpawnEntry>>() {}.getType();
            Map<String, SpawnEntry> loaded = GSON.fromJson(r, type);
            if (loaded != null) data.putAll(loaded);
            ArcadiaDungeon.LOGGER.info("[Arcadia][PLACEMENT] {} spawn(s) chargé(s)", data.size());
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][PLACEMENT] Erreur lecture placements.json", e);
        }
    }

    public void setSpawn(String dungeonId, Vec3 pos, String dimension) {
        SpawnEntry entry = new SpawnEntry();
        entry.x = pos.x;
        entry.y = pos.y;
        entry.z = pos.z;
        entry.dimension = dimension;
        data.put(dungeonId, entry);
        persist();
        ArcadiaDungeon.LOGGER.info("[Arcadia][PLACEMENT] spawn enregistré dungeonId={} dim={} pos={}/{}/{}",
            dungeonId, dimension, pos.x, pos.y, pos.z);
    }

    public Optional<Vec3> getSpawn(String dungeonId) {
        SpawnEntry e = data.get(dungeonId);
        if (e == null) return Optional.empty();
        return Optional.of(new Vec3(e.x, e.y, e.z));
    }

    public Optional<String> getDimension(String dungeonId) {
        SpawnEntry e = data.get(dungeonId);
        if (e == null || e.dimension == null) return Optional.empty();
        return Optional.of(e.dimension);
    }

    public boolean isSetup(String dungeonId) {
        return data.containsKey(dungeonId);
    }

    private void persist() {
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][PLACEMENT] Erreur écriture placements.json", e);
        }
    }
}
