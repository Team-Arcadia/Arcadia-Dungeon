package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.config.*;
import com.arcadia.dungeon.dungeon.*;
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
        PlayerProgress progress;
        if (playerName != null) {
            progress = PlayerProgressManager.getInstance().findByName(playerName);
            if (progress == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                return 0;
            }
        } else {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
                return 0;
            }
            progress = PlayerProgressManager.getInstance()
                    .getOrCreate(player.getUUID().toString(), player.getName().getString());
        }

        progress.normalize();
        ArcadiaProgressionConfig progressionConfig = ConfigManager.getInstance().getProgressionConfig();
        PlayerProgress.ArcadiaProgress arcadia = progress.arcadiaProgress;
        long nextXp = progressionConfig.getXpRequiredForNextLevel(arcadia.arcadiaLevel, arcadia.arcadiaXp);
        ChatFormatting rankColor = parseLegacyColor(progressionConfig.getRankColorForLevel(arcadia.arcadiaLevel));
        String displayName = progress.playerName == null || progress.playerName.isBlank() ? progress.uuid : progress.playerName;

        ctx.getSource().sendSuccess(() -> Component.literal("========= Profil Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Joueur: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Niveau: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(arcadia.arcadiaLevel)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Rang: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(arcadia.arcadiaRank).withStyle(rankColor, ChatFormatting.BOLD)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" XP: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(arcadia.arcadiaXp)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nextXp > 0 ? " (" + nextXp + " avant niveau suivant)" : " (niveau max config)").withStyle(ChatFormatting.DARK_GRAY)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Streak hebdo: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(arcadia.weeklyStreak + " semaine(s)").withStyle(ChatFormatting.AQUA)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Completions: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(progress.getTotalCompletions())).withStyle(ChatFormatting.WHITE)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("==================================").withStyle(ChatFormatting.GOLD), false);
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
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();

        ctx.getSource().sendSuccess(() -> Component.literal("======= Arcadia Admin =======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" XP par defaut: " + config.defaultDungeonXp).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Rangs:").withStyle(ChatFormatting.AQUA), false);
        for (ArcadiaProgressionConfig.RankThreshold rank : config.ranks) {
            ChatFormatting rankColor = parseLegacyColor(rank.color);
            ctx.getSource().sendSuccess(() -> Component.literal("  - niv " + rank.minLevel + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(rank.rankName + " (" + rank.color + ")").withStyle(rankColor)), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(" Milestones:").withStyle(ChatFormatting.AQUA), false);
        for (ArcadiaProgressionConfig.MilestoneReward milestone : config.milestoneRewards) {
            int rewardCount = milestone.rewards == null ? 0 : milestone.rewards.size();
            ctx.getSource().sendSuccess(() -> Component.literal("  - niv " + milestone.level + ": " + rewardCount + " reward(s)")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(" Streaks:").withStyle(ChatFormatting.AQUA), false);
        for (ArcadiaProgressionConfig.StreakBonus streak : config.streakBonuses) {
            ctx.getSource().sendSuccess(() -> Component.literal("  - " + streak.weeks + " semaine(s): +" + streak.xpBonus + " XP")
                    .withStyle(ChatFormatting.GRAY), false);
        }
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

    static int showProgression(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        PlayerProgress progress = PlayerProgressManager.getInstance()
                .getOrCreate(player.getUUID().toString(), player.getName().getString());

        List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values());
        dungeons.sort(Comparator.comparingInt(d -> d.order));

        ctx.getSource().sendSuccess(() -> Component.literal("").append(
                Component.literal("========= Donjons Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        ), false);

        for (DungeonConfig dungeon : dungeons) {
            if (!dungeon.enabled) continue;

            boolean completed = progress.hasCompleted(dungeon.id);
            boolean unlocked = isDungeonUnlocked(progress, dungeon);

            PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeon.id);

            // Build progress bar
            String bar;
            ChatFormatting color;
            String status;
            String details;

            if (completed) {
                bar = "##########";
                color = ChatFormatting.GREEN;
                status = "COMPLETE";
                details = dp.completions + "x | Record: " + formatTime(dp.bestTimeSeconds);
            } else if (unlocked) {
                bar = ">>--------";
                color = ChatFormatting.YELLOW;
                status = "DISPONIBLE";
                details = "Cliquez pour entrer!";
            } else {
                bar = "----------";
                color = ChatFormatting.RED;
                status = "VERROUILLE";
                DungeonConfig req = ConfigManager.getInstance().getDungeon(dungeon.requiredDungeon);
                if (dungeon.requiredArcadiaLevel > progress.arcadiaProgress.arcadiaLevel) {
                    details = "Niveau Arcadia requis: " + dungeon.requiredArcadiaLevel;
                } else {
                    details = "Requis: " + (req != null ? req.name : dungeon.requiredDungeon);
                }
            }

            MutableComponent line = Component.literal(" ");
            line.append(Component.literal("[" + bar + "] ").withStyle(color));
            line.append(Component.literal(dungeon.order + ". ").withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(dungeon.name).withStyle(color, ChatFormatting.BOLD));
            line.append(Component.literal(" ").withStyle(ChatFormatting.RESET));
            line.append(Component.literal("[" + status + "]").withStyle(color));

            // Add click event if unlocked
            if (unlocked && !completed) {
                line.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon start " + dungeon.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cliquez pour lancer " + dungeon.name)))
                );
            } else if (completed) {
                line.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/arcadia_dungeon start " + dungeon.id))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Relancer " + dungeon.name + "\n" + details)))
                );
            } else {
                line.withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(details)))
                );
            }

            ctx.getSource().sendSuccess(() -> line, false);

            // Details line
            MutableComponent detailLine = Component.literal("   ");
            detailLine.append(Component.literal(details).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            ctx.getSource().sendSuccess(() -> detailLine, false);
        }

        // Total stats
        int total = progress.getTotalCompletions();
        int completed = (int) dungeons.stream().filter(d -> d.enabled && progress.hasCompleted(d.id)).count();
        int totalEnabled = (int) dungeons.stream().filter(d -> d.enabled).count();

        ctx.getSource().sendSuccess(() -> Component.literal("====================================").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Progression: " + completed + "/" + totalEnabled + " donjons | " + total + " completions totales")
                .withStyle(ChatFormatting.AQUA), false);

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
            // Global top
            List<PlayerProgress> top = PlayerProgressManager.getInstance().getTopPlayers(10);

            ctx.getSource().sendSuccess(() -> Component.literal("=== Top Joueurs Arcadia ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

            if (top.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Aucune completion enregistree.").withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                final int rank = i + 1;
                PlayerProgress pp = top.get(i);
                pp.normalize();

                ChatFormatting rankColor = rank <= 3 ? (rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.GRAY : ChatFormatting.RED) : ChatFormatting.WHITE;
                ChatFormatting arcadiaRankColor = parseLegacyColor(ConfigManager.getInstance().getProgressionConfig()
                        .getRankColorForLevel(pp.arcadiaProgress.arcadiaLevel));

                ctx.getSource().sendSuccess(() -> Component.literal(" " + rank + ". ")
                        .withStyle(rankColor, ChatFormatting.BOLD)
                        .append(Component.literal(pp.playerName).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("Niv. " + pp.arcadiaProgress.arcadiaLevel).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(pp.arcadiaProgress.arcadiaRank).withStyle(arcadiaRankColor))
                        .append(Component.literal(" | " + pp.arcadiaProgress.arcadiaXp + " XP").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" | " + pp.getTotalCompletions() + " completions").withStyle(ChatFormatting.GRAY))
                , false);
            }
        }
        return 1;
    }

    static int showStats(CommandContext<CommandSourceStack> ctx, String playerName) {
        PlayerProgress progress;

        if (playerName != null) {
            progress = PlayerProgressManager.getInstance().findByName(playerName);
            if (progress == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                return 0;
            }
        } else {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
                return 0;
            }
            progress = PlayerProgressManager.getInstance()
                    .getOrCreate(player.getUUID().toString(), player.getName().getString());
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Stats: " + progress.playerName + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Completions totales: " + progress.getTotalCompletions()).withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Donjons completes: " + progress.completedDungeons.size()).withStyle(ChatFormatting.AQUA), false);

        if (!progress.completedDungeons.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Details:").withStyle(ChatFormatting.YELLOW), false);
            for (Map.Entry<String, PlayerProgress.DungeonProgress> entry : progress.completedDungeons.entrySet()) {
                String dId = entry.getKey();
                PlayerProgress.DungeonProgress dp = entry.getValue();
                DungeonConfig dc = ConfigManager.getInstance().getDungeon(dId);
                String dName = dc != null ? dc.name : dId;

                ctx.getSource().sendSuccess(() -> Component.literal("  - " + dName + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(dp.completions + "x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | Record: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(formatTime(dp.bestTimeSeconds)).withStyle(ChatFormatting.GREEN))
                , false);
            }
        }

        return 1;
    }
}

