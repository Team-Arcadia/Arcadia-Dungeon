package com.arcadia.dungeon.service.admin;

import com.arcadia.dungeon.boss.BossInstance;
import com.arcadia.dungeon.boss.BossManager;
import com.arcadia.dungeon.command.ArcadiaCommandHelper;
import com.arcadia.dungeon.config.ArcadiaProgressionConfig;
import com.arcadia.dungeon.config.BossConfig;
import com.arcadia.dungeon.config.ConfigManager;
import com.arcadia.dungeon.config.DungeonConfig;
import com.arcadia.dungeon.config.PhaseConfig;
import com.arcadia.dungeon.config.SpawnPointConfig;
import com.arcadia.dungeon.dungeon.DungeonInstance;
import com.arcadia.dungeon.dungeon.DungeonManager;
import com.arcadia.dungeon.dungeon.DungeonState;
import com.arcadia.dungeon.dungeon.PlayerProgress;
import com.arcadia.dungeon.dungeon.PlayerProgressManager;
import com.arcadia.dungeon.dungeon.WeeklyLeaderboard;
import com.arcadia.dungeon.event.DungeonEventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminGuiActionService {
    private AdminGuiActionService() {}

    @FunctionalInterface
    public interface MessageSink {
        void send(Component component);
    }

    public static boolean startDungeon(ServerPlayer player, String dungeonId) {
        if (player == null) return false;
        return DungeonManager.getInstance().startDungeon(dungeonId, player) != null;
    }

    public static void showPlayerProfile(ServerPlayer player) {
        showPlayerProfile(player, player == null ? null : player.getUUID().toString());
    }

    public static void showPlayerProfile(ServerPlayer player, String playerRef) {
        if (player == null) return;
        showPlayerProfile(player::sendSystemMessage, player.getUUID().toString(), player.getName().getString(), playerRef);
    }

    public static void showPlayerProfile(MessageSink sink, String fallbackUuid, String fallbackName, String playerRef) {
        if (sink == null || fallbackUuid == null || fallbackName == null) return;
        PlayerProgress progress = resolveProgress(playerRef, fallbackUuid, fallbackName);
        ArcadiaProgressionConfig progressionConfig = ConfigManager.getInstance().getProgressionConfig();
        PlayerProgress.ArcadiaProgress arcadia = progress.arcadiaProgress;
        long nextXp = progressionConfig.getXpRequiredForNextLevel(arcadia.arcadiaLevel, arcadia.arcadiaXp);
        ChatFormatting rankColor = ArcadiaCommandHelper.parseLegacyColor(progressionConfig.getRankColorForLevel(arcadia.arcadiaLevel));
        String displayName = displayPlayer(progress);

        send(sink, Component.literal("========= Perfil Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        send(sink, Component.literal(" Jugador: ").withStyle(ChatFormatting.GRAY).append(Component.literal(displayName).withStyle(ChatFormatting.WHITE)));
        send(sink, Component.literal(" Nivel: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(arcadia.arcadiaLevel)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        send(sink, Component.literal(" Rango: ").withStyle(ChatFormatting.GRAY).append(Component.literal(arcadia.arcadiaRank).withStyle(rankColor, ChatFormatting.BOLD)));
        send(sink, Component.literal(" XP: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(arcadia.arcadiaXp)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(nextXp > 0 ? " (" + nextXp + " para el siguiente nivel)" : " (nivel max config)").withStyle(ChatFormatting.DARK_GRAY)));
        send(sink, Component.literal(" Racha semanal: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(arcadia.weeklyStreak + " semana(s)").withStyle(ChatFormatting.AQUA)));
        send(sink, Component.literal(" Completaciones: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(progress.getTotalCompletions())).withStyle(ChatFormatting.WHITE)));
        send(sink, Component.literal("==================================").withStyle(ChatFormatting.GOLD));
    }

    public static void showPlayerTop(ServerPlayer player) {
        if (player == null) return;
        showPlayerTop(player::sendSystemMessage);
    }

    public static void showPlayerTop(MessageSink sink) {
        if (sink == null) return;
        List<PlayerProgress> top = PlayerProgressManager.getInstance().getTopPlayers(10);
        send(sink, Component.literal("=== Top Jugadores Arcadia ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (top.isEmpty()) {
            send(sink, Component.literal("Ninguna completacion registrada.").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            int rank = i + 1;
            PlayerProgress pp = top.get(i);
            pp.normalize();
            ChatFormatting rankColor = rank <= 3 ? (rank == 1 ? ChatFormatting.GOLD : rank == 2 ? ChatFormatting.GRAY : ChatFormatting.RED) : ChatFormatting.WHITE;
            ChatFormatting arcadiaRankColor = ArcadiaCommandHelper.parseLegacyColor(ConfigManager.getInstance().getProgressionConfig()
                    .getRankColorForLevel(pp.arcadiaProgress.arcadiaLevel));
            send(sink, Component.literal(" " + rank + ". ").withStyle(rankColor, ChatFormatting.BOLD)
                    .append(Component.literal(displayPlayer(pp)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Niv. " + pp.arcadiaProgress.arcadiaLevel).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(pp.arcadiaProgress.arcadiaRank).withStyle(arcadiaRankColor))
                    .append(Component.literal(" | " + pp.arcadiaProgress.arcadiaXp + " XP").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" | " + pp.getTotalCompletions() + " completaciones").withStyle(ChatFormatting.GRAY)));
        }
    }

    public static void showPlayerStats(ServerPlayer player) {
        showPlayerStats(player, player == null ? null : player.getUUID().toString());
    }

    public static void showPlayerStats(ServerPlayer player, String playerRef) {
        if (player == null) return;
        showPlayerStats(player::sendSystemMessage, player.getUUID().toString(), player.getName().getString(), playerRef);
    }

    public static void showPlayerStats(MessageSink sink, String fallbackUuid, String fallbackName, String playerRef) {
        if (sink == null || fallbackUuid == null || fallbackName == null) return;
        PlayerProgress progress = resolveProgress(playerRef, fallbackUuid, fallbackName);
        send(sink, Component.literal("=== Stats: " + displayPlayer(progress) + " ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        send(sink, Component.literal("Completaciones totales: " + progress.getTotalCompletions()).withStyle(ChatFormatting.AQUA));
        send(sink, Component.literal("Mazmorras completadas: " + progress.completedDungeons.size()).withStyle(ChatFormatting.AQUA));
        if (!progress.completedDungeons.isEmpty()) {
            send(sink, Component.literal("Detalles:").withStyle(ChatFormatting.YELLOW));
            for (Map.Entry<String, PlayerProgress.DungeonProgress> entry : progress.completedDungeons.entrySet()) {
                String dId = entry.getKey();
                PlayerProgress.DungeonProgress dp = entry.getValue();
                DungeonConfig dc = ConfigManager.getInstance().getDungeon(dId);
                String dName = dc != null ? dc.name : dId;
                send(sink, Component.literal("  - " + dName + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(dp.completions + "x").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | Record: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(ArcadiaCommandHelper.formatTime(dp.bestTimeSeconds)).withStyle(ChatFormatting.GREEN)));
            }
        }
    }

    public static void showPlayerProgression(ServerPlayer player) {
        if (player == null) return;
        showPlayerProgression(player::sendSystemMessage, player.getUUID().toString(), player.getName().getString());
    }

    public static void showPlayerProgression(MessageSink sink, String fallbackUuid, String fallbackName) {
        if (sink == null || fallbackUuid == null || fallbackName == null) return;
        PlayerProgress progress = PlayerProgressManager.getInstance().getOrCreate(fallbackUuid, fallbackName);
        List<DungeonConfig> dungeons = new ArrayList<>(ConfigManager.getInstance().getDungeonConfigs().values());
        dungeons.sort(Comparator.comparingInt(d -> d.order));

        send(sink, Component.literal("========= Mazmorras Arcadia =========").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (DungeonConfig dungeon : dungeons) {
            if (!dungeon.enabled) continue;
            boolean completed = progress.hasCompleted(dungeon.id);
            boolean unlocked = ArcadiaCommandHelper.isDungeonUnlocked(progress, dungeon);
            PlayerProgress.DungeonProgress dp = progress.completedDungeons.get(dungeon.id);
            String bar;
            ChatFormatting color;
            String status;
            String details;

            if (completed) {
                bar = "##########";
                color = ChatFormatting.GREEN;
                status = "COMPLETA";
                details = dp.completions + "x | Record: " + ArcadiaCommandHelper.formatTime(dp.bestTimeSeconds);
            } else if (unlocked) {
                bar = ">>--------";
                color = ChatFormatting.YELLOW;
                status = "DISPONIBLE";
                details = "Haz clic para entrar";
            } else {
                bar = "----------";
                color = ChatFormatting.RED;
                status = "BLOQUEADA";
                DungeonConfig req = ConfigManager.getInstance().getDungeon(dungeon.requiredDungeon);
                details = dungeon.requiredArcadiaLevel > progress.arcadiaProgress.arcadiaLevel
                        ? "Nivel Arcadia requerido: " + dungeon.requiredArcadiaLevel
                        : "Requerido: " + (req != null ? req.name : dungeon.requiredDungeon);
            }

            MutableComponent line = Component.literal(" ")
                    .append(Component.literal("[" + bar + "] ").withStyle(color))
                    .append(Component.literal(dungeon.order + ". ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(dungeon.name).withStyle(color, ChatFormatting.BOLD))
                    .append(Component.literal(" "))
                    .append(Component.literal("[" + status + "]").withStyle(color))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(details))));
            send(sink, line);
            send(sink, Component.literal("   " + details).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        int total = progress.getTotalCompletions();
        int completedCount = (int) dungeons.stream().filter(d -> d.enabled && progress.hasCompleted(d.id)).count();
        int totalEnabled = (int) dungeons.stream().filter(d -> d.enabled).count();
        send(sink, Component.literal("====================================").withStyle(ChatFormatting.GOLD));
        send(sink, Component.literal(" Progreso: " + completedCount + "/" + totalEnabled + " mazmorras | " + total + " completaciones totales").withStyle(ChatFormatting.AQUA));
    }

    public static void showArcadiaAdminOverview(ServerPlayer player) {
        if (player == null) return;
        showArcadiaAdminOverview(player::sendSystemMessage);
    }

    public static void showArcadiaAdminOverview(MessageSink sink) {
        if (sink == null) return;
        ArcadiaProgressionConfig config = ConfigManager.getInstance().getProgressionConfig();
        config.normalize();
        send(sink, Component.literal("======= Arcadia Admin =======").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        send(sink, Component.literal(" XP por defecto: " + config.defaultDungeonXp).withStyle(ChatFormatting.YELLOW));
        send(sink, Component.literal(" Rangos:").withStyle(ChatFormatting.AQUA));
        for (ArcadiaProgressionConfig.RankThreshold rank : config.ranks) {
            ChatFormatting rankColor = ArcadiaCommandHelper.parseLegacyColor(rank.color);
            send(sink, Component.literal("  - niv " + rank.minLevel + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(rank.rankName + " (" + rank.color + ")").withStyle(rankColor)));
        }
        send(sink, Component.literal(" Hitos:").withStyle(ChatFormatting.AQUA));
        for (ArcadiaProgressionConfig.MilestoneReward milestone : config.milestoneRewards) {
            int rewardCount = milestone.rewards == null ? 0 : milestone.rewards.size();
            send(sink, Component.literal("  - niv " + milestone.level + ": " + rewardCount + " recompensa(s)").withStyle(ChatFormatting.GRAY));
        }
        send(sink, Component.literal(" Rachas:").withStyle(ChatFormatting.AQUA));
        for (ArcadiaProgressionConfig.StreakBonus streak : config.streakBonuses) {
            send(sink, Component.literal("  - " + streak.weeks + " semana(s): +" + streak.xpBonus + " XP").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void reloadConfigs(ServerPlayer player) {
        if (player == null) return;
        reloadConfigs(player::sendSystemMessage);
    }

    public static void reloadConfigs(MessageSink sink) {
        ConfigManager.getInstance().loadAll();
        PlayerProgressManager.getInstance().loadAll();
        send(sink, Component.literal("[Arcadia] Configuraciones recargadas! " +
                ConfigManager.getInstance().getDungeonConfigs().size() + " mazmorra(s), " +
                PlayerProgressManager.getInstance().getAll().size() + " jugador(es).").withStyle(ChatFormatting.AQUA));
    }

    public static void showActiveInstances(ServerPlayer player) {
        if (player == null) return;
        showActiveInstances(player::sendSystemMessage);
    }

    public static void showActiveInstances(MessageSink sink) {
        if (sink == null) return;
        Map<String, DungeonInstance> active = DungeonManager.getInstance().getActiveInstances();
        if (active.isEmpty()) {
            send(sink, Component.literal("[Arcadia Debug] Ninguna mazmorra activa.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        send(sink, Component.literal("=== Mazmorras Activas ===").withStyle(ChatFormatting.AQUA));
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(active.entrySet())) {
            String key = entry.getKey();
            DungeonInstance inst = entry.getValue();
            send(sink, Component.literal(" - " + key + " | Estado: " + inst.getState() +
                    " | Jugadores: " + inst.getPlayerCount() + " | Tiempo: " + ArcadiaCommandHelper.formatTime(inst.getElapsedSeconds()))
                    .withStyle(ChatFormatting.WHITE));
        }
    }

    public static void showDungeonInfo(ServerPlayer player, String dungeonId) {
        if (player == null) return;
        showDungeonInfo(player::sendSystemMessage, dungeonId);
    }

    public static void showDungeonInfo(MessageSink sink, String dungeonId) {
        if (sink == null) return;
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            send(sink, Component.literal("[Arcadia] Mazmorra no encontrada: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        send(sink, Component.literal("=== " + config.name + " ===").withStyle(ChatFormatting.GOLD));
        send(sink, Component.literal("ID: " + config.id).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Cooldown: " + config.cooldownSeconds + "s").withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Disponible cada: " + (config.availableEverySeconds > 0 ? config.availableEverySeconds + "s" : "siempre")).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Anuncio periodico: " + (config.announceIntervalMinutes > 0 ? config.announceIntervalMinutes + " min" : "desactivado")).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("TP al completar: " + (config.teleportBackOnComplete ? "Si" : "No")).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Anunciar inicio: " + (config.announceStart ? "Si" : "No")).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Anunciar fin: " + (config.announceCompletion ? "Si" : "No")).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Jugadores max: " + config.settings.maxPlayers).withStyle(ChatFormatting.GRAY));
        send(sink, Component.literal("Tiempo limite: " + config.settings.timeLimitSeconds + "s").withStyle(ChatFormatting.GRAY));
        if (config.spawnPoint != null) {
            send(sink, Component.literal("Spawn: " + config.spawnPoint.dimension + " [" +
                    (int) config.spawnPoint.x + ", " + (int) config.spawnPoint.y + ", " + (int) config.spawnPoint.z + "]").withStyle(ChatFormatting.GRAY));
        }
        send(sink, Component.literal("Jefes (" + config.bosses.size() + "):").withStyle(ChatFormatting.YELLOW));
        for (BossConfig boss : config.bosses) {
            send(sink, Component.literal("  - " + boss.customName + " (" + boss.entityType + ") HP:" + boss.baseHealth + " DMG:" + boss.baseDamage +
                    " Fases:" + boss.phases.size() + " Recompensas:" + boss.rewards.size()).withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forceResetDungeon(ServerPlayer player, String dungeonId) {
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            send(player, Component.literal("[Arcadia] Ninguna instancia activa: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        DungeonManager.getInstance().forceReset(dungeonId);
        send(player, Component.literal("[Arcadia] Mazmorra " + dungeonId + " reiniciada. Jugadores expulsados, mobs eliminados.").withStyle(ChatFormatting.GREEN));
    }

    public static void debugInfo(ServerPlayer player, String dungeonId) {
        if (player == null) return;
        debugInfo(player::sendSystemMessage, dungeonId);
    }

    public static void debugInfo(MessageSink sink, String dungeonId) {
        if (sink == null) return;
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            send(sink, Component.literal("[Arcadia Debug] Ninguna instancia activa: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        DungeonConfig config = instance.getConfig();
        int totalBosses = config.bosses == null ? 0 : config.bosses.size();
        int bossCount = instance.getActiveBosses().size();
        send(sink, Component.literal("=== Debug " + config.name + " ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        send(sink, Component.literal("  Estado: " + instance.getState() + " | Jugadores: " + instance.getPlayerCount() + " | Tiempo: " + ArcadiaCommandHelper.formatTime(instance.getElapsedSeconds())).withStyle(ChatFormatting.WHITE));
        send(sink, Component.literal("  Jefes total: " + totalBosses + " (activos: " + bossCount + ")").withStyle(ChatFormatting.WHITE));
        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            String bossId = entry.getKey();
            boolean alive = boss.isBossAlive();
            int phase = boss.getCurrentPhase() + 1;
            String hp = "";
            if (boss.getBossEntity() != null) {
                hp = " HP: " + String.format("%.1f", boss.getBossEntity().getHealth()) + "/" + String.format("%.1f", boss.getBossEntity().getMaxHealth());
            }
            boolean trans = boss.isTransitioning();
            boolean needKill = boss.requiresKillSummons();
            send(sink, Component.literal("    " + bossId + ": " + (alive ? "Vivo" : "Muerto") + " Fase:" + phase + hp
                    + (trans ? " [TRANSICION]" : "") + (needKill ? " [MATAR INVOCACIONES]" : "")).withStyle(alive ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    public static void debugSpawnBoss(ServerPlayer player, String dungeonId) {
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            send(player, Component.literal("[Arcadia Debug] Ninguna instancia activa: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
        send(player, Component.literal("[Arcadia Debug] Jefe spawneado: " + (spawned ? "Si" : "No (no hay mas jefes)")).withStyle(ChatFormatting.AQUA));
    }

    public static void debugSkipBoss(ServerPlayer player, String dungeonId) {
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            send(player, Component.literal("[Arcadia Debug] Ninguna instancia activa: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        if (instance.getActiveBosses().isEmpty()) {
            send(player, Component.literal("[Arcadia Debug] Ningun jefe activo para saltear!").withStyle(ChatFormatting.RED));
            return;
        }

        boolean skippedInterWaveBoss = instance.isWaitingForInterWaveBoss();
        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            String bossId = entry.getKey();
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer member = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                if (member != null) {
                    instance.giveRewards(member, boss.getConfig().rewards);
                    send(member, Component.literal("[Arcadia Debug] Jefe " + boss.getConfig().customName + " salteado!").withStyle(ChatFormatting.AQUA));
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
                    send(player, Component.literal("[Arcadia Debug] Jefe entre-oleadas salteado, progresion reanudada.").withStyle(ChatFormatting.AQUA));
                } else if (instance.allRequiredBossesDefeated()) {
                    DungeonManager.getInstance().completeDungeon(dungeonId);
                    send(player, Component.literal("[Arcadia Debug] Jefe entre-oleadas salteado, mazmorra terminada!").withStyle(ChatFormatting.AQUA));
                }
            } else {
                send(player, Component.literal("[Arcadia Debug] Jefe entre-oleadas salteado, siguiente oleada reanudada.").withStyle(ChatFormatting.AQUA));
            }
        } else if (instance.hasNextBoss()) {
            BossManager.getInstance().spawnNextBoss(instance);
            send(player, Component.literal("[Arcadia Debug] Jefe salteado, siguiente jefe spawneado!").withStyle(ChatFormatting.AQUA));
        } else if (instance.allRequiredBossesDefeated() && (!instance.hasWaves() || instance.areWavesCompleted())) {
            DungeonManager.getInstance().completeDungeon(dungeonId);
            send(player, Component.literal("[Arcadia Debug] Jefe salteado, mazmorra terminada!").withStyle(ChatFormatting.AQUA));
        } else {
            instance.setState(DungeonState.ACTIVE);
            send(player, Component.literal("[Arcadia Debug] Jefe salteado, progresion de la mazmorra reanudada.").withStyle(ChatFormatting.AQUA));
        }
    }

    public static void debugComplete(ServerPlayer player, String dungeonId) {
        DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
        if (instance == null) {
            send(player, Component.literal("[Arcadia Debug] Ninguna instancia activa: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        DungeonManager.getInstance().completeDungeon(dungeonId);
        send(player, Component.literal("[Arcadia Debug] Mazmorra " + dungeonId + " forzada como completa! Mobs limpiados, jugadores TP.").withStyle(ChatFormatting.AQUA));
    }

    public static void setDungeonEnabled(ServerPlayer player, String dungeonId, boolean enabled) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            send(player, Component.literal("[Arcadia] Mazmorra no encontrada: " + dungeonId).withStyle(ChatFormatting.RED));
            return;
        }
        config.enabled = enabled;
        ConfigManager.getInstance().saveDungeon(config);
        send(player, Component.literal("[Arcadia] Mazmorra " + (enabled ? "activada" : "desactivada") + ": " + config.name).withStyle(ChatFormatting.GREEN));
    }

    public static void resetPlayerProgress(ServerPlayer player, String playerRef) {
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerRef);
        if (progress == null) {
            send(player, Component.literal("[Arcadia] Jugador no encontrado: " + playerRef).withStyle(ChatFormatting.RED));
            return;
        }
        PlayerProgressManager.getInstance().resetPlayer(progress.uuid);
        send(player, Component.literal("[Arcadia] Progresion de " + displayPlayer(progress) + " completamente reiniciada.").withStyle(ChatFormatting.GREEN));
    }

    public static boolean setPlayerDungeonProgress(ServerPlayer player, String playerRef, String dungeonId, int completions, long bestTime) {
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerRef);
        if (progress == null) {
            send(player, Component.literal("[Arcadia] Jugador no encontrado: " + playerRef).withStyle(ChatFormatting.RED));
            return false;
        }
        PlayerProgressManager.getInstance().setPlayerDungeon(progress.uuid, displayPlayer(progress), dungeonId, completions, bestTime);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        send(player, Component.literal("[Arcadia] " + displayPlayer(progress) + " -> " + dName + ": " + completions + " completaciones, record " + ArcadiaCommandHelper.formatTime(bestTime) + ".").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean resetPlayerDungeonProgress(ServerPlayer player, String playerRef, String dungeonId) {
        PlayerProgress progress = PlayerProgressManager.getInstance().findByName(playerRef);
        if (progress == null) {
            send(player, Component.literal("[Arcadia] Jugador no encontrado: " + playerRef).withStyle(ChatFormatting.RED));
            return false;
        }
        PlayerProgressManager.getInstance().resetPlayerDungeon(progress.uuid, dungeonId);
        DungeonConfig dc = ConfigManager.getInstance().getDungeon(dungeonId);
        String dName = dc != null ? dc.name : dungeonId;
        send(player, Component.literal("[Arcadia] Progresion de " + displayPlayer(progress) + " para " + dName + " reiniciada.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void giveAreaWand(ServerPlayer player, String dungeonId) {
        if (player == null) return;
        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_SHOVEL);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(DungeonEventHandler.AREA_WAND_TAG + " - Arcadia Dungeon Area Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        selectAreaWandDungeon(player, dungeonId);
    }

    public static void selectAreaWandDungeon(ServerPlayer player, String dungeonId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (player == null || config == null) return;
        DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        DungeonEventHandler.wandWall.remove(player.getUUID());
        DungeonEventHandler.wandPos1.remove(player.getUUID());
        DungeonEventHandler.wandPos2.remove(player.getUUID());
        send(player, Component.literal("[Arcadia Wand] Mazmorra seleccionada: " + config.name).withStyle(ChatFormatting.GOLD));
    }

    public static void giveWallWand(ServerPlayer player, String dungeonId, String wallId) {
        if (player == null) return;
        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HOE);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(DungeonEventHandler.WALL_WAND_TAG + " - Arcadia Scripted Wall Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        selectWallWand(player, dungeonId, wallId);
    }

    public static void giveWallWandOnly(ServerPlayer player) {
        if (player == null) return;
        net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HOE);
        wand.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(DungeonEventHandler.WALL_WAND_TAG + " - Arcadia Scripted Wall Wand").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        send(player, Component.literal("[Arcadia] Azada recibida! Selecciona luego un muro scripteado.").withStyle(ChatFormatting.GOLD));
    }

    public static void selectWallWand(ServerPlayer player, String dungeonId, String wallId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (player == null || config == null) return;
        DungeonConfig.ScriptedWallConfig wall = config.scriptedWalls.stream().filter(w -> wallId.equals(w.id)).findFirst().orElse(null);
        if (wall == null) {
            wall = new DungeonConfig.ScriptedWallConfig();
            wall.id = wallId;
            config.scriptedWalls.add(wall);
            ConfigManager.getInstance().saveDungeon(config);
        }
        DungeonEventHandler.wandDungeon.put(player.getUUID(), dungeonId);
        DungeonEventHandler.wandWall.put(player.getUUID(), wallId);
        DungeonEventHandler.wandPos1.remove(player.getUUID());
        DungeonEventHandler.wandPos2.remove(player.getUUID());
        int count = wall.blocks == null ? 0 : wall.blocks.size();
        send(player, Component.literal("[Arcadia Wall] Muro scripteado seleccionado: " + wallId + " (" + count + " bloque(s))").withStyle(ChatFormatting.GOLD));
    }

    private static String displayPlayer(PlayerProgress progress) {
        return progress.playerName == null || progress.playerName.isBlank() ? progress.uuid : progress.playerName;
    }

    private static PlayerProgress resolveProgress(String playerRef, String fallbackUuid, String fallbackName) {
        PlayerProgress progress = playerRef == null ? null : PlayerProgressManager.getInstance().findByName(playerRef);
        if (progress == null) {
            progress = PlayerProgressManager.getInstance().getOrCreate(fallbackUuid, fallbackName);
        }
        progress.normalize();
        return progress;
    }

    private static void send(MessageSink sink, Component component) {
        if (sink != null && component != null) {
            sink.send(component);
        }
    }

    private static void send(ServerPlayer player, Component component) {
        if (player != null && component != null) {
            player.sendSystemMessage(component);
        }
    }
}
