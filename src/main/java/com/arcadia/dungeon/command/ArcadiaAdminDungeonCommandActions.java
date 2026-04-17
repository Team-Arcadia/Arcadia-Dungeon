package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.config.*;
import com.arcadia.dungeon.dungeon.*;
import com.arcadia.dungeon.service.admin.AdminGuiActionService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class ArcadiaAdminDungeonCommandActions {

    private ArcadiaAdminDungeonCommandActions() {
    }

    static int createDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");

        if (ConfigManager.getInstance().getDungeon(id) != null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Un donjon avec l'id '" + id + "' existe deja!"));
            return 0;
        }

        DungeonConfig config = new DungeonConfig(id, name);

        if (ctx.getSource().getPlayer() != null) {
            ServerPlayer player = ctx.getSource().getPlayer();
            config.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + name + "' cree avec succes! (id: " + id + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int deleteDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        if (ConfigManager.getInstance().deleteDungeon(id)) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + id + "' supprime.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
        return 0;
    }

    static int listDungeons(CommandContext<CommandSourceStack> ctx) {
        java.util.Map<String, DungeonConfig> configs = ConfigManager.getInstance().getDungeonConfigs();
        if (configs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Aucun donjon configure.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Donjons Arcadia ===").withStyle(ChatFormatting.GOLD), false);
        for (DungeonConfig config : configs.values()) {
            String status = config.enabled ? "&aActif" : "&cDesactive";
            DungeonInstance active = DungeonManager.getInstance().getInstance(config.id);
            String running = active != null ? " &e[EN COURS]" : "";
            ctx.getSource().sendSuccess(() -> DungeonManager.parseColorCodes(
                    " &7- &f" + config.name + " &7(id: " + config.id + ") " + status + running
            ), false);
        }
        return 1;
    }

    static int dungeonInfo(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        AdminGuiActionService.showDungeonInfo(component -> ctx.getSource().sendSuccess(() -> component, false), id);
        return 1;
    }

    static int setSpawn(CommandContext<CommandSourceStack> ctx) {
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

        config.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Spawn du donjon defini a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setCooldown(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.cooldownSeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Cooldown defini a " + seconds + "s.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setAvailability(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.availableEverySeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Disponibilite definie a toutes les " + seconds + "s (0 = toujours).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setAnnounce(CommandContext<CommandSourceStack> ctx, String type) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        if ("start".equals(type)) {
            config.announceStart = enabled;
        } else if ("availability".equals(type)) {
            config.announceAvailability = enabled;
        } else {
            config.announceCompletion = enabled;
        }
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Annonce " + type + " " + (enabled ? "activee" : "desactivee") + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setAvailabilityMessage(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String msg = StringArgumentType.getString(ctx, "msg");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.availabilityMessage = msg;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message de disponibilite mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setSetting(CommandContext<CommandSourceStack> ctx, String setting) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }

        switch (setting) {
            case "maxplayers" -> config.settings.maxPlayers = IntegerArgumentType.getInteger(ctx, "value");
            case "minplayers" -> config.settings.minPlayers = IntegerArgumentType.getInteger(ctx, "value");
            case "recruitment" -> config.recruitmentDurationSeconds = IntegerArgumentType.getInteger(ctx, "value");
            case "timelimit" -> config.settings.timeLimitSeconds = IntegerArgumentType.getInteger(ctx, "value");
            case "pvp" -> config.settings.pvp = BoolArgumentType.getBool(ctx, "value");
            case "maxdeaths" -> config.settings.maxDeaths = IntegerArgumentType.getInteger(ctx, "value");
            case "completiondelay" -> config.settings.completionDelaySeconds = Math.max(0, IntegerArgumentType.getInteger(ctx, "value"));
            case "teleportback" -> config.teleportBackOnComplete = BoolArgumentType.getBool(ctx, "value");
            case "difficultyscaling" -> config.settings.difficultyScaling = BoolArgumentType.getBool(ctx, "value");
            case "antimonopole" -> config.settings.antiMonopole = BoolArgumentType.getBool(ctx, "value");
            case "antimonopolethreshold" -> config.settings.antiMonopoleThreshold = IntegerArgumentType.getInteger(ctx, "value");
            case "blockteleport" -> config.settings.blockTeleportCommands = BoolArgumentType.getBool(ctx, "value");
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Parametre " + setting + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setSettingDouble(CommandContext<CommandSourceStack> ctx, String setting) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");

        switch (setting) {
            case "wavehealthmultiplier" -> config.settings.waveHealthMultiplierPerPlayer = value;
            case "wavedamagemultiplier" -> config.settings.waveDamageMultiplierPerPlayer = value;
            case "wavecountmultiplier" -> config.settings.waveCountMultiplierPerPlayer = value;
        }

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Parametre " + setting + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setDungeonMessage(CommandContext<CommandSourceStack> ctx, String type) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String msg = StringArgumentType.getString(ctx, "msg");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        switch (type) {
            case "start" -> config.startMessage = msg;
            case "completion" -> config.completionMessage = msg;
            case "fail" -> config.failMessage = msg;
            case "recruitment" -> config.recruitmentMessage = msg;
        }
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message " + type + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int renameDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String name = StringArgumentType.getString(ctx, "name");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.name = name;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon renomme: " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addCompletionReward(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        config.completionRewards.add(new RewardConfig(item, count, chance));
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense de completion ajoutee: " + count + "x " + item + " (" + (chance * 100) + "%)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setOrder(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        int order = IntegerArgumentType.getInteger(ctx, "number");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.order = order;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Ordre du donjon defini a " + order + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setRequirement(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        String required = StringArgumentType.getString(ctx, "required");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        if (ConfigManager.getInstance().getDungeon(required) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon requis introuvable: " + required));
            return 0;
        }

        config.requiredDungeon = required;
        ConfigManager.getInstance().saveDungeon(config);
        DungeonConfig req = ConfigManager.getInstance().getDungeon(required);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + config.name + " necessite maintenant: " + req.name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int clearRequirement(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.requiredDungeon = "";
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Prerequis supprime pour " + config.name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
