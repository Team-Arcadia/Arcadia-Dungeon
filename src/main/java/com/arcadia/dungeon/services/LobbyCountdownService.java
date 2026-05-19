package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.ServerPayloadHandler;
import com.arcadia.dungeon.network.StructurePlacementStatusPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative visual launch countdown for STARTING lobbies.
 */
public final class LobbyCountdownService {

    private final RunLifecycleService runLifecycleService;
    private final RoomProgressionService roomProgressionService;
    private final Map<RunId, PendingLaunch> pendingLaunches = new ConcurrentHashMap<>();

    private int tickCursor = 0;

    public LobbyCountdownService(RunLifecycleService runLifecycleService,
                                 RoomProgressionService roomProgressionService) {
        this.runLifecycleService = runLifecycleService;
        this.roomProgressionService = roomProgressionService;
    }

    public void requestLaunch(ServerPlayer leader, Run run) {
        if (run.launchCountdownActive() || pendingLaunches.containsKey(run.id())) {
            ArcadiaToast.info(leader, "arcadia.server.run.countdown_active");
            leader.connection.send(com.arcadia.dungeon.network.RunStatePayload.from(run));
            return;
        }

        DungeonConfig config = ArcadiaDungeon.dungeonRegistry().get(run.dungeonId()).orElse(null);
        if (config == null) {
            ArcadiaToast.error(leader, "arcadia.server.run.dungeon_missing", run.dungeonId());
            runLifecycleService.completeRun(run, RunResult.ABANDONED);
            return;
        }
        int minPlayers = config.minPlayersOrDefault();
        if (run.playerIds().size() < minPlayers) {
            ArcadiaToast.warn(leader, "arcadia.server.run.waiting_min_players", run.playerIds().size(), minPlayers);
            leader.connection.send(com.arcadia.dungeon.network.RunStatePayload.from(run));
            return;
        }

        int countdownSeconds = config.lobbyCountdownSecondsOrDefault();
        run.scheduleLaunchCountdown(System.currentTimeMillis() + countdownSeconds * 1000L);
        boolean requiresInstance = ArcadiaDungeon.dungeonInstanceService().requiresRuntimeInstance(config);
        pendingLaunches.put(run.id(), new PendingLaunch(leader.getUUID(), requiresInstance));
        ServerPayloadHandler.broadcastRunState(run);
        ArcadiaToast.info(leader, "arcadia.server.run.countdown_started", countdownSeconds);
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=lobby_countdown_started runId={} player={} seconds={} instanceRegen={}",
            run.id(), leader.getGameProfile().getName(), countdownSeconds, requiresInstance);

        if (requiresInstance) {
            startInstancePreparation(leader, run, config);
        }
        tryActivate(leader.getServer(), run);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCursor % 5 != 0) return;
        MinecraftServer server = event.getServer();
        pendingLaunches.keySet().removeIf(id -> runLifecycleService.findById(id).isEmpty());
        for (Run run : runLifecycleService.activeRuns().values()) {
            if (run.phase() != RunPhase.STARTING || !run.launchCountdownActive()) continue;
            tryActivate(server, run);
        }
    }

    public void clear(Run run) {
        if (run != null) pendingLaunches.remove(run.id());
    }

    private void startInstancePreparation(ServerPlayer leader, Run run, DungeonConfig config) {
        ArcadiaToast.info(leader, "arcadia.server.run.instance_generating");
        ArcadiaDungeon.dungeonInstanceService().prepareRunInstance(leader.getServer(), run, config, prepared -> {
            PendingLaunch pending = pendingLaunches.get(run.id());
            if (pending == null || runLifecycleService.findById(run.id()).isEmpty()) {
                ArcadiaDungeon.dungeonInstanceService().cleanupRun(run);
                return;
            }
            pending.prepared = prepared;
            tryActivate(leader.getServer(), run);
        }, message -> failLaunch(leader, run, message), progress -> broadcastGenerationProgress(leader.getServer(), run, progress));
    }

    private void failLaunch(ServerPlayer leader, Run run, Component message) {
        pendingLaunches.remove(run.id());
        if (runLifecycleService.findById(run.id()).isPresent()) {
            runLifecycleService.completeRun(run, RunResult.ABANDONED);
        }
        ArcadiaToast.error(leader, "arcadia.server.run.instance_failed", message.getString());
    }

    private void tryActivate(MinecraftServer server, Run run) {
        if (run.phase() != RunPhase.STARTING || !run.launchCountdownActive()) {
            pendingLaunches.remove(run.id());
            return;
        }
        DungeonConfig config = ArcadiaDungeon.dungeonRegistry().get(run.dungeonId()).orElse(null);
        if (config != null && run.playerIds().size() < config.minPlayersOrDefault()) {
            PendingLaunch pending = pendingLaunches.remove(run.id());
            if (pending != null && pending.prepared != null) {
                ArcadiaDungeon.dungeonInstanceService().cleanupRun(run);
            }
            run.clearLaunchCountdown();
            ServerPayloadHandler.broadcastRunState(run);
            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=lobby_countdown_cancelled runId={} reason=min_players current={} min={}",
                run.id(), run.playerIds().size(), config.minPlayersOrDefault());
            return;
        }
        PendingLaunch pending = pendingLaunches.get(run.id());
        if (pending == null) {
            if (run.launchCountdownEndMs() <= System.currentTimeMillis()) {
                ServerPlayer leader = firstOnlinePlayer(server, run);
                if (leader != null) {
                    ServerPayloadHandler.launchLobbyRun(runLifecycleService, roomProgressionService, leader, run);
                }
            }
            return;
        }
        if (run.launchCountdownEndMs() > System.currentTimeMillis()) return;

        ServerPlayer leader = server.getPlayerList().getPlayer(pending.leaderId);
        if (leader == null) leader = firstOnlinePlayer(server, run);
        if (leader == null) {
            pendingLaunches.remove(run.id());
            runLifecycleService.completeRun(run, RunResult.ABANDONED);
            return;
        }

        if (pending.requiresInstance) {
            if (pending.prepared == null) return;
            pendingLaunches.remove(run.id());
            ServerPayloadHandler.activatePreparedLobbyRun(runLifecycleService, roomProgressionService, run, pending.prepared);
            return;
        }

        pendingLaunches.remove(run.id());
        ServerPayloadHandler.launchLobbyRun(runLifecycleService, roomProgressionService, leader, run);
    }

    private static ServerPlayer firstOnlinePlayer(MinecraftServer server, Run run) {
        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) return player;
        }
        return null;
    }

    private static void broadcastGenerationProgress(MinecraftServer server,
                                                    Run run,
                                                    StructurePlacementScheduler.Progress progress) {
        if (server == null || run == null || progress == null) return;
        StructurePlacementStatusPayload payload = new StructurePlacementStatusPayload(
            run.dungeonId(),
            progress.stage(),
            progress.processed(),
            progress.total(),
            progress.done(),
            progress.success(),
            progress.message()
        );
        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.connection.send(payload);
            }
        }
    }

    private static final class PendingLaunch {
        private final UUID leaderId;
        private final boolean requiresInstance;
        private DungeonInstanceService.PreparedInstance prepared;

        private PendingLaunch(UUID leaderId, boolean requiresInstance) {
            this.leaderId = leaderId;
            this.requiresInstance = requiresInstance;
        }
    }
}
