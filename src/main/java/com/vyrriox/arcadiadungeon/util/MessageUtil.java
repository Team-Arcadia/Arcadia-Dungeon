package com.vyrriox.arcadiadungeon.util;

import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class MessageUtil {
    private MessageUtil() {}

    public static void send(ServerPlayer player, String message) {
        if (player == null || message == null) return;

        String sanitizedMessage = stripInvalidColorCodes(message);
        player.sendSystemMessage(DungeonManager.parseColorCodes(sanitizedMessage));
    }

    public static void broadcast(DungeonInstance instance, String message) {
        if (instance == null || message == null) return;

        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (UUID playerId : instance.getPlayers()) {
            send(server.getPlayerList().getPlayer(playerId), message);
        }
    }

    private static String stripInvalidColorCodes(String message) {
        StringBuilder sanitized = new StringBuilder(message.length());

        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current != '&') {
                sanitized.append(current);
                continue;
            }

            if (i + 1 >= message.length()) {
                break;
            }

            char code = message.charAt(i + 1);
            if (isValidColorCode(code)) {
                sanitized.append('&').append(code);
            } else {
                sanitized.append(code);
            }
            i++;
        }

        return sanitized.toString();
    }

    private static boolean isValidColorCode(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'a', 'b', 'c', 'd', 'e', 'f',
                    'k', 'l', 'm', 'n', 'o', 'r' -> true;
            default -> false;
        };
    }
}
