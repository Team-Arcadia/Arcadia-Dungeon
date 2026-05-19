package com.arcadia.dungeon.domain.run;

import com.arcadia.dungeon.ArcadiaDungeon;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agrégat domain — état d'une run de donjon (lifecycle + state + boss + lives + chrono).
 *
 * <p>Boss state inclus dans Run (cf. architecture-v1 §4.1 — pas d'agrégat Boss séparé).
 *
 * <p><b>Règle critique</b> : toute mutation de cet agrégat DOIT s'effectuer sur le
 * ServerGameThread (cf. C-ARCH-2 + architecture-v1 §8.1). Enforce via {@link #requireSGT()}.
 */
public final class Run {

    private final RunId id;
    private final String dungeonId;
    private final List<UUID> playerIds;
    private RunPhase phase;
    private int currentRoomIndex;
    private int livesRemaining;
    private long startTimestampMs;
    private long endTimestampMs;
    private long launchCountdownEndMs;
    private RunResult result;

    private int currentWaveIndex;
    private final int totalRooms;
    // Archétype choisi par joueur — préparation S3 (multi), utilisé en S6.6
    private final Map<UUID, String> playerArchetypes;
    // Boss state inclus dans Run, pas un agrégat séparé
    private BossState bossState;

    public Run(RunId id, String dungeonId, List<UUID> playerIds, int livesRemaining, int totalRooms) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.playerIds = new ArrayList<>(playerIds);
        this.phase = RunPhase.STARTING;
        this.currentRoomIndex = 0;
        this.currentWaveIndex = 0;
        this.livesRemaining = livesRemaining;
        this.totalRooms = totalRooms;
        this.startTimestampMs = System.currentTimeMillis();
        this.playerArchetypes = new HashMap<>();
    }

    /**
     * Check runtime SGT obligatoire (PAS le mot-clé Java {@code assert} qui est désactivable).
     * Tout appelant qui mute l'état de Run hors SGT lève {@link IllegalStateException}.
     */
    private void requireSGT() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException(
                "Run mutation off-thread: " + Thread.currentThread().getName()
                    + " (must be ServerGameThread). Use server.submit() or event.enqueueWork()."
            );
        }
    }

    // === Getters (lecture libre) ===

    public RunId id() { return id; }
    public String dungeonId() { return dungeonId; }
    public List<UUID> playerIds() { return List.copyOf(playerIds); }
    public RunPhase phase() { return phase; }
    public int currentRoomIndex() { return currentRoomIndex; }
    public int currentWaveIndex() { return currentWaveIndex; }
    public int totalRooms() { return totalRooms; }
    public int livesRemaining() { return livesRemaining; }
    public long startTimestampMs() { return startTimestampMs; }
    public long endTimestampMs() { return endTimestampMs; }
    public long launchCountdownEndMs() { return launchCountdownEndMs; }
    public RunResult result() { return result; }
    public BossState bossState() { return bossState; }
    public Map<UUID, String> playerArchetypes() { return Map.copyOf(playerArchetypes); }

    public boolean launchCountdownActive() {
        return phase == RunPhase.STARTING && launchCountdownEndMs > 0L;
    }

    public long elapsedSeconds() {
        long end = phase == RunPhase.ENDED ? endTimestampMs : System.currentTimeMillis();
        return (end - startTimestampMs) / 1000L;
    }

    // === Mutations (SGT enforced) ===

    public void advanceToNextRoom() {
        requireSGT();
        currentRoomIndex++;
        currentWaveIndex = 0;
    }

    public void nextWave() {
        requireSGT();
        currentWaveIndex++;
    }

    public void setArchetype(UUID playerId, String archetypeId) {
        requireSGT();
        playerArchetypes.put(playerId, archetypeId);
    }

    public void recordDeath(UUID playerId) {
        requireSGT();
        if (livesRemaining > 0) livesRemaining--;
    }

    public void completeRun(RunResult r) {
        requireSGT();
        if (this.phase == RunPhase.ENDED) return; // idempotent
        this.result = r;
        this.phase = RunPhase.ENDED;
        this.launchCountdownEndMs = 0L;
        this.bossState = null;
        this.endTimestampMs = System.currentTimeMillis();
    }

    public void startActivePhase() {
        requireSGT();
        this.phase = RunPhase.IN_PROGRESS;
        this.launchCountdownEndMs = 0L;
        this.startTimestampMs = System.currentTimeMillis();
    }

    public void setBossState(BossState bossState) {
        requireSGT();
        this.bossState = bossState;
    }

    public void addPlayer(UUID playerId) {
        requireSGT();
        if (!playerIds.contains(playerId)) playerIds.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        requireSGT();
        playerIds.remove(playerId);
        playerArchetypes.remove(playerId);
    }

    public boolean hasPlayers() {
        return !playerIds.isEmpty();
    }

    public void scheduleLaunchCountdown(long endTimestampMs) {
        requireSGT();
        if (phase != RunPhase.STARTING) return;
        this.launchCountdownEndMs = Math.max(System.currentTimeMillis(), endTimestampMs);
    }

    public void clearLaunchCountdown() {
        requireSGT();
        this.launchCountdownEndMs = 0L;
    }
}
