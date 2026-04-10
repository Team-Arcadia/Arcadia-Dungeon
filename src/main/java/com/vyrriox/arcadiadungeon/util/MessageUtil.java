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

        com.arcadia.core.message.MessageUtil.send(player, message);
    }

    public static void broadcast(DungeonInstance instance, String message) {
        if (instance == null || message == null) return;

        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (UUID playerId : instance.getPlayers()) {
            send(server.getPlayerList().getPlayer(playerId), message);
        }
    }

}
