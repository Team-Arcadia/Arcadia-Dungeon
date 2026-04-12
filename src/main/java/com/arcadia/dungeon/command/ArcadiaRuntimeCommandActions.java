package com.arcadia.dungeon.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.arcadia.dungeon.boss.BossInstance;
import com.arcadia.dungeon.boss.BossManager;
import com.arcadia.dungeon.config.*;
import com.arcadia.dungeon.dungeon.*;
import com.arcadia.dungeon.service.admin.AdminGuiActionService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import static com.arcadia.dungeon.command.ArcadiaCommandHelper.*;
import static com.arcadia.dungeon.gui.admin.AdminGuiRouter.*;

final class ArcadiaRuntimeCommandActions {

    private ArcadiaRuntimeCommandActions() {
    }

    // === DUNGEON ACTIONS ===

    static int startDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().startDungeon(dungeonId, player);
        if (instance != null) {
            // If recruiting, waves/bosses start after recruitment ends (event handler)
            if (instance.getState() != DungeonState.RECRUITING) {
                if (!instance.hasWaves()) {
                    BossManager.getInstance().spawnNextBoss(instance);
                }
                // Waves are started automatically by the event handler
            }
            return 1;
        }
        return 0;
    }

    static int joinDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        return DungeonManager.getInstance().joinDungeon(dungeonId, player) ? 1 : 0;
    }

    static int stopDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        if (DungeonManager.getInstance().getInstance(dungeonId) == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucune instance active: " + dungeonId));
            return 0;
        }
        DungeonManager.getInstance().stopDungeon(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon '" + dungeonId + "' arrete.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    static int teleportToDungeon(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        DungeonManager.getInstance().teleportToSpawn(player, config.spawnPoint);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Teleporte au donjon " + config.name + "!")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    static int leaveDungeon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vous n'etes dans aucun donjon!"));
            return 0;
        }

        DungeonManager.getInstance().removePlayerFromDungeon(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vous avez quitte le donjon.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    static int toggleDungeon(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(id);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + id));
            return 0;
        }
        config.enabled = enabled;
        ConfigManager.getInstance().saveDungeon(config);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon " + (enabled ? "active" : "desactive") + ": " + config.name)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === ABANDON (player, no permission) ===

    static int abandonDungeon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Vous n'etes dans aucun donjon!"));
            return 0;
        }

        String dungeonName = instance.getConfig().name;
        DungeonManager.getInstance().removePlayerFromDungeon(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Vous avez abandonne " + dungeonName + ".")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // === STATUS (player) ===

    static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }

        String uuid = player.getUUID().toString();
        PlayerProgress progress = PlayerProgressManager.getInstance()
                .getOrCreate(uuid, player.getName().getString());

        ctx.getSource().sendSuccess(() -> Component.literal("========= Statut Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Current dungeon
        DungeonInstance currentInstance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (currentInstance != null) {
            var cfg = currentInstance.getConfig();
            String state = switch (currentInstance.getState()) {
                case RECRUITING -> "Recrutement (" + currentInstance.getRecruitmentRemainingSeconds() + "s)";
                case ACTIVE -> "En cours";
                case BOSS_FIGHT -> "Combat de boss";
                case WAITING -> "Attente";
                default -> currentInstance.getState().toString();
            };
            int lives = currentInstance.getRemainingLives(player.getUUID());
            long elapsed = currentInstance.getElapsedSeconds();
            ctx.getSource().sendSuccess(() -> Component.literal(" Donjon actuel: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(cfg.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Etat: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(state).withStyle(ChatFormatting.WHITE)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Temps: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(DungeonManager.formatTime(elapsed)).withStyle(ChatFormatting.WHITE))
                    .append(cfg.settings.timeLimitSeconds > 0 ?
                            Component.literal(" / " + DungeonManager.formatTime(cfg.settings.timeLimitSeconds)).withStyle(ChatFormatting.GRAY) :
                            Component.literal(" (illimite)").withStyle(ChatFormatting.GRAY)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Vies: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(cfg.settings.maxDeaths > 0 ? lives + "/" + cfg.settings.maxDeaths : "illimitees")
                            .withStyle(lives <= 1 && cfg.settings.maxDeaths > 0 ? ChatFormatting.RED : ChatFormatting.GREEN)), false);
            ctx.getSource().sendSuccess(() -> Component.literal("   Joueurs: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(currentInstance.getPlayerCount() + "/" + cfg.settings.maxPlayers).withStyle(ChatFormatting.WHITE)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(" Donjon actuel: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Aucun").withStyle(ChatFormatting.WHITE)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.RESET), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" Donjons:").withStyle(ChatFormatting.YELLOW), false);

        for (var entry : ConfigManager.getInstance().getDungeonConfigs().entrySet()) {
            DungeonConfig cfg = entry.getValue();
            if (!cfg.enabled) continue;

            boolean unlocked = isDungeonUnlocked(progress, cfg);
            boolean completed = progress.hasCompleted(cfg.id);

            DungeonInstance active = DungeonManager.getInstance().getInstance(cfg.id);

            String status;
            ChatFormatting color;

            if (!unlocked) {
                DungeonConfig req = ConfigManager.getInstance().getDungeon(cfg.requiredDungeon);
                if (cfg.requiredArcadiaLevel > progress.arcadiaProgress.arcadiaLevel) {
                    status = "Verrouille (Niveau Arcadia " + cfg.requiredArcadiaLevel + ")";
                } else {
                    status = "Verrouille (Requis: " + (req != null ? req.name : cfg.requiredDungeon) + ")";
                }
                color = ChatFormatting.RED;
            } else if (active != null) {
                status = active.getState() == DungeonState.RECRUITING ?
                        "Recrutement (" + active.getRecruitmentRemainingSeconds() + "s)" :
                        "En cours (" + active.getPlayerCount() + " joueurs)";
                color = ChatFormatting.AQUA;
            } else {
                status = "Disponible";
                color = ChatFormatting.GREEN;
                if (completed) {
                    PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(cfg.id);
                    if (dp != null) {
                        status = dp.completions + "x | Record: " + formatTime(dp.bestTimeSeconds);
                        color = ChatFormatting.GREEN;
                    }
                }
            }

            final String fStatus = status;
            final ChatFormatting fColor = color;
            ctx.getSource().sendSuccess(() -> Component.literal("   " + cfg.name + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(fStatus).withStyle(fColor)), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.RESET), false);
        int totalCompletions = progress.getTotalCompletions();
        int dungeonsCompleted = progress.completedDungeons.size();
        ctx.getSource().sendSuccess(() -> Component.literal(" Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(totalCompletions + " completions").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + dungeonsCompleted + " donjons)").withStyle(ChatFormatting.GRAY)), false);

        var weeklyData = WeeklyLeaderboard.getInstance().getData();
        int weeklyCount = weeklyData.playerCompletions.getOrDefault(uuid, 0);
        ctx.getSource().sendSuccess(() -> Component.literal(" Cette semaine: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(weeklyCount + " completions").withStyle(ChatFormatting.YELLOW)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("===================================").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    static int showAdminStatus(CommandContext<CommandSourceStack> ctx) {
        Map<String, DungeonInstance> active = DungeonManager.getInstance().getActiveInstances();
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Aucun donjon actif.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Donjons Actifs ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(active.entrySet())) {
            DungeonInstance instance = entry.getValue();
            DungeonConfig config = instance.getConfig();
            String state = instance.getState().toString();
            String players = instance.getPlayerNames(ctx.getSource().getServer());
            String phase = "Aucune";

            if (!instance.getActiveBosses().isEmpty()) {
                BossInstance boss = instance.getActiveBosses().values().iterator().next();
                phase = "Boss phase " + (boss.getCurrentPhase() + 1);
            } else if (instance.getCurrentWave() != null) {
                phase = "Vague " + instance.getCurrentWave().getConfig().waveNumber;
            } else if (instance.getState() == DungeonState.RECRUITING) {
                phase = "Recrutement " + instance.getRecruitmentRemainingSeconds() + "s";
            }

            String line = config.name + " (" + config.id + ") | Etat: " + state
                    + " | Phase: " + phase
                    + " | Joueurs: " + instance.getPlayerCount()
                    + " [" + players + "]";
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    // === FORCERESET ===

    static int forceReset(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Aucune instance active: " + dungeonId));
            return 0;
        }
        DungeonManager.getInstance().forceReset(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Donjon " + dungeonId + " reinitialise. Joueurs expulses, mobs supprimes.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === PROGRESS MANAGEMENT ===

    static int resetPlayerProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        PlayerProgressManager.getInstance().resetPlayer(progress.uuid);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Progression de " + playerName + " entierement reinitialisee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int resetPlayerDungeonProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        if (progress == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
            return 0;
        }
        PlayerProgressManager.getInstance().resetPlayerDungeon(progress.uuid, dungeonId);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Progression de " + playerName + " pour " + dName + " reinitialisee.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int setPlayerProgress(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        int completions = IntegerArgumentType.getInteger(ctx, "completions");
        int bestTime = IntegerArgumentType.getInteger(ctx, "bestTime");

        // Find or create player progress
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerName);
        String uuid;
        if (progress != null) {
            uuid = progress.uuid;
        } else {
            // Try to find online player
            if (ctx.getSource().getServer() != null) {
                ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
                if (target != null) {
                    uuid = target.getUUID().toString();
                } else {
                    ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                    return 0;
                }
            } else {
                ctx.getSource().sendFailure(Component.literal("[Arcadia] Joueur introuvable: " + playerName));
                return 0;
            }
        }

        PlayerProgressManager.getInstance().setPlayerDungeon(uuid, playerName, dungeonId, completions, bestTime);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] " + playerName + " -> " + dName + ": " + completions + " completions, record " + formatTime(bestTime) + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // === DEBUG COMMANDS ===

    static int reloadConfigs(CommandContext<CommandSourceStack> ctx) {
        AdminGuiActionService.reloadConfigs(component -> ctx.getSource().sendSuccess(() -> component, true));
        return 1;
    }

    static int setDungeonCuboid(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Donjon introuvable: " + dungeonId));
            return 0;
        }

        String dimension = ctx.getSource().getLevel().dimension().location().toString();
        config.areaPos1 = new DungeonConfig.AreaPos(
                dimension,
                IntegerArgumentType.getInteger(ctx, "x1"),
                IntegerArgumentType.getInteger(ctx, "y1"),
                IntegerArgumentType.getInteger(ctx, "z1")
        );
        config.areaPos2 = new DungeonConfig.AreaPos(
                dimension,
                IntegerArgumentType.getInteger(ctx, "x2"),
                IntegerArgumentType.getInteger(ctx, "y2"),
                IntegerArgumentType.getInteger(ctx, "z2")
        );
        ConfigManager.getInstance().saveDungeon(config);

        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Cuboid defini pour " + config.name + " en " + dimension + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int applyRuntimeTuning(CommandContext<CommandSourceStack> ctx) {
        int entityId = IntegerArgumentType.getInteger(ctx, "entityId");
        String key = StringArgumentType.getString(ctx, "key");
        double requestedValue = DoubleArgumentType.getDouble(ctx, "value");

        Entity entity = findEntityById(ctx.getSource().getServer(), entityId);
        if (!(entity instanceof LivingEntity living)) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Entite introuvable ou non-vivante: " + entityId));
            return 0;
        }

        Double specialValue = clampSpecialValue(key, requestedValue);
        if (specialValue != null) {
            CombatTuning.applySpecialAttribute(living, key, specialValue);
            warnIfClamped(key, requestedValue, specialValue);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Tuning runtime " + entityId + " : " + key + " = " + specialValue)
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        net.minecraft.resources.ResourceLocation attrLoc = net.minecraft.resources.ResourceLocation.tryParse(key);
        if (attrLoc == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Cle invalide. Cles speciales: " + String.join(", ", CombatTuning.getSpecialKeys()) + " ou attribut vanilla namespace:path."));
            return 0;
        }

        Attribute attribute = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getOptional(attrLoc).orElse(null);
        if (attribute == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Cle invalide. Cles speciales: " + String.join(", ", CombatTuning.getSpecialKeys()) + " ou attribut vanilla namespace:path."));
            return 0;
        }

        AttributeInstance attributeInstance = living.getAttribute(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        if (attributeInstance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] L'entite ne supporte pas l'attribut: " + key));
            return 0;
        }

        double clampedValue = Math.max(0.0D, requestedValue);
        warnIfClamped(key, requestedValue, clampedValue);
        attributeInstance.setBaseValue(clampedValue);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia] Tuning runtime " + entityId + " : " + key + " = " + clampedValue)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    static int debugInfo(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active pour: " + dungeonId));
            return 0;
        }
        AdminGuiActionService.debugInfo(component -> ctx.getSource().sendSuccess(() -> component, false), dungeonId);
        return 1;
    }

    static int debugActive(CommandContext<CommandSourceStack> ctx) {
        AdminGuiActionService.showActiveInstances(component -> ctx.getSource().sendSuccess(() -> component, false));
        return DungeonManager.getInstance().getActiveInstances().isEmpty() ? 0 : 1;
    }

    static int debugSpawnBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }
        boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss spawn: " + (spawned ? "Oui" : "Non (plus de boss)"))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    static int debugSkipBoss(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }

        if (instance.getActiveBosses().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucun boss actif a skip!"));
            return 0;
        }

        boolean skippedInterWaveBoss = instance.isWaitingForInterWaveBoss();

        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            String bossId = entry.getKey();

            // Give boss rewards to all players
            for (java.util.UUID playerId : instance.getPlayers()) {
                ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    instance.giveRewards(player, boss.getConfig().rewards);
                    player.sendSystemMessage(Component.literal("[Arcadia Debug] Boss " + boss.getConfig().customName + " skip!")
                            .withStyle(ChatFormatting.AQUA));
                }
            }

            boss.cleanup();
            instance.removeBossInstance(bossId);
        }

        if (skippedInterWaveBoss) {
            instance.setWaitingForInterWaveBoss(false);
            instance.setState(DungeonState.ACTIVE);
            if (instance.areWavesCompleted()) {
                if (instance.hasNextBoss()) {
                    BossManager.getInstance().spawnNextBoss(instance);
                    ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, progression reprise.")
                            .withStyle(ChatFormatting.AQUA), false);
                } else if (instance.allRequiredBossesDefeated()) {
                    DungeonManager.getInstance().completeDungeon(dungeonId);
                    ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, donjon termine!")
                            .withStyle(ChatFormatting.AQUA), false);
                }
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss inter-vague skip, vague suivante reprise.")
                        .withStyle(ChatFormatting.AQUA), false);
            }
        } else if (instance.hasNextBoss()) {
            BossManager.getInstance().spawnNextBoss(instance);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, prochain boss spawn!")
                    .withStyle(ChatFormatting.AQUA), false);
        } else if (instance.allRequiredBossesDefeated() && (!instance.hasWaves() || instance.areWavesCompleted())) {
            DungeonManager.getInstance().completeDungeon(dungeonId);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, donjon termine!")
                    .withStyle(ChatFormatting.AQUA), false);
        } else {
            instance.setState(DungeonState.ACTIVE);
            ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Boss skip, progression du donjon reprise.")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        return 1;
    }

    static int debugComplete(CommandContext<CommandSourceStack> ctx) {
        String dungeonId = StringArgumentType.getString(ctx, "dungeon");
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia Debug] Aucune instance active: " + dungeonId));
            return 0;
        }
        DungeonManager.getInstance().completeDungeon(dungeonId);
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Donjon " + dungeonId + " force complete! Mobs nettoyes, joueurs TP.")
                .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    static int debugResetCooldown(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("[Arcadia] Commande joueur uniquement!"));
            return 0;
        }
        DungeonManager.getInstance().clearCooldown(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcadia Debug] Tous vos cooldowns ont ete reset!")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }
}

