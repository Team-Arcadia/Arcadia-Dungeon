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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.arcadia.dungeon.command.ArcadiaCommandHelper.findBoss;
import static com.arcadia.dungeon.gui.admin.AdminGuiRouter.getItemId;

final class ArcadiaAdminConfigCommandActions {

    private ArcadiaAdminConfigCommandActions() {
    }


    static int createDungeon(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");

        if (ConfigManager.getInstance().getDungeon(id) != null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Un donjon avec l'id '" + id + "' existe deja!"));
            return 0;
        }

        DungeonConfig config = new DungeonConfig(id, name);

        // Set spawn to player position if available
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
        Map<String, DungeonConfig> configs = ConfigManager.getInstance().getDungeonConfigs();
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

    // === BOSS COMMANDS ===

    static int addBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        double health = DoubleArgumentType.getDouble(ctx, "health");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        for (BossConfig existing : config.bosses) {
            if (existing.id.equals(bossId)) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Boss '" + bossId + "' existe deja!"));
                return 0;
            }
        }

        BossConfig boss = new BossConfig(bossId, entityType, health, damage);
        boss.customName = bossId;

        if (ctx.getSource().getPlayer() != null) {
            ServerPlayer player = ctx.getSource().getPlayer();
            boss.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        // Add default phase 1
        PhaseConfig phase1 = new PhaseConfig(1, 1.0);
        phase1.description = "Phase initiale";
        boss.phases.add(phase1);

        config.bosses.add(boss);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss '" + bossId + "' ajoute! (" + entityType + " HP:" + health + " DMG:" + damage + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        boolean removed = config.bosses.removeIf(b -> b.id.equals(bossId));
        if (removed) {
            ConfigManager.getInstance().saveDungeon(config);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss '" + bossId + "' supprime.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Boss introuvable: " + bossId));
        return 0;
    }

    static int setBossName(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String name = StringArgumentType.getString(ctx, "name");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.customName = name;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Nom du boss defini: " + name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSpawn(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        boss.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Spawn du boss defini a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossAdaptive(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        double healthMult = DoubleArgumentType.getDouble(ctx, "healthMult");
        double damageMult = DoubleArgumentType.getDouble(ctx, "damageMult");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.adaptivePower = enabled;
        boss.healthMultiplierPerPlayer = healthMult;
        boss.damageMultiplierPerPlayer = damageMult;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Puissance adaptive: " + (enabled ? "Oui" : "Non") +
                " (HP x" + healthMult + "/joueur, DMG x" + damageMult + "/joueur)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossBar(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String color = StringArgumentType.getString(ctx, "color");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.showBossBar = true;
        boss.bossBarColor = color.toUpperCase();
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Barre de boss couleur: " + color)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSpawnAfterWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnAfterWave = waveNum;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        if (waveNum == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : spawn apres toutes les vagues (defaut)")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : spawn apres la vague " + waveNum)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    static int setBossOptional(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.optional = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " optionnel: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSpawnAtStart(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnAtStart = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " spawn au debut: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossRequiredKill(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.requiredKill = enabled;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " kill obligatoire: " + (enabled ? "Oui" : "Non"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSpawnMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String msg = StringArgumentType.getString(ctx, "msg");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnMessage = msg;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : message de spawn mis a jour")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSkipMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String msg = StringArgumentType.getString(ctx, "msg");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.skipMessage = msg;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : message de skip mis a jour")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossSpawnChance(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.spawnChance = chance;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " chance de spawn: " + (int)(chance * 100) + "%")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossHealth(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double health = DoubleArgumentType.getDouble(ctx, "health");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.baseHealth = health;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : vie de base = " + health)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossDamage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double damage = DoubleArgumentType.getDouble(ctx, "damage");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.baseDamage = damage;
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : degats de base = " + damage)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === DUNGEON MESSAGES ===

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

    // === WAVE CONFIG COMMANDS ===

    static int setWaveDelay(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }

        wave.delayBeforeSeconds = seconds;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " : delai = " + seconds + "s")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWaveGlowing(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        int delay = IntegerArgumentType.getInteger(ctx, "delaySeconds");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }

