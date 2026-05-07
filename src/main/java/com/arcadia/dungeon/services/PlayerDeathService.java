package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.ServerPayloadHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service mort joueur in-run — recordDeath, broadcast, respawn différé (Stories S5.2, S5.3).
 *
 * <p>Respawn planifié via un ScheduledExecutorService daemon (thread unique).
 * Le callback est réinjecté sur le SGT via {@code server.execute()}.
 * Doit être enregistré sur {@code NeoForge.EVENT_BUS}.
 */
public final class PlayerDeathService {

    private static final long RESPAWN_DELAY_S = 10L;

    private final RunLifecycleService runLifecycleService;
    private final RoomProgressionService roomProgressionService;
    private final RewardDistributionService rewardDistributionService;

    private final Map<UUID, ScheduledFuture<?>> pendingRespawns = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "arcadia-respawn");
        t.setDaemon(true);
        return t;
    });

    public PlayerDeathService(RunLifecycleService runLifecycleService,
                              RoomProgressionService roomProgressionService,
                              RewardDistributionService rewardDistributionService) {
        this.runLifecycleService = runLifecycleService;
        this.roomProgressionService = roomProgressionService;
        this.rewardDistributionService = rewardDistributionService;
    }

    // ── S5.2 ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Run run = runLifecycleService.findActiveRunForPlayer(player.getUUID()).orElse(null);
        if (run == null || run.phase() == RunPhase.ENDED) return;

        int livesBefore = run.livesRemaining();
        run.recordDeath(player.getUUID());
        int livesAfter = run.livesRemaining();

        ArcadiaDungeon.LOGGER.info(
            "[Arcadia][LIVES] event=death playerId={} runId={} livesBefore={} livesAfter={}",
            player.getUUID(), run.id(), livesBefore, livesAfter);

        ServerPayloadHandler.broadcastRunState(run);

        if (livesAfter <= 0) {
            roomProgressionService.cleanupRun(run.id());
            runLifecycleService.completeRun(run, RunResult.DEFEAT);
            rewardDistributionService.distribute(run, RunResult.DEFEAT);
            ServerPayloadHandler.broadcastRunState(run);
            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=defeat_lives_exhausted runId={}", run.id());
        } else {
            // S5.3 — respawn différé 10 s
            scheduleRespawn(player.getUUID(), run.id(), player.getServer());
        }
    }

    // ── S5.3 ──────────────────────────────────────────────────────────────

    private void scheduleRespawn(UUID playerId, RunId runId, MinecraftServer server) {
        ScheduledFuture<?> future = scheduler.schedule(
            () -> server.execute(() -> executeRespawn(playerId, runId, server)),
            RESPAWN_DELAY_S, TimeUnit.SECONDS);
        ScheduledFuture<?> old = pendingRespawns.put(playerId, future);
        if (old != null) old.cancel(false);
        ArcadiaDungeon.LOGGER.info("[Arcadia][LIVES] event=respawn_scheduled playerId={} runId={} delayS={}",
            playerId, runId, RESPAWN_DELAY_S);
    }

    private void executeRespawn(UUID playerId, RunId runId, MinecraftServer server) {
        pendingRespawns.remove(playerId);

        Run run = runLifecycleService.findById(runId).orElse(null);
        if (run == null || run.phase() == RunPhase.ENDED) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;

        Vec3 spawnPos = roomProgressionService.getSpawnPosition(runId)
            .orElseGet(() -> ArcadiaDungeon.placementRegistry()
                .getSpawn(run.dungeonId())
                .orElse(player.position()));

        // Résoudre la dimension du donjon — après mort MC respawn en overworld,
        // il faut player.teleportTo(level, ...) pour changer de dimension
        ServerLevel targetLevel = ArcadiaDungeon.placementRegistry()
            .getDimension(run.dungeonId())
            .map(dimId -> {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimId));
                return server.getLevel(key);
            })
            .orElseGet(player::serverLevel);
        if (targetLevel == null) targetLevel = player.serverLevel();

        player.teleportTo(targetLevel, spawnPos.x, spawnPos.y, spawnPos.z,
            player.getYRot(), player.getXRot());

        ServerPayloadHandler.broadcastRunState(run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][LIVES] event=respawn playerId={} runId={}",
            playerId, runId);
    }

    /** Annule les respawns en attente quand le serveur s'arrête. */
    public void shutdown() {
        pendingRespawns.values().forEach(f -> f.cancel(false));
        pendingRespawns.clear();
        scheduler.shutdownNow();
    }
}
