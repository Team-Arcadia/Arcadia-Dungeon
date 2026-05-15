package com.arcadia.dungeon.services;

import com.arcadia.dungeon.network.ArcadiaToastPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/** Server-side facade for translated client toast notifications. */
public final class ArcadiaToast {

    private static final int DEFAULT_DURATION_MS = 3_000;

    private ArcadiaToast() {}

    public static void success(ServerPlayer player, String key, Object... args) {
        send(player, "success", key, DEFAULT_DURATION_MS, args);
    }

    public static void error(ServerPlayer player, String key, Object... args) {
        send(player, "error", key, DEFAULT_DURATION_MS, args);
    }

    public static void warn(ServerPlayer player, String key, Object... args) {
        send(player, "warn", key, DEFAULT_DURATION_MS, args);
    }

    public static void info(ServerPlayer player, String key, Object... args) {
        send(player, "info", key, DEFAULT_DURATION_MS, args);
    }

    public static void send(ServerPlayer player, String variant, String key, int durationMs, Object... args) {
        if (player == null || player.connection == null || key == null || key.isBlank()) return;
        List<String> textArgs = Arrays.stream(args != null ? args : new Object[0])
            .limit(8)
            .map(arg -> arg != null ? String.valueOf(arg) : "")
            .toList();
        player.connection.send(new ArcadiaToastPayload(
            normalizeVariant(variant),
            key,
            textArgs,
            Math.max(500, Math.min(10_000, durationMs))));
    }

    private static String normalizeVariant(String variant) {
        return switch (variant != null ? variant : "") {
            case "success", "error", "warn" -> variant;
            default -> "info";
        };
    }
}
