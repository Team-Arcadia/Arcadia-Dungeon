package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.services.ArchetypeService;
import com.arcadia.dungeon.services.RoomProgressionService;
import com.arcadia.dungeon.services.RunLifecycleService;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handlers serveur pour les payloads C2S (Stories S2.5, S3.2, S3.4).
 *
 * <p>Toutes les mutations sont exécutées via {@code context.enqueueWork()}
 * pour garantir l'exécution sur le SGT.
 */
public final class ServerPayloadHandler {

    private static final PacketRateLimiter RESYNC_LIMITER    = new PacketRateLimiter(5_000L);
    private static final PacketRateLimiter START_RUN_LIMITER = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter RELOAD_LIMITER    = new PacketRateLimiter(10_000L);

    private static final int MAX_PLAYERS_PER_RUN = 2;

    private ServerPayloadHandler() {}

    // ── S2.5 ──────────────────────────────────────────────────────────────

    public static void handleStartRun(StartRunPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        // Fix #2 — rate-limit sur StartRun
        if (!START_RUN_LIMITER.tryAcquire(player.getUUID())) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia][RUN] event=start_run_rate_limited player={}",
                player.getGameProfile().getName());
            return;
        }

        context.enqueueWork(() -> {
            RunLifecycleService lifecycle = ArcadiaDungeon.runLifecycleService();
            RoomProgressionService progression = ArcadiaDungeon.roomProgressionService();

            if (lifecycle.findActiveRunForPlayer(player.getUUID()).isPresent()) {
                player.sendSystemMessage(Component.literal("§c✗ Tu es déjà dans une run."));
                return;
            }

            var dungeonOpt = ArcadiaDungeon.dungeonRegistry().get(payload.dungeonId());
            if (dungeonOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon inconnu : " + sanitize(payload.dungeonId())));
                return;
            }

            // Fix #1 — valider que l'archetypeId existe dans la config du donjon
            var dungeonConfig = dungeonOpt.get();
            boolean archetypeValid = dungeonConfig.archetypes() != null &&
                dungeonConfig.archetypes().stream().anyMatch(a -> a.id().equals(payload.archetypeId()));
            if (!archetypeValid) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] event=start_run_invalid_archetype player={} archetypeId={}",
                    player.getGameProfile().getName(), sanitize(payload.archetypeId()));
                player.sendSystemMessage(Component.literal("§c✗ Archétype invalide : " + sanitize(payload.archetypeId())));
                return;
            }

            // Vérifier que le donjon a été configuré via /arcadia setup
            var registry = ArcadiaDungeon.placementRegistry();
            Vec3 spawnPos = registry.getSpawn(payload.dungeonId()).orElse(null);
            if (spawnPos == null) {
                player.sendSystemMessage(Component.literal(
                    "§c✗ Donjon non configuré. Un admin doit lancer : /arcadia setup " + sanitize(payload.dungeonId())));
                return;
            }

            // Résoudre la dimension du donjon depuis le registre (enregistrée au /arcadia setup)
            ServerLevel level = registry.getDimension(payload.dungeonId())
                .map(dimId -> {
                    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(dimId));
                    return player.getServer().getLevel(key);
                })
                .orElseGet(player::serverLevel);
            if (level == null) level = player.serverLevel();

            Run run = lifecycle.startRun(payload.dungeonId(), List.of(player.getUUID()));
            run.setArchetype(player.getUUID(), payload.archetypeId());

            // S6.6 — strip inventaire + kit archétype
            ArcadiaDungeon.archetypeService().preparePlayer(player, payload.dungeonId(), payload.archetypeId());

            // Téléporter le joueur dans la dimension du donjon
            player.teleportTo(level, spawnPos.x, spawnPos.y, spawnPos.z,
                player.getYRot(), player.getXRot());

            progression.startRunWaves(run, level, spawnPos);

            broadcastRunState(run);

            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=start_run_workflow runId={} player={} dungeon={}",
                run.id(), player.getGameProfile().getName(), sanitize(payload.dungeonId()));
        });
    }

    public static void handleAbandonRun(AbandonRunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            RunLifecycleService lifecycle = ArcadiaDungeon.runLifecycleService();

            lifecycle.findActiveRunForPlayer(player.getUUID()).ifPresentOrElse(run -> {
                ArcadiaDungeon.roomProgressionService().cleanupRun(run.id());
                lifecycle.abandonRun(run, player.getUUID());
                player.sendSystemMessage(Component.literal("§7Run abandonnée."));
            }, () -> {
                player.sendSystemMessage(Component.literal("§c✗ Pas de run active."));
            });
        });
    }

    // ── S3.2 + S3.3 ───────────────────────────────────────────────────────

    public static void handleJoinRun(JoinRunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            RunLifecycleService lifecycle = ArcadiaDungeon.runLifecycleService();

            // Joueur pas déjà en run
            if (lifecycle.findActiveRunForPlayer(player.getUUID()).isPresent()) {
                player.sendSystemMessage(Component.literal("§c✗ Tu es déjà dans une run."));
                return;
            }

            // Run cible existe
            RunId targetId;
            try {
                targetId = new RunId(UUID.fromString(payload.runId()));
            } catch (IllegalArgumentException e) {
                player.sendSystemMessage(Component.literal("§c✗ runId invalide."));
                return;
            }

            Run run = lifecycle.findById(targetId).orElse(null);
            if (run == null) {
                player.sendSystemMessage(Component.literal("§c✗ Run introuvable."));
                return;
            }

            // Run en phase STARTING
            if (run.phase() != RunPhase.STARTING) {
                player.sendSystemMessage(Component.literal("§c✗ La run a déjà commencé."));
                return;
            }

            // Capacité max
            if (run.playerIds().size() >= MAX_PLAYERS_PER_RUN) {
                player.sendSystemMessage(Component.literal("§c✗ Run complète (" + MAX_PLAYERS_PER_RUN + " joueurs max)."));
                return;
            }

            // Fix #1 — valider que l'archetypeId existe dans la config du donjon
            var dungeonConfig = ArcadiaDungeon.dungeonRegistry().get(run.dungeonId()).orElse(null);
            boolean archetypeValid = dungeonConfig != null && dungeonConfig.archetypes() != null &&
                dungeonConfig.archetypes().stream().anyMatch(a -> a.id().equals(payload.archetypeId()));
            if (!archetypeValid) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] event=join_invalid_archetype player={} archetypeId={}",
                    player.getGameProfile().getName(), sanitize(payload.archetypeId()));
                player.sendSystemMessage(Component.literal("§c✗ Archétype invalide : " + sanitize(payload.archetypeId())));
                return;
            }

            run.addPlayer(player.getUUID());
            run.setArchetype(player.getUUID(), payload.archetypeId());

            // S6.6 — strip inventaire + kit archétype pour le joueur qui rejoint
            ArcadiaDungeon.archetypeService().preparePlayer(player, run.dungeonId(), payload.archetypeId());

            // S3.3 — resync complet au joueur qui rejoint
            player.connection.send(RunStatePayload.from(run));

            // Broadcast aux autres joueurs de la run
            broadcastRunState(run);

            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=join runId={} player={} totalPlayers={}",
                run.id(), player.getGameProfile().getName(), run.playerIds().size());
            ArcadiaDungeon.LOGGER.info("[Arcadia][SYNC] event=resync_full runId={} player={}",
                run.id(), player.getGameProfile().getName());
        });
    }

    // ── S3.4 ──────────────────────────────────────────────────────────────

    public static void handleRequestResync(RequestRunResyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();

            if (!RESYNC_LIMITER.tryAcquire(player.getUUID())) {
                ArcadiaDungeon.LOGGER.debug("[Arcadia][SYNC] event=resync_rate_limited player={}",
                    player.getGameProfile().getName());
                return;
            }

            ArcadiaDungeon.runLifecycleService()
                .findActiveRunForPlayer(player.getUUID())
                .ifPresent(run -> {
                    player.connection.send(RunStatePayload.from(run));
                    ArcadiaDungeon.LOGGER.info("[Arcadia][SYNC] event=resync_response runId={} player={}",
                        run.id(), player.getGameProfile().getName());
                });

            ArcadiaDungeon.LOGGER.info("[Arcadia][SYNC] event=resync_manual_request player={}",
                player.getGameProfile().getName());
        });
    }

    // ── S6.2 — Liste donjons ──────────────────────────────────────────────

    public static void handleRequestDungeonList(RequestDungeonListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            List<DungeonListPayload.DungeonSummary> summaries = new ArrayList<>();
            ArcadiaDungeon.dungeonRegistry().dungeons().forEach((id, config) -> {
                List<DungeonListPayload.ArchetypeSummary> archetypes = new ArrayList<>();
                if (config.archetypes() != null) {
                    for (DungeonConfig.ArchetypeDefinition a : config.archetypes()) {
                        archetypes.add(new DungeonListPayload.ArchetypeSummary(a.id(), a.nameKey()));
                    }
                }
                summaries.add(new DungeonListPayload.DungeonSummary(
                    config.id(), config.nameKey(), config.schemaVersion(), archetypes));
            });
            player.connection.send(new DungeonListPayload(summaries));
        });
    }

    // ── S6.4 — Reload admin ───────────────────────────────────────────────

    public static void handleReloadRequest(ReloadRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        // Fix #2 — rate-limit sur ReloadRequest
        if (!RELOAD_LIMITER.tryAcquire(player.getUUID())) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia][ADMIN] event=reload_rate_limited player={}",
                player.getGameProfile().getName());
            return;
        }

        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            ArcadiaDungeon.dungeonRegistry().reload();
            player.sendSystemMessage(Component.literal("§aDonjons rechargés."));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=reload requestedBy={}", player.getGameProfile().getName());
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Sanitise une chaîne venant du client avant de la loguer
     * (évite l'injection CRLF dans les logs).
     */
    private static String sanitize(String s) {
        if (s == null) return "null";
        return s.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(s.length(), 128));
    }

    /** Diffuse {@link RunStatePayload} à tous les joueurs connectés de la run. */
    public static void broadcastRunState(Run run) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        RunStatePayload payload = RunStatePayload.from(run);
        for (UUID id : run.playerIds()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) sp.connection.send(payload);
        }
    }
}
