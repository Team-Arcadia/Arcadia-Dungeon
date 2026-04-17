package com.arcadia.dungeon.event;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.boss.BossInstance;
import com.arcadia.dungeon.boss.BossManager;
import com.arcadia.dungeon.compat.ApotheosisCompat;
import com.arcadia.dungeon.dungeon.*;
import com.arcadia.dungeon.util.MessageUtil;
import com.arcadia.dungeon.util.SparkUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Drives all time-based dungeon logic: wave progression, recruitment countdown, timers,
 * containment, parasite eviction, anti-fly, periodic flushes, and managed combat ticking.
 */
public class DungeonTickHandler {

    // Milestone tracking (per dungeon-id + value hash)
    private final Set<Integer> announcedRecruitmentMilestones = new HashSet<>();
    private final Set<Integer> announcedTimerMilestones = new HashSet<>();

    // Throttle timestamps
    private long nextWaveCheckAt = 0L;
    private long nextRecruitmentCheckAt = 0L;
    private long nextDungeonTimerCheckAt = 0L;
    private long nextCharmCheckAt = 0L;
    private long nextAntiFlyCheckAt = 0L;
    private long nextParasiteCheckAt = 0L;
    private long nextContainmentCheckAt = 0L;
    private long nextRecruitFreezeAt = 0L;
    private long nextAvailabilityCheckAt = 0L;
    private long nextProgressFlushAt = 0L;
    private long nextWeeklyTickAt = 0L;
    private long nextPruneAt = 0L;
    private long nextManagedCombatCheckAt = 0L;
    private long nextCelebrationCheckAt = 0L;

    // Anti-fly: players whose flight was suspended inside a dungeon
    private final Map<UUID, Boolean> suspendedFlight = new HashMap<>();

    // Reference to combat handler for extended melee ticking
    private final DungeonCombatEventHandler combatHandler;

    public DungeonTickHandler(DungeonCombatEventHandler combatHandler) {
        this.combatHandler = combatHandler;
    }

