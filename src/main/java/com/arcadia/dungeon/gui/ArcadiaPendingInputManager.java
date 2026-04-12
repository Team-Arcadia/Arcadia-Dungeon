package com.arcadia.dungeon.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class ArcadiaPendingInputManager {
    private static final Map<UUID, PendingInput> PENDING_INPUTS = new ConcurrentHashMap<>();

    private ArcadiaPendingInputManager() {}

    public record PendingInput(
            String prompt,
            BiFunction<ServerPlayer, String, Boolean> applyAction,
            Consumer<ServerPlayer> reopenAction,
            boolean reopenOnSuccess
    ) {}

    public static void begin(ServerPlayer player, String prompt,
                             BiFunction<ServerPlayer, String, Boolean> applyAction,
                             Consumer<ServerPlayer> reopenAction) {
        begin(player, prompt, applyAction, reopenAction, true);
    }

    public static void begin(ServerPlayer player, String prompt,
                             BiFunction<ServerPlayer, String, Boolean> applyAction,
                             Consumer<ServerPlayer> reopenAction,
                             boolean reopenOnSuccess) {
        if (player == null || applyAction == null) return;
        PENDING_INPUTS.put(player.getUUID(), new PendingInput(prompt, applyAction, reopenAction, reopenOnSuccess));
        player.closeContainer();
        player.sendSystemMessage(Component.literal("[Arcadia] " + prompt).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("[Arcadia] Tape 'cancel' pour annuler.").withStyle(ChatFormatting.GRAY));
    }

    public static PendingInput get(ServerPlayer player) {
        return player == null ? null : PENDING_INPUTS.get(player.getUUID());
    }

    public static PendingInput clear(ServerPlayer player) {
        return player == null ? null : PENDING_INPUTS.remove(player.getUUID());
    }
}
