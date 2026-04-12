package com.arcadia.dungeon.gui.admin.entity;

import com.arcadia.dungeon.gui.admin.AdminGuiRouter;
import com.arcadia.dungeon.config.*;
import com.arcadia.dungeon.dungeon.CombatTuning;
import com.arcadia.dungeon.gui.ArcadiaChestMenu;
import com.arcadia.dungeon.gui.admin.AdminGuiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AdminEntityGuiMenus {
    private AdminEntityGuiMenus() {}

    public static void openBossListGui(ServerPlayer player, String dungeonId, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> AdminGuiRouter.openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.WITHER_SKELETON_SKULL, ChatFormatting.RED + "Ajouter Boss", "Format: <id> <entityType> <hp> <damage>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<id> <entityType> <hp> <damage>' pour ajouter un boss",
                            (playerInput, input) -> {
                                String[] parts = input.trim().split("\\s+");
                                if (parts.length != 4) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <id> <entityType> <hp> <damage>").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                                try {
                                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                    if (cfg == null) return false;
                                    if (findBoss(cfg, parts[0]) != null) {
                                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Cet id de boss existe deja.").withStyle(ChatFormatting.RED));
                                        return false;
                                    }
                                    BossConfig boss = new BossConfig(parts[0], parts[1], Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                                    boss.customName = parts[0];
                                    boss.spawnPoint = new SpawnPointConfig(playerInput.level().dimension().location().toString(), playerInput.getX(), playerInput.getY(), playerInput.getZ(), playerInput.getYRot(), playerInput.getXRot());
                                    PhaseConfig phase1 = new PhaseConfig(1, 1.0);
                                    phase1.description = "Phase initiale";
                                    boss.phases.add(phase1);
                                    cfg.bosses.add(boss);
                                    ConfigManager.getInstance().saveDungeon(cfg);
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Boss ajoute.").withStyle(ChatFormatting.GREEN));
                                    return true;
                                } catch (NumberFormatException e) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeurs HP/DMG invalides.").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                            },
                            reopen -> openBossListGui(reopen, dungeon.id, page)));

            int pageSize = 27;
            int totalPages = Math.max(1, (dungeon.bosses.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeon.bosses.size(), start + pageSize);
            int slot = 18;
            for (BossConfig boss : dungeon.bosses.subList(start, end)) {
                String bossId = boss.id;
                menu.setButton(slot++, guiItem(Items.WITHER_SKELETON_SKULL,
                        ChatFormatting.RED + safeText(boss.customName, boss.id),
                        "ID: " + boss.id,
                        "Entity: " + boss.entityType,
                        "HP: " + boss.baseHealth + " DMG: " + boss.baseDamage,
                        "Cliquer pour ouvrir"), sp -> openBossDetailGui(sp, dungeon.id, bossId, safePage));
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openBossListGui(sp, dungeon.id, nextPage));
            return menu;
        }, Component.literal("Bosses: " + dungeon.name)));
    }

    public static void openBossDetailGui(ServerPlayer player, String dungeonId, String bossId) {
        openBossDetailGui(player, dungeonId, bossId, 0);
    }

    public static void openBossDetailGui(ServerPlayer player, String dungeonId, String bossId, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour liste bosses"), sp -> openBossListGui(sp, dungeon.id, returnPage));
            menu.setButton(10, guiItem(Items.NAME_TAG, ChatFormatting.GOLD + "Nom", safeText(boss.customName, boss.id)), sp -> beginBossStringEdit(sp, dungeon.id, boss.id, "Entre le nom du boss", (current, value) -> current.customName = value, "[Arcadia] Nom du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(11, guiItem(Items.SPAWNER, ChatFormatting.AQUA + "Entity Type", boss.entityType), sp -> beginBossStringEdit(sp, dungeon.id, boss.id, "Entre le nouvel entity type", (current, value) -> current.entityType = value, "[Arcadia] Entity type du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(12, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.RED + "HP", String.valueOf(boss.baseHealth)), sp -> beginBossDoubleEdit(sp, dungeon.id, boss.id, "Entre les PV du boss", (current, value) -> current.baseHealth = Math.max(1.0, value), "[Arcadia] PV du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(13, guiItem(Items.IRON_SWORD, ChatFormatting.RED + "Damage", String.valueOf(boss.baseDamage)), sp -> beginBossDoubleEdit(sp, dungeon.id, boss.id, "Entre les degats du boss", (current, value) -> current.baseDamage = Math.max(0.0, value), "[Arcadia] Degats du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(14, guiItem(Items.RECOVERY_COMPASS, ChatFormatting.GREEN + "Spawn", formatSpawnSummary(boss.spawnPoint), "Cliquer pour definir a ta position"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                if (current != null) {
                    current.spawnPoint = new SpawnPointConfig(sp.level().dimension().location().toString(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossDetailGui(sp, dungeon.id, boss.id);
            });
            menu.setButton(15, guiItem(boss.adaptivePower ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.AQUA + "Adaptive Power", String.valueOf(boss.adaptivePower)), sp -> toggleBossBool(sp, dungeon.id, boss.id, current -> current.adaptivePower = !current.adaptivePower, () -> openBossDetailGui(sp, dungeon.id, boss.id)));
            menu.setButton(16, guiItem(boss.showBossBar ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.YELLOW + "Boss Bar", String.valueOf(boss.showBossBar)), sp -> toggleBossBool(sp, dungeon.id, boss.id, current -> current.showBossBar = !current.showBossBar, () -> openBossDetailGui(sp, dungeon.id, boss.id)));
            menu.setButton(17, guiItem(Items.GOLDEN_APPLE, ChatFormatting.AQUA + "Adaptive HP Mult", String.valueOf(boss.healthMultiplierPerPlayer)), sp -> beginBossDoubleEdit(sp, dungeon.id, boss.id, "Entre le multiplicateur de vie adaptive par joueur", (current, value) -> current.healthMultiplierPerPlayer = Math.max(0.0, value), "[Arcadia] Adaptive HP mult mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(18, guiItem(Items.GOLDEN_SWORD, ChatFormatting.AQUA + "Adaptive DMG Mult", String.valueOf(boss.damageMultiplierPerPlayer)), sp -> beginBossDoubleEdit(sp, dungeon.id, boss.id, "Entre le multiplicateur de degats adaptive par joueur", (current, value) -> current.damageMultiplierPerPlayer = Math.max(0.0, value), "[Arcadia] Adaptive DMG mult mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(19, guiItem(boss.optional ? Items.FEATHER : Items.NETHERITE_SWORD, ChatFormatting.GOLD + "Optional", String.valueOf(boss.optional)), sp -> toggleBossBool(sp, dungeon.id, boss.id, current -> current.optional = !current.optional, () -> openBossDetailGui(sp, dungeon.id, boss.id)));
            menu.setButton(20, guiItem(Items.CLOCK, ChatFormatting.GOLD + "Spawn Chance", String.valueOf(boss.spawnChance)), sp -> beginBossDoubleEdit(sp, dungeon.id, boss.id, "Entre la chance de spawn entre 0 et 1", (current, value) -> current.spawnChance = Math.max(0.0D, Math.min(1.0D, value)), "[Arcadia] Chance de spawn du boss mise a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(21, guiItem(boss.spawnAtStart ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.GREEN + "Spawn At Start", String.valueOf(boss.spawnAtStart)), sp -> toggleBossBool(sp, dungeon.id, boss.id, current -> current.spawnAtStart = !current.spawnAtStart, () -> openBossDetailGui(sp, dungeon.id, boss.id)));
            menu.setButton(22, guiItem(boss.requiredKill ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.RED + "Required Kill", String.valueOf(boss.requiredKill)), sp -> toggleBossBool(sp, dungeon.id, boss.id, current -> current.requiredKill = !current.requiredKill, () -> openBossDetailGui(sp, dungeon.id, boss.id)));
            menu.setButton(23, guiItem(Items.COMPARATOR, ChatFormatting.AQUA + "Spawn After Wave", String.valueOf(boss.spawnAfterWave), "0 = apres toutes les waves"), sp -> beginBossIntEdit(sp, dungeon.id, boss.id, "Entre le numero de vague apres lequel le boss spawn (0 = fin)", (current, value) -> current.spawnAfterWave = Math.max(0, value), "[Arcadia] Spawn after wave mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(24, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Message Spawn", trimLore(boss.spawnMessage)), sp -> beginBossStringEdit(sp, dungeon.id, boss.id, "Entre le message de spawn du boss", (current, value) -> current.spawnMessage = value, "[Arcadia] Message de spawn du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(25, guiItem(Items.BOOK, ChatFormatting.GRAY + "Message Skip", trimLore(boss.skipMessage)), sp -> beginBossStringEdit(sp, dungeon.id, boss.id, "Entre le message de skip du boss", (current, value) -> current.skipMessage = value, "[Arcadia] Message de skip du boss mis a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(26, guiItem(Items.BEACON, ChatFormatting.YELLOW + "Boss Bar Color", safeText(boss.bossBarColor, "RED")), sp -> beginBossStringEdit(sp, dungeon.id, boss.id, "Entre la couleur du boss bar (RED/BLUE/GREEN/YELLOW/PURPLE/WHITE/PINK)", (current, value) -> current.bossBarColor = safeText(value, "RED").trim().toUpperCase(), "[Arcadia] Couleur du boss bar mise a jour.", AdminEntityGuiMenus::openBossDetailGui));
            menu.setButton(28, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.AQUA + "Equipement", "Configurer l'equipement du boss"), sp -> openBossEquipmentGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(29, guiItem(Items.BREWING_STAND, ChatFormatting.GREEN + "Attributs", String.valueOf(boss.customAttributes == null ? 0 : boss.customAttributes.size())), sp -> openBossAttributesGui(sp, dungeon.id, boss.id, 0, returnPage));
            menu.setButton(30, guiItem(Items.CROSSBOW, ChatFormatting.GOLD + "Combat", "Attack range, cooldown, dodge..."), sp -> openBossCombatGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(31, guiItem(Items.PAPER, ChatFormatting.WHITE + "Phases", String.valueOf(boss.phases == null ? 0 : boss.phases.size())), sp -> openBossPhasesGui(sp, dungeon.id, boss.id, 0, returnPage));
            menu.setButton(32, guiItem(Items.CHEST, ChatFormatting.WHITE + "Rewards", String.valueOf(boss.rewards == null ? 0 : boss.rewards.size())), sp -> openBossRewardsGui(sp, dungeon.id, boss.id, 0, returnPage));
            menu.setButton(34, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Boss", boss.id), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Suppression Boss", safeText(boss.customName, boss.id), confirm -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg != null) {
                            cfg.bosses.removeIf(entry -> entry != null && boss.id.equals(entry.id));
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        openBossListGui(confirm, dungeon.id, returnPage);
                    }, cancel -> openBossDetailGui(cancel, dungeon.id, boss.id, returnPage)));
            return menu;
        }, Component.literal("Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openWaveListGui(ServerPlayer player, String dungeonId, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour hub donjon"), sp -> AdminGuiRouter.openDungeonAdminHubGui(sp, dungeon.id));
            menu.setButton(10, guiItem(Items.ZOMBIE_HEAD, ChatFormatting.GREEN + "Ajouter Wave", "Format: <numero>"), sp ->
                    AdminGuiSupport.beginPrompt(
                            sp,
                            "Entre le numero de la wave a ajouter",
                            (playerInput, input) -> {
                                try {
                                    int waveNum = Integer.parseInt(input.trim());
                                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                    if (cfg == null) return false;
                                    if (findWave(cfg, waveNum) != null) {
                                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Cette wave existe deja.").withStyle(ChatFormatting.RED));
                                        return false;
                                    }
                                    cfg.waves.add(new WaveConfig(waveNum));
                                    cfg.waves.sort(Comparator.comparingInt(w -> w.waveNumber));
                                    ConfigManager.getInstance().saveDungeon(cfg);
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Wave ajoutee.").withStyle(ChatFormatting.GREEN));
                                    return true;
                                } catch (NumberFormatException e) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Numero invalide.").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                            },
                            reopen -> openWaveListGui(reopen, dungeon.id, page)
                    ));

            int pageSize = 27;
            int totalPages = Math.max(1, (dungeon.waves.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(dungeon.waves.size(), start + pageSize);
            int slot = 18;
            for (WaveConfig wave : dungeon.waves.subList(start, end)) {
                int waveNumber = wave.waveNumber;
                int mobCount = wave.mobs == null ? 0 : wave.mobs.stream().mapToInt(m -> Math.max(0, m.count)).sum();
                menu.setButton(slot++, guiItem(Items.ZOMBIE_HEAD,
                        ChatFormatting.GREEN + "Wave " + wave.waveNumber,
                        safeText(wave.name, "Sans nom"),
                        "Mobs: " + mobCount,
                        "Cliquer pour ouvrir"), sp -> openWaveDetailGui(sp, dungeon.id, waveNumber, safePage));
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openWaveListGui(sp, dungeon.id, nextPage));
            return menu;
        }, Component.literal("Waves: " + dungeon.name)));
    }

    public static void openWaveDetailGui(ServerPlayer player, String dungeonId, int waveNumber, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        WaveConfig wave = dungeon == null ? null : findWave(dungeon, waveNumber);
        if (dungeon == null || wave == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Wave introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour liste waves"), sp -> openWaveListGui(sp, dungeon.id, page));
            menu.setButton(10, guiItem(Items.NAME_TAG, ChatFormatting.GOLD + "Nom", safeText(wave.name, "Sans nom")), sp ->
                    AdminGuiSupport.beginPrompt(
                            sp,
                            "Entre le nom de la wave",
                            (playerInput, input) -> {
                                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                                if (current == null) return false;
                                current.name = input.trim();
                                ConfigManager.getInstance().saveDungeon(cfg);
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Nom de la wave mis a jour.").withStyle(ChatFormatting.GREEN));
                                return true;
                            },
                            reopen -> openWaveDetailGui(reopen, dungeon.id, waveNumber, page)
                    ));
            menu.setButton(11, guiItem(Items.CLOCK, ChatFormatting.AQUA + "Delai", wave.delayBeforeSeconds + "s"), sp ->
                    AdminGuiSupport.beginIntPrompt(
                            sp,
                            "Entre le delai avant la wave en secondes",
                            (playerInput, value) -> {
                                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                                if (current == null) return false;
                                current.delayBeforeSeconds = Math.max(0, value);
                                ConfigManager.getInstance().saveDungeon(cfg);
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Delai de la wave mis a jour.").withStyle(ChatFormatting.GREEN));
                                return true;
                            },
                            reopen -> openWaveDetailGui(reopen, dungeon.id, waveNumber, page)
                    ));
            menu.setButton(12, guiItem(wave.glowingAfterDelay ? Items.GLOW_INK_SAC : Items.INK_SAC, ChatFormatting.YELLOW + "Glowing", String.valueOf(wave.glowingAfterDelay)), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                if (current != null) {
                    current.glowingAfterDelay = !current.glowingAfterDelay;
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWaveDetailGui(sp, dungeon.id, waveNumber, page);
            });
            menu.setButton(13, guiItem(Items.SPECTRAL_ARROW, ChatFormatting.YELLOW + "Delai Glowing", wave.glowingDelaySeconds + "s"), sp ->
                    AdminGuiSupport.beginIntPrompt(
                            sp,
                            "Entre le delai de glowing en secondes",
                            (playerInput, value) -> {
                                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                                if (current == null) return false;
                                current.glowingDelaySeconds = Math.max(0, value);
                                ConfigManager.getInstance().saveDungeon(cfg);
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Delai de glowing mis a jour.").withStyle(ChatFormatting.GREEN));
                                return true;
                            },
                            reopen -> openWaveDetailGui(reopen, dungeon.id, waveNumber, page)
                    ));
            menu.setButton(14, guiItem(Items.WRITABLE_BOOK, ChatFormatting.AQUA + "Message", trimLore(wave.startMessage)), sp ->
                    AdminGuiSupport.beginPrompt(
                            sp,
                            "Entre le message de debut de wave",
                            (playerInput, input) -> {
                                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                                if (current == null) return false;
                                current.startMessage = input;
                                ConfigManager.getInstance().saveDungeon(cfg);
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Message de wave mis a jour.").withStyle(ChatFormatting.GREEN));
                                return true;
                            },
                            reopen -> openWaveDetailGui(reopen, dungeon.id, waveNumber, page)
                    ));
            menu.setButton(15, guiItem(Items.SPAWNER, ChatFormatting.GREEN + "Ajouter Mob", "Format: <entityType> <count>"), sp ->
                    AdminGuiSupport.beginPrompt(
                            sp,
                            "Entre '<entityType> <count>' pour ajouter un mob a la wave",
                            (playerInput, input) -> {
                                String[] parts = input.trim().split("\\s+");
                                if (parts.length != 2) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <entityType> <count>").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                                try {
                                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                    WaveConfig current = cfg == null ? null : findWave(cfg, waveNumber);
                                    if (current == null) return false;
                                    MobSpawnConfig mob = new MobSpawnConfig();
                                    mob.entityType = parts[0];
                                    mob.count = Math.max(1, Integer.parseInt(parts[1]));
                                    mob.spawnPoint = new SpawnPointConfig(playerInput.level().dimension().location().toString(), playerInput.getX(), playerInput.getY(), playerInput.getZ(), playerInput.getYRot(), playerInput.getXRot());
                                    current.mobs.add(mob);
                                    ConfigManager.getInstance().saveDungeon(cfg);
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Mob ajoute a la wave.").withStyle(ChatFormatting.GREEN));
                                    return true;
                                } catch (NumberFormatException e) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Count invalide.").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                            },
                            reopen -> openWaveDetailGui(reopen, dungeon.id, waveNumber, page)
                    ));
            menu.setButton(16, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Wave", "Wave " + waveNumber), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Suppression Wave", "Wave " + waveNumber, confirm -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        if (cfg != null) {
                            cfg.waves.removeIf(entry -> entry != null && entry.waveNumber == waveNumber);
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        openWaveListGui(confirm, dungeon.id, page);
                    }, cancel -> openWaveDetailGui(cancel, dungeon.id, waveNumber, page)));

            int pageSize = 18;
            int totalPages = Math.max(1, (wave.mobs.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(wave.mobs.size(), start + pageSize);
            int slot = 18;
            for (int i = start; i < end; i++) {
                MobSpawnConfig mob = wave.mobs.get(i);
                int mobIndex = i;
                menu.setButton(slot++, guiItem(Items.ROTTEN_FLESH,
                        ChatFormatting.WHITE + mob.entityType,
                        "Count: " + mob.count,
                        "HP: " + mob.health + " DMG: " + mob.damage,
                        "Cliquer pour ouvrir"), sp -> openWaveMobDetailGui(sp, dungeon.id, waveNumber, mobIndex, safePage));
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openWaveDetailGui(sp, dungeon.id, waveNumber, nextPage));
            return menu;
        }, Component.literal("Wave " + waveNumber + ": " + dungeon.name)));
    }

    public static void openWaveMobDetailGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex) {
        openWaveMobDetailGui(player, dungeonId, waveNumber, mobIndex, 0);
    }

    public static void openWaveMobDetailGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        WaveConfig wave = dungeon == null ? null : findWave(dungeon, waveNumber);
        MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
        if (dungeon == null || wave == null || mob == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Mob de wave introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail wave"), sp -> openWaveDetailGui(sp, dungeon.id, waveNumber, returnPage));
            menu.setButton(10, guiItem(Items.SPAWNER, ChatFormatting.AQUA + "Entity Type", mob.entityType), sp ->
                    beginWaveMobStringEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre le nouvel entity type", (current, value) -> current.entityType = value, "[Arcadia] Entity type du mob mis a jour.", returnPage));
            menu.setButton(11, guiItem(Items.PAPER, ChatFormatting.GREEN + "Count", String.valueOf(mob.count)), sp ->
                    beginWaveMobIntEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre le count du mob", (current, value) -> current.count = Math.max(1, value), "[Arcadia] Count du mob mis a jour.", returnPage));
            menu.setButton(12, guiItem(Items.NAME_TAG, ChatFormatting.GOLD + "Nom Custom", safeText(mob.customName, "Aucun")), sp ->
                    beginWaveMobStringEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre le nom custom du mob ou vide", (current, value) -> current.customName = value, "[Arcadia] Nom custom du mob mis a jour.", returnPage));
            menu.setButton(13, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.RED + "HP", String.valueOf(mob.health)), sp ->
                    beginWaveMobDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre les PV du mob", (current, value) -> current.health = Math.max(1.0, value), "[Arcadia] PV du mob mis a jour.", returnPage));
            menu.setButton(14, guiItem(Items.IRON_SWORD, ChatFormatting.RED + "Damage", String.valueOf(mob.damage)), sp ->
                    beginWaveMobDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre les degats du mob", (current, value) -> current.damage = Math.max(0.0, value), "[Arcadia] Degats du mob mis a jour.", returnPage));
            menu.setButton(15, guiItem(Items.SUGAR, ChatFormatting.AQUA + "Vitesse", String.valueOf(mob.speed)), sp ->
                    beginWaveMobDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "Entre la vitesse du mob", (current, value) -> current.speed = Math.max(0.0, value), "[Arcadia] Vitesse du mob mise a jour.", returnPage));
            menu.setButton(16, guiItem(Items.RECOVERY_COMPASS, ChatFormatting.GREEN + "Spawn", formatSpawnSummary(mob.spawnPoint), "Cliquer pour definir a ta position"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                if (current != null) {
                    current.spawnPoint = new SpawnPointConfig(sp.level().dimension().location().toString(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWaveMobDetailGui(sp, dungeon.id, waveNumber, mobIndex, returnPage);
            });
            menu.setButton(19, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.AQUA + "Equipement", "Configurer l'equipement du mob"), sp -> openWaveMobEquipmentGui(sp, dungeon.id, waveNumber, mobIndex, returnPage));
            menu.setButton(20, guiItem(Items.BREWING_STAND, ChatFormatting.GREEN + "Attributs", String.valueOf(mob.customAttributes == null ? 0 : mob.customAttributes.size())), sp -> openWaveMobAttributesGui(sp, dungeon.id, waveNumber, mobIndex, 0, returnPage));
            menu.setButton(21, guiItem(Items.CROSSBOW, ChatFormatting.GOLD + "Combat", "Attack range, cooldown, dodge..."), sp -> openWaveMobCombatGui(sp, dungeon.id, waveNumber, mobIndex, returnPage));
            menu.setButton(22, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Mob", "Index " + mobIndex), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Suppression Mob", "Wave " + waveNumber + " | Index " + mobIndex, confirm -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                        if (currentWave != null && mobIndex >= 0 && mobIndex < currentWave.mobs.size()) {
                            currentWave.mobs.remove(mobIndex);
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        openWaveDetailGui(confirm, dungeon.id, waveNumber, returnPage);
                    }, cancel -> openWaveMobDetailGui(cancel, dungeon.id, waveNumber, mobIndex, returnPage)));
            return menu;
        }, Component.literal("Mob " + mobIndex + " - Wave " + waveNumber)));
    }
    public static void openBossEquipmentGui(ServerPlayer player, String dungeonId, String bossId) {
        openBossEquipmentGui(player, dungeonId, bossId, 0);
    }

    public static void openBossEquipmentGui(ServerPlayer player, String dungeonId, String bossId, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail boss"), sp -> openBossDetailGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Main Hand", safeText(boss.mainHand, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "mainhand", returnPage));
            menu.setButton(11, guiItem(Items.SHIELD, ChatFormatting.WHITE + "Off Hand", safeText(boss.offHand, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "offhand", returnPage));
            menu.setButton(12, guiItem(Items.IRON_HELMET, ChatFormatting.WHITE + "Helmet", safeText(boss.helmet, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "helmet", returnPage));
            menu.setButton(13, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.WHITE + "Chestplate", safeText(boss.chestplate, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "chestplate", returnPage));
            menu.setButton(14, guiItem(Items.IRON_LEGGINGS, ChatFormatting.WHITE + "Leggings", safeText(boss.leggings, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "leggings", returnPage));
            menu.setButton(15, guiItem(Items.IRON_BOOTS, ChatFormatting.WHITE + "Boots", safeText(boss.boots, "Vide")), sp -> beginBossEquipEdit(sp, dungeon.id, boss.id, "boots", returnPage));
            menu.setButton(22, guiItem(Items.ARMOR_STAND, ChatFormatting.AQUA + "Copier Equip Joueur", "Copie ton equipement actuel"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                if (current != null) {
                    current.mainHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    current.offHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    current.helmet = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.HEAD);
                    current.chestplate = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.CHEST);
                    current.leggings = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.LEGS);
                    current.boots = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.FEET);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossEquipmentGui(sp, dungeon.id, boss.id, returnPage);
            });
            return menu;
        }, Component.literal("Equip Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openBossAttributesGui(ServerPlayer player, String dungeonId, String bossId, int page) {
        openBossAttributesGui(player, dungeonId, bossId, page, 0);
    }

    public static void openBossAttributesGui(ServerPlayer player, String dungeonId, String bossId, int page, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        List<Map.Entry<String, Double>> entries = filterNonCombatAttributes(boss.customAttributes);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail boss"), sp -> openBossDetailGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <attribute> <value>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<attribute> <value>' pour le boss", (playerInput, input) -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 2) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <attribute> <value>").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        try {
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                            if (current == null) return false;
                            applyBossAttributeValue(current, parts[0], Double.parseDouble(parts[1]));
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Attribut du boss mis a jour.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openBossAttributesGui(reopen, dungeon.id, boss.id, page, returnPage)));
            int pageSize = 18;
            int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int slot = 18;
            for (Map.Entry<String, Double> entry : entries.subList(start, end)) {
                String key = entry.getKey();
                menu.setButton(slot++, guiItem(Items.BREWING_STAND, ChatFormatting.WHITE + key, "Valeur: " + entry.getValue(), "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                    if (current != null) {
                        removeBossAttributeValue(current, key);
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openBossAttributesGui(sp, dungeon.id, boss.id, safePage, returnPage);
                });
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openBossAttributesGui(sp, dungeon.id, boss.id, nextPage, returnPage));
            return menu;
        }, Component.literal("Attributs Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openBossCombatGui(ServerPlayer player, String dungeonId, String bossId) {
        openBossCombatGui(player, dungeonId, bossId, 0);
    }

    public static void openBossCombatGui(ServerPlayer player, String dungeonId, String bossId, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail boss"), sp -> openBossDetailGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Attack Range", String.valueOf(effectiveBossDouble(boss, CombatTuning.KEY_ATTACK_RANGE, boss.attackRange))), sp -> beginBossDirectCombatDoubleEdit(sp, dungeon.id, boss.id, "attack range", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_ATTACK_RANGE, value), returnPage));
            menu.setButton(11, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Attack CD", String.valueOf(effectiveBossInt(boss, CombatTuning.KEY_ATTACK_COOLDOWN_MS, boss.attackCooldownMs))), sp -> beginBossDirectCombatIntEdit(sp, dungeon.id, boss.id, "attack cooldown ms", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_ATTACK_COOLDOWN_MS, value), returnPage));
            menu.setButton(12, guiItem(Items.COMPASS, ChatFormatting.WHITE + "Aggro Range", String.valueOf(effectiveBossDouble(boss, CombatTuning.KEY_AGGRO_RANGE, boss.aggroRange))), sp -> beginBossDirectCombatDoubleEdit(sp, dungeon.id, boss.id, "aggro range", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_AGGRO_RANGE, value), returnPage));
            menu.setButton(13, guiItem(Items.BOW, ChatFormatting.WHITE + "Projectile CD", String.valueOf(effectiveBossInt(boss, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, boss.projectileCooldownMs))), sp -> beginBossDirectCombatIntEdit(sp, dungeon.id, boss.id, "projectile cooldown ms", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, value), returnPage));
            menu.setButton(14, guiItem(Items.FEATHER, ChatFormatting.WHITE + "Dodge Chance", String.valueOf(effectiveBossDouble(boss, CombatTuning.KEY_DODGE_CHANCE, boss.dodgeChance))), sp -> beginBossDirectCombatDoubleEdit(sp, dungeon.id, boss.id, "dodge chance (0-1)", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_DODGE_CHANCE, value), returnPage));
            menu.setButton(15, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Dodge CD", String.valueOf(effectiveBossInt(boss, CombatTuning.KEY_DODGE_COOLDOWN_MS, boss.dodgeCooldownMs))), sp -> beginBossDirectCombatIntEdit(sp, dungeon.id, boss.id, "dodge cooldown ms", (current, value) -> applyBossAttributeValue(current, CombatTuning.KEY_DODGE_COOLDOWN_MS, value), returnPage));
            menu.setButton(16, guiItem(effectiveBossBool(boss, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, boss.dodgeProjectilesOnly) ? Items.SPECTRAL_ARROW : Items.ARROW, ChatFormatting.WHITE + "Dodge Projectiles Only", String.valueOf(effectiveBossBool(boss, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, boss.dodgeProjectilesOnly))), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                if (current != null) {
                    applyBossAttributeValue(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, effectiveBossBool(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, current.dodgeProjectilesOnly) ? 0.0D : 1.0D);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossCombatGui(sp, dungeon.id, boss.id, returnPage);
            });
            menu.setButton(22, guiItem(Items.WRITABLE_BOOK, ChatFormatting.WHITE + "Dodge Message", trimLore(boss.dodgeMessage)), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le dodge message", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                        if (current == null) return false;
                        current.dodgeMessage = input;
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Dodge message du boss mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openBossCombatGui(reopen, dungeon.id, boss.id, returnPage)));
            return menu;
        }, Component.literal("Combat Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openBossRewardsGui(ServerPlayer player, String dungeonId, String bossId, int page) {
        openBossRewardsGui(player, dungeonId, bossId, page, 0);
    }

    public static void openBossRewardsGui(ServerPlayer player, String dungeonId, String bossId, int page, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail boss"), sp -> openBossDetailGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(10, guiItem(Items.CHEST, ChatFormatting.GREEN + "Ajouter Reward Item", "Format: <item> <count> <chance>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<item> <count> <chance>'", (playerInput, input) -> applyRewardInputToBoss(playerInput, dungeon.id, boss.id, input), reopen -> openBossRewardsGui(reopen, dungeon.id, boss.id, page, returnPage)));
            menu.setButton(12, guiItem(Items.BARRIER, ChatFormatting.RED + "Vider Rewards", "Supprimer tous les rewards"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                if (current != null) {
                    current.rewards.clear();
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossRewardsGui(sp, dungeon.id, boss.id, 0, returnPage);
            });
            int pageSize = 18;
            int totalPages = Math.max(1, (boss.rewards.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(boss.rewards.size(), start + pageSize);
            int slot = 18;
            for (int i = start; i < end; i++) {
                RewardConfig reward = boss.rewards.get(i);
                int rewardIndex = i;
                menu.setButton(slot++, guiItem(Items.PAPER, ChatFormatting.WHITE + buildRewardSummary(reward), "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                    if (current != null && rewardIndex >= 0 && rewardIndex < current.rewards.size()) {
                        current.rewards.remove(rewardIndex);
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openBossRewardsGui(sp, dungeon.id, boss.id, safePage, returnPage);
                });
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openBossRewardsGui(sp, dungeon.id, boss.id, nextPage, returnPage));
            return menu;
        }, Component.literal("Rewards Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openBossPhasesGui(ServerPlayer player, String dungeonId, String bossId, int page) {
        openBossPhasesGui(player, dungeonId, bossId, page, 0);
    }

    public static void openBossPhasesGui(ServerPlayer player, String dungeonId, String bossId, int page, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        if (dungeon == null || boss == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        List<PhaseConfig> phases = new ArrayList<>(boss.phases);
        phases.sort(Comparator.comparingInt(pcfg -> pcfg.phase));
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail boss"), sp -> openBossDetailGui(sp, dungeon.id, boss.id, returnPage));
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter Phase", "Format: <numero> <threshold>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<numero> <threshold>' pour ajouter une phase", (playerInput, input) -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 2) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <numero> <threshold>").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        try {
                            int localPhaseNum = Integer.parseInt(parts[0]);
                            double threshold = Double.parseDouble(parts[1]);
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            BossConfig current = cfg == null ? null : findBoss(cfg, boss.id);
                            if (current == null) return false;
                            if (findPhase(current, localPhaseNum) != null) {
                                playerInput.sendSystemMessage(Component.literal("[Arcadia] Cette phase existe deja.").withStyle(ChatFormatting.RED));
                                return false;
                            }
                            PhaseConfig phaseConfig = new PhaseConfig(localPhaseNum, threshold);
                            phaseConfig.description = "Phase " + localPhaseNum;
                            current.phases.add(phaseConfig);
                            current.phases.sort(Comparator.comparingInt(pcfg -> pcfg.phase));
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Phase ajoutee.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openBossPhasesGui(reopen, dungeon.id, boss.id, page, returnPage)));
            int pageSize = 18;
            int totalPages = Math.max(1, (phases.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(phases.size(), start + pageSize);
            int slot = 18;
            for (PhaseConfig phase : phases.subList(start, end)) {
                int localPhaseNum = phase.phase;
                menu.setButton(slot++, guiItem(Items.PAPER, ChatFormatting.WHITE + "Phase " + phase.phase, "Threshold: " + phase.healthThreshold, "Summons: " + (phase.summonMobs == null ? 0 : phase.summonMobs.size()), "Cliquer pour ouvrir"), sp -> openBossPhaseDetailGui(sp, dungeon.id, boss.id, localPhaseNum, 0, safePage));
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openBossPhasesGui(sp, dungeon.id, boss.id, nextPage, returnPage));
            return menu;
        }, Component.literal("Phases Boss: " + safeText(boss.customName, boss.id))));
    }

    public static void openBossPhaseDetailGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int page) {
        openBossPhaseDetailGui(player, dungeonId, bossId, phaseNum, page, 0);
    }

    public static void openBossPhaseDetailGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int page, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        if (dungeon == null || boss == null || phase == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Phase introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        List<PhaseDetailEntry> entries = buildPhaseDetailEntries(dungeon.id, boss.id, phaseNum, phase, page);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 6);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour liste phases"), sp -> openBossPhasesGui(sp, dungeon.id, boss.id, returnPage, returnPage));
            menu.setButton(10, guiItem(Items.NAME_TAG, ChatFormatting.WHITE + "Description", safeText(phase.description, "Aucune")), sp -> beginPhaseStringEdit(sp, dungeon.id, boss.id, phaseNum, "Entre la description de phase", (current, value) -> current.description = value, page, returnPage));
            menu.setButton(11, guiItem(Items.REDSTONE, ChatFormatting.WHITE + "Threshold", String.valueOf(phase.healthThreshold)), sp -> beginPhaseDoubleEdit(sp, dungeon.id, boss.id, phaseNum, "Entre le threshold de phase", (current, value) -> current.healthThreshold = value, page, returnPage));
            menu.setButton(12, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Damage Mult", String.valueOf(phase.damageMultiplier)), sp -> beginPhaseDoubleEdit(sp, dungeon.id, boss.id, phaseNum, "Entre le multiplicateur de degats", (current, value) -> current.damageMultiplier = value, page, returnPage));
            menu.setButton(13, guiItem(Items.SUGAR, ChatFormatting.WHITE + "Speed Mult", String.valueOf(phase.speedMultiplier)), sp -> beginPhaseDoubleEdit(sp, dungeon.id, boss.id, phaseNum, "Entre le multiplicateur de vitesse", (current, value) -> current.speedMultiplier = value, page, returnPage));
            menu.setButton(14, guiItem(Items.WRITABLE_BOOK, ChatFormatting.WHITE + "Message", trimLore(phase.phaseStartMessage)), sp -> beginPhaseStringEdit(sp, dungeon.id, boss.id, phaseNum, "Entre le message de debut de phase", (current, value) -> current.phaseStartMessage = value, page, returnPage));
            menu.setButton(15, guiItem(Items.LEVER, ChatFormatting.WHITE + "Required Action", safeText(phase.requiredAction, "NONE")), sp -> beginPhaseStringEdit(sp, dungeon.id, boss.id, phaseNum, "Entre la required action", (current, value) -> current.requiredAction = value, page, returnPage));
            menu.setButton(16, guiItem(phase.invulnerableDuringTransition ? Items.LIME_DYE : Items.GRAY_DYE, ChatFormatting.WHITE + "Invulnerable", String.valueOf(phase.invulnerableDuringTransition)), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                PhaseConfig current = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                if (current != null) {
                    current.invulnerableDuringTransition = !current.invulnerableDuringTransition;
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossPhaseDetailGui(sp, dungeon.id, boss.id, phaseNum, page, returnPage);
            });
            menu.setButton(19, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Transition", String.valueOf(phase.transitionDurationSeconds)), sp -> beginPhaseDoubleEdit(sp, dungeon.id, boss.id, phaseNum, "Entre la duree de transition en secondes", (current, value) -> current.transitionDurationSeconds = value, page, returnPage));
            menu.setButton(20, guiItem(Items.TOTEM_OF_UNDYING, ChatFormatting.WHITE + "Immunity", String.valueOf(phase.immunityDuration)), sp -> beginPhaseDoubleEdit(sp, dungeon.id, boss.id, phaseNum, "Entre la duree d'immunite en secondes", (current, value) -> current.immunityDuration = value, page, returnPage));
            menu.setButton(21, guiItem(Items.SPAWNER, ChatFormatting.GREEN + "Ajouter Summon", "Format: <entityType> <count>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<entityType> <count>'", (playerInput, input) -> applyPhaseSummonInput(playerInput, dungeon.id, boss.id, phaseNum, input), reopen -> openBossPhaseDetailGui(reopen, dungeon.id, boss.id, phaseNum, page, returnPage)));
            menu.setButton(22, guiItem(Items.POTION, ChatFormatting.AQUA + "Ajouter Effet", "Format: <effect> <duration> <amplifier>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<effect> <duration> <amplifier>'", (playerInput, input) -> applyPhaseEffectInput(playerInput, dungeon.id, boss.id, phaseNum, input), reopen -> openBossPhaseDetailGui(reopen, dungeon.id, boss.id, phaseNum, page, returnPage)));
            menu.setButton(23, guiItem(Items.COMMAND_BLOCK, ChatFormatting.GOLD + "Ajouter Commande", "Commande phase"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre la commande de phase", (playerInput, input) -> applyPhaseCommandInput(playerInput, dungeon.id, boss.id, phaseNum, input), reopen -> openBossPhaseDetailGui(reopen, dungeon.id, boss.id, phaseNum, page, returnPage)));
            menu.setButton(24, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Phase", "Phase " + phaseNum), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                if (currentBoss != null) {
                    currentBoss.phases.removeIf(entry -> entry != null && entry.phase == phaseNum);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openBossPhasesGui(sp, dungeon.id, boss.id, returnPage, returnPage);
            });
            int summonCount = phase.summonMobs == null ? 0 : phase.summonMobs.size();
            int effectCount = phase.playerEffects == null ? 0 : phase.playerEffects.size();
            int commandCount = phase.phaseCommands == null ? 0 : phase.phaseCommands.size();
            menu.setButton(31, guiItem(Items.PAPER, ChatFormatting.WHITE + "Summons", String.valueOf(summonCount), "Cliquer sur une entree plus bas pour supprimer"), null);
            menu.setButton(32, guiItem(Items.PAPER, ChatFormatting.WHITE + "Effects", String.valueOf(effectCount), "Cliquer sur une entree plus bas pour supprimer"), null);
            menu.setButton(33, guiItem(Items.PAPER, ChatFormatting.WHITE + "Commands", String.valueOf(commandCount), "Cliquer sur une entree plus bas pour supprimer"), null);
            int pageSize = 9;
            int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int slot = 36;
            for (PhaseDetailEntry entry : entries.subList(start, end)) {
                menu.setButton(slot++, guiItem(entry.item(), entry.title(), entry.lore(), entry.clickHint()), entry.clickAction());
            }
            AdminGuiSupport.addPagination(menu, 45, 49, 53, safePage, totalPages, (sp, nextPage) -> openBossPhaseDetailGui(sp, dungeon.id, boss.id, phaseNum, nextPage, returnPage));
            return menu;
        }, Component.literal("Phase " + phaseNum + " - " + safeText(boss.customName, boss.id))));
    }

    private record PhaseDetailEntry(net.minecraft.world.item.Item item, String title, String lore, String clickHint, Consumer<ServerPlayer> clickAction) {}

    private static List<PhaseDetailEntry> buildPhaseDetailEntries(String dungeonId, String bossId, int phaseNum, PhaseConfig phase, int pageNum) {
        List<PhaseDetailEntry> entries = new ArrayList<>();
        if (phase.summonMobs != null) {
            for (int i = 0; i < phase.summonMobs.size(); i++) {
                int summonIndex = i;
                SummonConfig summon = phase.summonMobs.get(i);
                entries.add(new PhaseDetailEntry(
                        Items.SPAWNER,
                        ChatFormatting.GREEN + "Summon #" + summonIndex,
                        safeText(summon.entityType, "inconnu") + " x" + summon.count,
                        "Cliquer pour ouvrir",
                        sp -> openPhaseSummonDetailGui(sp, dungeonId, bossId, phaseNum, summonIndex, pageNum)
                ));
            }
        }
        if (phase.playerEffects != null) {
            for (int i = 0; i < phase.playerEffects.size(); i++) {
                int effectIndex = i;
                PhaseConfig.PhaseEffect effect = phase.playerEffects.get(i);
                entries.add(new PhaseDetailEntry(
                        Items.POTION,
                        ChatFormatting.AQUA + "Effet #" + effectIndex,
                        safeText(effect.effect, "inconnu") + " " + effect.durationSeconds + "s amp " + effect.amplifier,
                        "Cliquer pour supprimer",
                        sp -> {
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
                            BossConfig currentBoss = cfg == null ? null : findBoss(cfg, bossId);
                            PhaseConfig current = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                            if (current != null && current.playerEffects != null && effectIndex >= 0 && effectIndex < current.playerEffects.size()) {
                                current.playerEffects.remove(effectIndex);
                                ConfigManager.getInstance().saveDungeon(cfg);
                            }
                        }
                ));
            }
        }
        if (phase.phaseCommands != null) {
            for (int i = 0; i < phase.phaseCommands.size(); i++) {
                int commandIndex = i;
                String command = phase.phaseCommands.get(i);
                entries.add(new PhaseDetailEntry(
                        Items.COMMAND_BLOCK,
                        ChatFormatting.GOLD + "Commande #" + commandIndex,
                        trimLore(command),
                        "Cliquer pour supprimer",
                        sp -> {
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
                            BossConfig currentBoss = cfg == null ? null : findBoss(cfg, bossId);
                            PhaseConfig current = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                            if (current != null && current.phaseCommands != null && commandIndex >= 0 && commandIndex < current.phaseCommands.size()) {
                                current.phaseCommands.remove(commandIndex);
                                ConfigManager.getInstance().saveDungeon(cfg);
                            }
                        }
                ));
            }
        }
        return entries;
    }

    public static void openPhaseSummonDetailGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex) {
        openPhaseSummonDetailGui(player, dungeonId, bossId, phaseNum, summonIndex, 0);
    }

    public static void openPhaseSummonDetailGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        SummonConfig summon = phase == null ? null : findSummon(phase, summonIndex);
        if (dungeon == null || boss == null || phase == null || summon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Summon introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail phase"), sp -> openBossPhaseDetailGui(sp, dungeon.id, boss.id, phaseNum, returnPage));
            menu.setButton(10, guiItem(Items.SPAWNER, ChatFormatting.WHITE + "Entity Type", summon.entityType), sp -> beginPhaseSummonStringEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le nouvel entity type du summon", (current, value) -> current.entityType = value, "[Arcadia] Entity type du summon mis a jour.", (reopenPlayer, rd, rb, rp, rs) -> openPhaseSummonDetailGui(reopenPlayer, rd, rb, rp, rs, returnPage)));
            menu.setButton(11, guiItem(Items.NAME_TAG, ChatFormatting.WHITE + "Nom", safeText(summon.customName, "Aucun")), sp -> beginPhaseSummonStringEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le nom du summon", (current, value) -> current.customName = value, "[Arcadia] Nom du summon mis a jour.", (reopenPlayer, rd, rb, rp, rs) -> openPhaseSummonDetailGui(reopenPlayer, rd, rb, rp, rs, returnPage)));
            menu.setButton(12, guiItem(Items.ZOMBIE_HEAD, ChatFormatting.WHITE + "Count", String.valueOf(summon.count)), sp -> beginPhaseSummonIntEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le count du summon", (current, value) -> current.count = Math.max(1, value), "[Arcadia] Count du summon mis a jour.", (reopenPlayer, rd, rb, rp, rs) -> openPhaseSummonDetailGui(reopenPlayer, rd, rb, rp, rs, returnPage)));
            menu.setButton(13, guiItem(Items.EXPERIENCE_BOTTLE, ChatFormatting.WHITE + "HP", String.valueOf(summon.health)), sp -> beginPhaseSummonDoubleEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre les PV du summon", (current, value) -> current.health = Math.max(1.0, value), "[Arcadia] PV du summon mis a jour.", (reopenPlayer, rd, rb, rp, rs) -> openPhaseSummonDetailGui(reopenPlayer, rd, rb, rp, rs, returnPage)));
            menu.setButton(14, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Damage", String.valueOf(summon.damage)), sp -> beginPhaseSummonDoubleEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre les degats du summon", (current, value) -> current.damage = Math.max(0.0, value), "[Arcadia] Degats du summon mis a jour.", (reopenPlayer, rd, rb, rp, rs) -> openPhaseSummonDetailGui(reopenPlayer, rd, rb, rp, rs, returnPage)));
            menu.setButton(15, guiItem(Items.RECOVERY_COMPASS, ChatFormatting.GREEN + "Spawn", formatSpawnSummary(summon.spawnPoint), "Cliquer pour definir a ta position"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                SummonConfig current = currentPhase == null ? null : findSummon(currentPhase, summonIndex);
                if (current != null) {
                    current.spawnPoint = new SpawnPointConfig(sp.level().dimension().location().toString(), sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openPhaseSummonDetailGui(sp, dungeon.id, boss.id, phaseNum, summonIndex, returnPage);
            });
            menu.setButton(19, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.AQUA + "Equipement", "Configurer l'equipement du summon"), sp -> openPhaseSummonEquipmentGui(sp, dungeon.id, boss.id, phaseNum, summonIndex));
            menu.setButton(20, guiItem(Items.BREWING_STAND, ChatFormatting.GREEN + "Attributs", String.valueOf(summon.customAttributes == null ? 0 : summon.customAttributes.size())), sp -> openPhaseSummonAttributesGui(sp, dungeon.id, boss.id, phaseNum, summonIndex, 0));
            menu.setButton(21, guiItem(Items.CROSSBOW, ChatFormatting.GOLD + "Combat", "Attack range, cooldown, dodge..."), sp -> openPhaseSummonCombatGui(sp, dungeon.id, boss.id, phaseNum, summonIndex));
            menu.setButton(23, guiItem(Items.LAVA_BUCKET, ChatFormatting.RED + "Supprimer Summon", safeText(summon.entityType, "summon")), sp ->
                    AdminGuiSupport.openConfirmationGui(sp, "Confirmer Suppression Summon", safeText(summon.entityType, "summon"), confirm -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                        PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                        if (currentPhase != null && currentPhase.summonMobs != null && summonIndex >= 0 && summonIndex < currentPhase.summonMobs.size()) {
                            currentPhase.summonMobs.remove(summonIndex);
                            ConfigManager.getInstance().saveDungeon(cfg);
                        }
                        openBossPhaseDetailGui(confirm, dungeon.id, boss.id, phaseNum, returnPage);
                    }, cancel -> openPhaseSummonDetailGui(cancel, dungeon.id, boss.id, phaseNum, summonIndex, returnPage)));
            return menu;
        }, Component.literal("Summon " + summonIndex + " - " + safeText(summon.entityType, "summon"))));
    }

    public static void openPhaseSummonEquipmentGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        SummonConfig summon = phase == null ? null : findSummon(phase, summonIndex);
        if (dungeon == null || boss == null || phase == null || summon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Summon introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail summon"), sp -> openPhaseSummonDetailGui(sp, dungeon.id, boss.id, phaseNum, summonIndex));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Main Hand", safeText(summon.mainHand, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "mainhand"));
            menu.setButton(11, guiItem(Items.SHIELD, ChatFormatting.WHITE + "Off Hand", safeText(summon.offHand, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "offhand"));
            menu.setButton(12, guiItem(Items.IRON_HELMET, ChatFormatting.WHITE + "Helmet", safeText(summon.helmet, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "helmet"));
            menu.setButton(13, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.WHITE + "Chestplate", safeText(summon.chestplate, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "chestplate"));
            menu.setButton(14, guiItem(Items.IRON_LEGGINGS, ChatFormatting.WHITE + "Leggings", safeText(summon.leggings, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "leggings"));
            menu.setButton(15, guiItem(Items.IRON_BOOTS, ChatFormatting.WHITE + "Boots", safeText(summon.boots, "Vide")), sp -> beginPhaseSummonEquipEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "boots"));
            menu.setButton(22, guiItem(Items.ARMOR_STAND, ChatFormatting.AQUA + "Copier Equip Joueur", "Copie ton equipement actuel"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                SummonConfig current = currentPhase == null ? null : findSummon(currentPhase, summonIndex);
                if (current != null) {
                    current.mainHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    current.offHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    current.helmet = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.HEAD);
                    current.chestplate = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.CHEST);
                    current.leggings = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.LEGS);
                    current.boots = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.FEET);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openPhaseSummonEquipmentGui(sp, dungeon.id, boss.id, phaseNum, summonIndex);
            });
            return menu;
        }, Component.literal("Equip Summon " + summonIndex)));
    }

    public static void openPhaseSummonAttributesGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, int page) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        SummonConfig summon = phase == null ? null : findSummon(phase, summonIndex);
        if (dungeon == null || boss == null || phase == null || summon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Summon introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        if (summon.customAttributes == null) {
            summon.customAttributes = new HashMap<>();
        }
        List<Map.Entry<String, Double>> entries = filterNonCombatAttributes(summon.customAttributes);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 5);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail summon"), sp -> openPhaseSummonDetailGui(sp, dungeon.id, boss.id, phaseNum, summonIndex));
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <attribute> <value>"), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre '<attribute> <value>' pour le summon", (playerInput, input) -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 2) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <attribute> <value>").withStyle(ChatFormatting.RED));
                            return false;
                        }
                        try {
                            double value = Double.parseDouble(parts[1]);
                            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                            BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                            PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                            SummonConfig current = currentPhase == null ? null : findSummon(currentPhase, summonIndex);
                            if (current == null) return false;
                            if (current.customAttributes == null) {
                                current.customAttributes = new HashMap<>();
                            }
                            applySummonAttributeValue(current, parts[0], value);
                            ConfigManager.getInstance().saveDungeon(cfg);
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Attribut du summon mis a jour.").withStyle(ChatFormatting.GREEN));
                            return true;
                        } catch (NumberFormatException e) {
                            playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                            return false;
                        }
                    }, reopen -> openPhaseSummonAttributesGui(reopen, dungeon.id, boss.id, phaseNum, summonIndex, page)));
            int pageSize = 18;
            int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int slot = 18;
            for (Map.Entry<String, Double> entry : entries.subList(start, end)) {
                String key = entry.getKey();
                menu.setButton(slot++, guiItem(Items.BREWING_STAND, ChatFormatting.WHITE + key, String.valueOf(entry.getValue()), "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                    PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                    SummonConfig current = currentPhase == null ? null : findSummon(currentPhase, summonIndex);
                    if (current != null) {
                        removeSummonAttributeValue(current, key);
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openPhaseSummonAttributesGui(sp, dungeon.id, boss.id, phaseNum, summonIndex, safePage);
                });
            }
            AdminGuiSupport.addPagination(menu, 36, 40, 44, safePage, totalPages, (sp, nextPage) -> openPhaseSummonAttributesGui(sp, dungeon.id, boss.id, phaseNum, summonIndex, nextPage));
            return menu;
        }, Component.literal("Attrs Summon " + summonIndex)));
    }

    public static void openPhaseSummonCombatGui(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = dungeon == null ? null : findBoss(dungeon, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        SummonConfig summon = phase == null ? null : findSummon(phase, summonIndex);
        if (dungeon == null || boss == null || phase == null || summon == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Summon introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail summon"), sp -> openPhaseSummonDetailGui(sp, dungeon.id, boss.id, phaseNum, summonIndex));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Attack Range", String.valueOf(effectiveSummonDouble(summon, CombatTuning.KEY_ATTACK_RANGE, summon.attackRange))), sp -> beginPhaseSummonDoubleEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre l'attack range du summon", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_ATTACK_RANGE, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(11, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Attack CD", String.valueOf(effectiveSummonInt(summon, CombatTuning.KEY_ATTACK_COOLDOWN_MS, summon.attackCooldownMs))), sp -> beginPhaseSummonIntEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre l'attack cooldown ms du summon", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_ATTACK_COOLDOWN_MS, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(12, guiItem(Items.COMPASS, ChatFormatting.WHITE + "Aggro Range", String.valueOf(effectiveSummonDouble(summon, CombatTuning.KEY_AGGRO_RANGE, summon.aggroRange))), sp -> beginPhaseSummonDoubleEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre l'aggro range du summon", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_AGGRO_RANGE, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(13, guiItem(Items.BOW, ChatFormatting.WHITE + "Projectile CD", String.valueOf(effectiveSummonInt(summon, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, summon.projectileCooldownMs))), sp -> beginPhaseSummonIntEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le projectile cooldown ms du summon", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(14, guiItem(Items.FEATHER, ChatFormatting.WHITE + "Dodge Chance", String.valueOf(effectiveSummonDouble(summon, CombatTuning.KEY_DODGE_CHANCE, summon.dodgeChance))), sp -> beginPhaseSummonDoubleEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre la dodge chance du summon (0-1)", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_DODGE_CHANCE, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(15, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Dodge CD", String.valueOf(effectiveSummonInt(summon, CombatTuning.KEY_DODGE_COOLDOWN_MS, summon.dodgeCooldownMs))), sp -> beginPhaseSummonIntEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le dodge cooldown ms du summon", (current, value) -> applySummonAttributeValue(current, CombatTuning.KEY_DODGE_COOLDOWN_MS, value), "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            menu.setButton(16, guiItem(effectiveSummonBool(summon, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, summon.dodgeProjectilesOnly) ? Items.SPECTRAL_ARROW : Items.ARROW, ChatFormatting.WHITE + "Dodge Projectiles Only", String.valueOf(effectiveSummonBool(summon, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, summon.dodgeProjectilesOnly))), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                BossConfig currentBoss = cfg == null ? null : findBoss(cfg, boss.id);
                PhaseConfig currentPhase = currentBoss == null ? null : findPhase(currentBoss, phaseNum);
                SummonConfig current = currentPhase == null ? null : findSummon(currentPhase, summonIndex);
                if (current != null) {
                    applySummonAttributeValue(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, effectiveSummonBool(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, current.dodgeProjectilesOnly) ? 0.0D : 1.0D);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openPhaseSummonCombatGui(sp, dungeon.id, boss.id, phaseNum, summonIndex);
            });
            menu.setButton(22, guiItem(Items.WRITABLE_BOOK, ChatFormatting.WHITE + "Dodge Message", trimLore(summon.dodgeMessage)), sp -> beginPhaseSummonStringEdit(sp, dungeon.id, boss.id, phaseNum, summonIndex, "Entre le dodge message du summon", (current, value) -> current.dodgeMessage = value, "[Arcadia] Combat summon mis a jour.", AdminEntityGuiMenus::openPhaseSummonCombatGui));
            return menu;
        }, Component.literal("Combat Summon " + summonIndex)));
    }
    public static void openWaveMobEquipmentGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex) {
        openWaveMobEquipmentGui(player, dungeonId, waveNumber, mobIndex, 0);
    }

    public static void openWaveMobEquipmentGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        WaveConfig wave = dungeon == null ? null : findWave(dungeon, waveNumber);
        MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
        if (dungeon == null || wave == null || mob == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail mob"), sp -> openWaveMobDetailGui(sp, dungeon.id, waveNumber, mobIndex, returnPage));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Main Hand", safeText(mob.mainHand, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "mainhand", returnPage));
            menu.setButton(11, guiItem(Items.SHIELD, ChatFormatting.WHITE + "Off Hand", safeText(mob.offHand, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "offhand", returnPage));
            menu.setButton(12, guiItem(Items.IRON_HELMET, ChatFormatting.WHITE + "Helmet", safeText(mob.helmet, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "helmet", returnPage));
            menu.setButton(13, guiItem(Items.IRON_CHESTPLATE, ChatFormatting.WHITE + "Chestplate", safeText(mob.chestplate, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "chestplate", returnPage));
            menu.setButton(14, guiItem(Items.IRON_LEGGINGS, ChatFormatting.WHITE + "Leggings", safeText(mob.leggings, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "leggings", returnPage));
            menu.setButton(15, guiItem(Items.IRON_BOOTS, ChatFormatting.WHITE + "Boots", safeText(mob.boots, "Vide")), sp -> beginWaveMobEquipEdit(sp, dungeon.id, waveNumber, mobIndex, "boots", returnPage));
            menu.setButton(22, guiItem(Items.ARMOR_STAND, ChatFormatting.AQUA + "Copier Equip Joueur", "Copie ton equipement actuel"), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                if (current != null) {
                    current.mainHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    current.offHand = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                    current.helmet = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.HEAD);
                    current.chestplate = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.CHEST);
                    current.leggings = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.LEGS);
                    current.boots = AdminGuiRouter.getItemId(sp, net.minecraft.world.entity.EquipmentSlot.FEET);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWaveMobEquipmentGui(sp, dungeon.id, waveNumber, mobIndex, returnPage);
            });
            return menu;
        }, Component.literal("Equip Mob " + mobIndex)));
    }

    public static void openWaveMobAttributesGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, int page) {
        openWaveMobAttributesGui(player, dungeonId, waveNumber, mobIndex, page, 0);
    }

    public static void openWaveMobAttributesGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, int page, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        WaveConfig wave = dungeon == null ? null : findWave(dungeon, waveNumber);
        MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
        if (dungeon == null || wave == null || mob == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(mob.customAttributes.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 4);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail mob"), sp -> openWaveMobDetailGui(sp, dungeon.id, waveNumber, mobIndex, returnPage));
            menu.setButton(10, guiItem(Items.EMERALD, ChatFormatting.GREEN + "Ajouter / Modifier", "Format: <attribute> <value>"), sp ->
                    AdminGuiSupport.beginPrompt(
                            sp,
                            "Entre '<attribute> <value>' pour le mob",
                            (playerInput, input) -> {
                                String[] parts = input.trim().split("\\s+");
                                if (parts.length != 2) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <attribute> <value>").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                                try {
                                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                                    WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                                    MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                                    if (current == null) return false;
                                    applyWaveMobAttributeValue(current, parts[0], Double.parseDouble(parts[1]));
                                    ConfigManager.getInstance().saveDungeon(cfg);
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Attribut du mob mis a jour.").withStyle(ChatFormatting.GREEN));
                                    return true;
                                } catch (NumberFormatException e) {
                                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Valeur invalide.").withStyle(ChatFormatting.RED));
                                    return false;
                                }
                            },
                            reopen -> openWaveMobAttributesGui(reopen, dungeon.id, waveNumber, mobIndex, page, returnPage)
                    ));
            int pageSize = 18;
            int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
            int safePage = Math.max(0, Math.min(page, totalPages - 1));
            int start = safePage * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int slot = 18;
            for (Map.Entry<String, Double> entry : entries.subList(start, end)) {
                String key = entry.getKey();
                menu.setButton(slot++, guiItem(Items.BREWING_STAND, ChatFormatting.WHITE + key, "Valeur: " + entry.getValue(), "Cliquer pour supprimer"), sp -> {
                    DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                    WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                    MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                    if (current != null) {
                        removeWaveMobAttributeValue(current, key);
                        ConfigManager.getInstance().saveDungeon(cfg);
                    }
                    openWaveMobAttributesGui(sp, dungeon.id, waveNumber, mobIndex, safePage, returnPage);
                });
            }
            AdminGuiSupport.addPagination(menu, 27, 31, 35, safePage, totalPages, (sp, nextPage) -> openWaveMobAttributesGui(sp, dungeon.id, waveNumber, mobIndex, nextPage, returnPage));
            return menu;
        }, Component.literal("Attributs Mob " + mobIndex)));
    }

    public static void openWaveMobCombatGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex) {
        openWaveMobCombatGui(player, dungeonId, waveNumber, mobIndex, 0);
    }

    public static void openWaveMobCombatGui(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, int returnPage) {
        DungeonConfig dungeon = ConfigManager.getInstance().getDungeon(dungeonId);
        WaveConfig wave = dungeon == null ? null : findWave(dungeon, waveNumber);
        MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
        if (dungeon == null || wave == null || mob == null) {
            player.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            ArcadiaChestMenu menu = new ArcadiaChestMenu(containerId, inventory, 3);
            menu.setButton(0, guiItem(Items.ARROW, ChatFormatting.YELLOW + "Retour", "Retour detail mob"), sp -> openWaveMobDetailGui(sp, dungeon.id, waveNumber, mobIndex, returnPage));
            menu.setButton(10, guiItem(Items.IRON_SWORD, ChatFormatting.WHITE + "Attack Range", String.valueOf(effectiveWaveMobDouble(mob, CombatTuning.KEY_ATTACK_RANGE, mob.attackRange))), sp -> beginWaveMobCombatDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "attack range", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_ATTACK_RANGE, value), returnPage));
            menu.setButton(11, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Attack CD", String.valueOf(effectiveWaveMobInt(mob, CombatTuning.KEY_ATTACK_COOLDOWN_MS, mob.attackCooldownMs))), sp -> beginWaveMobCombatIntEdit(sp, dungeon.id, waveNumber, mobIndex, "attack cooldown ms", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_ATTACK_COOLDOWN_MS, value), returnPage));
            menu.setButton(12, guiItem(Items.COMPASS, ChatFormatting.WHITE + "Aggro Range", String.valueOf(effectiveWaveMobDouble(mob, CombatTuning.KEY_AGGRO_RANGE, mob.aggroRange))), sp -> beginWaveMobCombatDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "aggro range", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_AGGRO_RANGE, value), returnPage));
            menu.setButton(13, guiItem(Items.BOW, ChatFormatting.WHITE + "Projectile CD", String.valueOf(effectiveWaveMobInt(mob, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, mob.projectileCooldownMs))), sp -> beginWaveMobCombatIntEdit(sp, dungeon.id, waveNumber, mobIndex, "projectile cooldown ms", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_PROJECTILE_COOLDOWN_MS, value), returnPage));
            menu.setButton(14, guiItem(Items.FEATHER, ChatFormatting.WHITE + "Dodge Chance", String.valueOf(effectiveWaveMobDouble(mob, CombatTuning.KEY_DODGE_CHANCE, mob.dodgeChance))), sp -> beginWaveMobCombatDoubleEdit(sp, dungeon.id, waveNumber, mobIndex, "dodge chance (0-1)", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_DODGE_CHANCE, value), returnPage));
            menu.setButton(15, guiItem(Items.CLOCK, ChatFormatting.WHITE + "Dodge CD", String.valueOf(effectiveWaveMobInt(mob, CombatTuning.KEY_DODGE_COOLDOWN_MS, mob.dodgeCooldownMs))), sp -> beginWaveMobCombatIntEdit(sp, dungeon.id, waveNumber, mobIndex, "dodge cooldown ms", (current, value) -> applyWaveMobAttributeValue(current, CombatTuning.KEY_DODGE_COOLDOWN_MS, value), returnPage));
            menu.setButton(16, guiItem(effectiveWaveMobBool(mob, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, mob.dodgeProjectilesOnly) ? Items.SPECTRAL_ARROW : Items.ARROW, ChatFormatting.WHITE + "Dodge Projectiles Only", String.valueOf(effectiveWaveMobBool(mob, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, mob.dodgeProjectilesOnly))), sp -> {
                DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                if (current != null) {
                    applyWaveMobAttributeValue(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, effectiveWaveMobBool(current, CombatTuning.KEY_DODGE_PROJECTILES_ONLY, current.dodgeProjectilesOnly) ? 0.0D : 1.0D);
                    ConfigManager.getInstance().saveDungeon(cfg);
                }
                openWaveMobCombatGui(sp, dungeon.id, waveNumber, mobIndex, returnPage);
            });
            menu.setButton(22, guiItem(Items.WRITABLE_BOOK, ChatFormatting.WHITE + "Dodge Message", trimLore(mob.dodgeMessage)), sp ->
                    AdminGuiSupport.beginPrompt(sp, "Entre le dodge message", (playerInput, input) -> {
                        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeon.id);
                        WaveConfig currentWave = cfg == null ? null : findWave(cfg, waveNumber);
                        MobSpawnConfig current = currentWave == null ? null : findMob(currentWave, mobIndex);
                        if (current == null) return false;
                        current.dodgeMessage = input;
                        ConfigManager.getInstance().saveDungeon(cfg);
                        playerInput.sendSystemMessage(Component.literal("[Arcadia] Dodge message du mob mis a jour.").withStyle(ChatFormatting.GREEN));
                        return true;
                    }, reopen -> openWaveMobCombatGui(reopen, dungeon.id, waveNumber, mobIndex, returnPage)));
            return menu;
        }, Component.literal("Combat Mob " + mobIndex)));
    }

    private static ItemStack guiItem(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        return stack;
    }

    private static String safeText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String trimLore(String value) { String text = safeText(value, "Aucun"); return text.length() > 60 ? text.substring(0, 57) + "..." : text; }
    private static String formatSpawnSummary(SpawnPointConfig spawnPoint) { return spawnPoint == null ? "Non defini" : spawnPoint.dimension + " " + (int) spawnPoint.x + " " + (int) spawnPoint.y + " " + (int) spawnPoint.z; }
    private static String buildRewardSummary(RewardConfig reward) {
        if (reward == null) return "Reward vide";
        if (reward.item != null && !reward.item.isBlank()) return reward.item + " x" + reward.count + " (" + reward.chance + ")";
        if (reward.experience > 0) return reward.experience + " XP";
        if (reward.command != null && !reward.command.isBlank()) return "CMD: " + trimLore(reward.command);
        return "Reward vide";
    }
    private static BossConfig findBoss(DungeonConfig config, String bossId) { return config == null || bossId == null ? null : config.bosses.stream().filter(b -> b != null && bossId.equals(b.id)).findFirst().orElse(null); }
    private static PhaseConfig findPhase(BossConfig boss, int phaseNum) { return boss == null || boss.phases == null ? null : boss.phases.stream().filter(p -> p != null && p.phase == phaseNum).findFirst().orElse(null); }
    private static WaveConfig findWave(DungeonConfig config, int waveNumber) { return config == null ? null : config.waves.stream().filter(w -> w != null && w.waveNumber == waveNumber).findFirst().orElse(null); }
    private static MobSpawnConfig findMob(WaveConfig wave, int mobIndex) { return wave == null || wave.mobs == null || mobIndex < 0 || mobIndex >= wave.mobs.size() ? null : wave.mobs.get(mobIndex); }
    private static List<Map.Entry<String, Double>> filterNonCombatAttributes(Map<String, Double> attributes) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>();
        if (attributes == null) return entries;
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            if (entry != null && entry.getKey() != null && !CombatTuning.SPECIAL_KEYS.contains(entry.getKey())) {
                entries.add(entry);
            }
        }
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    private static void applyBossAttributeValue(BossConfig boss, String key, double value) {
        if (boss == null || key == null) return;
        if (boss.customAttributes == null) boss.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> boss.attackRange = Math.max(0.0D, value);
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> boss.attackCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_AGGRO_RANGE -> boss.aggroRange = Math.max(0.0D, value);
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> boss.projectileCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_CHANCE -> boss.dodgeChance = Math.max(0.0D, Math.min(1.0D, value));
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> boss.dodgeCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> boss.dodgeProjectilesOnly = value >= 0.5D;
            default -> boss.customAttributes.put(key, value);
        }
        if (CombatTuning.SPECIAL_KEYS.contains(key)) boss.customAttributes.remove(key);
    }

    private static void removeBossAttributeValue(BossConfig boss, String key) {
        if (boss == null || key == null) return;
        if (boss.customAttributes == null) boss.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> boss.attackRange = 0.0D;
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> boss.attackCooldownMs = 0;
            case CombatTuning.KEY_AGGRO_RANGE -> boss.aggroRange = 0.0D;
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> boss.projectileCooldownMs = 0;
            case CombatTuning.KEY_DODGE_CHANCE -> boss.dodgeChance = 0.0D;
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> boss.dodgeCooldownMs = 0;
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> boss.dodgeProjectilesOnly = false;
            default -> boss.customAttributes.remove(key);
        }
        boss.customAttributes.remove(key);
    }

    private static void applyWaveMobAttributeValue(MobSpawnConfig mob, String key, double value) {
        if (mob == null || key == null) return;
        if (mob.customAttributes == null) mob.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> mob.attackRange = Math.max(0.0D, value);
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> mob.attackCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_AGGRO_RANGE -> mob.aggroRange = Math.max(0.0D, value);
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> mob.projectileCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_CHANCE -> mob.dodgeChance = Math.max(0.0D, Math.min(1.0D, value));
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> mob.dodgeCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> mob.dodgeProjectilesOnly = value >= 0.5D;
            default -> mob.customAttributes.put(key, value);
        }
        if (CombatTuning.SPECIAL_KEYS.contains(key)) mob.customAttributes.remove(key);
    }

    private static void removeWaveMobAttributeValue(MobSpawnConfig mob, String key) {
        if (mob == null || key == null) return;
        if (mob.customAttributes == null) mob.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> mob.attackRange = 0.0D;
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> mob.attackCooldownMs = 0;
            case CombatTuning.KEY_AGGRO_RANGE -> mob.aggroRange = 0.0D;
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> mob.projectileCooldownMs = 0;
            case CombatTuning.KEY_DODGE_CHANCE -> mob.dodgeChance = 0.0D;
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> mob.dodgeCooldownMs = 0;
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> mob.dodgeProjectilesOnly = false;
            default -> mob.customAttributes.remove(key);
        }
        mob.customAttributes.remove(key);
    }

    private static void applySummonAttributeValue(SummonConfig summon, String key, double value) {
        if (summon == null || key == null) return;
        if (summon.customAttributes == null) summon.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> summon.attackRange = Math.max(0.0D, value);
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> summon.attackCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_AGGRO_RANGE -> summon.aggroRange = Math.max(0.0D, value);
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> summon.projectileCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_CHANCE -> summon.dodgeChance = Math.max(0.0D, Math.min(1.0D, value));
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> summon.dodgeCooldownMs = Math.max(0, (int) Math.round(value));
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> summon.dodgeProjectilesOnly = value >= 0.5D;
            default -> summon.customAttributes.put(key, value);
        }
        if (CombatTuning.SPECIAL_KEYS.contains(key)) summon.customAttributes.remove(key);
    }

    private static void removeSummonAttributeValue(SummonConfig summon, String key) {
        if (summon == null || key == null) return;
        if (summon.customAttributes == null) summon.customAttributes = new HashMap<>();
        switch (key) {
            case CombatTuning.KEY_ATTACK_RANGE -> summon.attackRange = 0.0D;
            case CombatTuning.KEY_ATTACK_COOLDOWN_MS -> summon.attackCooldownMs = 0;
            case CombatTuning.KEY_AGGRO_RANGE -> summon.aggroRange = 0.0D;
            case CombatTuning.KEY_PROJECTILE_COOLDOWN_MS -> summon.projectileCooldownMs = 0;
            case CombatTuning.KEY_DODGE_CHANCE -> summon.dodgeChance = 0.0D;
            case CombatTuning.KEY_DODGE_COOLDOWN_MS -> summon.dodgeCooldownMs = 0;
            case CombatTuning.KEY_DODGE_PROJECTILES_ONLY -> summon.dodgeProjectilesOnly = false;
            default -> summon.customAttributes.remove(key);
        }
        summon.customAttributes.remove(key);
    }

    private static double effectiveBossDouble(BossConfig boss, String key, double directValue) { return effectiveDouble(boss == null ? null : boss.customAttributes, key, directValue); }
    private static int effectiveBossInt(BossConfig boss, String key, int directValue) { return effectiveInt(boss == null ? null : boss.customAttributes, key, directValue); }
    private static boolean effectiveBossBool(BossConfig boss, String key, boolean directValue) { return effectiveBool(boss == null ? null : boss.customAttributes, key, directValue); }
    private static double effectiveWaveMobDouble(MobSpawnConfig mob, String key, double directValue) { return effectiveDouble(mob == null ? null : mob.customAttributes, key, directValue); }
    private static int effectiveWaveMobInt(MobSpawnConfig mob, String key, int directValue) { return effectiveInt(mob == null ? null : mob.customAttributes, key, directValue); }
    private static boolean effectiveWaveMobBool(MobSpawnConfig mob, String key, boolean directValue) { return effectiveBool(mob == null ? null : mob.customAttributes, key, directValue); }
    private static double effectiveSummonDouble(SummonConfig summon, String key, double directValue) { return effectiveDouble(summon == null ? null : summon.customAttributes, key, directValue); }
    private static int effectiveSummonInt(SummonConfig summon, String key, int directValue) { return effectiveInt(summon == null ? null : summon.customAttributes, key, directValue); }
    private static boolean effectiveSummonBool(SummonConfig summon, String key, boolean directValue) { return effectiveBool(summon == null ? null : summon.customAttributes, key, directValue); }

    private static double effectiveDouble(Map<String, Double> attributes, String key, double directValue) {
        if (attributes != null && attributes.containsKey(key) && directValue == 0.0D) return attributes.getOrDefault(key, 0.0D);
        return directValue;
    }

    private static int effectiveInt(Map<String, Double> attributes, String key, int directValue) {
        if (attributes != null && attributes.containsKey(key) && directValue == 0) return Math.max(0, (int) Math.round(attributes.getOrDefault(key, 0.0D)));
        return directValue;
    }

    private static boolean effectiveBool(Map<String, Double> attributes, String key, boolean directValue) {
        if (attributes != null && attributes.containsKey(key) && !directValue) return attributes.getOrDefault(key, 0.0D) >= 0.5D;
        return directValue;
    }

    private static void beginWaveMobStringEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String prompt,
                                               java.util.function.BiConsumer<MobSpawnConfig, String> setter,
                                               String successMessage,
                                               int returnPage) {
        AdminGuiSupport.beginPrompt(player, prompt, (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(mob, input);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobDetailGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }

    private static void beginWaveMobIntEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String prompt,
                                            java.util.function.BiConsumer<MobSpawnConfig, Integer> setter,
                                            String successMessage,
                                            int returnPage) {
        AdminGuiSupport.beginIntPrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(mob, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobDetailGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }

    private static void beginWaveMobDoubleEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String prompt,
                                               java.util.function.BiConsumer<MobSpawnConfig, Double> setter,
                                               String successMessage,
                                               int returnPage) {
        AdminGuiSupport.beginDoublePrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            setter.accept(mob, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobDetailGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }

    private static void beginWaveMobEquipEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String slot, int returnPage) {
        AdminGuiSupport.beginPrompt(player, "Entre l'item id pour " + slot + " ou 'clear'", (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Mob introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            String value = input.trim().equalsIgnoreCase("clear") ? "" : input.trim();
            switch (slot) {
                case "mainhand" -> mob.mainHand = value;
                case "offhand" -> mob.offHand = value;
                case "helmet" -> mob.helmet = value;
                case "chestplate" -> mob.chestplate = value;
                case "leggings" -> mob.leggings = value;
                case "boots" -> mob.boots = value;
                default -> {
                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Slot invalide.").withStyle(ChatFormatting.RED));
                    return false;
                }
            }
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Equipement du mob mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobEquipmentGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }
    private static void beginWaveMobCombatIntEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String prompt, java.util.function.BiConsumer<MobSpawnConfig, Integer> setter, int returnPage) {
        AdminGuiSupport.beginIntPrompt(player, "Entre " + prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) return false;
            setter.accept(mob, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Combat du mob mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobCombatGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }
    private static void beginWaveMobCombatDoubleEdit(ServerPlayer player, String dungeonId, int waveNumber, int mobIndex, String prompt, java.util.function.BiConsumer<MobSpawnConfig, Double> setter, int returnPage) {
        AdminGuiSupport.beginDoublePrompt(player, "Entre " + prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            WaveConfig wave = cfg == null ? null : findWave(cfg, waveNumber);
            MobSpawnConfig mob = wave == null ? null : findMob(wave, mobIndex);
            if (mob == null) return false;
            setter.accept(mob, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Combat du mob mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openWaveMobCombatGui(reopen, dungeonId, waveNumber, mobIndex, returnPage));
    }

    private static void toggleBossBool(ServerPlayer player, String dungeonId, String bossId, java.util.function.Consumer<BossConfig> toggle, Runnable reopen) {
        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig current = cfg == null ? null : findBoss(cfg, bossId);
        if (current != null) {
            toggle.accept(current);
            ConfigManager.getInstance().saveDungeon(cfg);
        }
        reopen.run();
    }

    private static void beginBossStringEdit(ServerPlayer player, String dungeonId, String bossId, String prompt,
                                            java.util.function.BiConsumer<BossConfig, String> setter,
                                            String successMessage,
                                            TriConsumer<ServerPlayer, String, String> reopenAction) {
        AdminGuiSupport.beginPrompt(player, prompt, (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            setter.accept(boss, input);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId));
    }

    private static void beginBossIntEdit(ServerPlayer player, String dungeonId, String bossId, String prompt,
                                         java.util.function.BiConsumer<BossConfig, Integer> setter,
                                         String successMessage,
                                         TriConsumer<ServerPlayer, String, String> reopenAction) {
        AdminGuiSupport.beginIntPrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            setter.accept(boss, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId));
    }

    private static void beginBossDoubleEdit(ServerPlayer player, String dungeonId, String bossId, String prompt,
                                            java.util.function.BiConsumer<BossConfig, Double> setter,
                                            String successMessage,
                                            TriConsumer<ServerPlayer, String, String> reopenAction) {
        AdminGuiSupport.beginDoublePrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            setter.accept(boss, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId));
    }

    private static void beginBossEquipEdit(ServerPlayer player, String dungeonId, String bossId, String slot, int returnPage) {
        AdminGuiSupport.beginPrompt(player, "Entre l'item id pour " + slot + " ou 'clear'", (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Boss introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            String value = input.trim().equalsIgnoreCase("clear") ? "" : input.trim();
            switch (slot) {
                case "mainhand" -> boss.mainHand = value;
                case "offhand" -> boss.offHand = value;
                case "helmet" -> boss.helmet = value;
                case "chestplate" -> boss.chestplate = value;
                case "leggings" -> boss.leggings = value;
                case "boots" -> boss.boots = value;
                default -> {
                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Slot invalide.").withStyle(ChatFormatting.RED));
                    return false;
                }
            }
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Equipement du boss mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openBossEquipmentGui(reopen, dungeonId, bossId, returnPage));
    }

    private static void beginBossDirectCombatIntEdit(ServerPlayer player, String dungeonId, String bossId, String prompt,
                                                     java.util.function.BiConsumer<BossConfig, Integer> setter,
                                                     int returnPage) {
        AdminGuiSupport.beginIntPrompt(player, "Entre " + prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            setter.accept(boss, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Combat du boss mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openBossCombatGui(reopen, dungeonId, bossId, returnPage));
    }

    private static void beginBossDirectCombatDoubleEdit(ServerPlayer player, String dungeonId, String bossId, String prompt,
                                                        java.util.function.BiConsumer<BossConfig, Double> setter,
                                                        int returnPage) {
        AdminGuiSupport.beginDoublePrompt(player, "Entre " + prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            setter.accept(boss, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Combat du boss mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openBossCombatGui(reopen, dungeonId, bossId, returnPage));
    }

    private static void beginPhaseStringEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, String prompt,
                                             java.util.function.BiConsumer<PhaseConfig, String> setter,
                                             int page,
                                             int returnPage) {
        AdminGuiSupport.beginPrompt(player, prompt, (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            if (phase == null) return false;
            setter.accept(phase, input);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Phase mise a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openBossPhaseDetailGui(reopen, dungeonId, bossId, phaseNum, page, returnPage));
    }

    private static void beginPhaseDoubleEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, String prompt,
                                             java.util.function.BiConsumer<PhaseConfig, Double> setter,
                                             int page,
                                             int returnPage) {
        AdminGuiSupport.beginDoublePrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            if (phase == null) return false;
            setter.accept(phase, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Phase mise a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openBossPhaseDetailGui(reopen, dungeonId, bossId, phaseNum, page, returnPage));
    }

    private static boolean applyPhaseSummonInput(ServerPlayer player, String dungeonId, String bossId, int phaseNum, String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <entityType> <count>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            int count = Integer.parseInt(parts[1]);
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            if (phase == null) return false;
            phase.summonMobs.add(new SummonConfig(parts[0], Math.max(1, count)));
            ConfigManager.getInstance().saveDungeon(cfg);
            player.sendSystemMessage(Component.literal("[Arcadia] Summon de phase ajoute.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Count invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    private static boolean applyPhaseEffectInput(ServerPlayer player, String dungeonId, String bossId, int phaseNum, String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 3) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <effect> <duration> <amplifier>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            int duration = Integer.parseInt(parts[1]);
            int amplifier = Integer.parseInt(parts[2]);
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            if (phase == null) return false;
            phase.playerEffects.add(new PhaseConfig.PhaseEffect(parts[0], duration, amplifier));
            ConfigManager.getInstance().saveDungeon(cfg);
            player.sendSystemMessage(Component.literal("[Arcadia] Effet de phase ajoute.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    private static boolean applyPhaseCommandInput(ServerPlayer player, String dungeonId, String bossId, int phaseNum, String input) {
        DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
        BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
        PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
        if (phase == null) return false;
        phase.phaseCommands.add(input.trim());
        ConfigManager.getInstance().saveDungeon(cfg);
        player.sendSystemMessage(Component.literal("[Arcadia] Commande de phase ajoutee.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static SummonConfig findSummon(PhaseConfig phase, int summonIndex) {
        if (phase == null || phase.summonMobs == null || summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            return null;
        }
        return phase.summonMobs.get(summonIndex);
    }

    private static void beginPhaseSummonStringEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, String prompt,
                                                   java.util.function.BiConsumer<SummonConfig, String> setter,
                                                   String successMessage,
                                                   QuintConsumer<ServerPlayer, String, String, Integer, Integer> reopenAction) {
        AdminGuiSupport.beginPrompt(player, prompt, (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            SummonConfig summon = findSummon(phase, summonIndex);
            if (summon == null) return false;
            setter.accept(summon, input);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId, phaseNum, summonIndex));
    }

    private static void beginPhaseSummonIntEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, String prompt,
                                                java.util.function.BiConsumer<SummonConfig, Integer> setter,
                                                String successMessage,
                                                QuintConsumer<ServerPlayer, String, String, Integer, Integer> reopenAction) {
        AdminGuiSupport.beginIntPrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            SummonConfig summon = findSummon(phase, summonIndex);
            if (summon == null) return false;
            setter.accept(summon, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId, phaseNum, summonIndex));
    }

    private static void beginPhaseSummonDoubleEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, String prompt,
                                                   java.util.function.BiConsumer<SummonConfig, Double> setter,
                                                   String successMessage,
                                                   QuintConsumer<ServerPlayer, String, String, Integer, Integer> reopenAction) {
        AdminGuiSupport.beginDoublePrompt(player, prompt, (playerInput, value) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            SummonConfig summon = findSummon(phase, summonIndex);
            if (summon == null) return false;
            setter.accept(summon, value);
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> reopenAction.accept(reopen, dungeonId, bossId, phaseNum, summonIndex));
    }

    private static void beginPhaseSummonEquipEdit(ServerPlayer player, String dungeonId, String bossId, int phaseNum, int summonIndex, String slot) {
        AdminGuiSupport.beginPrompt(player, "Entre l'item id pour " + slot + " ou 'clear'", (playerInput, input) -> {
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            PhaseConfig phase = boss == null ? null : findPhase(boss, phaseNum);
            SummonConfig summon = findSummon(phase, summonIndex);
            if (summon == null) {
                playerInput.sendSystemMessage(Component.literal("[Arcadia] Summon introuvable.").withStyle(ChatFormatting.RED));
                return false;
            }
            String value = input.trim().equalsIgnoreCase("clear") ? "" : input.trim();
            switch (slot) {
                case "mainhand" -> summon.mainHand = value;
                case "offhand" -> summon.offHand = value;
                case "helmet" -> summon.helmet = value;
                case "chestplate" -> summon.chestplate = value;
                case "leggings" -> summon.leggings = value;
                case "boots" -> summon.boots = value;
                default -> {
                    playerInput.sendSystemMessage(Component.literal("[Arcadia] Slot invalide.").withStyle(ChatFormatting.RED));
                    return false;
                }
            }
            ConfigManager.getInstance().saveDungeon(cfg);
            playerInput.sendSystemMessage(Component.literal("[Arcadia] Equipement du summon mis a jour.").withStyle(ChatFormatting.GREEN));
            return true;
        }, reopen -> openPhaseSummonEquipmentGui(reopen, dungeonId, bossId, phaseNum, summonIndex));
    }

    private static boolean applyRewardInputToBoss(ServerPlayer player, String dungeonId, String bossId, String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 3) {
            player.sendSystemMessage(Component.literal("[Arcadia] Format attendu: <item> <count> <chance>").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            RewardConfig reward = new RewardConfig(parts[0], Integer.parseInt(parts[1]), Double.parseDouble(parts[2]));
            reward.normalize();
            DungeonConfig cfg = ConfigManager.getInstance().getDungeon(dungeonId);
            BossConfig boss = cfg == null ? null : findBoss(cfg, bossId);
            if (boss == null) return false;
            boss.rewards.add(reward);
            ConfigManager.getInstance().saveDungeon(cfg);
            player.sendSystemMessage(Component.literal("[Arcadia] Reward du boss ajoute.").withStyle(ChatFormatting.GREEN));
            return true;
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("[Arcadia] Valeurs invalides.").withStyle(ChatFormatting.RED));
            return false;
        }
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    @FunctionalInterface
    private interface QuintConsumer<A, B, C, D, E> {
        void accept(A a, B b, C c, D d, E e);
    }
}

