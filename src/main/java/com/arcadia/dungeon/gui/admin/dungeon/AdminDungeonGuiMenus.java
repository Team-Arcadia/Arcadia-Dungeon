package com.arcadia.dungeon.gui.admin.dungeon;

import com.arcadia.dungeon.service.admin.AdminDungeonConfigService;
import com.arcadia.dungeon.gui.admin.AdminGuiRouter;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.RewardConfig;
import com.arcadia.dungeon.config.SpawnPointConfig;
import com.arcadia.dungeon.dungeon.WeeklyLeaderboard;
import com.arcadia.dungeon.gui.ArcadiaChestMenu;
import com.arcadia.dungeon.gui.ArcadiaPendingInputManager;
import com.arcadia.dungeon.gui.admin.AdminGuiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class AdminDungeonGuiMenus {
    private AdminDungeonGuiMenus() {}

    public static void openDungeonAdminHubGui(ServerPlayer player, String dungeonId) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour liste donjons"), sp -> AdminGuiRouter.openAdminHubGui(sp, 0));
            menu.setButton(10, guiItem(Items.COMPASS, ChatFormatting.GREEN + "Donjon", "Reglages coeur du donjon"), sp -> openDungeonCoreGui(sp, dungeon.id));
            menu.setButton(12, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Messages", "Messages start/completion/fail/recruitment"), sp -> openDungeonMessagesGui(sp, dungeon.id));
            menu.setButton(14, guiItem(Items.GOLD_INGOT, ChatFormatting.GOLD + "Rewards", "Rewards de completion"), sp -> openDungeonRewardsGui(sp, dungeon.id, 0));
            menu.setButton(16, guiItem(Items.IRON_BARS, ChatFormatting.LIGHT_PURPLE + "Zone / Murs", "Zone du donjon et murs scripted"), sp -> openDungeonAreaGui(sp, dungeon.id, 0));
            menu.setButton(20, guiItem(Items.WITHER_SKELETON_SKULL, ChatFormatting.RED + "Bosses", "Configurer les bosses"), sp -> AdminGuiRouter.openBossListGui(sp, dungeon.id, 0));
            menu.setButton(22, guiItem(Items.ZOMBIE_HEAD, ChatFormatting.GREEN + "Waves", "Configurer les vagues"), sp -> AdminGuiRouter.openWaveListGui(sp, dungeon.id, 0));
            menu.setButton(24, guiItem(Items.ENCHANTED_BOOK, ChatFormatting.AQUA + "Arcadia", "Reglages Arcadia du donjon"), sp -> AdminGuiRouter.openDungeonArcadiaAdminGui(sp, dungeon.id));
            menu.setButton(31, guiItem(dungeon.enabled ? Items.LIME_DYE : Items.GRAY_DYE, dungeon.enabled ? ChatFormatting.GREEN + "Donjon active" : ChatFormatting.RED + "Donjon desactive", "Cliquer pour basculer"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.enabled = !cfg.enabled;
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openDungeonAdminHubGui(sp, dungeon.id);
            });
            menu.setButton(33, guiItem(Items.NAME_TAG, ChatFormatting.YELLOW + dungeon.name, "ID: " + dungeon.id), null);
            return menu;
        }, Component.literal("Donjon: " + dungeon.name)));
    }

    public static void openDungeonCoreGui(ServerPlayer player, String dungeonId) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.NAME_TAG, ChatFormatting.GOLD + "Nom", dungeon.name), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre le nouveau nom du donjon", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg == null) return false;
                        if (input == null || input.isBlank()) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Nom vide interdit.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        cfg.name = input.trim();
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Nom du donjon mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(11, guiItem(Items.RECOVERY_COMPASS, ChatFormatting.GREEN + "Spawn", formatSpawnSummary(dungeon.spawnPoint), "Cliquer pour definir a ta position"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.spawnPoint = new SpawnPointConfig(sp.level().dimension().location().toString(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
                    ConfigManager.getInstance().saveDungeon(cfg);
                    sp.sendSystemMessage(Component.literal("[Arcadia] Spawn du donjon mis a jour.").withStyle(ChatFormatting.GREEN));
                }
                openDungeonCoreGui(sp, dungeon.id);
            });
            menu.setButton(12, guiItem(Items.CLOCK, ChatFormatting.AQUA + "Cooldown", dungeon.cooldownSeconds + "s"), sp -> openIntPrompt(sp, "Entre le cooldown en secondes", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.cooldownSeconds = Math.max(0, value), "[Arcadia] Cooldown mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(13, guiItem(Items.SPYGLASS, ChatFormatting.AQUA + "Disponibilite", dungeon.availableEverySeconds + "s", "0 = toujours disponible"), sp -> openIntPrompt(sp, "Entre la disponibilite en secondes (0 = toujours)", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.availableEverySeconds = Math.max(0, value), "[Arcadia] Disponibilite mise a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(14, guiItem(Items.COMPARATOR, ChatFormatting.YELLOW + "Ordre", String.valueOf(dungeon.order)), sp -> openIntPrompt(sp, "Entre l'ordre d'affichage du donjon", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.order = value, "[Arcadia] Ordre mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(15, guiItem(dungeon.enabled ? Items.LIME_DYE : Items.GRAY_DYE, dungeon.enabled ? ChatFormatting.GREEN + "Active" : ChatFormatting.RED + "Desactive", "Cliquer pour basculer"), sp -> toggleBool(dungeon.id, cfg -> cfg.enabled = !cfg.enabled, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(16, guiItem(Items.TRIPWIRE_HOOK, ChatFormatting.LIGHT_PURPLE + "Prerequis", safeText(dungeon.requiredDungeon, "Aucun")), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre l'id du donjon requis ou 'none'", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg == null) return false;
                        String trimmed = input.trim();
                        if (trimmed.equalsIgnoreCase("none")) cfg.requiredDungeon = "";
                        else if (ConfigManager.getInstance().getDungeon(trimmed) == null) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Donjon requis introuvable.").withStyle(ChatFormatting.RED));
                            return false;
                        } else if (trimmed.equalsIgnoreCase(dungeon.id)) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Un donjon ne peut pas se requerir lui-meme.").withStyle(ChatFormatting.RED));
                            return false;
                        } else cfg.requiredDungeon = trimmed;
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Prerequis mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(19, guiItem(Items.PLAYER_HEAD, ChatFormatting.GREEN + "Max Joueurs", String.valueOf(dungeon.settings.maxPlayers)), sp -> openIntPrompt(sp, "Entre le nombre max de joueurs", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.settings.maxPlayers = Math.max(1, value), "[Arcadia] Max joueurs mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(20, guiItem(Items.SKELETON_SKULL, ChatFormatting.YELLOW + "Min Joueurs", String.valueOf(dungeon.settings.minPlayers)), sp -> openIntPrompt(sp, "Entre le nombre min de joueurs", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.settings.minPlayers = Math.max(1, value), "[Arcadia] Min joueurs mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(21, guiItem(Items.BELL, ChatFormatting.AQUA + "Recrutement", dungeon.recruitmentDurationSeconds + "s"), sp -> openIntPrompt(sp, "Entre la duree de recrutement en secondes", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.recruitmentDurationSeconds = Math.max(0, value), "[Arcadia] Duree de recrutement mise a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(22, guiItem(Items.HOPPER, ChatFormatting.GOLD + "Temps Limite", dungeon.settings.timeLimitSeconds + "s"), sp -> openIntPrompt(sp, "Entre le temps limite en secondes", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.settings.timeLimitSeconds = Math.max(0, value), "[Arcadia] Temps limite mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(23, guiItem(Items.TOTEM_OF_UNDYING, ChatFormatting.RED + "Max Morts", String.valueOf(dungeon.settings.maxDeaths)), sp -> openIntPrompt(sp, "Entre le nombre max de morts", input -> AdminDungeonConfigService.applyDungeonIntValue(sp, dungeon.id, input, (cfg, value) -> cfg.settings.maxDeaths = Math.max(0, value), "[Arcadia] Max morts mis a jour: "), reopen -> openDungeonCoreGui(reopen, dungeon.id)));
            menu.setButton(28, guiItem(dungeon.announceStart ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.GREEN + "Annonce Start", String.valueOf(dungeon.announceStart)), sp -> toggleBool(dungeon.id, cfg -> cfg.announceStart = !cfg.announceStart, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(29, guiItem(dungeon.announceCompletion ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.GREEN + "Annonce Fin", String.valueOf(dungeon.announceCompletion)), sp -> toggleBool(dungeon.id, cfg -> cfg.announceCompletion = !cfg.announceCompletion, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(30, guiItem(dungeon.announceAvailability ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.GREEN + "Annonce Disponibilite", String.valueOf(dungeon.announceAvailability)), sp -> toggleBool(dungeon.id, cfg -> cfg.announceAvailability = !cfg.announceAvailability, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(31, guiItem(dungeon.settings.pvp ? Items.IRON_SWORD : Items.WOODEN_SWORD, ChatFormatting.RED + "PvP", String.valueOf(dungeon.settings.pvp)), sp -> toggleBool(dungeon.id, cfg -> cfg.settings.pvp = !cfg.settings.pvp, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(32, guiItem(dungeon.teleportBackOnComplete ? Items.ENDER_PEARL : Items.BARRIER, ChatFormatting.AQUA + "Teleport Back", String.valueOf(dungeon.teleportBackOnComplete)), sp -> toggleBool(dungeon.id, cfg -> cfg.teleportBackOnComplete = !cfg.teleportBackOnComplete, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(33, guiItem(dungeon.settings.blockTeleportCommands ? Items.OBSIDIAN : Items.GLASS, ChatFormatting.DARK_PURPLE + "Bloc TP Cmd", String.valueOf(dungeon.settings.blockTeleportCommands)), sp -> toggleBool(dungeon.id, cfg -> cfg.settings.blockTeleportCommands = !cfg.settings.blockTeleportCommands, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(34, guiItem(dungeon.settings.difficultyScaling ? Items.ANVIL : Items.FEATHER, ChatFormatting.GOLD + "Difficulty Scaling", String.valueOf(dungeon.settings.difficultyScaling)), sp -> toggleBool(dungeon.id, cfg -> cfg.settings.difficultyScaling = !cfg.settings.difficultyScaling, () -> openDungeonCoreGui(sp, dungeon.id)));
            menu.setButton(35, guiItem(Items.CLOCK, ChatFormatting.YELLOW + "Timer Warnings", dungeon.settings.timerWarnings.isEmpty() ? "Aucun" : dungeon.settings.timerWarnings.toString()), sp -> openDungeonTimerWarningsGui(sp, dungeon.id, 0));
            return menu;
        }, Component.literal("Donjon Core: " + dungeon.name)));
    }

    public static void openDungeonTimerWarningsGui(ServerPlayer player, String dungeonId, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        List<Integer> warnings = new ArrayList<>(dungeon.settings.timerWarnings);
        warnings.sort(Collections.reverseOrder());
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour coeur du donjon"), sp -> openDungeonCoreGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter Warning", "Ajouter un avertissement en secondes"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre le warning timer en secondes", (playerInput, seconds) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg == null) return false;
                        if (!cfg.settings.timerWarnings.contains(seconds)) {
                            cfg.settings.timerWarnings.add(seconds);
                            cfg.settings.timerWarnings.sort(Collections.reverseOrder());
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Warning ajoute: " + seconds + "s").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openDungeonTimerWarningsGui(reopen, dungeon.id, page)));
            menu.setButton(12, guiItem(Items.PAPER, ChatFormatting.WHITE + "Etat", warnings.isEmpty() ? "Aucun warning configure" : warnings.toString()), null);
            int pageSize = 18;
            int totalPages = Math.max(1, (warnings.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(warnings.size(), start + pageSize);
            int slot = 18;
            for (int seconds : warnings.subList(start, end)) {
                menu.setButton(slot++, guiItem(Items.CLOCK, ChatFormatting.YELLOW + String.valueOf(seconds) + "s", "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    if (cfg != null) {
                        cfg.settings.timerWarnings.remove(Integer.valueOf(seconds));
                        cfg.settings.timerWarnings.sort(Collections.reverseOrder());
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openDungeonTimerWarningsGui(sp, dungeon.id, safePage);
                });
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openDungeonTimerWarningsGui(sp, dungeon.id, nextPage));
            return menu;
        }, Component.literal("Timer Warnings: " + dungeon.name)));
    }

    public static void openWeeklyAdminGui(ServerPlayer player) {
        WeeklyLeaderboard.WeeklyConfig config = WeeklyLeaderboard.getInstance().getConfig();
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour admin Arcadia"), AdminGuiRouter::openArcadiaAdminGui);
            menu.setButton(10, guiItem(Items.CLOCK, ChatFormatting.AQUA + "Reset Day", String.valueOf(config.resetDay)), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le jour de reset (MONDAY..SUNDAY)", (playerInput, input) -> {
                        try {
                            WeeklyLeaderboard.getInstance().setResetDay(java.time.DayOfWeek.valueOf(input.trim().toUpperCase(Locale.ROOT)));
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Jour de reset hebdo mis a jour.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (IllegalArgumentException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Jour invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, AdminDungeonGuiMenus::openWeeklyAdminGui));
            menu.setButton(11, guiItem(Items.CLOCK, ChatFormatting.AQUA + "Announce Hour", config.announceHour + "h"), sp ->
                    AdminGuiSupport.beginIntPrompt(sp, "Entre l'heure d'annonce (0-23)", (playerInput, hour) -> {
                        if (hour < 0 || hour > 23) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Heure invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        WeeklyLeaderboard.getInstance().setAnnounceHour(hour);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Heure d'annonce mise a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, AdminDungeonGuiMenus::openWeeklyAdminGui));
            menu.setButton(12, guiItem(Items.CHEST, ChatFormatting.GOLD + "Top 1 Reward", summarizeWeeklyRewards(config.top1Rewards)), sp -> beginWeeklyRewardPrompt(sp, 1));
            menu.setButton(13, guiItem(Items.CHEST, ChatFormatting.GRAY + "Top 2 Reward", summarizeWeeklyRewards(config.top2Rewards)), sp -> beginWeeklyRewardPrompt(sp, 2));
            menu.setButton(14, guiItem(Items.CHEST, ChatFormatting.RED + "Top 3 Reward", summarizeWeeklyRewards(config.top3Rewards)), sp -> beginWeeklyRewardPrompt(sp, 3));
            menu.setButton(16, guiItem(Items.BOOK, ChatFormatting.WHITE + "Info", "Jour: " + config.resetDay, "Heure: " + config.announceHour + "h"), null);
            menu.setButton(22, guiItem(Items.TNT, ChatFormatting.RED + "Force Reset", "Distribuer et reset maintenant"), sp -> {
                WeeklyLeaderboard.getInstance().forceReset(sp.server);
                sp.sendSystemMessage(Component.literal("[Arcadia] Leaderboard hebdo reset.").withStyle(ChatFormatting.GREEN));
                openWeeklyAdminGui(sp);
            });
            return menu;
        }, Component.literal("Admin Weekly")));
    }

    private static void beginWeeklyRewardPrompt(ServerPlayer player, int top) {
        AdminGuiSupport.beginPrompt(player, "Entre '<item> <count>' pour la recompense du top " + top, (playerInput, input) -> {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 2) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <item> <count>").withStyle(ChatFormatting.RED));
                return false;
            }
            try {
                int count = Integer.parseInt(parts[1]);
                WeeklyLeaderboard.getInstance().setReward(top, parts[0], Math.max(1, count));
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Reward top " + top + " mise a jour.").withStyle(ChatFormatting.GREEN));
                return true;
            } catch (NumberFormatException e) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Quantite invalide.").withStyle(ChatFormatting.RED));
                return false;
            }
        }, AdminDungeonGuiMenus::openWeeklyAdminGui);
    }

    private static String summarizeWeeklyRewards(List<RewardConfig> rewards) {
        if (rewards == null || rewards.isEmpty()) return "Aucune";
        RewardConfig reward = rewards.get(0);
        return reward.item + " x" + reward.count;
    }

    public static void openDungeonMessagesGui(ServerPlayer player, String dungeonId) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.WRITABLE_BOOK, ChatFormatting.GREEN + "Message Start", trimLore(dungeon.startMessage)), sp -> beginDungeonStringEdit(sp, dungeon.id, "Entre le message de lancement", (cfg, value) -> cfg.startMessage = value, "[Arcadia] Message de lancement mis a jour.", AdminDungeonGuiMenus::openDungeonMessagesGui));
            menu.setButton(11, guiItem(Items.BOOK, ChatFormatting.GREEN + "Message Fin", trimLore(dungeon.completionMessage)), sp -> beginDungeonStringEdit(sp, dungeon.id, "Entre le message de completion", (cfg, value) -> cfg.completionMessage = value, "[Arcadia] Message de completion mis a jour.", AdminDungeonGuiMenus::openDungeonMessagesGui));
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Message Echec", trimLore(dungeon.failMessage)), sp -> beginDungeonStringEdit(sp, dungeon.id, "Entre le message d'echec", (cfg, value) -> cfg.failMessage = value, "[Arcadia] Message d'echec mis a jour.", AdminDungeonGuiMenus::openDungeonMessagesGui));
            menu.setButton(13, guiItem(Items.BELL, ChatFormatting.AQUA + "Message Recrutement", trimLore(dungeon.recruitmentMessage)), sp -> beginDungeonStringEdit(sp, dungeon.id, "Entre le message de recrutement", (cfg, value) -> cfg.recruitmentMessage = value, "[Arcadia] Message de recrutement mis a jour.", AdminDungeonGuiMenus::openDungeonMessagesGui));
            menu.setButton(14, guiItem(Items.SPYGLASS, ChatFormatting.GOLD + "Message Disponibilite", trimLore(dungeon.availabilityMessage)), sp -> beginDungeonStringEdit(sp, dungeon.id, "Entre le message de disponibilite", (cfg, value) -> cfg.availabilityMessage = value, "[Arcadia] Message de disponibilite mis a jour.", AdminDungeonGuiMenus::openDungeonMessagesGui));
            return menu;
        }, Component.literal("Messages: " + dungeon.name)));
    }

    public static void openDungeonRewardsGui(ServerPlayer player, String dungeonId, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.CHEST, ChatFormatting.GREEN + "Ajouter Reward Item", "Format: <item> <count> <chance>"), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre '<item> <count> <chance>' pour ajouter un reward", (playerInput, input) -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 3) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <item> <count> <chance>").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        try {
                            RewardConfig reward = new RewardConfig(parts[0], Integer.parseInt(parts[1]), Double.parseDouble(parts[2]));
                            reward.normalize();
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            if (cfg == null) return false;
                            cfg.completionRewards.add(reward);
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Reward item ajoute.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openDungeonRewardsGui(reopen, dungeon.id, page)));
            menu.setButton(12, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.AQUA + "Ajouter Reward XP", "Format: <xp>"), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre l'XP du reward", (playerInput, input) -> {
                        try {
                            int xp = Integer.parseInt(input.trim());
                            RewardConfig reward = new RewardConfig();
                            reward.item = "";
                            reward.command = "";
                            reward.experience = Math.max(0, xp);
                            reward.chance = 1.0;
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            if (cfg == null) return false;
                            cfg.completionRewards.add(reward);
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Reward XP ajoute.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openDungeonRewardsGui(reopen, dungeon.id, page)));
            menu.setButton(14, guiItem(Items.COMMAND_BLOCK, ChatFormatting.GOLD + "Ajouter Reward Commande", "Commande executee a la completion"), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre la commande du reward", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg == null) return false;
                        RewardConfig reward = new RewardConfig();
                        reward.item = "";
                        reward.command = input.trim();
                        reward.experience = 0;
                        reward.chance = 1.0;
                        cfg.completionRewards.add(reward);
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Reward commande ajoute.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openDungeonRewardsGui(reopen, dungeon.id, page)));
            menu.setButton(16, guiItem(Items.BARRIER, ChatFormatting.RED + "Vider Rewards", "Supprimer tous les rewards"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.completionRewards.clear();
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openDungeonRewardsGui(sp, dungeon.id, 0);
            });
            int pageSize = 18;
            int totalPages = Math.max(1, (dungeon.completionRewards.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeon.completionRewards.size(), start + pageSize);
            int slot = 18;
            for (int i = start; i < end; i++) {
                RewardConfig reward = dungeon.completionRewards.get(i);
                int rewardIndex = i;
                menu.setButton(slot++, guiItem(Items.PAPER, ChatFormatting.WHITE + AdminDungeonConfigService.buildRewardSummary(reward), "Index: " + rewardIndex, "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    if (cfg != null && rewardIndex >= 0 && rewardIndex < cfg.completionRewards.size()) {
                        cfg.completionRewards.remove(rewardIndex);
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openDungeonRewardsGui(sp, dungeon.id, safePage);
                });
            }
            addPagination(menu, safePage, totalPages, (sp, nextPage) -> openDungeonRewardsGui(sp, dungeon.id, nextPage));
            return menu;
        }, Component.literal("Rewards: " + dungeon.name)));
    }

    public static void openDungeonAreaGui(ServerPlayer player, String dungeonId, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.GOLDEN_AXE, ChatFormatting.GREEN + "Pos1 Zone", dungeon.areaPos1 == null ? "Non definie" : formatAreaPos(dungeon.areaPos1)), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.areaPos1 = new DungeonConfig.AreaPos(sp.level().dimension().location().toString(), sp.blockPosition().getX(), sp.blockPosition().getY(), sp.blockPosition().getZ());
                    ConfigManager.getInstance().saveDungeon(cfg);
                    sp.sendSystemMessage(Component.literal("[Arcadia] Zone pos1 mise a jour.").withStyle(ChatFormatting.GREEN));
                }
                openDungeonAreaGui(sp, dungeon.id, page);
            });
            menu.setButton(11, guiItem(Items.GOLDEN_SHOVEL, ChatFormatting.GREEN + "Pos2 Zone", dungeon.areaPos2 == null ? "Non definie" : formatAreaPos(dungeon.areaPos2)), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.areaPos2 = new DungeonConfig.AreaPos(sp.level().dimension().location().toString(), sp.blockPosition().getX(), sp.blockPosition().getY(), sp.blockPosition().getZ());
                    ConfigManager.getInstance().saveDungeon(cfg);
                    sp.sendSystemMessage(Component.literal("[Arcadia] Zone pos2 mise a jour.").withStyle(ChatFormatting.GREEN));
                }
                openDungeonAreaGui(sp, dungeon.id, page);
            });
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Clear Zone", "Supprimer la zone"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.areaPos1 = null;
                    cfg.areaPos2 = null;
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openDungeonAreaGui(sp, dungeon.id, page);
            });
            menu.setButton(13, guiItem(Items.WOODEN_AXE, ChatFormatting.AQUA + "Wand Zone", "Donne la wand de selection"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon admin wand");
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon admin wand_select " + dungeon.id);
            });
            menu.setButton(14, guiItem(Items.GOLDEN_HOE, ChatFormatting.LIGHT_PURPLE + "Wall Wand", "Donne la wand mur scripted"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon admin wallwand");
            });
            menu.setButton(15, guiItem(Items.IRON_BARS, ChatFormatting.LIGHT_PURPLE + "Ajouter / Selectionner Mur", "Format: <wallId>"), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre l'id du mur scripted a selectionner", (playerInput, input) -> {
                        String wallId = input.trim();
                        if (wallId.isBlank()) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Wall id vide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg == null) return false;
                        DungeonConfig.ScriptedWallConfig wall = findScriptedWall(cfg, wallId);
                        if (wall == null) {
                            wall = new DungeonConfig.ScriptedWallConfig();
                            wall.id = wallId;
                            cfg.scriptedWalls.add(wall);
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        com.arcadia.dungeon.event.DungeonEventHandler.wandDungeon.put(playerInput.getUUID(), dungeon.id);
                        com.arcadia.dungeon.event.DungeonEventHandler.wandWall.put(playerInput.getUUID(), wallId);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Mur scripted selectionne: " + wallId).withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openDungeonAreaGui(reopen, dungeon.id, page)));
            int pageSize = 18;
            int totalPages = Math.max(1, (dungeon.scriptedWalls.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeon.scriptedWalls.size(), start + pageSize);
            int slot = 18;
            for (DungeonConfig.ScriptedWallConfig wall : dungeon.scriptedWalls.subList(start, end)) {
                String wallId = wall.id;
                int blockCount = wall.blocks == null ? 0 : wall.blocks.size();
                menu.setButton(slot++, guiItem(Items.IRON_BARS, ChatFormatting.LIGHT_PURPLE + wallId, "Condition: " + safeText(wall.activationCondition, "Aucune"), "Blocs: " + blockCount, "Cliquer pour ouvrir"), sp -> openWallDetailGui(sp, dungeon.id, wallId));
            }
            if (safePage > 0) menu.setButton(36, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page precedente", "Page " + safePage), sp -> openDungeonAreaGui(sp, dungeon.id, safePage - 1));
            menu.setButton(40, guiItem(Items.PAPER, ChatFormatting.WHITE + "Page " + (safePage + 1) + "/" + totalPages, "Navigation"), null);
            if (safePage + 1 < totalPages) menu.setButton(44, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page suivante", "Page " + (safePage + 2)), sp -> openDungeonAreaGui(sp, dungeon.id, safePage + 1));
            return menu;
        }, Component.literal("Zone / Murs: " + dungeon.name)));
    }

    public static void openWallDetailGui(ServerPlayer player, String dungeonId, String wallId) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        DungeonConfig.ScriptedWallConfig wall = dungeon == null ? null : findScriptedWall(dungeon, wallId);
        if (dungeon == null || wall == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Mur scripted introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour zone / murs"), sp -> openDungeonAreaGui(sp, dungeon.id, 0));
            menu.setButton(10, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Condition", safeText(wall.activationCondition, "Aucune")), sp ->
                    ArcadiaPendingInputManager.begin(sp, "Entre la condition du mur (ex: WAVE_COMPLETE:2) ou 'none'", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        DungeonConfig.ScriptedWallConfig current = cfg == null ? null : findScriptedWall(cfg, wallId);
                        if (current == null) return false;
                        current.activationCondition = input.trim().equalsIgnoreCase("none") ? "" : input.trim();
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Condition du mur mise a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openWallDetailGui(reopen, dungeon.id, wallId)));
            menu.setButton(11, guiItem(Items.OBSERVER, ChatFormatting.GOLD + "Action", safeText(wall.action, "TOGGLE"), "Cliquer pour alterner TOGGLE/REMOVE"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                DungeonConfig.ScriptedWallConfig current = cfg == null ? null : findScriptedWall(cfg, wallId);
                if (current != null) {
                    String currentAction = safeText(current.action, "TOGGLE").toUpperCase(Locale.ROOT);
                    current.action = currentAction.equals("REMOVE") ? "TOGGLE" : "REMOVE";
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWallDetailGui(sp, dungeon.id, wallId);
            });
            menu.setButton(12, guiItem(Items.GOLDEN_HOE, ChatFormatting.LIGHT_PURPLE + "Selection Wand", "Clique les blocs avec la hoe"), sp -> {
                sp.closeContainer();
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon admin wallwand");
                sp.server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "arcadia_dungeon admin wall_select " + dungeon.id + " " + wallId);
            });
            menu.setButton(14, guiItem(Items.BARRIER, ChatFormatting.RED + "Clear Blocs", "Supprimer tous les blocs du mur"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                DungeonConfig.ScriptedWallConfig current = cfg == null ? null : findScriptedWall(cfg, wallId);
                if (current != null) {
                    current.blocks.clear();
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWallDetailGui(sp, dungeon.id, wallId);
            });
            menu.setButton(16, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Mur", wallId), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                if (cfg != null) {
                    cfg.scriptedWalls.removeIf(entry -> entry != null && wallId.equals(entry.id));
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openDungeonAreaGui(sp, dungeon.id, 0);
            });
            menu.setButton(22, guiItem(Items.PAPER, ChatFormatting.WHITE + "Blocs", String.valueOf(wall.blocks == null ? 0 : wall.blocks.size())), null);
            return menu;
        }, Component.literal("Mur: " + wallId)));
    }

    private static ItemStack guiItem(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private static void toggleBool(String dungeonId, java.util.function.Consumer<DungeonConfig> toggle, Runnable reopen) {
        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
        if (cfg != null) {
            toggle.accept(cfg);
            ConfigManager.getInstance().saveDungeon(cfg);
        }
        reopen.run();
    }

    private static void openIntPrompt(ServerPlayer player, String prompt, java.util.function.Function<String, Boolean> handler, java.util.function.Consumer<ServerPlayer> reopen) {
        ArcadiaPendingInputManager.begin(player, prompt, (playerInput, input) -> handler.apply(input), reopen);
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trimLore(String value) {
        String text = safeText(value, "Aucun");
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }

    private static String formatSpawnSummary(SpawnPointConfig spawnPoint) {
        return spawnPoint == null ? "Non defini" : spawnPoint.dimension + " " + (int) spawnPoint.x + " " + (int) spawnPoint.y + " " + (int) spawnPoint.z;
    }

    private static String formatAreaPos(DungeonConfig.AreaPos pos) {
        return pos == null ? "Non definie" : pos.dimension + " " + pos.x + " " + pos.y + " " + pos.z;
    }

    private static DungeonConfig.ScriptedWallConfig findScriptedWall(DungeonConfig config, String wallId) {
        if (config == null || wallId == null || config.scriptedWalls == null) return null;
        return config.scriptedWalls.stream().filter(w -> w != null && wallId.equals(w.id)).findFirst().orElse(null);
    }

    private static void beginDungeonStringEdit(ServerPlayer player, String dungeonId, String prompt,
                                               BiConsumer<DungeonConfig, String> setter,
                                               String successMessage,
                                               BiConsumer<ServerPlayer, String> reopenAction) {
        AdminGuiSupport.beginPrompt(player, prompt, (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            if (cfg == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(cfg, input);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId));
    }

    private static void addPagination(ArcadiaChestMenu menu, int safePage, int totalPages, java.util.function.BiConsumer<ServerPlayer, Integer> reopen) {
        if (safePage > 0) menu.setButton(27, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page precedente", "Page " + safePage), sp -> reopen.accept(sp, safePage - 1));
        menu.setButton(31, guiItem(Items.PAPER, ChatFormatting.WHITE + "Page " + (safePage + 1) + "/" + totalPages, "Navigation"), null);
        if (safePage + 1 < totalPages) menu.setButton(35, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Page suivante", "Page " + (safePage + 2)), sp -> reopen.accept(sp, safePage + 1));
    }
}


