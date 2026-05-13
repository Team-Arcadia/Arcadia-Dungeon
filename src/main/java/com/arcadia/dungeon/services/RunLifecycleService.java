package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service cycle de vie des runs (Story S2.2).
 *
 * <p>Opérations : start, completeRun, abandonRun, findActiveRunForPlayer.
 * Mode SOLO uniquement en MVP — le multi est étendu en Sprint 3 (S3.1-S3.2).
 *
 * <p>Les mutations sur {@link Run} sont SGT-enforced via {@code requireSGT()}.
 * Les opérations de lecture ({@link #findActiveRunForPlayer}, {@link #activeRuns})
 * sont thread-safe via {@link ConcurrentHashMap}.
 */
public final class RunLifecycleService {

    record OriginPos(String dimensionId, double x, double y, double z, float yaw, float pitch) {}

    private final DungeonRegistry dungeonRegistry;
    private final Map<RunId, Run> activeRuns = new ConcurrentHashMap<>();
    private final Map<UUID, OriginPos> playerOrigins = new ConcurrentHashMap<>();

    private ArchetypeService archetypeService;

    public RunLifecycleService(DungeonRegistry dungeonRegistry) {
        this.dungeonRegistry = dungeonRegistry;
    }

    /** Injecté après construction (évite la dépendance circulaire). */
    public void setArchetypeService(ArchetypeService svc) {
        this.archetypeService = svc;
    }

    /**
     * Crée et enregistre une nouvelle run en phase STARTING.
     *
     * @throws IllegalArgumentException si le donjon n'existe pas dans le registry
     */
    public Run startRun(String dungeonId, List<UUID> playerIds) {
        DungeonConfig config = dungeonRegistry.get(dungeonId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown dungeon: " + dungeonId));
        int totalRooms = 1;
        Run run = new Run(RunId.generate(), dungeonId, playerIds, config.lives(), totalRooms);
        activeRuns.put(run.id(), run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=start runId={} dungeon={} players={}",
            run.id(), dungeonId, playerIds.size());
        return run;
    }

    /**
     * Termine une run (VICTORY ou DEFEAT) et la retire des runs actives.
     * Doit être appelé sur le SGT (run.completeRun() enforce via requireSGT).
     */
    public void completeRun(Run run, RunResult result) {
        run.completeRun(result);
        activeRuns.remove(run.id());
        restorePlayerOrigins(run);
        tryRestoreInventories(run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=end runId={} result={} duration={}s",
            run.id(), result, run.elapsedSeconds());
    }

    /**
     * Abandonne une run à la demande d'un joueur.
     * Doit être appelé sur le SGT.
     */
    public void abandonRun(Run run, UUID requestingPlayerId) {
        run.completeRun(RunResult.ABANDONED);
        activeRuns.remove(run.id());
        restorePlayerOrigins(run);
        tryRestoreInventories(run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=abandon runId={} requestedBy={}",
            run.id(), requestingPlayerId);
    }

    /** Sauvegarde la position du joueur avant le téléport dans le donjon. */
    public void savePlayerOrigin(UUID playerId, ServerPlayer player) {
        String dimId = player.serverLevel().dimension().location().toString();
        playerOrigins.put(playerId, new OriginPos(
            dimId, player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot()));
    }

    public Optional<Run> findActiveRunForPlayer(UUID playerId) {
        return activeRuns.values().stream()
            .filter(r -> r.playerIds().contains(playerId))
            .findFirst();
    }

    public Optional<Run> findById(RunId id) {
        return Optional.ofNullable(activeRuns.get(id));
    }

    /** Snapshot immutable des runs actives (read-only). */
    public Map<RunId, Run> activeRuns() {
        return Map.copyOf(activeRuns);
    }

    /** Appelé au ServerStoppingEvent pour marquer toutes les runs actives SERVER_SHUTDOWN. */
    public void shutdownAll() {
        activeRuns.values().forEach(run -> {
            try {
                run.completeRun(RunResult.SERVER_SHUTDOWN);
                // Pas de restore inventaire au shutdown : serveur s'arrête, joueurs déjà déconnectés
            } catch (IllegalStateException ignored) {
                // Peut arriver hors SGT si le shutdown est rapide — acceptable
            }
        });
        activeRuns.clear();
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=shutdown_all");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void restorePlayerOrigins(Run run) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID playerId : run.playerIds()) {
            OriginPos origin = playerOrigins.remove(playerId);
            if (origin == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(origin.dimensionId()));
            ServerLevel targetLevel = server.getLevel(dimKey);
            if (targetLevel == null) targetLevel = server.overworld();
            player.teleportTo(targetLevel, origin.x(), origin.y(), origin.z(),
                origin.yaw(), origin.pitch());
            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=origin_restored playerId={} dim={} pos={},{},{}",
                playerId, origin.dimensionId(), origin.x(), origin.y(), origin.z());
        }
    }

    private void tryRestoreInventories(Run run) {
        if (archetypeService == null) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        archetypeService.restoreAll(run, server);
    }
}