    // === MAIN TICK ===

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        boolean sparkSectionStarted = SparkUtil.startSection("tick");
        try {
            try {
                long now = System.currentTimeMillis();
                DungeonManager.getInstance().processPendingFails();
                BossManager.getInstance().tickAllBosses();

                if (now >= nextWaveCheckAt) {
                    nextWaveCheckAt = now + 500L;
                    checkWaveStates();
                }
                if (now >= nextRecruitmentCheckAt) {
                    nextRecruitmentCheckAt = now + 1000L;
                    checkRecruitment();
                }
                if (now >= nextDungeonTimerCheckAt) {
                    nextDungeonTimerCheckAt = now + 1000L;
                    checkDungeonTimers();
                }
                if (now >= nextCharmCheckAt) {
                    nextCharmCheckAt = now + 5000L;
                    checkCharmSuppression();
                }
                if (now >= nextAntiFlyCheckAt) {
                    nextAntiFlyCheckAt = now + 2000L;
                    checkAntiFly();
                }
                if (now >= nextParasiteCheckAt) {
                    nextParasiteCheckAt = now + 5000L;
                    checkParasites();
                }
                if (now >= nextContainmentCheckAt) {
                    nextContainmentCheckAt = now + 5000L;
                    checkPlayerContainment();
                }
                if (now >= nextRecruitFreezeAt) {
                    nextRecruitFreezeAt = now + 5000L;
                    freezeRecruitingPlayers();
                }
                if (now >= nextAvailabilityCheckAt) {
                    nextAvailabilityCheckAt = now + 10000L;
                    DungeonManager.getInstance().checkAvailabilityAnnouncements();
                }
                if (now >= nextProgressFlushAt) {
                    nextProgressFlushAt = now + 30000L;
                    PlayerProgressManager.getInstance().flushDirty();
                }
                if (now >= nextWeeklyTickAt) {
                    nextWeeklyTickAt = now + 60000L;
                    var server = DungeonManager.getInstance().getServer();
                    if (server != null) WeeklyLeaderboard.getInstance().tick(server);
                }
                if (now >= nextPruneAt) {
                    nextPruneAt = now + 300000L;
                    DungeonManager.getInstance().pruneExpiredData();
                }
                if (now >= nextCelebrationCheckAt) {
                    nextCelebrationCheckAt = now + 1000L;
                    checkCelebrationEnd();
                }
                if (now >= nextManagedCombatCheckAt) {
                    nextManagedCombatCheckAt = now + 100L;
                    combatHandler.checkManagedCombat(now);
                }
            } catch (RuntimeException e) {
                DungeonEventUtil.logHandlerError("onServerTick", e);
            }
        } finally {
            if (sparkSectionStarted) {
                SparkUtil.endSection(sparkSectionStarted);
            }
        }
    }

    // === WAVES ===

    private void checkWaveStates() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();

            // Handle inter-wave boss fight: wait for required inter-wave bosses to die, then resume waves
            if (instance.isWaitingForInterWaveBoss()) {
                if (instance.allRequiredBossesDefeated()) {
                    instance.setWaitingForInterWaveBoss(false);
                    instance.setState(DungeonState.ACTIVE);
                    if (instance.areWavesCompleted()) {
                        onWavesCompleted(instance);
                    }
                }
                continue;
            }

            // Allow wave processing if state is ACTIVE, or BOSS_FIGHT with only optional bosses alive
            if (instance.areWavesCompleted() || !instance.hasWaves()) continue;
            if (instance.getState() != DungeonState.ACTIVE && instance.getState() != DungeonState.BOSS_FIGHT) continue;
            if (instance.getState() == DungeonState.BOSS_FIGHT && !instance.allRequiredBossesDefeated()) continue;

            WaveInstance currentWave = instance.getCurrentWave();
            if (currentWave == null) { instance.setWavesCompleted(true); onWavesCompleted(instance); continue; }
            if (!currentWave.isSpawned() && !currentWave.tickDelay()) continue;

            if (!currentWave.isSpawned()) {
                currentWave.spawn(server, instance.getPlayerCount(), instance.getConfig().settings);
                String msg = currentWave.getConfig().startMessage;
                if (msg == null || msg.isEmpty()) msg = "&e[Donjon] &7Vague " + currentWave.getConfig().waveNumber + " !";
                MessageUtil.broadcast(instance, msg);
                DungeonManager.getInstance().triggerScriptedWalls(instance.getConfig().id, "WAVE_START:" + currentWave.getConfig().waveNumber);
                if (DungeonEventUtil.isDebugEnabled(instance.getConfig())) {
                    ArcadiaDungeon.LOGGER.debug("Wave spawned dungeon={} wave={} playerCount={}",
                            instance.getConfig().id, currentWave.getConfig().waveNumber, instance.getPlayerCount());
                }
            }

            currentWave.checkGlowing();

            if (currentWave.isCleared()) {
                int clearedWaveNumber = currentWave.getConfig().waveNumber;
                DungeonManager.getInstance().triggerScriptedWalls(instance.getConfig().id, "WAVE_COMPLETE:" + clearedWaveNumber);
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Vague " + clearedWaveNumber + " terminee!").withStyle(ChatFormatting.GREEN));
                }

                // Check for inter-wave boss after this wave
                boolean bossSpawned = BossManager.getInstance().spawnBossesForWave(instance, clearedWaveNumber);
                if (bossSpawned) {
                    instance.setWaitingForInterWaveBoss(true);
                    // Advance wave index now so waves resume after boss dies
                    if (!instance.advanceWave()) {
                        instance.setWavesCompleted(true);
                    }
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Un boss approche!").withStyle(ChatFormatting.GOLD));
                    }
                } else {
                    if (!instance.advanceWave()) onWavesCompleted(instance);
                }
            }
        }
    }

    private void onWavesCompleted(DungeonInstance instance) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        boolean bossSpawned = false;
        if (instance.hasNextBoss()) {
            bossSpawned = BossManager.getInstance().spawnNextBoss(instance);
        }

        if (bossSpawned) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) player.sendSystemMessage(Component.literal("[Donjon] Toutes les vagues eliminees! Le boss approche...").withStyle(ChatFormatting.GOLD));
            }
            if (DungeonEventUtil.isDebugEnabled(instance.getConfig())) {
                ArcadiaDungeon.LOGGER.debug("Boss spawn scheduled after waves dungeon={}", instance.getConfig().id);
            }
        } else if (instance.allRequiredBossesDefeated()) {
            // No more bosses to spawn and all required are dead — complete
            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                remaining.cleanup();
            }
            instance.getActiveBosses().clear();
            DungeonManager.getInstance().scheduleCompletion(instance.getConfig().id);
        }
        // else: required spawnAtStart bosses still alive — wait for them to die
    }

    // === RECRUITMENT ===

    private void freezeRecruitingPlayers() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() != DungeonState.RECRUITING) continue;
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Fix #18: longer duration (120 ticks = 6s), applied every 5s
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 255, false, false, false));
                }
            }
        }
    }

    /**
     * Periodic charm suppression sweep.
     * <ul>
     *   <li>Suppresses newly equipped Apotheosis charms for players already in dungeon.</li>
     *   <li>Restores charms for players whose UUID is tracked but no longer in any dungeon
     *       (handles edge-cases where the restore path was missed, e.g. crash recovery).</li>
     * </ul>
     */
    private void checkCharmSuppression() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        // Sweep active dungeon players for newly equipped charms
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() == DungeonState.RECRUITING) continue;
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    ApotheosisCompat.sweepNewCharms(player);
                }
            }
        }

        // Restore charms for tracked players who are no longer in any dungeon
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!ApotheosisCompat.hasSuppressedCharms(online.getUUID())) continue;
            if (DungeonManager.getInstance().getPlayerDungeon(online.getUUID()) == null) {
                ApotheosisCompat.restoreCharms(online);
            }
        }
    }

    /**
     * Polls CELEBRATING dungeons every second; calls finalizeDungeon once the timer expires.
     */
    private void checkCelebrationEnd() {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            if (instance.getState() != DungeonState.CELEBRATING) continue;
            if (instance.isCelebrationOver()) {
                DungeonManager.getInstance().finalizeDungeon(entry.getKey());
            }
        }
    }

    private void checkRecruitment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() != DungeonState.RECRUITING) continue;

            long remaining = instance.getRecruitmentRemainingSeconds();

            // Fix #9: range-based milestone checks (won't miss on lag)
            int[] milestones = {60, 30, 10, 5};
            for (int m : milestones) {
                int key = Objects.hash(dungeonId, m);
                if (remaining <= m && remaining > m - 2 && !announcedRecruitmentMilestones.contains(key)) {
                    announcedRecruitmentMilestones.add(key);
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Recrutement: " + m + "s restantes!").withStyle(ChatFormatting.YELLOW));
                    }
                    DungeonManager.getInstance().broadcastClickableReminder(instance.getConfig(), m);
                }
            }

            if (instance.isRecruitmentOver()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.removeEffect(MobEffects.BLINDNESS);
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        // Disable Apotheosis charms for the duration of the dungeon
                        ApotheosisCompat.suppressCharms(player);
                    }
                }
                clearRecruitmentMilestones(dungeonId);
                DungeonManager.getInstance().finishRecruitment(dungeonId);
            }
        }
    }

    // === CONTAINMENT / PARASITE / ANTI-FLY ===

    private void checkPlayerContainment() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            var config = instance.getConfig();
            if (!config.hasArea() || instance.getState() == DungeonState.RECRUITING) continue;

            for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) continue;
                if (!config.isInArea(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
                    DungeonManager.getInstance().teleportToSpawn(player, config.spawnPoint);
                    player.sendSystemMessage(Component.literal("[Arcadia] Vous ne pouvez pas quitter la zone du donjon!").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private void checkParasites() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getPlayers().isEmpty()) continue;
            var config = instance.getConfig();
            if (!config.hasArea() || instance.getState() == DungeonState.RECRUITING) continue;

            Set<UUID> dungeonPlayers = instance.getPlayers();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (dungeonPlayers.contains(player.getUUID())) continue;
                if (player.isSpectator()) continue;
                if (player.hasPermissions(2)) continue;
                try {
                    if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIPARASITE)) {
                        continue;
                    }
                } catch (RuntimeException e) {
                    ArcadiaDungeon.LOGGER.debug("Impossible de vérifier BYPASS_ANTIPARASITE pour {} : {}", player.getName().getString(), e.getMessage());
                }

                String dim = player.level().dimension().location().toString();
                if (!config.isInArea(dim, player.getX(), player.getY(), player.getZ())) continue;

                net.minecraft.server.level.ServerLevel overworld = server.overworld();
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("[Arcadia] Un donjon est en cours dans cette zone! Vous avez ete teleporte au spawn.")
                        .withStyle(ChatFormatting.RED));
            }

            ResourceLocation dimLoc = ResourceLocation.tryParse(config.areaPos1.dimension);
            if (dimLoc == null) continue;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;

            int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
            int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
            int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
            int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
            int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
            int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);
            AABB area = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (entity instanceof ServerPlayer) continue;
                if (entity.getTags().contains("arcadia_managed")) continue;
                entity.discard();
            }
        }
    }

    private void checkAntiFly() {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return;

        Set<UUID> dungeonPlayers = new HashSet<>();
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getState() == DungeonState.RECRUITING) continue;

            for (UUID playerId : instance.getPlayers()) {
                dungeonPlayers.add(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.isAlive() || player.isSpectator()) continue;
                if (player.hasPermissions(2)) continue;
                try {
                    if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIFLY)) {
                        restoreFlight(player);
                        continue;
                    }
                } catch (RuntimeException e) {
                    ArcadiaDungeon.LOGGER.debug("Impossible de vérifier BYPASS_ANTIFLY pour {} : {}", player.getName().getString(), e.getMessage());
                }

                if (player.getAbilities().mayfly && !suspendedFlight.containsKey(playerId)) {
                    suspendedFlight.put(playerId, true);
                }

                boolean changed = false;
                if (player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                    changed = true;
                }
                if (player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    changed = true;
                }
                if (player.isFallFlying()) {
                    player.stopFallFlying();
                }
                if (changed) {
                    player.onUpdateAbilities();
                    player.sendSystemMessage(Component.literal("[Arcadia] Le fly est desactive dans ce donjon.").withStyle(ChatFormatting.RED));
                }
            }
        }

        for (UUID playerId : new ArrayList<>(suspendedFlight.keySet())) {
            if (dungeonPlayers.contains(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                restoreFlight(player);
            } else {
                suspendedFlight.remove(playerId);
            }
        }
    }

    private void restoreFlight(ServerPlayer player) {
        if (!Boolean.TRUE.equals(suspendedFlight.remove(player.getUUID()))) return;
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    // === TIMERS ===

    private void checkDungeonTimers() {
        clearOrphanedMilestones();
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();
            if (instance.getState() == DungeonState.RECRUITING) continue;
            if (instance.getState() == DungeonState.CELEBRATING) continue;

            if (instance.isTimedOut()) {
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps ecoule!").withStyle(ChatFormatting.RED));
                }
                clearDungeonMilestones(dungeonId, instance);
                DungeonManager.getInstance().scheduleRemoval(dungeonId);
                continue;
            }

            int timeLimit = instance.getConfig().settings.timeLimitSeconds;
            if (timeLimit > 0) {
                long remaining = timeLimit - instance.getElapsedSeconds();
                // Fix #9: range-based checks
                List<Integer> milestones = instance.getConfig().settings.timerWarnings;
                for (int m : milestones) {
                    int key = Objects.hash(dungeonId, "timer", m);
                    if (remaining <= m && remaining > m - 2 && !announcedTimerMilestones.contains(key)) {
                        announcedTimerMilestones.add(key);
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Temps restant: " + DungeonManager.formatTime(m) + "!").withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }
        }
    }

    /** Called externally by DungeonManager when a dungeon finishes recruitment. */
    public void clearRecruitmentMilestones(String dungeonId) {
        int[] milestones = {60, 30, 10, 5};
        for (int m : milestones) {
            announcedRecruitmentMilestones.remove(Objects.hash(dungeonId, m));
        }
    }

    private void clearTimerMilestones(String dungeonId, DungeonInstance instance) {
        if (instance == null) return;
        for (int m : instance.getConfig().settings.timerWarnings) {
            announcedTimerMilestones.remove(Objects.hash(dungeonId, "timer", m));
        }
    }

    private void clearDungeonMilestones(String dungeonId, DungeonInstance instance) {
        clearRecruitmentMilestones(dungeonId);
        clearTimerMilestones(dungeonId, instance);
    }

    private void clearOrphanedMilestones() {
        Set<String> activeDungeonIds = DungeonManager.getInstance().getActiveInstances().keySet();
        announcedRecruitmentMilestones.removeIf(key -> !belongsToActiveDungeon(key, activeDungeonIds, false));
        announcedTimerMilestones.removeIf(key -> !belongsToActiveDungeon(key, activeDungeonIds, true));
    }

    private boolean belongsToActiveDungeon(int storedKey, Set<String> activeDungeonIds, boolean timerKey) {
        for (String dungeonId : activeDungeonIds) {
            if (timerKey) {
                DungeonInstance instance = DungeonManager.getInstance().getInstance(dungeonId);
                if (instance == null) continue;
                for (int milestone : instance.getConfig().settings.timerWarnings) {
                    if (storedKey == Objects.hash(dungeonId, "timer", milestone)) return true;
                }
            } else {
                int[] milestones = {60, 30, 10, 5};
                for (int milestone : milestones) {
                    if (storedKey == Objects.hash(dungeonId, milestone)) return true;
                }
            }
        }
        return false;
    }
}
