package com.vyrriox.arcadiadungeon.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the player cooldowns and the dungeon availability timers on disk, in
 * config/arcadia/dungeon/timers.json, so a server restart no longer resets them.
 * Without it a dungeon available every 24 or 36 hours opened again at every daily reboot.
 *
 * @author vyrriox
 */
public final class DungeonTimerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DungeonTimerStore() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("dungeon").resolve("timers.json");
    }

    /** Fills both maps from the file, keeping what they already hold when the file is missing or unreadable. */
    public static void load(Map<String, Long> playerCooldowns, Map<String, Long> dungeonAvailability) {
        Path path = file();
        if (!Files.exists(path)) return;
        try {
            Data data = GSON.fromJson(Files.readString(path), Data.class);
            if (data == null) return;
            if (data.playerCooldowns != null) playerCooldowns.putAll(data.playerCooldowns);
            if (data.dungeonAvailability != null) dungeonAvailability.putAll(data.dungeonAvailability);
            ArcadiaDungeon.LOGGER.info("Loaded dungeon timers: {} player cooldown(s), {} availability timer(s)",
                    playerCooldowns.size(), dungeonAvailability.size());
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to load dungeon timers", e);
        }
    }

    public static void save(Map<String, Long> playerCooldowns, Map<String, Long> dungeonAvailability) {
        Data data = new Data();
        data.playerCooldowns = new HashMap<>(playerCooldowns);
        data.dungeonAvailability = new HashMap<>(dungeonAvailability);
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("timers.json.tmp");
            Files.writeString(tmp, GSON.toJson(data, Data.class));
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to save dungeon timers", e);
        }
    }

    private static final class Data {
        Map<String, Long> playerCooldowns;
        Map<String, Long> dungeonAvailability;
    }
}
