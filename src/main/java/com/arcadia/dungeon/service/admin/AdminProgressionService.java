package com.arcadia.dungeon.service.admin;

import com.arcadia.dungeon.config.ArcadiaProgressionConfig;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.RewardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AdminProgressionService {
    private AdminProgressionService() {}

    public static boolean applyRankInput(ServerPlayer player, String input) {
        String[] parts = input.trim().split("\\s+", 3);
        if (parts.length < 3) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <niveau> <nom> <couleur>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            int minLevel = Integer.parseInt(parts[0]);
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            config.ranks.removeIf(rank -> rank != null && rank.minLevel == minLevel);
            config.ranks.add(new ArcadiaProgressionConfig.RankThreshold(minLevel, parts[1], parts[2]));
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Rang ajoute ou mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Niveau invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyRankRemoveInput(ServerPlayer player, String input) {
        try {
            int minLevel = Integer.parseInt(input.trim());
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            boolean removed = config.ranks.removeIf(rank -> rank != null && rank.minLevel == minLevel);
            if (!removed) {
                player.sendSystemMessage(Component.literal("[Arcadia] Rang introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Rang supprime.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Niveau invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyMilestoneInput(ServerPlayer player, String input) {
        String[] parts = input.trim().split("\\s+", 2);
        if (parts.length < 2) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <niveau> <message>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            int level = Integer.parseInt(parts[0]);
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            ArcadiaProgressionConfig.MilestoneReward existing = findMilestone(level);
            if (existing == null) {
                existing = new ArcadiaProgressionConfig.MilestoneReward(level, parts[1]);
                config.milestoneRewards.add(existing);
            } else {
                existing.message = parts[1];
            }
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Milestone ajoute ou mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Niveau invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyMilestoneRemoveInput(ServerPlayer player, String input) {
        try {
            int level = Integer.parseInt(input.trim());
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            boolean removed = config.milestoneRewards.removeIf(entry -> entry != null && entry.level == level);
            if (!removed) {
                player.sendSystemMessage(Component.literal("[Arcadia] Milestone introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Milestone supprime.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Niveau invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyMilestoneItemRewardInput(ServerPlayer player, int level, String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 3) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <item> <count> <chance>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            RewardConfig reward = new RewardConfig(parts[0], Integer.parseInt(parts[1]), Double.parseDouble(parts[2]));
            reward.normalize();
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            ArcadiaProgressionConfig.MilestoneReward milestone = findMilestone(level);
            if (milestone == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Milestone introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            milestone.rewards.add(reward);
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Reward item ajoute au milestone.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyMilestoneXpRewardInput(ServerPlayer player, int level, String input) {
        try {
            int xp = Integer.parseInt(input.trim());
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            ArcadiaProgressionConfig.MilestoneReward milestone = findMilestone(level);
            if (milestone == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Milestone introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            RewardConfig reward = new RewardConfig();
            reward.item = "";
            reward.command = "";
            reward.experience = Math.max(0, xp);
            reward.chance = 1.0;
            milestone.rewards.add(reward);
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Reward XP ajoute au milestone.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] XP invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyMilestoneCommandRewardInput(ServerPlayer player, int level, String input) {
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        ArcadiaProgressionConfig.MilestoneReward milestone = findMilestone(level);
        if (milestone == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Milestone introuvable.").withStyle(ChatFormatting.RED));
            return false;
        }
        RewardConfig reward = new RewardConfig();
        reward.item = "";
        reward.command = input.trim();
        reward.experience = 0;
        reward.chance = 1.0;
        milestone.rewards.add(reward);
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        player.sendSystemMessage(Component.literal("[Arcadia] Reward commande ajoute au milestone.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean applyStreakInput(ServerPlayer player, String input) {
        String[] parts = input.trim().split("\\s+", 3);
        if (parts.length < 3) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <semaines> <xp> <message>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            int weeks = Integer.parseInt(parts[0]);
            int xpBonus = Integer.parseInt(parts[1]);
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            ArcadiaProgressionConfig.StreakBonus existing = findStreakBonus(weeks);
            if (existing == null) {
                existing = new ArcadiaProgressionConfig.StreakBonus(weeks, xpBonus, parts[2]);
                config.streakBonuses.add(existing);
            } else {
                existing.xpBonus = xpBonus;
                existing.message = parts[2];
            }
            config.normalize();
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Streak bonus ajoute ou mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean applyStreakRemoveInput(ServerPlayer player, String input) {
        try {
            int weeks = Integer.parseInt(input.trim());
            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            boolean removed = config.streakBonuses.removeIf(entry -> entry != null && entry.weeks == weeks);
            if (!removed) {
                player.sendSystemMessage(Component.literal("[Arcadia] Streak introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            ConfigManager.getInstance().saveProgressionConfig();
            player.sendSystemMessage(Component.literal("[Arcadia] Streak bonus supprime.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static ArcadiaProgressionConfig.MilestoneReward findMilestone(int level) {
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        return config.milestoneRewards.stream().filter(entry -> entry != null && entry.level == level).findFirst().orElse(null);
    }

    public static ArcadiaProgressionConfig.StreakBonus findStreakBonus(int weeks) {
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        return config.streakBonuses.stream().filter(entry -> entry != null && entry.weeks == weeks).findFirst().orElse(null);
    }
}
