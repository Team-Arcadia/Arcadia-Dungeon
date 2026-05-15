package com.arcadia.dungeon.network;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.player.PlayerProgress;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.event.DungeonAreaWandEventHandler;
import com.arcadia.dungeon.services.ArcadiaToast;
import com.arcadia.dungeon.services.DungeonPlacementSlots;
import com.arcadia.dungeon.services.RoomProgressionService;
import com.arcadia.dungeon.services.RunLifecycleService;
import com.arcadia.dungeon.services.StructurePlacementScheduler;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
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
import java.util.Arrays;
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
    private static final PacketRateLimiter MONITOR_LIMITER        = new PacketRateLimiter(1_500L);
    private static final PacketRateLimiter FORCE_END_LIMITER      = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter EDIT_LIMITER           = new PacketRateLimiter(2_000L);
    private static final PacketRateLimiter SAVE_CONFIG_LIMITER    = new PacketRateLimiter(3_000L);
    private static final PacketRateLimiter SAVE_ZONE_LIMITER      = new PacketRateLimiter(2_000L);
    private static final PacketRateLimiter TEMPLATE_LIMITER       = new PacketRateLimiter(500L);
    private static final PacketRateLimiter LOADOUT_SELECT_LIMITER = new PacketRateLimiter(500L);
    private static final PacketRateLimiter LOADOUT_SAVE_LIMITER   = new PacketRateLimiter(1_000L);
    private static final PacketRateLimiter ADMIN_DEBUG_LIMITER    = new PacketRateLimiter(300L);

    private static final Gson GSON = new Gson();

    private static final int MAX_PLAYERS_PER_RUN = 2;

    private ServerPayloadHandler() {}

    // ── S2.5 ──────────────────────────────────────────────────────────────

    public static void handleStartRun(StartRunPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        if (!START_RUN_LIMITER.tryAcquire(player.getUUID())) {
            ArcadiaDungeon.LOGGER.debug("[Arcadia][RUN] event=start_run_rate_limited player={}",
                player.getGameProfile().getName());
            return;
        }

        context.enqueueWork(() -> {
            RunLifecycleService lifecycle = ArcadiaDungeon.runLifecycleService();
            RoomProgressionService progression = ArcadiaDungeon.roomProgressionService();

            Run activeRun = lifecycle.findActiveRunForPlayer(player.getUUID()).orElse(null);
            if (activeRun != null) {
                if (activeRun.phase() == RunPhase.STARTING && activeRun.dungeonId().equals(payload.dungeonId())
                    && isRunLeader(activeRun, player)) {
                    launchLobbyRun(lifecycle, progression, player, activeRun);
                } else if (activeRun.phase() == RunPhase.STARTING) {
                    ArcadiaToast.warn(player, "arcadia.server.run.lobby_wait_leader");
                    player.connection.send(RunStatePayload.from(activeRun));
                } else {
                    ArcadiaToast.error(player, "arcadia.toast.run.already_in_run");
                }
                return;
            }

            var dungeonOpt = ArcadiaDungeon.dungeonRegistry().get(payload.dungeonId());
            if (dungeonOpt.isEmpty()) {
                ArcadiaToast.error(player, "arcadia.toast.run.unknown_dungeon", sanitize(payload.dungeonId()));
                return;
            }

            DungeonConfig dungeonConfig = dungeonOpt.get();
            String activeClassId = resolveSelectedClass(player, payload.archetypeId());
            if (activeClassId.isBlank()) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] event=start_run_missing_class player={}",
                    player.getGameProfile().getName());
                ArcadiaToast.error(player, "arcadia.server.run.no_free_class");
                return;
            }

            var instanceService = ArcadiaDungeon.dungeonInstanceService();
            Run openLobby = lifecycle.findOpenLobby(payload.dungeonId(), MAX_PLAYERS_PER_RUN)
                .filter(run -> !instanceService.isPreparing(run))
                .orElse(null);
            if (openLobby != null) {
                openLobby.addPlayer(player.getUUID());
                openLobby.setArchetype(player.getUUID(), activeClassId);
                ArcadiaToast.success(player, "arcadia.server.run.lobby_joined");
                broadcastRunState(openLobby);
                ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=lobby_joined runId={} player={} totalPlayers={}",
                    openLobby.id(), player.getGameProfile().getName(), openLobby.playerIds().size());
                return;
            }

            Run run = lifecycle.startRun(dungeonConfig.id(), List.of(player.getUUID()));
            run.setArchetype(player.getUUID(), activeClassId);
            player.connection.send(RunStatePayload.from(run));
            ArcadiaToast.success(player, "arcadia.server.run.lobby_created");
            ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=lobby_created runId={} player={} dungeon={}",
                run.id(), player.getGameProfile().getName(), sanitize(payload.dungeonId()));
        });
    }

    private static void launchLobbyRun(RunLifecycleService lifecycle,
                                       RoomProgressionService progression,
                                       ServerPlayer leader,
                                       Run run) {
        DungeonConfig dungeonConfig = ArcadiaDungeon.dungeonRegistry().get(run.dungeonId()).orElse(null);
        if (dungeonConfig == null) {
            ArcadiaToast.error(leader, "arcadia.server.run.dungeon_missing", sanitize(run.dungeonId()));
            lifecycle.completeRun(run, RunResult.ABANDONED);
            return;
        }

        var instanceService = ArcadiaDungeon.dungeonInstanceService();
        if (instanceService.requiresRuntimeInstance(dungeonConfig)) {
            MinecraftServer server = leader.getServer();
            ArcadiaToast.info(leader, "arcadia.server.run.instance_generating");
            instanceService.prepareRunInstance(server, run, dungeonConfig, prepared -> {
                if (lifecycle.findById(run.id()).isEmpty()) {
                    instanceService.cleanupRun(run);
                    return;
                }
                activateRunPlayers(lifecycle, progression, run, prepared.level(), prepared.spawnPos());
                ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=multiplayer_run_started runId={} dungeon={} players={} instanceSlot={}",
                    run.id(), sanitize(run.dungeonId()), run.playerIds().size(), prepared.slot());
            }, message -> {
                if (lifecycle.findById(run.id()).isPresent()) {
                    lifecycle.completeRun(run, RunResult.ABANDONED);
                }
                ArcadiaToast.error(leader, "arcadia.server.run.instance_failed", message.getString());
            });
            return;
        }

        var registry = ArcadiaDungeon.placementRegistry();
        Vec3 spawnPos = registry.getSpawn(run.dungeonId()).orElse(null);
        if (spawnPos == null) {
            ArcadiaToast.error(leader, "arcadia.server.run.dungeon_not_configured", sanitize(run.dungeonId()));
            return;
        }

        ServerLevel level = registry.getDimension(run.dungeonId())
            .map(dimId -> {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimId));
                return leader.getServer().getLevel(key);
            })
            .orElseGet(leader::serverLevel);
        if (level == null) level = leader.serverLevel();

        activateRunPlayers(lifecycle, progression, run, level, spawnPos);
        ArcadiaDungeon.LOGGER.info("[Arcadia][RUN] event=multiplayer_run_started runId={} dungeon={} players={}",
            run.id(), sanitize(run.dungeonId()), run.playerIds().size());
    }

    private static void activateRunPlayers(RunLifecycleService lifecycle,
                                           RoomProgressionService progression,
                                           Run run,
                                           ServerLevel level,
                                           Vec3 spawnPos) {
        MinecraftServer server = level.getServer();
        int onlinePlayers = 0;
        for (UUID playerId : run.playerIds()) {
            ServerPlayer runPlayer = server.getPlayerList().getPlayer(playerId);
            if (runPlayer == null) continue;
            onlinePlayers++;
            String classId = run.playerArchetypes().get(playerId);
            if (classId == null || classId.isBlank()) {
                classId = resolveSelectedClass(runPlayer, "");
            }
            lifecycle.savePlayerOrigin(playerId, runPlayer);
            ArcadiaDungeon.archetypeService().preparePlayer(runPlayer, run.id(), run.dungeonId(), classId);
            runPlayer.teleportTo(level, spawnPos.x, spawnPos.y, spawnPos.z,
                runPlayer.getYRot(), runPlayer.getXRot());
        }
        if (onlinePlayers == 0) {
            lifecycle.completeRun(run, RunResult.ABANDONED);
            return;
        }
        progression.startRunWaves(run, level, spawnPos);
        broadcastRunState(run);
    }

    private static boolean isRunLeader(Run run, ServerPlayer player) {
        List<UUID> players = run.playerIds();
        return !players.isEmpty() && players.getFirst().equals(player.getUUID());
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

            if (ArcadiaDungeon.dungeonInstanceService().isPreparing(run)) {
                player.sendSystemMessage(Component.literal("§c✗ Instance en generation, rejoins dans quelques secondes."));
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

            String activeClassId = resolveSelectedClass(player, payload.archetypeId());
            if (activeClassId.isBlank()) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][RUN] event=start_run_missing_class player={}",
                    player.getGameProfile().getName());
                player.sendSystemMessage(Component.translatable("arcadia.server.run.no_free_class"));
                return;
            }
            run.addPlayer(player.getUUID());
            run.setArchetype(player.getUUID(), activeClassId);

            // Sauvegarder l'origine avant téléport (comme handleStartRun)
            lifecycle.savePlayerOrigin(player.getUUID(), player);

            // S6.6 — strip inventaire + kit archétype pour le joueur qui rejoint
            ArcadiaDungeon.archetypeService().preparePlayer(player, run.id(), run.dungeonId(), activeClassId);

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
            sendDungeonList(player);
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

    // ── 8.5 — Monitor admin ───────────────────────────────────────────────

    // ── Helpers ────────────────────────────────────────────────────────────

    public static void handleCreateDungeon(CreateDungeonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("�c? Permissions insuffisantes (op2 requis)."));
                return;
            }
            if (!CREATE_DUNGEON_LIMITER.tryAcquire(player.getUUID())) return;

            String id = normalizeDungeonId(payload.id());
            String nameKey = payload.nameKey() != null ? payload.nameKey().trim() : "";
            if (!isValidDungeonResourceId(id) || nameKey.isEmpty()) {
                player.sendSystemMessage(Component.literal("�c? ID ou nom invalide."));
                return;
            }
            if (ArcadiaDungeon.dungeonRegistry().get(id).isPresent()) {
                player.sendSystemMessage(Component.literal("�c? Donjon deja existant : " + sanitize(id)));
                return;
            }

            int lives = Math.max(1, Math.min(99, payload.lives()));
            DungeonConfig.BossDefinition defaultBoss =
                new DungeonConfig.BossDefinition("minecraft:wither_skeleton", 100, List.of());
            DungeonConfig cfg = new DungeonConfig(
                DungeonConfig.CURRENT_SCHEMA_VERSION, id, nameKey, null, lives,
                List.of(), List.of(), List.of(defaultBoss), new DungeonConfig.Rewards(0L, List.of()), List.of(),
                null, ArcadiaDungeon.DUNGEON_DIMENSION_ID, null, null, null, "custom", null, null,
                null, null, null, null, null, null);

            ArcadiaDungeon.dungeonRegistry().save(cfg);
            sendDungeonList(player);
            player.sendSystemMessage(Component.literal("�a? Donjon cree : " + sanitize(id)));
        });
    }

    public static void handleDeleteDungeon(DeleteDungeonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!DELETE_DUNGEON_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (!isValidDungeonResourceId(id)) return;
            boolean deleted = ArcadiaDungeon.dungeonRegistry().delete(id);
            player.sendSystemMessage(Component.literal(deleted
                ? "�a? Donjon supprime : " + sanitize(id)
                : "�c? Donjon introuvable : " + sanitize(id)));
            sendDungeonList(player);
        });
    }

    public static void handleMonitorRefresh(MonitorRefreshPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!MONITOR_LIMITER.tryAcquire(player.getUUID())) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            List<MonitorDataPayload.RunSummary> summaries = new ArrayList<>();
            ArcadiaDungeon.runLifecycleService().activeRuns().values().forEach(run -> {
                StringBuilder names = new StringBuilder();
                for (UUID pid : run.playerIds()) {
                    if (names.length() > 0) names.append(" � ");
                    ServerPlayer sp = server.getPlayerList().getPlayer(pid);
                    names.append(sp != null ? sp.getGameProfile().getName() : pid.toString().substring(0, 8));
                }
                summaries.add(new MonitorDataPayload.RunSummary(
                    run.id().toString(),
                    run.dungeonId(),
                    ArcadiaDungeon.dungeonRegistry().get(run.dungeonId()).map(DungeonConfig::nameKey).orElse(run.dungeonId()),
                    run.phase().name(),
                    run.currentRoomIndex(),
                    run.totalRooms(),
                    run.livesRemaining(),
                    run.playerIds().size(),
                    names.toString(),
                    run.elapsedSeconds()));
            });
            player.connection.send(new MonitorDataPayload(summaries));
        });
    }

    public static void handleForceEndRun(ForceEndRunPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!FORCE_END_LIMITER.tryAcquire(player.getUUID())) return;
            String rawId = payload.runId() != null ? payload.runId().trim() : "";
            RunId runId;
            try {
                runId = new RunId(UUID.fromString(rawId));
            } catch (IllegalArgumentException e) {
                player.sendSystemMessage(Component.literal("�c? runId invalide."));
                return;
            }
            Run run = ArcadiaDungeon.runLifecycleService().findById(runId).orElse(null);
            if (run == null) return;
            ArcadiaDungeon.roomProgressionService().cleanupRun(runId);
            RunResult result = payload.success() ? RunResult.VICTORY : RunResult.DEFEAT;
            ArcadiaDungeon.runLifecycleService().completeRun(run, result);
            ArcadiaDungeon.rewardDistributionService().distribute(run, result);
            player.sendSystemMessage(Component.literal("�a? Run terminee : " + sanitize(rawId)));
        });
    }

    public static void handleAdminDebugAction(AdminDebugActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer admin = (ServerPlayer) context.player();
            if (!admin.hasPermissions(2)) return;
            if (!ADMIN_DEBUG_LIMITER.tryAcquire(admin.getUUID())) return;

            ServerPlayer target = resolveDebugTarget(admin, payload.targetPlayer());
            if (target == null) {
                ArcadiaToast.error(admin, "arcadia.admin.debug.toast.player_not_found", sanitize(payload.targetPlayer()));
                return;
            }

            String action = payload.action() != null ? payload.action().trim().toUpperCase() : "";
            var progress = ArcadiaDungeon.playerProgressService();
            String targetName = target.getGameProfile().getName();
            UUID targetId = target.getUUID();
            boolean mutated = true;

            switch (action) {
                case "ADD_CURRENCY" -> progress.addCurrency(targetId, targetName, payload.amount());
                case "SET_CURRENCY" -> progress.setCurrency(targetId, targetName, Math.max(0L, payload.amount()));
                case "ADD_LOADOUT_POINTS" -> progress.addLoadoutPoints(targetId, targetName, clampInt(payload.amount(), -999, 999));
                case "UNLOCK_CUSTOM_LOADOUT" -> progress.unlockCustomLoadout(targetId, targetName);
                case "RESET_PROGRESS" -> progress.resetProgress(targetId, targetName);
                case "PREVIEW_DUNGEON_INVENTORY" -> {
                    if (!ArcadiaDungeon.archetypeService().prepareDebugInventory(target, debugDungeonId(payload.dungeonId()))) {
                        ArcadiaToast.error(admin, "arcadia.admin.debug.toast.inventory_unavailable", sanitize(targetName));
                        return;
                    }
                    ArcadiaToast.success(admin, "arcadia.admin.debug.toast.hud_inventory_on");
                    return;
                }
                case "RESTORE_DEBUG_INVENTORY" -> {
                    if (!ArcadiaDungeon.archetypeService().restoreDebugInventory(target)) {
                        ArcadiaToast.error(admin, "arcadia.admin.debug.toast.inventory_unavailable", sanitize(targetName));
                        return;
                    }
                    ArcadiaToast.success(admin, "arcadia.admin.debug.toast.inventory_restored");
                    return;
                }
                case "GRANT_COMPLETION" -> progress.recordRunCompletion(
                    targetId, targetName, debugDungeonId(payload.dungeonId()), Math.max(1L, payload.timeSeconds()));
                case "UNLOCK_PROFILE_BADGES" -> {
                    String dungeonId = debugDungeonId(payload.dungeonId());
                    progress.recordRunCompletion(targetId, targetName, dungeonId, Math.max(1L, payload.timeSeconds()));
                    progress.unlockCustomLoadout(targetId, targetName);
                    progress.addLoadoutPoints(targetId, targetName, 3);
                }
                case "SYNC_PROGRESS" -> mutated = false;
                default -> {
                    ArcadiaToast.error(admin, "arcadia.admin.debug.toast.unknown_action", sanitize(action));
                    return;
                }
            }

            if (mutated) {
                progress.save();
            }
            sendPlayerProgress(target);
            if (!target.getUUID().equals(admin.getUUID())) {
                sendPlayerProgress(admin);
            }
            ArcadiaToast.success(admin, "arcadia.admin.debug.toast.done", sanitize(action), sanitize(targetName));
        });
    }

    public static void handleRequestDungeonEdit(RequestDungeonEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!EDIT_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            var configOpt = ArcadiaDungeon.dungeonRegistry().get(id);
            if (configOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("�c? Donjon introuvable : " + sanitize(id)));
                return;
            }
            Vec3 pos = ArcadiaDungeon.placementRegistry().getSpawn(id).orElse(Vec3.ZERO);
            String dim = ArcadiaDungeon.placementRegistry().getDimension(id).orElse("");
            boolean set = ArcadiaDungeon.placementRegistry().isSetup(id);
            player.connection.send(new DungeonEditDataPayload(id, GSON.toJson(configOpt.get()),
                pos.x, pos.y, pos.z, dim, set));
        });
    }

    public static void handleSaveDungeonConfig(SaveDungeonConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!SAVE_CONFIG_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            String json = payload.configJson() != null ? payload.configJson() : "";
            if (!isValidDungeonResourceId(id) || json.isEmpty() || json.length() > 65536) return;
            try {
                DungeonConfig cfg = GSON.fromJson(json, DungeonConfig.class);
                if (cfg == null || cfg.id() == null || !cfg.id().equals(id)) return;
                ArcadiaDungeon.dungeonRegistry().save(cfg);
                player.sendSystemMessage(Component.literal("�a? Config sauvegardee : " + sanitize(id)));
                sendDungeonList(player);
            } catch (JsonSyntaxException e) {
                player.sendSystemMessage(Component.literal("�c? Erreur JSON : " + e.getMessage()));
            }
        });
    }

    public static void handleSaveGlobalClasses(SaveGlobalClassesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!SAVE_CONFIG_LIMITER.tryAcquire(player.getUUID())) return;
            try {
                DungeonConfig.ArchetypeDefinition[] parsed =
                    GSON.fromJson(payload.classesJson(), DungeonConfig.ArchetypeDefinition[].class);
                ArcadiaDungeon.globalClassRegistry().save(parsed != null ? Arrays.asList(parsed) : List.of());
                player.sendSystemMessage(Component.literal("�a? Classes gratuites sauvegardees."));
                sendDungeonList(player);
            } catch (JsonSyntaxException e) {
                player.sendSystemMessage(Component.literal("�c? Erreur JSON classes : " + e.getMessage()));
            }
        });
    }

    public static void handleSelectLoadoutClass(SelectLoadoutClassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!LOADOUT_SELECT_LIMITER.tryAcquire(player.getUUID())) return;
            String classId = payload.classId() != null ? payload.classId().trim() : "";
            PlayerProgress progress = ArcadiaDungeon.playerProgressService()
                .getOrCreate(player.getUUID(), player.getGameProfile().getName());
            boolean customAllowed = PlayerProgress.CUSTOM_LOADOUT_ID.equals(classId) && progress.customLoadoutUnlocked();
            if (!customAllowed && !ArcadiaDungeon.globalClassRegistry().isKnownClass(classId)) {
                ArcadiaToast.error(player, "arcadia.toast.loadout.unknown_class", sanitize(classId));
                sendPlayerProgress(player);
                return;
            }
            ArcadiaDungeon.playerProgressService().selectClass(
                player.getUUID(), player.getGameProfile().getName(), classId);
            sendPlayerProgress(player);
            ArcadiaDungeon.LOGGER.info("[Arcadia][LOADOUT] event=class_selected player={} class={}",
                player.getGameProfile().getName(), sanitize(classId));
        });
    }

    public static void handleSaveCustomLoadout(SaveCustomLoadoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!LOADOUT_SAVE_LIMITER.tryAcquire(player.getUUID())) return;
            String main = normalizeItemId(payload.mainItem(), PlayerProgress.DEFAULT_CUSTOM_MAIN);
            String off = normalizeItemId(payload.offItem(), PlayerProgress.DEFAULT_CUSTOM_OFF);
            String utility = normalizeItemId(payload.utilityItem(), PlayerProgress.DEFAULT_CUSTOM_UTILITY);
            if (!isKnownItem(main) || !isKnownItem(off) || !isKnownItem(utility)) {
                ArcadiaToast.error(player, "arcadia.server.loadout.invalid_item");
                sendPlayerProgress(player);
                return;
            }
            boolean saved = ArcadiaDungeon.playerProgressService().saveCustomLoadout(
                player.getUUID(), player.getGameProfile().getName(), main, off, utility);
            if (!saved) {
                ArcadiaToast.error(player, "arcadia.server.loadout.locked");
                sendPlayerProgress(player);
                return;
            }
            ArcadiaToast.success(player, "arcadia.server.loadout.saved");
            sendPlayerProgress(player);
            ArcadiaDungeon.LOGGER.info("[Arcadia][LOADOUT] event=custom_saved player={} main={} off={} utility={}",
                player.getGameProfile().getName(), sanitize(main), sanitize(off), sanitize(utility));
        });
    }

    public static void handleSaveZone(SaveZonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!SAVE_ZONE_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            String dim = payload.dimension() != null && !payload.dimension().isBlank()
                ? payload.dimension().trim() : ArcadiaDungeon.DUNGEON_DIMENSION_ID;
            ArcadiaDungeon.placementRegistry().setSpawn(id, new Vec3(payload.x(), payload.y(), payload.z()), dim);
            player.sendSystemMessage(Component.literal("�a? Spawn enregistre : " + sanitize(id)));
        });
    }

    public static void handleCaptureSpawn(CaptureSpawnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!SAVE_ZONE_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            Vec3 pos = new Vec3(player.getX(), player.getY(), player.getZ());
            String dim = player.level().dimension().location().toString();
            ArcadiaDungeon.placementRegistry().setSpawn(id, pos, dim);
            ArcadiaDungeon.dungeonRegistry().get(id).ifPresent(cfg ->
                player.connection.send(new DungeonEditDataPayload(id, GSON.toJson(cfg), pos.x, pos.y, pos.z, dim, true)));
            player.sendSystemMessage(Component.literal("�a? Spawn capture : " + sanitize(id)));
        });
    }

    public static void handleRequestAreaWand(RequestAreaWandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!SAVE_ZONE_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            if (ArcadiaDungeon.dungeonRegistry().get(id).isEmpty()) return;
            DungeonAreaWandEventHandler.beginSelection(player, id);
        });
    }

    public static void handleGenerateDungeonTemplate(GenerateDungeonTemplatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) return;
            if (!TEMPLATE_LIMITER.tryAcquire(player.getUUID())) return;
            String id = payload.dungeonId() != null ? payload.dungeonId().trim() : "";
            var cfgOpt = ArcadiaDungeon.dungeonRegistry().get(id);
            ResourceLocation structure = ResourceLocation.tryParse(payload.structureRef());
            ResourceLocation dimLoc = ResourceLocation.tryParse(payload.dimension());
            if (cfgOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("[Arcadia] Donjon inconnu : " + sanitize(id)));
                return;
            }
            if (structure == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Structure invalide : " + sanitize(payload.structureRef())));
                return;
            }
            if (dimLoc == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Dimension invalide : " + sanitize(payload.dimension())));
                return;
            }
            ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
            if (level == null) {
                player.sendSystemMessage(Component.literal("[Arcadia] Dimension introuvable : " + sanitize(payload.dimension())));
                return;
            }

            DungeonConfig cfg = cfgOpt.get();
            int slot = DungeonPlacementSlots.clampSlot(payload.placementSlot());
            if (DungeonPlacementSlots.isOccupied(ArcadiaDungeon.dungeonRegistry().dungeons().values(),
                id, dimLoc.toString(), slot)) {
                player.sendSystemMessage(Component.literal("[Arcadia] Slot de placement deja utilise : " + slot));
                return;
            }

            int originY = payload.originY() > -64 && payload.originY() < 320
                ? payload.originY() : DungeonPlacementSlots.DEFAULT_Y;
            BlockPos origin = DungeonPlacementSlots.originFor(slot, originY);
            StructurePlacementScheduler.ClearArea clearArea = shouldClearPreviousGeneration(cfg, dimLoc.toString(), slot, payload.resetExisting())
                ? previousGeneratedArea(player.getServer(), cfg)
                : null;
            boolean queued = ArcadiaDungeon.structurePlacementScheduler().enqueueTemplate(level, structure, origin, clearArea,
                "admin-generate:" + id,
                placed -> {
                    Vec3 spawn = placed.spawnPos();
                    BlockPos size = placed.size();
                    String dim = level.dimension().location().toString();
                    DungeonConfig.AreaPos area1 = new DungeonConfig.AreaPos(dim, origin.getX(), origin.getY(), origin.getZ());
                    DungeonConfig.AreaPos area2 = new DungeonConfig.AreaPos(dim,
                        origin.getX() + Math.max(0, size.getX() - 1),
                        origin.getY() + Math.max(0, size.getY() - 1),
                        origin.getZ() + Math.max(0, size.getZ() - 1));
                    DungeonConfig latest = ArcadiaDungeon.dungeonRegistry().get(id).orElse(cfg);
                    DungeonConfig updated = latest.withGeneration(structure.toString(), dim, origin.getY(), area1, area2,
                        area1, new DungeonConfig.GeneratedSize(size.getX(), size.getY(), size.getZ()), slot);
                    ArcadiaDungeon.dungeonRegistry().save(updated);
                    ArcadiaDungeon.placementRegistry().setSpawn(id, spawn, dim);
                    if (player.connection != null) {
                        player.connection.send(new DungeonEditDataPayload(id, GSON.toJson(updated), spawn.x, spawn.y, spawn.z, dim, true));
                        player.teleportTo(level, spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
                        player.sendSystemMessage(Component.literal("[Arcadia] NBT genere : " + sanitize(id)));
                    }
                },
                message -> player.sendSystemMessage(Component.literal("[Arcadia] " + message.getString())),
                progress -> {
                    if (player.connection != null) {
                        player.connection.send(new StructurePlacementStatusPayload(
                            id,
                            progress.stage(),
                            progress.processed(),
                            progress.total(),
                            progress.done(),
                            progress.success(),
                            progress.message()
                        ));
                    }
                });
            if (queued) {
                player.sendSystemMessage(Component.literal("[Arcadia] Generation NBT planifiee : " + sanitize(id) + " slot " + slot));
            }
        });
    }

    private static boolean shouldClearPreviousGeneration(DungeonConfig cfg,
                                                         String targetDimension,
                                                         int targetSlot,
                                                         boolean resetExisting) {
        if (cfg.generatedOrigin() == null || cfg.generatedSize() == null || cfg.generatedSlot() == null) {
            return false;
        }
        if (resetExisting) {
            return true;
        }
        return cfg.generatedSlot() == targetSlot
            && targetDimension.equals(cfg.generatedOrigin().dimension());
    }

    private static StructurePlacementScheduler.ClearArea previousGeneratedArea(MinecraftServer server, DungeonConfig cfg) {
        if (cfg.generatedOrigin() == null || cfg.generatedSize() == null) return null;

        DungeonConfig.AreaPos oldOrigin = cfg.generatedOrigin();
        DungeonConfig.GeneratedSize oldSize = cfg.generatedSize();
        ResourceLocation oldDim = ResourceLocation.tryParse(oldOrigin.dimension());
        if (oldDim == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][STRUCT] event=clear_skip dungeon={} reason=invalid_dimension dim={}",
                cfg.id(), oldOrigin.dimension());
            return null;
        }

        ServerLevel oldLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, oldDim));
        if (oldLevel == null) {
            ArcadiaDungeon.LOGGER.warn("[Arcadia][STRUCT] event=clear_skip dungeon={} reason=missing_dimension dim={}",
                cfg.id(), oldDim);
            return null;
        }

        return new StructurePlacementScheduler.ClearArea(oldLevel,
            new BlockPos(oldOrigin.x(), oldOrigin.y(), oldOrigin.z()),
            new BlockPos(oldSize.x(), oldSize.y(), oldSize.z()));
    }
    /**
     * Sanitise une chaîne venant du client avant de la loguer
     * (évite l'injection CRLF dans les logs).
     */
    private static void sendDungeonList(ServerPlayer player) {
        player.connection.send(new DungeonListPayload(buildDungeonSummaries(), buildGlobalClasses()));
        sendPlayerProgress(player);
    }

    private static String resolveSelectedClass(ServerPlayer player, String requestedClassId) {
        PlayerProgress progress = ArcadiaDungeon.playerProgressService()
            .getOrCreate(player.getUUID(), player.getGameProfile().getName());

        String requested = requestedClassId != null ? requestedClassId.trim() : "";
        if (PlayerProgress.CUSTOM_LOADOUT_ID.equals(requested) && progress.customLoadoutUnlocked()) {
            ArcadiaDungeon.playerProgressService().selectClass(
                player.getUUID(), player.getGameProfile().getName(), requested);
            return requested;
        }
        if (ArcadiaDungeon.globalClassRegistry().isKnownClass(requested)) {
            ArcadiaDungeon.playerProgressService().selectClass(
                player.getUUID(), player.getGameProfile().getName(), requested);
            return requested;
        }

        String persisted = progress.selectedClassId() != null ? progress.selectedClassId().trim() : "";
        if (PlayerProgress.CUSTOM_LOADOUT_ID.equals(persisted) && progress.customLoadoutUnlocked()) {
            return persisted;
        }
        if (ArcadiaDungeon.globalClassRegistry().isKnownClass(persisted)) {
            return persisted;
        }

        List<DungeonConfig.ArchetypeDefinition> classes = ArcadiaDungeon.globalClassRegistry().classes();
        if (classes.isEmpty()) return "";
        String fallback = classes.getFirst().id();
        ArcadiaDungeon.playerProgressService().selectClass(
            player.getUUID(), player.getGameProfile().getName(), fallback);
        return fallback;
    }

    public static void sendPlayerProgress(ServerPlayer player) {
        PlayerProgress progress = ArcadiaDungeon.playerProgressService()
            .getOrCreate(player.getUUID(), player.getGameProfile().getName());
        List<PlayerProgressPayload.DungeonStat> stats = new ArrayList<>();
        int totalRuns = 0;
        long bestTime = 0L;
        for (var entry : progress.dungeons().entrySet()) {
            PlayerProgress.DungeonProgress dungeon = entry.getValue();
            totalRuns += Math.max(0, dungeon.completions);
            if (dungeon.bestTimeSeconds > 0 && (bestTime == 0 || dungeon.bestTimeSeconds < bestTime)) {
                bestTime = dungeon.bestTimeSeconds;
            }
            stats.add(new PlayerProgressPayload.DungeonStat(
                entry.getKey(), dungeon.completions, dungeon.bestTimeSeconds));
        }
        player.connection.send(new PlayerProgressPayload(progress.currency(), totalRuns, bestTime,
            progress.selectedClassId(), progress.customLoadoutUnlocked(), progress.loadoutPoints(),
            progress.customMainItem(), progress.customOffItem(), progress.customUtilityItem(), stats));
    }

    private static String normalizeItemId(String itemId, String fallback) {
        String raw = itemId != null ? itemId.trim() : "";
        if (raw.isEmpty()) return fallback;
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }

    private static boolean isKnownItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id != null && BuiltInRegistries.ITEM.getOptional(id).isPresent();
    }

    private static List<DungeonListPayload.DungeonSummary> buildDungeonSummaries() {
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
        return summaries;
    }

    private static List<DungeonListPayload.ClassSummary> buildGlobalClasses() {
        List<DungeonListPayload.ClassSummary> classes = new ArrayList<>();
        for (DungeonConfig.ArchetypeDefinition c : ArcadiaDungeon.globalClassRegistry().classes()) {
            classes.add(new DungeonListPayload.ClassSummary(c.id(), c.nameKey(), c.items()));
        }
        return classes;
    }

    private static String sanitize(String s) {
        if (s == null) return "null";
        return s.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(s.length(), 128));
    }

    private static String normalizeDungeonId(String id) {
        String trimmed = id != null ? id.trim() : "";
        if (trimmed.isEmpty() || trimmed.contains(":")) return trimmed;
        return ArcadiaDungeon.MODID + ":" + trimmed;
    }

    private static ServerPlayer resolveDebugTarget(ServerPlayer admin, String targetPlayer) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        String query = targetPlayer != null ? targetPlayer.trim() : "";
        if (query.isEmpty() || "self".equalsIgnoreCase(query) || "@s".equalsIgnoreCase(query)) {
            return admin;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(query)) {
                return player;
            }
        }
        return null;
    }

    private static String debugDungeonId(String dungeonId) {
        String id = dungeonId != null ? dungeonId.trim() : "";
        if (!id.isEmpty()) return normalizeDungeonId(id);
        return ArcadiaDungeon.dungeonRegistry().dungeons().keySet().stream()
            .findFirst()
            .orElse(ArcadiaDungeon.MODID + ":debug");
    }

    private static int clampInt(long value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return (int) value;
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
