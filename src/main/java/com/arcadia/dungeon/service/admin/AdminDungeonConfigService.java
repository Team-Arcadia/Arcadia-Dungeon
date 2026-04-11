package com.arcadia.dungeon.service.admin;

import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.RewardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public final class AdminDungeonConfigService {
    private AdminDungeonConfigService() {}

    public static boolean applyDungeonIntValue(ServerPlayer player, String dungeonId, String input,
                                        BiConsumer<DungeonConfig, Integer> setter,
                                        String successPrefix) {
        try {
            int value = Integer.parseInt(input.trim());
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            if (cfg == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(cfg, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            player.sendSystemMessage(Component.literal(successPrefix + value).withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyDungeonDoubleValue(ServerPlayer player, String dungeonId, String input,
                                           BiConsumer<DungeonConfig, Double> setter,
                                           String successPrefix) {
        try {
            double value = Double.parseDouble(input.trim());
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            if (cfg == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(cfg, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            player.sendSystemMessage(Component.literal(successPrefix + value).withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static String buildRewardSummary(RewardConfig reward) {
        if (reward == null) return "Reward vide";
        if (reward.item != null && !reward.item.isBlank()) {
            return reward.item + " x" + reward.count + " (" + reward.chance + ")";
        }
        if (reward.experience > 0) {
            return reward.experience + " XP";
        }
        if (reward.command != null && !reward.command.isBlank()) {
            return "cmd: " + reward.command;
        }
        return "Reward";
    }
}
