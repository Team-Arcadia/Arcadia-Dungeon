package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.config.*;
import com.arcadia.dungeon.dungeon.*;
import com.arcadia.dungeon.service.admin.AdminGuiActionService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.arcadia.dungeon.command.ArcadiaCommandHelper.*;
import static com.arcadia.dungeon.gui.admin.AdminGuiRouter.*;

final class ArcadiaProgressionCommandActions {

    private ArcadiaProgressionCommandActions() {
    }

    // === PROGRESSION COMMANDS ===

    static int showArcadiaProfile(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer sourcePlayer = ctx.getSource().getPlayer();
        if (sourcePlayer == null && playerName == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        if (playerName != null && PlayerProgressManager.getInstance().findByName(playerName) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        String fallbackUuid = sourcePlayer != null ? sourcePlayer.getUUID().toString() : playerName;
        String fallbackName = sourcePlayer != null ? sourcePlayer.getName().getString() : playerName;
        AdminGuiActionService.showPlayerProfile(component -> ctx.getSource().sendSuccess(() -> component, false), fallbackUuid, fallbackName, playerName);
        return 1;
    }

    static int showArcadiaPlayerAdminMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        String playerName = StringArgumentType.getString(ctx, "player");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        openArcadiaPlayerAdminGui(player, progress.uuid);
        return 1;
    }

    static int setPlayerArcadiaLevel(CommandContext<CommandSourceStack> ctx) {
        PlayerProgress progress = requireKnownPlayer(ctx);
        if (progress == null) return 0;
        int level = IntegerArgumentType.getInteger(ctx, "level");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        long xpForLevel = getXpForLevel(config, level);
        progress.normalize();
        progress.arcadiaProgress.arcadiaLevel = Math.max(1, level);
        progress.arcadiaProgress.arcadiaXp = Math.max(0L, xpForLevel);
        progress.arcadiaProgress.arcadiaRank = config.getRankForLevel(progress.arcadiaProgress.arcadiaLevel);
        PlayerProgressManager.getInstance().saveNow(progress.uuid);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Niveau de " + progress.playerName + " = " + progress.arcadiaProgress.arcadiaLevel)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setPlayerArcadiaXp(CommandContext<CommandSourceStack> ctx) {
        PlayerProgress progress = requireKnownPlayer(ctx);
        if (progress == null) return 0;
        long xp = LongArgumentType.getLong(ctx, "xp");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        progress.normalize();
        progress.arcadiaProgress.arcadiaXp = Math.max(0L, xp);
        progress.arcadiaProgress.arcadiaLevel = config.getLevelForXp(progress.arcadiaProgress.arcadiaXp);
        progress.arcadiaProgress.arcadiaRank = config.getRankForLevel(progress.arcadiaProgress.arcadiaLevel);
        PlayerProgressManager.getInstance().saveNow(progress.uuid);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] XP de " + progress.playerName + " = " + progress.arcadiaProgress.arcadiaXp)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addPlayerArcadiaXp(CommandContext<CommandSourceStack> ctx) {
        PlayerProgress progress = requireKnownPlayer(ctx);
        if (progress == null) return 0;
        long xp = LongArgumentType.getLong(ctx, "xp");
        LevelUpResult result = PlayerProgressManager.getInstance().addXp(progress.uuid, xp);
        PlayerProgress updated = PlayerProgressManager.getInstance().get(progress.uuid);
        PlayerProgressManager.getInstance().saveNow(progress.uuid);
        String message = "[Arcadia] +" + xp + " XP pour " + progress.playerName;
        if (updated != null) {
            message += " | niv " + updated.arcadiaProgress.arcadiaLevel + " | " + updated.arcadiaProgress.arcadiaXp + " XP";
        } else if (result != null) {
            message += " | niv " + result.newLevel;
        }
        String finalMessage = message;
        ctx.getSource().sendSuccess(() -> Component.literal(finalMessage).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setPlayerArcadiaStreak(CommandContext<CommandSourceStack> ctx) {
        PlayerProgress progress = requireKnownPlayer(ctx);
        if (progress == null) return 0;
        int weeks = IntegerArgumentType.getInteger(ctx, "weeks");
        progress.normalize();
        progress.arcadiaProgress.weeklyStreak = Math.max(0, weeks);
        PlayerProgressManager.getInstance().saveNow(progress.uuid);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Streak de " + progress.playerName + " = " + progress.arcadiaProgress.weeklyStreak + " semaine(s)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDungeonArcadiaXp(CommandContext<CommandSourceStack> ctx) {
        DungeonConfig config = requireDungeon(ctx);
        if (config == null) return 0;
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        config.arcadiaXp = xp;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] XP Arcadia de " + config.name + " = " + xp)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDungeonDifficultyMultiplier(CommandContext<CommandSourceStack> ctx) {
        DungeonConfig config = requireDungeon(ctx);
        if (config == null) return 0;
        double value = DoubleArgumentType.getDouble(ctx, "value");
        config.difficultyMultiplier = value;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Multiplicateur XP de " + config.name + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDungeonRequiredArcadiaLevel(CommandContext<CommandSourceStack> ctx) {
        DungeonConfig config = requireDungeon(ctx);
        if (config == null) return 0;
        int level = IntegerArgumentType.getInteger(ctx, "level");
        config.requiredArcadiaLevel = level;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Niveau Arcadia requis pour " + config.name + " = " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDungeonSpeedrunBonus(CommandContext<CommandSourceStack> ctx) {
        DungeonConfig config = requireDungeon(ctx);
        if (config == null) return 0;
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        config.speedrunBonusSeconds = seconds;
        config.speedrunBonusXp = xp;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Bonus speedrun de " + config.name + " = +" + xp + " XP sous " + seconds + "s")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDefaultArcadiaXp(CommandContext<CommandSourceStack> ctx) {
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.defaultDungeonXp = xp;
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] XP Arcadia par defaut = " + xp)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addOrUpdateRank(CommandContext<CommandSourceStack> ctx) {
        int minLevel = IntegerArgumentType.getInteger(ctx, "minLevel");
        String name = StringArgumentType.getString(ctx, "name");
        String color = normalizeLegacyColor(StringArgumentType.getString(ctx, "color"));

        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        ArcadiaProgressionConfig.RankThreshold existing = config.ranks.stream()
                .filter(rank -> rank.minLevel == minLevel)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            config.ranks.add(new ArcadiaProgressionConfig.RankThreshold(minLevel, name, color));
        } else {
            existing.rankName = name;
            existing.color = color;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Rang sauvegarde: niveau " + minLevel + " -> " + name + " (" + color + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeRank(CommandContext<CommandSourceStack> ctx) {
        int minLevel = IntegerArgumentType.getInteger(ctx, "minLevel");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        boolean removed = config.ranks.removeIf(rank -> rank != null && rank.minLevel == minLevel);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun rang pour le niveau " + minLevel));
            return 0;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Rang supprime au niveau " + minLevel)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addOrUpdateMilestone(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        String message = StringArgumentType.getString(ctx, "message");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        ArcadiaProgressionConfig.MilestoneReward existing = config.milestoneRewards.stream()
                .filter(milestone -> milestone.level == level)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            config.milestoneRewards.add(new ArcadiaProgressionConfig.MilestoneReward(level, message));
        } else {
            existing.message = message;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Milestone sauvegarde pour le niveau " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeMilestone(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        boolean removed = config.milestoneRewards.removeIf(milestone -> milestone != null && milestone.level == level);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun milestone pour le niveau " + level));
            return 0;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Milestone supprime pour le niveau " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setStreakBonus(CommandContext<CommandSourceStack> ctx) {
        int weeks = IntegerArgumentType.getInteger(ctx, "weeks");
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        String message = StringArgumentType.getString(ctx, "message");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        ArcadiaProgressionConfig.StreakBonus existing = config.streakBonuses.stream()
                .filter(streak -> streak.weeks == weeks)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            config.streakBonuses.add(new ArcadiaProgressionConfig.StreakBonus(weeks, xp, message));
        } else {
            existing.xpBonus = xp;
            existing.message = message;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Bonus streak " + weeks + " semaine(s) = +" + xp + " XP")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addMilestoneItemReward(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");
        ArcadiaProgressionConfig.MilestoneReward milestone = requireMilestone(ctx, level);
        if (milestone == null) return 0;

