package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Nettoyage des runs zombie — runs restées actives au-delà de {@value TIMEOUT_MIN} min
 * sans se terminer normalement (Story S7.2 AC5).
 *
 * <p>Vérifie toutes les {@value CHECK_INTERVAL_S} secondes sur un thread daemon.
 * Les mutations (completeRun) sont réinjectées sur le SGT via {@code server.execute()}.
 * Doit être démarré via {@link #start()} après {@code ServerStartingEvent}.
 */
public final class RunCleanupService {

    private static final long TIMEOUT_MIN       = 30L;
    private static final long TIMEOUT_MS        = TIMEOUT_MIN * 60L * 1_000L;
    private static final long CHECK_INTERVAL_S  = 60L;

    private final RunLifecycleService     runLifecycleService;
    private final RoomProgressionService  roomProgressionService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "arcadia-cleanup");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> task;

    public RunCleanupService(RunLifecycleService runLifecycleService,
                             RoomProgressionService roomProgressionService) {
        this.runLifecycleService    = runLifecycleService;
        this.roomProgressionService = roomProgressionService;
    }

    public void start() {
        task = scheduler.scheduleAtFixedRate(
            this::checkZombieRuns, CHECK_INTERVAL_S, CHECK_INTERVAL_S, TimeUnit.SECONDS);
        ArcadiaDungeon.LOGGER.info(
            "[Arcadia][CLEANUP] event=started interval={}s timeout={}min",
            CHECK_INTERVAL_S, TIMEOUT_MIN);
    }

    public void shutdown() {
        if (task != null) task.cancel(false);
        scheduler.shutdownNow();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void checkZombieRuns() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = System.currentTimeMillis();

        runLifecycleService.activeRuns().forEach((runId, run) -> {
            if (run.phase() == RunPhase.ENDED) return;
            if (now - run.startTimestampMs() < TIMEOUT_MS) return;

            ArcadiaDungeon.LOGGER.warn(
                "[Arcadia][CLEANUP] event=zombie_detected runId={} ageMs={}",
                runId, now - run.startTimestampMs());

            server.execute(() -> terminateZombie(runId));
        });
    }

    private void terminateZombie(RunId runId) {
        Run run = runLifecycleService.findById(runId).orElse(null);
        if (run == null || run.phase() == RunPhase.ENDED) return;

        // TODO: kill tracked mobs on cleanup — cleanupRun() supprime le tracking mais ne kill
        //       pas les entités en jeu. RunCleanupService n'a pas accès au ServerLevel.
        //       Solution v1.1 : passer server.getAllLevels() en paramètre et hurt les entités.
        roomProgressionService.cleanupRun(runId);
        runLifecycleService.completeRun(run, RunResult.ABANDONED);

        ArcadiaDungeon.LOGGER.warn(
            "[Arcadia][CLEANUP] event=zombie_cleaned runId={}", runId);
    }
}
