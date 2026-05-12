package com.arcadia.dungeon.client.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Cache client — copie de travail Gson de la config d'un donjon en cours d'édition.
 *
 * <p>Peuplé par {@link com.arcadia.dungeon.network.ClientPayloadHandler#handleDungeonEditData}.
 * Chaque sous-écran admin lit et modifie ce {@link JsonObject} mutable.
 * La sauvegarde finale envoie le JSON sérialisé via {@link com.arcadia.dungeon.network.SaveDungeonConfigPayload}.
 *
 * <p>Thread-safety : toutes les mises à jour se font via {@code context.enqueueWork()}
 * — accès garanti depuis le thread render seul.
 */
public final class DungeonEditClient {

    private DungeonEditClient() {}

    private static volatile String currentDungeonId = "";
    private static volatile JsonObject currentConfig = new JsonObject();

    // ── Spawn coords ──────────────────────────────────────────────────────
    private static volatile double spawnX   = 0.0;
    private static volatile double spawnY   = 0.0;
    private static volatile double spawnZ   = 0.0;
    private static volatile String spawnDim = "";
    private static volatile boolean spawnSet = false;

    // ── Update from server ────────────────────────────────────────────────

    public static void update(String dungeonId, String configJson,
                              double x, double y, double z,
                              String dim, boolean set) {
        currentDungeonId = dungeonId;
        try {
            currentConfig = JsonParser.parseString(configJson).getAsJsonObject();
        } catch (Exception e) {
            currentConfig = new JsonObject();
        }
        spawnX   = x;
        spawnY   = y;
        spawnZ   = z;
        spawnDim = dim;
        spawnSet = set;
    }

    /** Met à jour uniquement les coords spawn (après CaptureSpawnPayload round-trip). */
    public static void updateSpawn(double x, double y, double z, String dim) {
        spawnX   = x;
        spawnY   = y;
        spawnZ   = z;
        spawnDim = dim;
        spawnSet = true;
    }

    public static void clear() {
        currentDungeonId = "";
        currentConfig    = new JsonObject();
        spawnX = spawnY = spawnZ = 0.0;
        spawnDim = "";
        spawnSet = false;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public static String dungeonId()   { return currentDungeonId; }
    /** Retourne la copie de travail mutable — modifiable directement par les sous-écrans. */
    public static JsonObject config()  { return currentConfig; }

    public static double spawnX()      { return spawnX; }
    public static double spawnY()      { return spawnY; }
    public static double spawnZ()      { return spawnZ; }
    public static String spawnDim()    { return spawnDim; }
    public static boolean spawnSet()   { return spawnSet; }

    /** Retourne le JSON sérialisé compact pour l'envoi réseau. */
    public static String toJson() {
        return currentConfig.toString();
    }

    // ── Helpers lecture JSON ──────────────────────────────────────────────

    public static String getString(String key, String def) {
        try { return currentConfig.get(key).getAsString(); }
        catch (Exception e) { return def; }
    }

    public static int getInt(String key, int def) {
        try { return currentConfig.get(key).getAsInt(); }
        catch (Exception e) { return def; }
    }

    public static double getDouble(String key, double def) {
        try { return currentConfig.get(key).getAsDouble(); }
        catch (Exception e) { return def; }
    }

    public static boolean getBool(String key, boolean def) {
        try { return currentConfig.get(key).getAsBoolean(); }
        catch (Exception e) { return def; }
    }
}