        RewardConfig reward = new RewardConfig(item, count, chance);
        milestone.rewards.add(reward);
        ConfigManager.getInstance().getProgressionConfig().normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Reward item ajoute au milestone niveau " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addMilestoneExperienceReward(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        int xp = IntegerArgumentType.getInteger(ctx, "xp");
        ArcadiaProgressionConfig.MilestoneReward milestone = requireMilestone(ctx, level);
        if (milestone == null) return 0;

        RewardConfig reward = new RewardConfig();
        reward.item = "";
        reward.count = 1;
        reward.chance = 1.0;
        reward.experience = xp;
        milestone.rewards.add(reward);
        ConfigManager.getInstance().getProgressionConfig().normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Reward XP ajoute au milestone niveau " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addMilestoneCommandReward(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        String command = StringArgumentType.getString(ctx, "command");
        ArcadiaProgressionConfig.MilestoneReward milestone = requireMilestone(ctx, level);
        if (milestone == null) return 0;

        RewardConfig reward = new RewardConfig();
        reward.item = "";
        reward.count = 1;
        reward.chance = 1.0;
        reward.command = command;
        milestone.rewards.add(reward);
        ConfigManager.getInstance().getProgressionConfig().normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Reward commande ajoute au milestone niveau " + level)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int clearMilestoneRewards(CommandContext<CommandSourceStack> ctx) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        ArcadiaProgressionConfig.MilestoneReward milestone = requireMilestone(ctx, level);
        if (milestone == null) return 0;

