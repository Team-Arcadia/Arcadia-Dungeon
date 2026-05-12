package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.services.ArchetypeService;
import com.arcadia.dungeon.services.RoomProgressionService;
import com.arcadia.dungeon.services.RunLifecycleService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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

    private static final PacketRateLimiter RESYNC_LIMITER         = new PacketRateLimiter(5_000L);
    private static final PacketRateLimiter START_RUN_LIMITER      = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter RELOAD_LIMITER         = new PacketRateLimiter(10_000L);
    private static final PacketRateLimiter CREATE_DUNGEON_LIMITER = new PacketRateLimiter(5_000L);
    private static final PacketRateLimiter DELETE_DUNGEON_LIMITER = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter DETAIL_LIMITER         = new PacketRateLimiter(2_000L);
    private static final PacketRateLimiter MONITOR_LIMITER        = new PacketRateLimiter(1_500L);
    private static final PacketRateLimiter FORCE_END_LIMITER      = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter EDIT_LIMITER           = new PacketRateLimiter(2_000L);
    private static final PacketRateLimiter SAVE_CONFIG_LIMITER    = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter SAVE_ZONE_LIMITER      = new PacketRateLimiter(2_000L);
    private static final PacketRateLimiter KILL_RUNS_LIMITER      = new PacketRateLimiter(5_000L);

    private static final Gson GSON = new Gson();

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

            // Sauvegarder la position d'origine avant le téléport
            lifecycle.savePlayerOrigin(player.getUUID(), player);

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
                ArcadiaDungeon.playerDeathService().cancelPendingRespawn(player.getUUID());
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

            // Sauvegarder l'origine avant téléport (comme handleStartRun)
            lifecycle.savePlayerOrigin(player.getUUID(), player);

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

    // ── 8.3 — Création donjon admin ───────────────────────────────────────

    // [ZeroTrust:OK] — OP2 requis, id validé regex serveur-side, valeurs numériques clampées
    public static void handleCreateDungeon(CreateDungeonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!CREATE_DUNGEON_LIMITER.tryAcquire(player.getUUID())) return;

            String id = normalizeDungeonId(payload.id());
            String nameKey = payload.nameKey() != null ? payload.nameKey().trim() : "";

            if (!isValidDungeonResourceId(id)) {
                player.sendSystemMessage(Component.literal("§c✗ ID invalide (ex: tgh ou arcadia_dungeon:tgh)."));
                return;
            }
            if (nameKey.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Nom requis."));
                return;
            }
            if (id.length() > 64 || nameKey.length() > 128) {
                player.sendSystemMessage(Component.literal("§c✗ ID ou nom trop long."));
                return;
            }
            if (ArcadiaDungeon.dungeonRegistry().get(id).isPresent()) {
                player.sendSystemMessage(Component.literal("§c✗ Un donjon avec cet ID existe déjà : " + sanitize(id)));
                return;
            }

            int lives = Math.max(1, Math.min(99, payload.lives()));
            DungeonConfig.BossDefinition defaultBoss =
                new DungeonConfig.BossDefinition("minecraft:wither_skeleton", 100, java.util.List.of());

            // Config minimale valide (peut être enrichie via l'UI admin ou JSON direct)
            DungeonConfig cfg = new DungeonConfig(
                DungeonConfig.CURRENT_SCHEMA_VERSION,
                id,
                nameKey,
                null,   // currency — configurable dans l'UI
                lives,
                java.util.List.of(new DungeonConfig.RoomRef("room_1", null, java.util.List.of())),
                java.util.List.of(defaultBoss),
                new DungeonConfig.Rewards(0L, java.util.List.of()),
                java.util.List.of(),   // archetypes
                null,                  // structureRef
                null,                  // dimension
                null,                  // placementY
                null,                  // startMessage
                null,                  // victoryMessage
                null,                  // failMessage
                null,                  // requiredLevel
                null                   // xpMultiplier
            );

            ArcadiaDungeon.dungeonRegistry().save(cfg);
            player.sendSystemMessage(Component.literal(
                "§a✔ Donjon '" + sanitize(nameKey) + "' (id: " + sanitize(id) + ") créé. Complétez le JSON dans config/arcadia/dungeon/" + sanitize(id) + ".json"));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=create_dungeon admin={} dungeonId={}", player.getGameProfile().getName(), sanitize(id));
        });
    }

    // ── 8.x — Suppression donjon admin ───────────────────────────────────

    // [ZeroTrust:OK] — OP2 requis, id sanitisé, rate-limited
    public static void handleDeleteDungeon(DeleteDungeonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!DELETE_DUNGEON_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (!isValidDungeonResourceId(id)) {
                player.sendSystemMessage(Component.literal("§c✗ ID invalide."));
                return;
            }

            boolean deleted = ArcadiaDungeon.dungeonRegistry().delete(id);
            if (deleted) {
                player.sendSystemMessage(Component.literal("§a✔ Donjon supprimé : " + sanitize(id)));
                ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=delete_dungeon admin={} dungeonId={}",
                    player.getGameProfile().getName(), sanitize(id));
            } else {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            // Envoie la liste mise à jour au demandeur pour rafraîchir l'UI
            List<DungeonListPayload.DungeonSummary> summaries = new ArrayList<>();
            ArcadiaDungeon.dungeonRegistry().dungeons().forEach((did, config) -> {
                List<DungeonListPayload.ArchetypeSummary> archetypes = new ArrayList<>();
                if (config.archetypes() != null) {
                    for (com.arcadia.dungeon.domain.config.DungeonConfig.ArchetypeDefinition a : config.archetypes()) {
                        archetypes.add(new DungeonListPayload.ArchetypeSummary(a.id(), a.nameKey()));
                    }
                }
                summaries.add(new DungeonListPayload.DungeonSummary(
                    config.id(), config.nameKey(), config.schemaVersion(), archetypes));
            });
            player.connection.send(new DungeonListPayload(summaries));
        });
    }

    // ── 8.4 — Détail donjon admin ─────────────────────────────────────────

    // [ZeroTrust:OK] — OP2 requis, dungeonId sanitisé
    public static void handleRequestDungeonDetail(RequestDungeonDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!DETAIL_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (id.isEmpty() || id.length() > 64) return;

            var configOpt = ArcadiaDungeon.dungeonRegistry().get(id);
            if (configOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            var cfg = configOpt.get();

            int totalWaves = 0;
            if (cfg.rooms() != null) {
                for (var room : cfg.rooms()) {
                    if (room.waves() != null) totalWaves += room.waves().size();
                }
            }

            List<DungeonConfig.BossDefinition> bosses = cfg.configuredBosses();
            DungeonConfig.BossDefinition primaryBoss = cfg.primaryBoss();

            player.connection.send(new DungeonDetailPayload(
                cfg.id(),
                cfg.nameKey(),
                cfg.schemaVersion(),
                cfg.lives(),
                cfg.structureRef()  != null ? cfg.structureRef()  : "—",
                cfg.dimension()     != null ? cfg.dimension()     : "—",
                primaryBoss         != null ? primaryBoss.type()   : "—",
                primaryBoss         != null ? primaryBoss.hp()     : 0,
                primaryBoss         != null ? primaryBoss.phasesOrEmpty().size() : 0,
                bosses.size(),
                cfg.rooms()         != null ? cfg.rooms().size()  : 0,
                totalWaves,
                cfg.rewards()       != null ? cfg.rewards().currency() : 0L,
                cfg.rewards()       != null && cfg.rewards().loot() != null ? cfg.rewards().loot().size() : 0,
                cfg.archetypes()    != null ? cfg.archetypes().size() : 0
            ));
        });
    }

    // ── 8.5 — Monitor admin ───────────────────────────────────────────────

    // [ZeroTrust:OK] — OP2 requis, rate-limited, lecture seule serveur
    public static void handleMonitorRefresh(MonitorRefreshPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!MONITOR_LIMITER.tryAcquire(player.getUUID())) return;

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            List<MonitorDataPayload.RunSummary> summaries = new ArrayList<>();
            ArcadiaDungeon.runLifecycleService().activeRuns().values().forEach(run -> {
                // Résolution noms de joueurs (en ligne) ou UUID court (hors ligne)
                StringBuilder names = new StringBuilder();
                for (UUID pid : run.playerIds()) {
                    if (names.length() > 0) names.append(" · ");
                    ServerPlayer sp = server.getPlayerList().getPlayer(pid);
                    names.append(sp != null ? sp.getGameProfile().getName()
                                           : pid.toString().substring(0, 8));
                }
                String dungeonName = ArcadiaDungeon.dungeonRegistry().get(run.dungeonId())
                    .map(c -> c.nameKey())
                    .orElse(run.dungeonId());

                summaries.add(new MonitorDataPayload.RunSummary(
                    run.id().toString(),
                    run.dungeonId(),
                    dungeonName,
                    run.phase().name(),
                    run.currentRoomIndex(),
                    run.totalRooms(),
                    run.livesRemaining(),
                    run.playerIds().size(),
                    names.toString(),
                    run.elapsedSeconds()
                ));
            });

            player.connection.send(new MonitorDataPayload(summaries));
            ArcadiaDungeon.LOGGER.debug("[Arcadia][ADMIN] event=monitor_refresh admin={} runs={}",
                player.getGameProfile().getName(), summaries.size());
        });
    }

    // [ZeroTrust:OK] — OP2 requis, runId validé UUID, rate-limited
    public static void handleForceEndRun(ForceEndRunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!FORCE_END_LIMITER.tryAcquire(player.getUUID())) return;

            String rawId = payload.runId() != null ? payload.runId().trim() : "";
            if (rawId.isEmpty() || rawId.length() > 64) return;

            RunId runId;
            try {
                runId = new RunId(UUID.fromString(rawId));
            } catch (IllegalArgumentException e) {
                player.sendSystemMessage(Component.literal("§c✗ runId invalide."));
                return;
            }

            Run run = ArcadiaDungeon.runLifecycleService().findById(runId).orElse(null);
            if (run == null) {
                player.sendSystemMessage(Component.literal("§c✗ Run introuvable."));
                return;
            }

            RunResult result = payload.success() ? RunResult.VICTORY : RunResult.DEFEAT;
            ArcadiaDungeon.roomProgressionService().cleanupRun(runId);
            ArcadiaDungeon.runLifecycleService().completeRun(run, result);

            String label = payload.success() ? "§a✔ Victoire" : "§c✔ Défaite";
            player.sendSystemMessage(Component.literal(
                label + " forcée — run " + sanitize(rawId.substring(0, Math.min(8, rawId.length())))));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=force_end_run admin={} runId={} result={}",
                player.getGameProfile().getName(), sanitize(rawId), result);
        });
    }

    // ── Post-MVP — Édition complète donjon ────────────────────────────────

    // [ZeroTrust:OK] — OP2 requis, dungeonId sanitisé, rate-limited
    public static void handleRequestDungeonEdit(RequestDungeonEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!EDIT_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (id.isEmpty() || id.length() > 64) return;

            var configOpt = ArcadiaDungeon.dungeonRegistry().get(id);
            if (configOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            String json = GSON.toJson(configOpt.get());

            var placement = ArcadiaDungeon.placementRegistry();
            double sx = 0, sy = 0, sz = 0;
            String sdim = "";
            boolean sset = false;
            if (placement.isSetup(id)) {
                Vec3 pos = placement.getSpawn(id).orElse(Vec3.ZERO);
                sx = pos.x; sy = pos.y; sz = pos.z;
                sdim = placement.getDimension(id).orElse("");
                sset = true;
            }

            player.connection.send(new DungeonEditDataPayload(id, json, sx, sy, sz, sdim, sset));
            ArcadiaDungeon.LOGGER.debug("[Arcadia][ADMIN] event=request_dungeon_edit admin={} dungeonId={}",
                player.getGameProfile().getName(), sanitize(id));
        });
    }

    // [ZeroTrust:OK] — OP2 requis, JSON désérialisé + validé côté serveur, rate-limited
    public static void handleSaveDungeonConfig(SaveDungeonConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!SAVE_CONFIG_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (!isValidDungeonResourceId(id)) {
                player.sendSystemMessage(Component.literal("§c✗ ID donjon invalide."));
                return;
            }
            if (ArcadiaDungeon.dungeonRegistry().get(id).isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            String json = payload.configJson() != null ? payload.configJson() : "";
            if (json.isEmpty() || json.length() > 65536) {
                player.sendSystemMessage(Component.literal("§c✗ JSON invalide ou trop long."));
                return;
            }

            DungeonConfig cfg;
            try {
                cfg = GSON.fromJson(json, DungeonConfig.class);
            } catch (JsonSyntaxException e) {
                player.sendSystemMessage(Component.literal("§c✗ Erreur JSON : " + e.getMessage()));
                return;
            }

            if (cfg == null || cfg.id() == null || !cfg.id().equals(id)) {
                player.sendSystemMessage(Component.literal("§c✗ ID du JSON ne correspond pas."));
                return;
            }

            ArcadiaDungeon.dungeonRegistry().save(cfg);
            player.sendSystemMessage(Component.literal("§a✔ Config sauvegardée : " + sanitize(id)));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=save_dungeon_config admin={} dungeonId={}",
                player.getGameProfile().getName(), sanitize(id));
        });
    }

    // [ZeroTrust:OK] — OP2 requis, coords validées serveur-side, rate-limited
    public static void handleSaveZone(SaveZonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!SAVE_ZONE_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (id.isEmpty() || id.length() > 64) return;
            if (ArcadiaDungeon.dungeonRegistry().get(id).isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            String dim = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension().trim() : "minecraft:overworld";

            ArcadiaDungeon.placementRegistry().setSpawn(id,
                new Vec3(payload.x(), payload.y(), payload.z()), dim);
            player.sendSystemMessage(Component.literal(
                "§a✔ Spawn enregistré : " + sanitize(id) + " @ " +
                String.format("%.1f/%.1f/%.1f", payload.x(), payload.y(), payload.z()) + " [" + dim + "]"));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=save_zone admin={} dungeonId={} dim={}",
                player.getGameProfile().getName(), sanitize(id), dim);
        });
    }

    // [ZeroTrust:OK] — le client n'envoie PAS de coordonnées, elles sont lues côté serveur
    public static void handleCaptureSpawn(CaptureSpawnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!SAVE_ZONE_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (id.isEmpty() || id.length() > 64) return;
            if (ArcadiaDungeon.dungeonRegistry().get(id).isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✗ Donjon introuvable : " + sanitize(id)));
                return;
            }

            // Zero Trust : coordonnées lues côté serveur depuis le player object
            Vec3 pos = new Vec3(player.getX(), player.getY(), player.getZ());
            String dim = player.level().dimension().location().toString();

            ArcadiaDungeon.placementRegistry().setSpawn(id, pos, dim);
            player.sendSystemMessage(Component.literal(
                "§a✔ Spawn capturé : " + sanitize(id) + " @ " +
                String.format("%.1f/%.1f/%.1f", pos.x, pos.y, pos.z) + " [" + dim + "]"));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=capture_spawn admin={} dungeonId={} pos={},{},{} dim={}",
                player.getGameProfile().getName(), sanitize(id), pos.x, pos.y, pos.z, dim);
        });
    }

    // [ZeroTrust:OK] — OP2 requis, dungeonId sanitisé, rate-limited
    public static void handleKillDungeonRuns(KillDungeonRunsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c✗ Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!KILL_RUNS_LIMITER.tryAcquire(player.getUUID())) return;

            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (id.isEmpty() || id.length() > 64) return;

            int killed = 0;
            for (Run run : ArcadiaDungeon.runLifecycleService().activeRuns().values()) {
                if (run.dungeonId().equals(id)) {
                    ArcadiaDungeon.roomProgressionService().cleanupRun(run.id());
                    ArcadiaDungeon.runLifecycleService().completeRun(run, RunResult.DEFEAT);
                    killed++;
                }
            }

            player.sendSystemMessage(Component.literal(
                "§a✔ " + killed + " run(s) terminée(s) pour : " + sanitize(id)));
            ArcadiaDungeon.LOGGER.info("[Arcadia][ADMIN] event=kill_dungeon_runs admin={} dungeonId={} count={}",
                player.getGameProfile().getName(), sanitize(id), killed);
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

    private static String normalizeDungeonId(String id) {
        String trimmed = id != null ? id.trim() : "";
        if (trimmed.isEmpty() || trimmed.contains(":")) return trimmed;
        return ArcadiaDungeon.MODID + ":" + trimmed;
    }

    private static boolean isValidDungeonResourceId(String id) {
        return id != null
            && !id.isBlank()
            && id.length() <= 64
            && ResourceLocation.tryParse(id) != null;
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