        wave.glowingAfterDelay = enabled;
        wave.glowingDelaySeconds = delay;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " : glowing " + (enabled ? "actif apres " + delay + "s" : "desactive"))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWaveMobProp(CommandContext<CommandSourceStack> ctx, String prop) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId)); return 0; }
        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum)); return 0; }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) { ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex)); return 0; }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        String display;
        switch (prop) {
            case "health" -> { mob.health = DoubleArgumentType.getDouble(ctx, "value"); display = "vie = " + mob.health; }
            case "damage" -> { mob.damage = DoubleArgumentType.getDouble(ctx, "value"); display = "degats = " + mob.damage; }
            case "speed" -> { mob.speed = DoubleArgumentType.getDouble(ctx, "value"); display = "vitesse = " + mob.speed; }
            case "count" -> { mob.count = IntegerArgumentType.getInteger(ctx, "value"); display = "nombre = " + mob.count; }
            case "name" -> { mob.customName = StringArgumentType.getString(ctx, "name"); display = "nom = " + mob.customName; }
            default -> { return 0; }
        }

        ConfigManager.getInstance().saveDungeon(config);
        final String d = display;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + d)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === PHASE COMMANDS ===

    static int addPhase(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        double healthThreshold = DoubleArgumentType.getDouble(ctx, "healthThreshold");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = new PhaseConfig(phaseNum, healthThreshold);
        phase.description = "Phase " + phaseNum;
        boss.phases.add(phase);
        boss.phases.sort((a, b) -> Double.compare(b.healthThreshold, a.healthThreshold));

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " ajoutee (seuil: " + (healthThreshold * 100) + "% HP)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removePhase(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boolean removed = boss.phases.removeIf(p -> p.phase == phaseNum);
        if (removed) {
            ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " supprimee.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
        return 0;
    }

    static int setPhaseProperty(CommandContext<CommandSourceStack> ctx, String property) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        switch (property) {
            case "damage" -> phase.damageMultiplier = DoubleArgumentType.getDouble(ctx, "value");
            case "speed" -> phase.speedMultiplier = DoubleArgumentType.getDouble(ctx, "value");
            case "action" -> phase.requiredAction = StringArgumentType.getString(ctx, "action");
            case "message" -> phase.phaseStartMessage = StringArgumentType.getString(ctx, "msg");
            case "invulnerable" -> {
                int seconds = IntegerArgumentType.getInteger(ctx, "ticks");
                phase.invulnerableDuringTransition = seconds > 0;
                phase.transitionDurationSeconds = seconds;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Phase " + phaseNum + " " + property + " mis a jour.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int addPhaseSummon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        phase.summonMobs.add(new SummonConfig(entityType, count));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Invocation ajoutee: " + count + "x " + entityType + " en phase " + phaseNum)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === REWARD COMMANDS ===

    static int addReward(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String item = ResourceLocationArgument.getId(ctx, "item").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double chance = DoubleArgumentType.getDouble(ctx, "chance");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.rewards.add(new RewardConfig(item, count, chance));
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompense ajoutee: " + count + "x " + item + " (" + (chance * 100) + "%)")
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

    static int clearRewards(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        boss.rewards.clear();
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Recompenses du boss " + bossId + " effacees.")
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

    // === WAVE COMMANDS ===

    static int addWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        for (WaveConfig wave : config.waves) {
            if (wave.waveNumber == waveNum) {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague " + waveNum + " existe deja!"));
                return 0;
            }
        }

        WaveConfig wave = new WaveConfig(waveNum);
        config.waves.add(wave);
        config.waves.sort(Comparator.comparingInt(w -> w.waveNumber));
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " ajoutee au donjon " + config.name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeWave(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        boolean removed = config.waves.removeIf(w -> w.waveNumber == waveNum);
        if (removed) {
            ConfigManager.getInstance().saveDungeon(config);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " supprimee.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
        return 0;
    }

    static int addWaveMob(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        String entityType = ResourceLocationArgument.getId(ctx, "entityType").toString();
        int count = IntegerArgumentType.getInteger(ctx, "count");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        MobSpawnConfig mob = new MobSpawnConfig();
        mob.entityType = entityType;
        mob.count = count;

        // Use player position as spawn point
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            mob.spawnPoint = new SpawnPointConfig(
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        wave.mobs.add(mob);
        ConfigManager.getInstance().saveDungeon(config);

        int mobIndex = wave.mobs.size() - 1;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + count + "x " + entityType + " ajoute a la vague " + waveNum + " (index: " + mobIndex + ")")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWaveMobPos(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index de mob invalide: " + mobIndex + " (max: " + (wave.mobs.size() - 1) + ")"));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        mob.spawnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Position du mob " + mobIndex + " (vague " + waveNum + ") definie a votre position!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWaveMessage(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        String message = StringArgumentType.getString(ctx, "msg");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        wave.startMessage = message;
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Message de la vague " + waveNum + " defini.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int listWaves(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        if (config.waves.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Aucune vague configuree pour " + config.name + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Vagues de " + config.name + " ===").withStyle(ChatFormatting.GOLD), false);
        for (WaveConfig wave : config.waves) {
            int totalMobs = wave.mobs.stream().mapToInt(m -> m.count).sum();
            ctx.getSource().sendSuccess(() -> Component.literal("  Vague " + wave.waveNumber + ": " + wave.mobs.size() + " type(s), " + totalMobs + " mobs total")
                    .withStyle(ChatFormatting.YELLOW), false);
            for (int i = 0; i < wave.mobs.size(); i++) {
                MobSpawnConfig mob = wave.mobs.get(i);
                final int idx = i;
                ctx.getSource().sendSuccess(() -> Component.literal("    [" + idx + "] " + mob.count + "x " + mob.entityType + " @ " +
                        (int) mob.spawnPoint.x + "," + (int) mob.spawnPoint.y + "," + (int) mob.spawnPoint.z)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    // === EQUIP COMMANDS ===

    static int setWaveMobEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }

        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        applyEquipSlot(mob, slot, item);
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vague " + waveNum + " mob " + mobIndex + " : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setSummonEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }

        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex + " (max: " + (phase.summonMobs.size() - 1) + ")"));
            return 0;
        }

        SummonConfig summon = phase.summonMobs.get(summonIndex);
        switch (slot) {
            case "mainhand" -> summon.mainHand = item;
            case "offhand" -> summon.offHand = item;
            case "helmet" -> summon.helmet = item;
            case "chestplate" -> summon.chestplate = item;
            case "leggings" -> summon.leggings = item;
            case "boots" -> summon.boots = item;
            default -> {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Slot invalide: " + slot + " (mainhand/offhand/helmet/chestplate/leggings/boots)"));
                return 0;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static void applyEquipSlot(MobSpawnConfig mob, String slot, String item) {
        switch (slot) {
            case "mainhand" -> mob.mainHand = item;
            case "offhand" -> mob.offHand = item;
            case "helmet" -> mob.helmet = item;
            case "chestplate" -> mob.chestplate = item;
            case "leggings" -> mob.leggings = item;
            case "boots" -> mob.boots = item;
        }
    }

    // === BOSS EQUIP ===

    static int setBossEquip(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
        String item = ResourceLocationArgument.getId(ctx, "item").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        switch (slot) {
            case "mainhand" -> boss.mainHand = item;
            case "offhand" -> boss.offHand = item;
            case "helmet" -> boss.helmet = item;
            case "chestplate" -> boss.chestplate = item;
            case "leggings" -> boss.leggings = item;
            case "boots" -> boss.boots = item;
            default -> {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Slot invalide: " + slot));
                return 0;
            }
        }

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + slot + " = " + item)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int copyEquipToBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        boss.mainHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        boss.offHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        boss.helmet = getItemId(player, net.minecraft.world.entity.EquipmentSlot.HEAD);
        boss.chestplate = getItemId(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
        boss.leggings = getItemId(player, net.minecraft.world.entity.EquipmentSlot.LEGS);
        boss.boots = getItemId(player, net.minecraft.world.entity.EquipmentSlot.FEET);

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Equipement copie sur le boss " + bossId + "!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int copyEquipToWaveMob(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        MobSpawnConfig mob = wave.mobs.get(mobIndex);
        mob.mainHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        mob.offHand = getItemId(player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        mob.helmet = getItemId(player, net.minecraft.world.entity.EquipmentSlot.HEAD);
        mob.chestplate = getItemId(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
        mob.leggings = getItemId(player, net.minecraft.world.entity.EquipmentSlot.LEGS);
        mob.boots = getItemId(player, net.minecraft.world.entity.EquipmentSlot.FEET);

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Equipement copie sur le mob " + mobIndex + " (vague " + waveNum + ")!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static String getItemId(ServerPlayer player, net.minecraft.world.entity.EquipmentSlot slot) {
        net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
        if (stack.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // === BOSS ATTRIBUTE ===

    static int setBossAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        applyBossAttributeValue(boss, attribute, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeBossAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        if (!hasBossAttributeValue(boss, attribute)) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }
        removeBossAttributeValue(boss, attribute);

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int listBossAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        Map<String, Double> attrs = filteredNonCombatAttributes(boss.customAttributes);
        if (attrs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du boss " + bossId + " :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : attrs.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    static int setBossCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        double value = readNumericCombatValue(ctx);

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        applyBossAttributeValue(boss, key, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setBossCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        applyBossAttributeValue(boss, key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Boss " + bossId + " : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === WAVE MOB ATTRIBUTE ===

    static int setWaveMobAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        applyWaveMobAttributeValue(wave.mobs.get(mobIndex), attribute, value);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeWaveMobAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        if (!hasWaveMobAttributeValue(wave.mobs.get(mobIndex), attribute)) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }
        removeWaveMobAttributeValue(wave.mobs.get(mobIndex), attribute);

        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int listWaveMobAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        Map<String, Double> attrs = filteredNonCombatAttributes(wave.mobs.get(mobIndex).customAttributes);
        if (attrs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du mob " + mobIndex + " (vague " + waveNum + ") :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : attrs.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    static int setWaveCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        double value = readNumericCombatValue(ctx);

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        applyWaveMobAttributeValue(wave.mobs.get(mobIndex), key, value);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setWaveCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int waveNum = IntegerArgumentType.getInteger(ctx, "waveNum");
        int mobIndex = IntegerArgumentType.getInteger(ctx, "mobIndex");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        WaveConfig wave = config.waves.stream().filter(w -> w.waveNumber == waveNum).findFirst().orElse(null);
        if (wave == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vague introuvable: " + waveNum));
            return 0;
        }
        if (mobIndex < 0 || mobIndex >= wave.mobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index mob invalide: " + mobIndex));
            return 0;
        }

        applyWaveMobAttributeValue(wave.mobs.get(mobIndex), key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Mob " + mobIndex + " (vague " + waveNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === SUMMON ATTRIBUTE ===

    static int setSummonAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        applySummonAttributeValue(phase.summonMobs.get(summonIndex), attribute, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + attribute + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int removeSummonAttribute(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        String attribute = ResourceLocationArgument.getId(ctx, "attribute").toString();

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        if (!hasSummonAttributeValue(phase.summonMobs.get(summonIndex), attribute)) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Attribut non defini: " + attribute));
            return 0;
        }
        removeSummonAttributeValue(phase.summonMobs.get(summonIndex), attribute);

        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : attribut " + attribute + " supprime")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int listSummonAttributes(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        Map<String, Double> attrs = filteredNonCombatAttributes(phase.summonMobs.get(summonIndex).customAttributes);
        if (attrs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : aucun attribut personnalise")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Attributs du sbire " + summonIndex + " (phase " + phaseNum + ") :")
                .withStyle(ChatFormatting.GOLD), false);
        for (Map.Entry<String, Double> entry : attrs.entrySet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.getKey() + " = " + entry.getValue())
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    static int setSummonCombatValue(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        double value = readNumericCombatValue(ctx);

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        applySummonAttributeValue(phase.summonMobs.get(summonIndex), key, value);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setSummonCombatBool(CommandContext<CommandSourceStack> ctx, String key) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        String bossId = StringArgumentType.getString(ctx, "bossId");
        int phaseNum = IntegerArgumentType.getInteger(ctx, "phaseNum");
        int summonIndex = IntegerArgumentType.getInteger(ctx, "summonIndex");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        BossConfig boss = findBoss(ctx, dungeonId, bossId);
        if (boss == null) return 0;

        PhaseConfig phase = boss.phases.stream().filter(p -> p.phase == phaseNum).findFirst().orElse(null);
        if (phase == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Phase introuvable: " + phaseNum));
            return 0;
        }
        if (summonIndex < 0 || summonIndex >= phase.summonMobs.size()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Index invocation invalide: " + summonIndex));
            return 0;
        }

        applySummonAttributeValue(phase.summonMobs.get(summonIndex), key, value ? 1.0D : 0.0D);
        ConfigManager.getInstance().saveDungeon(ConfigManager.getInstance().getDungeon(dungeonId));
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Sbire " + summonIndex + " (phase " + phaseNum + ") : " + key + " = " + value)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static double readNumericCombatValue(CommandContext<CommandSourceStack> ctx) {
        try {
            return DoubleArgumentType.getDouble(ctx, "value");
        } catch (IllegalArgumentException ignored) {
            return IntegerArgumentType.getInteger(ctx, "value");
        }
    }

    private static Map<String, Double> filteredNonCombatAttributes(Map<String, Double> attributes) {
        Map<String, Double> filtered = new LinkedHashMap<>();
        if (attributes == null || attributes.isEmpty()) return filtered;
        attributes.entrySet().stream()
                .filter(entry -> entry != null && entry.getKey() != null && !CombatTuning.SPECIAL_KEYS.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> filtered.put(entry.getKey(), entry.getValue()));
        return filtered;
    }

    private static boolean hasBossAttributeValue(BossConfig boss, String key) {
        if (boss == null || key == null) return false;
        if (boss.customAttributes != null && boss.customAttributes.containsKey(key)) return true;
        if (CombatTuning.KEY_ATTACK_RANGE.equals(key)) return boss.attackRange != 0.0D;
        if (CombatTuning.KEY_ATTACK_COOLDOWN_MS.equals(key)) return boss.attackCooldownMs != 0;
        if (CombatTuning.KEY_AGGRO_RANGE.equals(key)) return boss.aggroRange != 0.0D;
        if (CombatTuning.KEY_PROJECTILE_COOLDOWN_MS.equals(key)) return boss.projectileCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_CHANCE.equals(key)) return boss.dodgeChance != 0.0D;
        if (CombatTuning.KEY_DODGE_COOLDOWN_MS.equals(key)) return boss.dodgeCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_PROJECTILES_ONLY.equals(key)) return boss.dodgeProjectilesOnly;
        return boss.customAttributes != null && boss.customAttributes.containsKey(key);
    }

    private static void applyBossAttributeValue(BossConfig boss, String key, double value) {
        if (boss == null || key == null) return;
        if (boss.customAttributes == null) boss.customAttributes = new java.util.HashMap<>();
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
        if (boss.customAttributes == null) boss.customAttributes = new java.util.HashMap<>();
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

    private static boolean hasWaveMobAttributeValue(MobSpawnConfig mob, String key) {
        if (mob == null || key == null) return false;
        if (mob.customAttributes != null && mob.customAttributes.containsKey(key)) return true;
        if (CombatTuning.KEY_ATTACK_RANGE.equals(key)) return mob.attackRange != 0.0D;
        if (CombatTuning.KEY_ATTACK_COOLDOWN_MS.equals(key)) return mob.attackCooldownMs != 0;
        if (CombatTuning.KEY_AGGRO_RANGE.equals(key)) return mob.aggroRange != 0.0D;
        if (CombatTuning.KEY_PROJECTILE_COOLDOWN_MS.equals(key)) return mob.projectileCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_CHANCE.equals(key)) return mob.dodgeChance != 0.0D;
        if (CombatTuning.KEY_DODGE_COOLDOWN_MS.equals(key)) return mob.dodgeCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_PROJECTILES_ONLY.equals(key)) return mob.dodgeProjectilesOnly;
        return mob.customAttributes != null && mob.customAttributes.containsKey(key);
    }

    private static void applyWaveMobAttributeValue(MobSpawnConfig mob, String key, double value) {
        if (mob == null || key == null) return;
        if (mob.customAttributes == null) mob.customAttributes = new java.util.HashMap<>();
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
        if (mob.customAttributes == null) mob.customAttributes = new java.util.HashMap<>();
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

    private static boolean hasSummonAttributeValue(SummonConfig summon, String key) {
        if (summon == null || key == null) return false;
        if (summon.customAttributes != null && summon.customAttributes.containsKey(key)) return true;
        if (CombatTuning.KEY_ATTACK_RANGE.equals(key)) return summon.attackRange != 0.0D;
        if (CombatTuning.KEY_ATTACK_COOLDOWN_MS.equals(key)) return summon.attackCooldownMs != 0;
        if (CombatTuning.KEY_AGGRO_RANGE.equals(key)) return summon.aggroRange != 0.0D;
        if (CombatTuning.KEY_PROJECTILE_COOLDOWN_MS.equals(key)) return summon.projectileCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_CHANCE.equals(key)) return summon.dodgeChance != 0.0D;
        if (CombatTuning.KEY_DODGE_COOLDOWN_MS.equals(key)) return summon.dodgeCooldownMs != 0;
        if (CombatTuning.KEY_DODGE_PROJECTILES_ONLY.equals(key)) return summon.dodgeProjectilesOnly;
        return summon.customAttributes != null && summon.customAttributes.containsKey(key);
    }

    private static void applySummonAttributeValue(SummonConfig summon, String key, double value) {
        if (summon == null || key == null) return;
        if (summon.customAttributes == null) summon.customAttributes = new java.util.HashMap<>();
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
        if (summon.customAttributes == null) summon.customAttributes = new java.util.HashMap<>();
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
}
