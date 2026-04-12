package com.arcadia.dungeon.gui.admin.progression;

import com.arcadia.dungeon.service.admin.AdminDungeonConfigService;
import com.arcadia.dungeon.service.admin.AdminGuiActionService;
import com.arcadia.dungeon.service.admin.AdminProgressionService;
import com.arcadia.dungeon.gui.admin.AdminGuiRouter;
import com.arcadia.dungeon.config.ArcadiaProgressionConfig;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.RewardConfig;
import com.arcadia.dungeon.dungeon.PlayerProgress;
import com.arcadia.dungeon.dungeon.PlayerProgressManager;
import com.arcadia.dungeon.gui.ArcadiaChestMenu;
import com.arcadia.dungeon.gui.admin.AdminGuiSupport;
import com.arcadia.dungeon.gui.admin.dungeon.AdminDungeonGuiMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdminProgressionGuiMenus {
    private AdminProgressionGuiMenus() {}

    public static void openArcadiaAdminGui(ServerPlayer player) {
        openArcadiaAdminGui(player, 0);
    }

    public static void openArcadiaAdminGui(ServerPlayer player, int page) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(10, guiItem(Items.BOOK, ChatFormatting.GOLD + "Overview", "Voir le resume Arcadia"), sp -> {
                sp.closeContainer();
                AdminGuiActionService.showArcadiaAdminOverview(sp);
            });
            menu.setButton(12, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.GREEN + "XP Global", "Configurer l'XP globale"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre la nouvelle XP Arcadia globale par defaut", (playerInput, xp) -> {
                        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
                        config.defaultDungeonXp = xp;
                        ConfigManager.getInstance().saveProgressionConfig();
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] XP globale mise a jour: " + xp).withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaAdminGui(reopen, page)));
            menu.setButton(14, guiItem(Items.NAME_TAG, ChatFormatting.AQUA + "Rangs", "Ouvrir le menu des rangs"), sp -> openArcadiaRanksGui(sp, 0));
            menu.setButton(16, guiItem(Items.DIAMOND, ChatFormatting.LIGHT_PURPLE + "Milestones", "Ouvrir le menu des milestones"), sp -> openArcadiaMilestonesGui(sp, 0));
            menu.setButton(20, guiItem(Items.CLOCK, ChatFormatting.YELLOW + "Weekly", "Configurer le leaderboard hebdo"), AdminDungeonGuiMenus::openWeeklyAdminGui);
            menu.setButton(22, guiItem(Items.CLOCK, ChatFormatting.GOLD + "Streaks", "Ouvrir le menu des streaks"), sp -> openArcadiaStreaksGui(sp, 0));
            menu.setButton(24, guiItem(Items.PLAYER_HEAD, ChatFormatting.AQUA + "Joueurs", "Admin profiler/debug joueurs Arcadia"), sp -> openArcadiaPlayersGui(sp, 0));

            List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values().stream()
                    .filter(d -> d.enabled)
                    .sorted(Comparator.comparingInt(d -> d.order))
                    .toList());
            int pageSize = 9;
            int totalPages = Math.max(1, (dungeons.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeons.size(), start + pageSize);
            int slot = 27;
            for (DungeonConfig dungeon : dungeons.subList(start, end)) {
                if (slot >= 36) break;
                menu.setButton(slot++, guiItem(Items.CHEST, ChatFormatting.YELLOW + dungeon.name, "Ouvrir le panneau Arcadia du donjon"), sp -> openDungeonArcadiaAdminGui(sp, dungeon.id));
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openArcadiaAdminGui(sp, nextPage));
            return menu;
        }, Component.literal("Admin Arcadia")));
    }

    public static void openArcadiaPlayersGui(ServerPlayer player, int page) {
        List<PlayerProgress> players = new ArrayList<>(PlayerProgressManager.getInstance().getAll());
        players.forEach(PlayerProgress::normalize);
        players.sort(Comparator
                .comparingInt((PlayerProgress progress) -> progress.arcadiaProgress.arcadiaLevel).reversed()
                .thenComparing(progress -> safeText(progress.playerName, progress.uuid), String.CASE_INSENSITIVE_ORDER));

        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir au menu admin Arcadia"), AdminProgressionGuiMenus::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.COMPASS, ChatFormatting.AQUA + "Ouvrir Joueur", "Entrer un pseudo dans le chat"), sp ->
                    AdminGuiSupport.beginPromptNoReopen(sp, "Entre le pseudo du joueur Arcadia a ouvrir", (playerInput, input) -> {
                        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(input.trim());
                        if (progress == null) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Joueur introuvable.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        openArcadiaPlayerAdminGui(playerInput, progress.uuid, page);
                        return true;
                    }, reopen -> openArcadiaPlayersGui(reopen, page)));

            int pageSize = 18;
            int totalPages = Math.max(1, (players.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(players.size(), start + pageSize);
            int slot = 18;
            for (PlayerProgress progress : players.subList(start, end)) {
                String name = safeText(progress.playerName, progress.uuid);
                menu.setButton(slot++, guiItem(
                        Items.PLAYER_HEAD,
                        ChatFormatting.GOLD + name,
                        "Niveau: " + progress.arcadiaProgress.arcadiaLevel,
                        "XP: " + progress.arcadiaProgress.arcadiaXp,
                        "Streak: " + progress.arcadiaProgress.weeklyStreak,
                        progress.playerName == null || progress.playerName.isBlank() ? "Profil legacy via UUID" : "Pseudo connu",
                        "Cliquer pour ouvrir"), sp -> openArcadiaPlayerAdminGui(sp, progress.uuid, safePage));
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openArcadiaPlayersGui(sp, nextPage));
            return menu;
        }, Component.literal("Arcadia Joueurs")));
    }

    public static void openArcadiaPlayerAdminGui(ServerPlayer player, String playerRef, int returnPage) {
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerRef);
        if (progress == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Joueur introuvable: " + playerRef).withStyle(ChatFormatting.RED));
            return;
        }
        progress.normalize();
        String playerLabel = safeText(progress.playerName, progress.uuid);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir a la liste des joueurs"), sp -> openArcadiaPlayersGui(sp, returnPage));
            menu.setButton(10, guiItem(Items.PLAYER_HEAD, ChatFormatting.GOLD + playerLabel, "UUID: " + progress.uuid), null);
            menu.setButton(11, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.GREEN + "Niveau", String.valueOf(progress.arcadiaProgress.arcadiaLevel), "Definit le niveau du joueur"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre le niveau Arcadia a definir pour " + playerLabel, (playerInput, value) -> {
                        PlayerProgress current = PlayerProgressManager.getInstance().findByName(playerRef);
                        if (current == null) return false;
                        ArcadiaProgressionConfig cfg = ConfigManager.getInstance().getProgressionConfig();
                        cfg.normalize();
                        long targetXp = 0L;
                        for (ArcadiaProgressionConfig.LevelThreshold threshold : cfg.levels) {
                            if (threshold == null) continue;
                            if (threshold.level > value) break;
                            targetXp = Math.max(targetXp, threshold.xpRequired);
                        }
                        current.arcadiaProgress.arcadiaLevel = Math.max(1, value);
                        current.arcadiaProgress.arcadiaXp = Math.max(0L, targetXp);
                        current.arcadiaProgress.arcadiaRank = cfg.getRankForLevel(current.arcadiaProgress.arcadiaLevel);
                        PlayerProgressManager.getInstance().saveNow(current.uuid);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Niveau joueur mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(12, guiItem(Items.GOLD_NUGGET, ChatFormatting.YELLOW + "XP", String.valueOf(progress.arcadiaProgress.arcadiaXp), "Definit l'XP du joueur"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre l'XP Arcadia a definir pour " + playerLabel, (playerInput, input) -> {
                        try {
                            long xp = Long.parseLong(input.trim());
                            PlayerProgress current = PlayerProgressManager.getInstance().findByName(playerRef);
                            if (current == null) return false;
                            ArcadiaProgressionConfig cfg = ConfigManager.getInstance().getProgressionConfig();
                            cfg.normalize();
                            current.arcadiaProgress.arcadiaXp = Math.max(0L, xp);
                            current.arcadiaProgress.arcadiaLevel = cfg.getLevelForXp(current.arcadiaProgress.arcadiaXp);
                            current.arcadiaProgress.arcadiaRank = cfg.getRankForLevel(current.arcadiaProgress.arcadiaLevel);
                            PlayerProgressManager.getInstance().saveNow(current.uuid);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] XP joueur mise a jour.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] XP invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(13, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter XP", "Ajouter de l'XP Arcadia"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre l'XP Arcadia a ajouter pour " + playerLabel, (playerInput, input) -> {
                        try {
                            long xp = Long.parseLong(input.trim());
                            PlayerProgress current = PlayerProgressManager.getInstance().findByName(playerRef);
                            if (current == null) return false;
                            PlayerProgressManager.getInstance().addXp(current.uuid, xp);
                            PlayerProgressManager.getInstance().saveNow(current.uuid);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] XP joueur ajoutee.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] XP invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(14, guiItem(Items.CLOCK, ChatFormatting.GOLD + "Streak", String.valueOf(progress.arcadiaProgress.weeklyStreak), "Definit le streak hebdo"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre le streak hebdo a definir pour " + playerLabel, (playerInput, value) -> {
                        PlayerProgress current = PlayerProgressManager.getInstance().findByName(playerRef);
                        if (current == null) return false;
                        current.arcadiaProgress.weeklyStreak = Math.max(0, value);
                        PlayerProgressManager.getInstance().saveNow(current.uuid);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Streak joueur mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(15, guiItem(Items.PAPER, ChatFormatting.AQUA + "Profil Chat", "Afficher le profil complet dans le chat"), sp -> {
                sp.closeContainer();
                AdminGuiActionService.showPlayerProfile(sp, progress.uuid);
                AdminGuiActionService.showPlayerStats(sp, progress.uuid);
            });
            menu.setButton(16, guiItem(Items.BARRIER, ChatFormatting.RED + "Reset Milestones", "Vide les milestones deja reclames"), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Reset Milestones", playerLabel, confirm -> {
                        PlayerProgress current = PlayerProgressManager.getInstance().findByName(playerRef);
                        if (current != null) {
                            current.arcadiaProgress.claimedMilestoneLevels.clear();
                            PlayerProgressManager.getInstance().saveNow(current.uuid);
                        }
                        openArcadiaPlayerAdminGui(confirm, playerRef, returnPage);
                    }, cancel -> openArcadiaPlayerAdminGui(cancel, playerRef, returnPage)));
            menu.setButton(22, guiItem(Items.TNT, ChatFormatting.RED + "Reset Progress", "Reset toute la progression donjon"), sp -> {
                AdminGuiSupport.openConfirmationGui(sp, "Confirmer Reset Progress", "Reset complet pour " + playerLabel, confirm -> {
                    confirm.closeContainer();
                    AdminGuiActionService.resetPlayerProgress(confirm, progress.uuid);
                }, cancel -> openArcadiaPlayerAdminGui(cancel, playerRef, returnPage));
            });
            menu.setButton(23, guiItem(Items.CHEST, ChatFormatting.GOLD + "Set Progress Donjon", "Format chat: <dungeon> <completions> <bestTime>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<dungeon> <completions> <bestTime>'", (playerInput, input) -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 3) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <dungeon> <completions> <bestTime>").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        try {
                            int completions = Integer.parseInt(parts[1]);
                            int bestTime = Integer.parseInt(parts[2]);
                            playerInput.closeContainer();
                            return AdminGuiActionService.setPlayerDungeonProgress(playerInput, progress.uuid, parts[0], completions, bestTime);
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(24, guiItem(Items.BARRIER, ChatFormatting.YELLOW + "Reset Donjon", "Format chat: <dungeon>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre l'id du donjon a reset pour ce joueur", (playerInput, input) -> {
                        String dungeonId = input.trim();
                        if (dungeonId.isBlank()) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Donjon invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        playerInput.closeContainer();
                        return AdminGuiActionService.resetPlayerDungeonProgress(playerInput, progress.uuid, dungeonId);
                    }, reopen -> openArcadiaPlayerAdminGui(reopen, playerRef, returnPage)));
            menu.setButton(19, guiItem(Items.NAME_TAG, ChatFormatting.WHITE + "Rang", safeText(progress.arcadiaProgress.arcadiaRank, "Novice")), null);
            menu.setButton(20, guiItem(Items.CHEST, ChatFormatting.WHITE + "Completions", String.valueOf(progress.getTotalCompletions())), null);
            menu.setButton(21, guiItem(Items.NETHER_STAR, ChatFormatting.WHITE + "Milestones Reclames", String.valueOf(progress.arcadiaProgress.claimedMilestoneLevels.size())), null);
            return menu;
        }, Component.literal("Joueur Arcadia: " + playerLabel)));
    }

    public static void openDungeonArcadiaAdminGui(ServerPlayer player, String dungeonId) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir au menu admin"), AdminProgressionGuiMenus::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.GREEN + "XP Donjon", "Configurer l'XP du donjon"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre la nouvelle XP Arcadia pour " + dungeon.name,
                            (playerInput, input) -> AdminDungeonConfigService.applyDungeonIntValue(playerInput, dungeon.id, input, (cfg, value) -> cfg.arcadiaXp = value, "[Arcadia] XP du donjon mise a jour: "),
                            reopen -> openDungeonArcadiaAdminGui(reopen, dungeon.id)));
            menu.setButton(12, guiItem(Items.ANVIL, ChatFormatting.AQUA + "Multiplicateur", "Configurer le multiplicateur"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le multiplicateur XP pour " + dungeon.name + " (ex: 1.5)",
                            (playerInput, input) -> AdminDungeonConfigService.applyDungeonDoubleValue(playerInput, dungeon.id, input, (cfg, value) -> cfg.difficultyMultiplier = value, "[Arcadia] Multiplicateur mis a jour: "),
                            reopen -> openDungeonArcadiaAdminGui(reopen, dungeon.id)));
            menu.setButton(14, guiItem(Items.IRON_SWORD, ChatFormatting.RED + "Niveau Requis", "Configurer le niveau Arcadia requis"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le niveau Arcadia requis pour " + dungeon.name,
                            (playerInput, input) -> AdminDungeonConfigService.applyDungeonIntValue(playerInput, dungeon.id, input, (cfg, value) -> cfg.requiredArcadiaLevel = value, "[Arcadia] Niveau requis mis a jour: "),
                            reopen -> openDungeonArcadiaAdminGui(reopen, dungeon.id)));
            menu.setButton(16, guiItem(Items.CLOCK, ChatFormatting.GOLD + "Bonus Speedrun", "Configurer le bonus speedrun"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<secondes> <xp>' pour le bonus speedrun de " + dungeon.name, (playerInput, input) -> {
                        try {
                            String[] parts = input.trim().split("\\s+");
                            if (parts.length != 2) {
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <secondes> <xp>").withStyle(ChatFormatting.RED));
                                return false;
                            }
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            if (cfg == null) return false;
                            cfg.speedrunBonusSeconds = Integer.parseInt(parts[0]);
                            cfg.speedrunBonusXp = Integer.parseInt(parts[1]);
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Bonus speedrun mis a jour.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openDungeonArcadiaAdminGui(reopen, dungeon.id)));
            menu.setButton(22, guiItem(Items.COMPASS, ChatFormatting.YELLOW + "Reouvrir", "Rafraichir le panneau"), sp -> openDungeonArcadiaAdminGui(sp, dungeon.id));
            return menu;
        }, Component.literal("Arcadia: " + dungeon.name)));
    }

    public static void openArcadiaRanksGui(ServerPlayer player, int page) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir au menu admin"), AdminProgressionGuiMenus::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <niveau> <nom> <couleur>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<niveau> <nom> <couleur>' pour ajouter ou modifier un rang", AdminProgressionService::applyRankInput, reopen -> openArcadiaRanksGui(reopen, page)));
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Supprimer", "Format: <niveau>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le niveau du rang a supprimer", AdminProgressionService::applyRankRemoveInput, reopen -> openArcadiaRanksGui(reopen, page)));

            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            int pageSize = 9;
            int totalPages = Math.max(1, (config.ranks.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(config.ranks.size(), start + pageSize);
            int slot = 18;
            for (ArcadiaProgressionConfig.RankThreshold rank : config.ranks.subList(start, end)) {
                menu.setButton(slot++, guiItem(Items.NAME_TAG, ChatFormatting.AQUA + rank.rankName, "Niveau min: " + rank.minLevel, "Couleur: " + rank.color), null);
            }
            AdminGuiSupport.addPagination(menu, 19, 22, 25, safePage, totalPages, (sp, nextPage) -> openArcadiaRanksGui(sp, nextPage));
            return menu;
        }, Component.literal("Arcadia Rangs")));
    }

    public static void openArcadiaMilestonesGui(ServerPlayer player, int page) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir au menu admin"), AdminProgressionGuiMenus::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <niveau> <message>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<niveau> <message>' pour ajouter ou modifier un milestone", AdminProgressionService::applyMilestoneInput, reopen -> openArcadiaMilestonesGui(reopen, page)));
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Supprimer", "Format: <niveau>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le niveau du milestone a supprimer", AdminProgressionService::applyMilestoneRemoveInput, reopen -> openArcadiaMilestonesGui(reopen, page)));

            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            int pageSize = 18;
            int totalPages = Math.max(1, (config.milestoneRewards.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(config.milestoneRewards.size(), start + pageSize);
            int slot = 18;
            for (ArcadiaProgressionConfig.MilestoneReward milestone : config.milestoneRewards.subList(start, end)) {
                int level = milestone.level;
                menu.setButton(slot++, guiItem(Items.DIAMOND, ChatFormatting.LIGHT_PURPLE + "Niveau " + milestone.level, trimLore(milestone.message), "Rewards: " + milestone.rewards.size(), "Cliquer pour ouvrir"), sp -> openArcadiaMilestoneDetailGui(sp, level, safePage));
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openArcadiaMilestonesGui(sp, nextPage));
            return menu;
        }, Component.literal("Arcadia Milestones")));
    }

    public static void openArcadiaMilestoneDetailGui(ServerPlayer player, int level, int page) {
        ArcadiaProgressionConfig.MilestoneReward milestone = AdminProgressionService.findMilestone(level);
        if (milestone == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Milestone introuvable: " + level).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir aux milestones"), sp -> openArcadiaMilestonesGui(sp, page));
            menu.setButton(10, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Message", trimLore(milestone.message)), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le nouveau message du milestone " + level, (playerInput, input) -> {
                        ArcadiaProgressionConfig.MilestoneReward current = AdminProgressionService.findMilestone(level);
                        if (current == null) return false;
                        current.message = input;
                        ConfigManager.getInstance().saveProgressionConfig();
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Message du milestone mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaMilestoneDetailGui(reopen, level, page)));
            menu.setButton(12, guiItem(Items.CHEST, ChatFormatting.GREEN + "Reward Item", "Format: <item> <count> <chance>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<item> <count> <chance>' pour ajouter un reward item au milestone " + level, (playerInput, input) -> AdminProgressionService.applyMilestoneItemRewardInput(playerInput, level, input), reopen -> openArcadiaMilestoneDetailGui(reopen, level, page)));
            menu.setButton(14, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.GREEN + "Reward XP", "Format: <xp>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre l'XP a ajouter au milestone " + level, (playerInput, input) -> AdminProgressionService.applyMilestoneXpRewardInput(playerInput, level, input), reopen -> openArcadiaMilestoneDetailGui(reopen, level, page)));
            menu.setButton(16, guiItem(Items.COMMAND_BLOCK, ChatFormatting.GOLD + "Reward Commande", "Entrer la commande complete"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre la commande du reward pour le milestone " + level, (playerInput, input) -> AdminProgressionService.applyMilestoneCommandRewardInput(playerInput, level, input), reopen -> openArcadiaMilestoneDetailGui(reopen, level, page)));
            menu.setButton(17, guiItem(Items.BARRIER, ChatFormatting.RED + "Vider Rewards", "Supprimer tous les rewards"), sp -> {
                ArcadiaProgressionConfig.MilestoneReward current = AdminProgressionService.findMilestone(level);
                if (current != null) {
                    current.rewards.clear();
                    ConfigManager.getInstance().saveProgressionConfig();
                }
                openArcadiaMilestoneDetailGui(sp, level, page);
            });

            int pageSize = 4;
            int totalPages = Math.max(1, (milestone.rewards.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(milestone.rewards.size(), start + pageSize);
            int slot = 18;
            for (RewardConfig reward : milestone.rewards.subList(start, end)) {
                menu.setButton(slot++, guiItem(Items.PAPER, ChatFormatting.WHITE + AdminDungeonConfigService.buildRewardSummary(reward), "Reward existant"), null);
            }
            AdminGuiSupport.addPagination(menu, 19, 22, 25, safePage, totalPages, (sp, nextPage) -> openArcadiaMilestoneDetailGui(sp, level, nextPage));
            return menu;
        }, Component.literal("Milestone " + level)));
    }

    public static void openArcadiaStreaksGui(ServerPlayer player, int page) {
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir au menu admin"), AdminProgressionGuiMenus::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <semaines> <xp> <message>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<semaines> <xp> <message>' pour ajouter ou modifier un streak bonus", AdminProgressionService::applyStreakInput, reopen -> openArcadiaStreaksGui(reopen, page)));
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Supprimer", "Format: <semaines>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le nombre de semaines du streak a supprimer", AdminProgressionService::applyStreakRemoveInput, reopen -> openArcadiaStreaksGui(reopen, page)));

            ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
            config.normalize();
            int pageSize = 9;
            int totalPages = Math.max(1, (config.streakBonuses.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(config.streakBonuses.size(), start + pageSize);
            int slot = 18;
            for (ArcadiaProgressionConfig.StreakBonus streak : config.streakBonuses.subList(start, end)) {
                int streakWeeks = streak.weeks;
                menu.setButton(slot++, guiItem(Items.CLOCK, ChatFormatting.GOLD + String.valueOf(streak.weeks) + " semaine(s)", "+" + streak.xpBonus + " XP", "Cliquer pour ouvrir"), sp -> openArcadiaStreakDetailGui(sp, streakWeeks, safePage));
            }
            AdminGuiSupport.addPagination(menu, 19, 22, 25, safePage, totalPages, (sp, nextPage) -> openArcadiaStreaksGui(sp, nextPage));
            return menu;
        }, Component.literal("Arcadia Streaks")));
    }

    public static void openArcadiaStreakDetailGui(ServerPlayer player, int weeks) {
        openArcadiaStreakDetailGui(player, weeks, 0);
    }

    public static void openArcadiaStreakDetailGui(ServerPlayer player, int weeks, int returnPage) {
        ArcadiaProgressionConfig.StreakBonus streak = AdminProgressionService.findStreakBonus(weeks);
        if (streak == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Streak bonus introuvable: " + weeks).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Revenir aux streaks"), sp -> openArcadiaStreaksGui(sp, returnPage));
            menu.setButton(10, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.GREEN + "XP Bonus", "+" + streak.xpBonus + " XP"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre le nouvel XP bonus pour le streak " + weeks, (playerInput, xp) -> {
                        ArcadiaProgressionConfig.StreakBonus current = AdminProgressionService.findStreakBonus(weeks);
                        if (current == null) return false;
                        current.xpBonus = xp;
                        ConfigManager.getInstance().saveProgressionConfig();
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] XP bonus mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaStreakDetailGui(reopen, weeks, returnPage)));
            menu.setButton(12, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Message", safeText(streak.message, "Aucun message")), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le nouveau message du streak " + weeks, (playerInput, input) -> {
                        ArcadiaProgressionConfig.StreakBonus current = AdminProgressionService.findStreakBonus(weeks);
                        if (current == null) return false;
                        current.message = input;
                        ConfigManager.getInstance().saveProgressionConfig();
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Message mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openArcadiaStreakDetailGui(reopen, weeks, returnPage)));
            menu.setButton(14, guiItem(Items.BARRIER, ChatFormatting.RED + "Supprimer", "Supprimer ce streak"), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Suppression Streak", weeks + " semaine(s)", confirm -> {
                        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
                        config.streakBonuses.removeIf(entry -> entry != null && entry.weeks == weeks);
                        config.normalize();
                        ConfigManager.getInstance().saveProgressionConfig();
                        openArcadiaStreaksGui(confirm, returnPage);
                    }, cancel -> openArcadiaStreakDetailGui(cancel, weeks, returnPage)));
            menu.setButton(21, guiItem(Items.CLOCK, ChatFormatting.GOLD + "Semaines", String.valueOf(weeks)), null);
            return menu;
        }, Component.literal("Streak " + weeks)));
    }

    private static ItemStack guiItem(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trimLore(String value) {
        String text = safeText(value, "Aucun");
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }
}