        milestone.rewards.clear();
        ConfigManager.getInstance().getProgressionConfig().normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Rewards du milestone niveau " + level + " supprimes")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeStreakBonus(CommandContext<CommandSourceStack> ctx) {
        int weeks = IntegerArgumentType.getInteger(ctx, "weeks");
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        boolean removed = config.streakBonuses.removeIf(streak -> streak != null && streak.weeks == weeks);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun bonus streak pour " + weeks + " semaine(s)"));
            return 0;
        }
        config.normalize();
        ConfigManager.getInstance().saveProgressionConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Bonus streak supprime pour " + weeks + " semaine(s)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int showArcadiaAdminOverview(CommandContext<CommandSourceStack> ctx) {
        AdminGuiActionService.showArcadiaAdminOverview(component -> ctx.getSource().sendSuccess(() -> component, false));
        return 1;
    }

    static int showArcadiaAdminMenu(CommandContext<CommandSourceStack> ctx, String dungeonId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        if (dungeonId != null) {
            DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
            if (dungeon == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
                return 0;
            }
            openDungeonArcadiaAdminGui(player, dungeon.id);
            return 1;
        }

        openArcadiaAdminGui(player);
        return 1;
    }

    static int showAdminGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        openAdminHubGui(player, 0);
        return 1;
    }

    static int showDungeonAdminGui(CommandContext<CommandSourceStack> ctx, String dungeonId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        openDungeonAdminHubGui(player, dungeonId);
        return 1;
    }

    static int showPlayerMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        openPlayerArcadiaGui(player);
        return 1;
    }

    private static PlayerProgress requireKnownPlayer(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return null;
        }
        progress.normalize();
        return progress;
    }

    private static long getXpForLevel(ArcadiaProgressionConfig config, int level) {
        long resolved = 0L;
        for (ArcadiaProgressionConfig.LevelThreshold threshold : config.levels) {
            if (threshold == null) continue;
            if (threshold.level > level) break;
            resolved = Math.max(resolved, threshold.xpRequired);
        }
        return resolved;
    }

    static int showProgression(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        AdminGuiActionService.showPlayerProgression(component -> ctx.getSource().sendSuccess(() -> component, false), player.getUUID().toString(), player.getName().getString());
        return 1;
    }

    static int showTop(CommandContext<CommandSourceStack> ctx, String dungeonId) {
        if (dungeonId != null) {
            // Top for specific dungeon
            DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
            if (config == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
                return 0;
            }

            List<Map.Entry<String, Long>> top = WeeklyLeaderboard.getInstance().getTopForDungeon(dungeonId, 10);

            ctx.getSource().sendSuccess(() -> Component.literal("=== Top hebdo " + config.name + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

            if (top.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Aucun temps hebdo enregistre.").withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                final int rank = i + 1;
                Map.Entry<String, Long> entry = top.get(i);
                String playerName = WeeklyLeaderboard.getInstance().getPlayerName(entry.getKey());
                long bestTime = entry.getValue();

                ChatFormatting rankColor = rank <= 3 ? (rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.GRAY : ChatFormatting.RED) : ChatFormatting.WHITE;

                ctx.getSource().sendSuccess(() -> Component.literal(" " + rank + ". ")
                        .withStyle(rankColor, ChatFormatting.BOLD)
                        .append(Component.literal(playerName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(formatTime(bestTime)).withStyle(ChatFormatting.YELLOW))
                , false);
            }
        } else {
            AdminGuiActionService.showPlayerTop(component -> ctx.getSource().sendSuccess(() -> component, false));
        }
        return 1;
    }

    static int showStats(CommandContext<CommandSourceStack> ctx, String playerName) {
        ServerPlayer sourcePlayer = ctx.getSource().getPlayer();
        if (sourcePlayer == null && playerName == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        if (playerName != null && PlayerProgressManager.getInstance().findByName(playerName) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        String fallbackUuid = sourcePlayer != null ? sourcePlayer.getUUID().toString() : playerName;
        String fallbackName = sourcePlayer != null ? sourcePlayer.getName().getString() : playerName;
        AdminGuiActionService.showPlayerStats(component -> ctx.getSource().sendSuccess(() -> component, false), fallbackUuid, fallbackName, playerName);
        return 1;
    }
}

