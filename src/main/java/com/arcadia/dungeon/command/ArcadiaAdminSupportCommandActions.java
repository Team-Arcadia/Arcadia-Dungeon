package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.config.BossConfig;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.PhaseConfig;
import com.arcadia.dungeon.dungeon.WeeklyLeaderboard;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

import static com.arcadia.dungeon.command.ArcadiaCommandHelper.findBoss;

final class ArcadiaAdminSupportCommandActions {

    private ArcadiaAdminSupportCommandActions() {
    }

    static int giveAreaWand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SHOVEL);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(com.arcadia.dungeon.event.DungeonEventHandler.AREA_WAND_TAG + " - Arcadia Dungeon Area Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Ask which dungeon
        Map<String, DungeonConfig> dungeons = ConfigManager.getInstance().getDungeonConfigs();
        if (dungeons.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucun donjon configure!"));
            return 0;
        }

        // Give the wand
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }

        // Default to first dungeon, show selection
        String firstId = dungeons.keySet().iterator().next();
        com.arcadia.dungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), firstId);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Wand recue! Donjon: " + firstId).withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Clic gauche = Pos1 | Clic droit = Pos2").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  /arcadia_dungeon admin wand_select <dungeon> pour la zone du donjon").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  La pelle en or sert uniquement a la zone globale du donjon").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Les zones de boss/vagues ont ete retirees pour ne plus casser l'IA").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Changer de donjon:").withStyle(ChatFormatting.GRAY), false);

        for (DungeonConfig cfg : dungeons.values()) {
            MutableComponent line = Component.literal("    [" + cfg.name + "]").withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/arcadia_dungeon admin wand_select " + cfg.id))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Cliquer pour selectionner " + cfg.name))));
            ctx.getSource().sendSuccess(() -> line, false);
        }

        return 1;
    }

    // === WAND SELECT ===

    static int wandSelect(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!")); return 0; }

        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }

        com.arcadia.dungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        com.arcadia.dungeon.event.DungeonEventHandler.wandWall.remove(player.getUUID());
        com.arcadia.dungeon.event.DungeonEventHandler.wandPos1.remove(player.getUUID());
        com.arcadia.dungeon.event.DungeonEventHandler.wandPos2.remove(player.getUUID());

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wand] Donjon selectionne: " + config.name)
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    static int giveWallWand(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HOE);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(com.arcadia.dungeon.event.DungeonEventHandler.WALL_WAND_TAG + " - Arcadia Scripted Wall Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Hoe recue! Selectionnez un mur avec /arcadia_dungeon admin wall_select <dungeon> <wallId>, puis cliquez sur les blocs.")
                .withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Les murs scriptes reagissent aux conditions configurees comme DUNGEON_START ou WAVE_COMPLETE:<n>.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    static int wallSelect(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!")); return 0; }

        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String wallId = StringArgumentType.getString(ctx, "wallId");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }

        var wall = config.scriptedWalls.stream().filter(w -> w.id.equals(wallId)).findFirst().orElse(null);
        if (wall == null) {
            wall = new DungeonConfig.ScriptedWallConfig();
            wall.id = wallId;
            config.scriptedWalls.add(wall);
            ConfigManager.getInstance().saveDungeon(config);
        }

        com.arcadia.dungeon.event.DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        com.arcadia.dungeon.event.DungeonEventHandler.wandWall.put(player.getUUID(), wallId);
        com.arcadia.dungeon.event.DungeonEventHandler.wandPos1.remove(player.getUUID());
        com.arcadia.dungeon.event.DungeonEventHandler.wandPos2.remove(player.getUUID());

        int count = wall.blocks == null ? 0 : wall.blocks.size();
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] Mur scripte selectionne: " + wallId + " (" + count + " bloc(s))")
                .withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] Cliquez sur les blocs avec la hoe en or pour les ajouter/retirer.")
                .withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Wall] Configure une condition comme DUNGEON_START ou WAVE_COMPLETE:2 pour declencher le mur.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === PHASE EFFECT + COMMAND ===

    static int addPhaseEffect(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String effect = ResourceLocationArgument.getId(ctx, "effect").toString();
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum)); return 0; }

        phase.playerEffects.add(new PhaseConfig.PhaseEffect(effect, duration, amplifier));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Effet " + effect + " (lvl " + (amplifier + 1) + ", " + duration + "s) ajoute a la phase " + phaseNum)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addPhaseCommand(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String cmd = StringArgumentType.getString(ctx, "cmd");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum)); return 0; }

        phase.phaseCommands.add(cmd);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Commande ajoutee a la phase " + phaseNum + ": " + cmd)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WEEKLY COMMANDS ===

    static int setWeeklyReward(CommandContext<CommandSourceStack> ctx) {
        int top = IntegerArgumentType.getInteger(ctx, "top");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        WeeklyLeaderboard.getInstance().setReward(top, item, count);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense top " + top + " definie: " + count + "x " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWeeklyResetDay(CommandContext<CommandSourceStack> ctx) {
        String dayStr = StringArgumentType.getString(ctx, "day").toUpperCase();
        try {
            java.time.DayOfWeek day = java.time.DayOfWeek.valueOf(dayStr);
            WeeklyLeaderboard.getInstance().setResetDay(day);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Jour de reset: " + dayStr).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Jour invalide! (MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)"));
            return 0;
        }
    }

    static int setWeeklyHour(CommandContext<CommandSourceStack> ctx) {
        int hour = IntegerArgumentType.getInteger(ctx, "hour");
        WeeklyLeaderboard.getInstance().setAnnounceHour(hour);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Heure d'annonce: " + hour + "h").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int weeklyInfo(CommandContext<CommandSourceStack> ctx) {
        var config = WeeklyLeaderboard.getInstance().getConfig();
        var data = WeeklyLeaderboard.getInstance().getData();

        ctx.getSource().sendSuccess(() -> Component.literal("=== Leaderboard Hebdo ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Semaine: " + data.weekId).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Reset: " + config.resetDay + " a " + config.announceHour + "h").withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Joueurs: " + data.playerCompletions.size()).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Donjons suivis: " + data.dungeonBestTimes.size()).withStyle(ChatFormatting.WHITE), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Recompenses:").withStyle(ChatFormatting.YELLOW), false);

        for (int i = 1; i <= 3; i++) {
            List<com.arcadia.dungeon.config.RewardConfig> rewards = switch (i) {
                case 1 -> config.top1Rewards;
                case 2 -> config.top2Rewards;
                case 3 -> config.top3Rewards;
                default -> List.of();
            };
            final int rank = i;
            if (!rewards.isEmpty()) {
                var r = rewards.get(0);
                ctx.getSource().sendSuccess(() -> Component.literal("    Top " + rank + ": " + r.count + "x " + r.item).withStyle(ChatFormatting.GRAY), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("    Top " + rank + ": aucune").withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    static int weeklyForceReset(CommandContext<CommandSourceStack> ctx) {
        WeeklyLeaderboard.getInstance().forceReset(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Leaderboard hebdo reset! Annonce envoyee et recompenses distribuees.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === TIMER WARNINGS ===

    static int addTimerWarning(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        if (!config.settings.timerWarnings.contains(seconds)) {
            config.settings.timerWarnings.add(seconds);
            config.settings.timerWarnings.sort(java.util.Collections.reverseOrder());
            ConfigManager.getInstance().saveDungeon(config);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissement a " + seconds + "s ajoute.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeTimerWarning(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        config.settings.timerWarnings.remove(Integer.valueOf(seconds));
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissement a " + seconds + "s retire.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int listTimerWarnings(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id)); return 0; }

        String warnings = config.settings.timerWarnings.stream().map(s -> s + "s").reduce((a, b) -> a + ", " + b).orElse("aucun");
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Avertissements pour " + config.name + ": " + warnings).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === DUNGEON AREA ===

    static int setDungeonArea(CommandContext<CommandSourceStack> ctx, int posNumber) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonConfig.AreaPos pos = new DungeonConfig.AreaPos(
                player.level().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ()
        );

        if (posNumber == 1) {
            config.areaPos1 = pos;
        } else {
            config.areaPos2 = pos;
        }

        ConfigManager.getInstance().saveDungeon(config);

        String coordStr = pos.x + ", " + pos.y + ", " + pos.z;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone du donjon pos" + posNumber + " definie: " + coordStr)
                .withStyle(ChatFormatting.GREEN), true);

        if (config.hasArea()) {
            int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
            int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
            int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
            int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
            int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
            int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);
            int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone complete! " +
                    (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1) + " (" + volume + " blocs)")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Definissez maintenant pos" + (posNumber == 1 ? "2" : "1") + " pour completer la zone.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    static int clearDungeonArea(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.areaPos1 = null;
        config.areaPos2 = null;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Zone du donjon " + config.name + " supprimee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}

