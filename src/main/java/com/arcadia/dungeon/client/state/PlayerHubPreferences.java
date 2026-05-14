package com.arcadia.dungeon.client.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local client preferences for the player hub.
 */
public final class PlayerHubPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("player_hub_client.json");
    private static Preferences prefs = new Preferences();

    private PlayerHubPreferences() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            Preferences loaded = GSON.fromJson(Files.readString(FILE), Preferences.class);
            if (loaded != null) prefs = loaded;
        } catch (IOException ignored) {
            prefs = new Preferences();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(prefs));
        } catch (IOException ignored) {}
    }

    public static String selectedClassId() {
        return prefs.selectedClassId != null ? prefs.selectedClassId : "";
    }

    public static void selectedClassId(String id) {
        prefs.selectedClassId = id != null ? id : "";
        save();
    }

    public static boolean hud() { return prefs.hud; }
    public static boolean feed() { return prefs.feed; }
    public static boolean toasts() { return prefs.toasts; }
    public static boolean spectator() { return prefs.spectator; }

    public static void hud(boolean value) { prefs.hud = value; save(); }
    public static void feed(boolean value) { prefs.feed = value; save(); }
    public static void toasts(boolean value) { prefs.toasts = value; save(); }
    public static void spectator(boolean value) { prefs.spectator = value; save(); }

    private static final class Preferences {
        String selectedClassId = "";
        boolean hud = true;
        boolean feed = true;
        boolean toasts = true;
        boolean spectator = false;
    }
}
