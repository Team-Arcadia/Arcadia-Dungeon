package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.AreaWandStatusPayload;
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

    private static volatile int areaVersion = 0;
    private static volatile boolean areaSelecting = false;
    private static volatile boolean areaSet = false;
    private static volatile boolean areaPos1Set = false;
    private static volatile boolean areaPos2Set = false;
    private static volatile String areaDim = "";
    private static volatile int areaX1, areaY1, areaZ1, areaX2, areaY2, areaZ2;

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
        readAreaFromConfig();
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
        areaVersion++;
        areaSelecting = false;
        areaSet = false;
        areaPos1Set = false;
        areaPos2Set = false;
        areaDim = "";
        areaX1 = areaY1 = areaZ1 = areaX2 = areaY2 = areaZ2 = 0;
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
    public static int areaVersion()     { return areaVersion; }
    public static boolean areaSelecting() { return areaSelecting; }
    public static boolean areaSet()     { return areaSet; }
    public static boolean areaPos1Set() { return areaPos1Set; }
    public static boolean areaPos2Set() { return areaPos2Set; }
    public static String areaDim()      { return areaDim; }
    public static int areaX1()          { return areaX1; }
    public static int areaY1()          { return areaY1; }
    public static int areaZ1()          { return areaZ1; }
    public static int areaX2()          { return areaX2; }
    public static int areaY2()          { return areaY2; }
    public static int areaZ2()          { return areaZ2; }

    public static void updateAreaWand(AreaWandStatusPayload payload) {
        if (!currentDungeonId.isBlank() && !currentDungeonId.equals(payload.dungeonId())) return;

        areaSelecting = payload.selecting();
        areaSet = payload.areaSet();
        areaPos1Set = payload.pos1Set();
        areaPos2Set = payload.pos2Set();
        areaDim = payload.dimension();
        areaX1 = payload.x1();
        areaY1 = payload.y1();
        areaZ1 = payload.z1();
        areaX2 = payload.x2();
        areaY2 = payload.y2();
        areaZ2 = payload.z2();
        areaVersion++;

        if (areaSet) {
            JsonObject pos1 = new JsonObject();
            pos1.addProperty("dimension", areaDim);
            pos1.addProperty("x", areaX1);
            pos1.addProperty("y", areaY1);
            pos1.addProperty("z", areaZ1);
            JsonObject pos2 = new JsonObject();
            pos2.addProperty("dimension", areaDim);
            pos2.addProperty("x", areaX2);
            pos2.addProperty("y", areaY2);
            pos2.addProperty("z", areaZ2);
            currentConfig.add("areaPos1", pos1);
            currentConfig.add("areaPos2", pos2);
        }
    }

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

    private static void readAreaFromConfig() {
        try {
            JsonObject pos1 = currentConfig.getAsJsonObject("areaPos1");
            JsonObject pos2 = currentConfig.getAsJsonObject("areaPos2");
            if (pos1 == null || pos2 == null) {
                areaSet = false;
                areaSelecting = false;
                areaPos1Set = false;
                areaPos2Set = false;
                areaVersion++;
                return;
            }
            areaDim = pos1.has("dimension") ? pos1.get("dimension").getAsString() : "";
            areaX1 = pos1.get("x").getAsInt();
            areaY1 = pos1.get("y").getAsInt();
            areaZ1 = pos1.get("z").getAsInt();
            areaX2 = pos2.get("x").getAsInt();
            areaY2 = pos2.get("y").getAsInt();
            areaZ2 = pos2.get("z").getAsInt();
            areaSet = true;
            areaSelecting = false;
            areaPos1Set = true;
            areaPos2Set = true;
            areaVersion++;
        } catch (Exception e) {
            areaSet = false;
            areaSelecting = false;
            areaPos1Set = false;
            areaPos2Set = false;
            areaVersion++;
        }
    }
}
