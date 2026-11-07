package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.config.Messages;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.boss.BossManager;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.ConfigManager;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.RewardConfig;
import com.vyrriox.arcadiadungeon.config.SpawnPointConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DungeonManager {
    private static final DungeonManager INSTANCE = new DungeonManager();

    private MinecraftServer server;
    private final Map<String, DungeonInstance> activeInstances = new ConcurrentHashMap<>();
    // Per-dungeon cooldown: key = "uuid:dungeonId"
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnPointConfig> playerReturnPoints = new ConcurrentHashMap<>();
    private final Map<String, Long> dungeonAvailability = new ConcurrentHashMap<>();
    private final Set<String> availabilityAnnounced = new HashSet<>();
    private final Map<UUID, Map<String, Long>> allowedBeneficialEffects = new ConcurrentHashMap<>();
    // Reverse lookup: player UUID -> dungeon ID for O(1) lookups
    private final Map<UUID, String> playerToDungeon = new ConcurrentHashMap<>();
    // Dungeons pending fail (to avoid reentrant modification)
    private final Set<String> pendingFails = ConcurrentHashMap.newKeySet();
    // Players who were dead when the run sent them home, waiting for their respawn
    private final Set<UUID> returnOnRespawn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<RewardConfig>> rewardsOnRespawn = new ConcurrentHashMap<>();
    // Set while a run executes one of its own commands, so what the command summons is let in
    private DungeonInstance commandRun = null;

    private DungeonManager() {}

    public static DungeonManager getInstance() {
        return INSTANCE;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server == null) {
            saveTimers();
            activeInstances.clear();
            playerCooldowns.clear();
            dungeonAvailability.clear();
            availabilityAnnounced.clear();
            playerReturnPoints.clear();
            allowedBeneficialEffects.clear();
            playerToDungeon.clear();
            pendingFails.clear();
            pendingPlayerRemovals.clear();
            returnOnRespawn.clear();
            rewardsOnRespawn.clear();
        } else {
            PlayerProgressManager.getInstance().loadAll();
            DungeonTimerStore.load(playerCooldowns, dungeonAvailability);
            // A timer that ran out while the server was down announces nothing at start
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : dungeonAvailability.entrySet()) {
                DungeonConfig config = ConfigManager.getInstance().getDungeon(entry.getKey());
                if (config == null || (now - entry.getValue()) / 1000 >= config.availableEverySeconds) {
                    availabilityAnnounced.add(entry.getKey());
                }
            }
        }
    }

    private void saveTimers() {
        DungeonTimerStore.save(playerCooldowns, dungeonAvailability);
    }

    /**
     * True when both dungeons have an area in the same dimension and the two boxes touch.
     * A normal dungeon and its challenge version share the same build, so they must never run together.
     */
    private static boolean areasOverlap(DungeonConfig a, DungeonConfig b) {
        if (!a.hasArea() || !b.hasArea()) return false;
        if (!a.areaPos1.dimension.equals(b.areaPos1.dimension)) return false;
        return rangesOverlap(a.areaPos1.x, a.areaPos2.x, b.areaPos1.x, b.areaPos2.x)
                && rangesOverlap(a.areaPos1.y, a.areaPos2.y, b.areaPos1.y, b.areaPos2.y)
                && rangesOverlap(a.areaPos1.z, a.areaPos2.z, b.areaPos1.z, b.areaPos2.z);
    }

    private static boolean rangesOverlap(int a1, int a2, int b1, int b2) {
        return Math.max(Math.min(a1, a2), Math.min(b1, b2)) <= Math.min(Math.max(a1, a2), Math.max(b1, b2));
    }

    public MinecraftServer getServer() {
        return server;
    }

    public DungeonInstance startDungeon(String dungeonId, ServerPlayer initiator) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.unknown_dungeon", "id", dungeonId));
            return null;
        }

        if (!config.enabled) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.disabled"));
            return null;
        }

        if (activeInstances.containsKey(dungeonId)) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.already_running"));
            return null;
        }

        // Joining already refuses this. A player left in two runs is pulled back by both of them.
        if (getPlayerDungeon(initiator.getUUID()) != null) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.already_in_dungeon"));
            return null;
        }

        if (config.bosses.isEmpty() && config.waves.isEmpty()) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.empty"));
            return null;
        }

        // On peaceful every monster is removed on its first tick, persistent or not, so the run
        // would announce its waves as cleared and its boss would never be there.
        if (server.overworld().getDifficulty() == Difficulty.PEACEFUL && hasMonsters(config)) {
            initiator.sendSystemMessage(Messages.get(initiator, "error.peaceful"));
            ArcadiaDungeon.LOGGER.warn("Refused to start {}: the server is on peaceful and this dungeon spawns monsters", dungeonId);
            return null;
        }

        for (DungeonInstance other : activeInstances.values()) {
            DungeonConfig otherConfig = other.getConfig();
            if (areasOverlap(config, otherConfig)) {
                initiator.sendSystemMessage(Messages.get(initiator, "error.same_area", "dungeon", otherConfig.name));
                return null;
            }
        }

        // Check progression requirement
        if (config.requiredDungeon != null && !config.requiredDungeon.isEmpty()) {
            if (!PlayerProgressManager.getInstance().hasCompleted(initiator.getUUID().toString(), config.requiredDungeon)) {
                DungeonConfig required = ConfigManager.getInstance().getDungeon(config.requiredDungeon);
                String requiredName = required != null ? required.name : config.requiredDungeon;
                initiator.sendSystemMessage(Messages.get(initiator, "error.requires_dungeon", "dungeon", requiredName));
                return null;
            }
        }

        // Check per-dungeon cooldown
        String cooldownKey = initiator.getUUID() + ":" + dungeonId;
        Long lastRun = playerCooldowns.get(cooldownKey);
        if (lastRun != null && config.cooldownSeconds > 0) {
            long elapsed = (System.currentTimeMillis() - lastRun) / 1000;
            if (elapsed < config.cooldownSeconds) {
                long remaining = config.cooldownSeconds - elapsed;
                initiator.sendSystemMessage(Messages.get(initiator, "error.cooldown", "time", formatTime(remaining)));
                return null;
            }
        }

        // Check dungeon availability timer
        if (config.availableEverySeconds > 0) {
            Long lastAvailable = dungeonAvailability.get(dungeonId);
            if (lastAvailable != null) {
                long elapsed = (System.currentTimeMillis() - lastAvailable) / 1000;
                if (elapsed < config.availableEverySeconds) {
                    long remaining = config.availableEverySeconds - elapsed;
                    initiator.sendSystemMessage(Messages.get(initiator, "error.available_in", "time", formatTime(remaining)));
                    return null;
                }
            }
        }

        // Save return point & create instance
        saveReturnPoint(initiator);
        DungeonInstance instance = new DungeonInstance(config, server);
        instance.addPlayer(initiator);
        playerToDungeon.put(initiator.getUUID(), dungeonId);
        activeInstances.put(dungeonId, instance);
        teleportToSpawn(initiator, config.spawnPoint);

        // Recruitment phase or immediate start
        if (config.recruitmentDurationSeconds > 0) {
            instance.startRecruitment();
            broadcastClickableJoin(config, initiator.getName().getString(), config.recruitmentDurationSeconds);
            MutableComponent recruitMsg = Messages.get(initiator, "recruit.opened", "time", config.recruitmentDurationSeconds)
                    .append(" ");
            MutableComponent leaveBtn2 = leaveButton(initiator);
            initiator.sendSystemMessage(recruitMsg.append(leaveBtn2));
        } else {
            beginRun(instance);
            if (config.announceStart) {
                broadcast(config.startMessage, "announce.start",
                        "player", initiator.getName().getString(), "dungeon", config.name, "id", config.id);
            }
        }

        ArcadiaDungeon.LOGGER.info("Dungeon {} started by {}", dungeonId, initiator.getName().getString());
        return instance;
    }

    public boolean joinDungeon(String dungeonId, ServerPlayer player) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) {
            player.sendSystemMessage(Messages.get(player, "error.no_active", "id", dungeonId));
            return false;
        }

        DungeonConfig config = instance.getConfig();

        // Allow joining only during recruitment phase
        if (instance.getState() != DungeonState.RECRUITING) {
            player.sendSystemMessage(Messages.get(player, "error.doors_closed", "dungeon", config.name));
            return false;
        }

        if (instance.getPlayers().size() >= config.settings.maxPlayers) {
            player.sendSystemMessage(Messages.get(player, "error.full"));
            return false;
        }

        // Check if already in a dungeon
        if (getPlayerDungeon(player.getUUID()) != null) {
            player.sendSystemMessage(Messages.get(player, "error.already_in_dungeon"));
            return false;
        }

        // Anti-monopole: frequent players can only join after half the recruitment timer
        if (config.settings.antiMonopole && config.recruitmentDurationSeconds > 0) {
            int weeklyCount = WeeklyLeaderboard.getInstance().getData()
                    .playerCompletions.getOrDefault(player.getUUID().toString(), 0);

            if (weeklyCount >= config.settings.antiMonopoleThreshold) {
                long remaining = instance.getRecruitmentRemainingSeconds();
                long halfTime = config.recruitmentDurationSeconds / 2;
                if (remaining > halfTime) {
                    long waitSeconds = remaining - halfTime;
                    player.sendSystemMessage(Messages.get(player, "error.anti_monopole",
                            "count", weeklyCount, "time", formatTime(waitSeconds)));
                    return false;
                }
            }
        }

        saveReturnPoint(player);
        instance.addPlayer(player);
        playerToDungeon.put(player.getUUID(), dungeonId);
        teleportToSpawn(player, config.spawnPoint);

        MutableComponent joinMsg = Messages.get(player, "join.joined", "dungeon", config.name).append(" ");
        MutableComponent leaveBtn = leaveButton(player);
        player.sendSystemMessage(joinMsg.append(leaveBtn));

        // Notify other players in dungeon
        for (UUID otherId : instance.getPlayers()) {
            if (!otherId.equals(player.getUUID())) {
                ServerPlayer other = server.getPlayerList().getPlayer(otherId);
                if (other != null) {
                    other.sendSystemMessage(Messages.get(other, "join.other", "player", player.getName().getString()));
                }
            }
        }

        return true;
    }

    public void finishRecruitment(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null || instance.getState() != DungeonState.RECRUITING) return;

        DungeonConfig config = instance.getConfig();

        if (instance.getPlayerCount() < config.settings.minPlayers) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    teleportBack(player);
                    player.sendSystemMessage(Messages.get(player, "recruit.not_enough",
                            "count", instance.getPlayerCount(), "min", config.settings.minPlayers));
                }
                playerToDungeon.remove(playerId);
            }
            instance.cleanup();
            activeInstances.remove(dungeonId);
            return;
        }

        beginRun(instance);

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Messages.get(player, "start.begins", "count", instance.getPlayerCount()));
            }
        }

        if (config.announceStart) {
            broadcast(null, "start.gates_closing", "dungeon", config.name, "count", instance.getPlayerCount());
        }
    }

    /**
     * What starts a run, with or without a recruitment before it. The two paths used to do different
     * things: without recruitment the bosses meant for the start never came, and with one a dungeon
     * that has no waves never spawned its boss at all.
     */
    private void beginRun(DungeonInstance instance) {
        DungeonConfig config = instance.getConfig();

        // Kick parasites from dungeon area before starting
        kickParasites(instance);

        // Start the actual dungeon
        instance.startDungeon();

        // Spawn bosses configured to appear at dungeon start
        for (BossConfig bossConfig : config.bosses) {
            if (!bossConfig.spawnAtStart) continue;
            if (bossConfig.optional && bossConfig.spawnChance < 1.0) {
                double roll = server.overworld().getRandom().nextDouble();
                if (roll > bossConfig.spawnChance) continue;
            }
            ResourceLocation dimLoc = ResourceLocation.tryParse(bossConfig.spawnPoint.dimension);
            if (dimLoc == null) continue;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;
            BossInstance bossInstance = new BossInstance(bossConfig, level, instance);
            if (bossInstance.spawn()) {
                instance.addBossInstance(bossConfig.id, bossInstance);
                instance.setState(DungeonState.ACTIVE);
                bossInstance.enterFirstPhase();
            }
        }

        // With no waves to wait for, the fight starts now, unless a required boss of the start stands
        // first: the next one comes when it dies.
        if (!instance.hasWaves() && instance.allRequiredBossesDefeated()
                && !BossManager.getInstance().spawnNextBoss(instance) && instance.getActiveBosses().isEmpty()) {
            // Every boss was optional and rolled to stay away, or none can spawn, for instance because
            // its mod was removed. Nobody fought anything, so the run ends with no reward and no timer.
            ArcadiaDungeon.LOGGER.warn("Dungeon {}: nothing came, the run ends without a reward. Check debug validate.", config.id);
            for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) player.sendSystemMessage(Messages.get(player, "run.nothing_came"));
            }
            forceReset(config.id);
        }
    }

    private static boolean hasMonsters(DungeonConfig config) {
        for (BossConfig boss : config.bosses) {
            if (isMonster(boss.entityType)) return true;
        }
        for (var wave : config.waves) {
            for (var mob : wave.mobs) {
                if (isMonster(mob.entityType)) return true;
            }
        }
        return false;
    }

    private static boolean isMonster(String entityType) {
        return EntityType.byString(entityType).map(type -> type.getCategory() == MobCategory.MONSTER).orElse(false);
    }

    /** The run this mob was spawned by, or null when that run is over. */
    public DungeonInstance runOf(Entity entity) {
        for (DungeonInstance instance : activeInstances.values()) {
            if (entity.getTags().contains(instance.getRunTag())) return instance;
        }
        return null;
    }

    /**
     * True for a dungeon mob read back from disk that no run has a use for: its run is over, or it is
     * a boss the run gave up waiting for and replaced.
     */
    public boolean isLeftover(Entity entity) {
        DungeonInstance run = runOf(entity);
        if (run == null) return true;
        if (!entity.getTags().contains("arcadia_boss")) return false;
        for (BossInstance boss : run.getActiveBosses().values()) {
            if (boss.matches(entity)) return false;
        }
        return true;
    }

    public DungeonInstance getCommandRun() {
        return commandRun;
    }

    /** Runs a command of the dungeon's config. What it summons in the area belongs to the run. */
    public void runOwnCommand(DungeonInstance run, String command) {
        DungeonInstance previous = commandRun;
        commandRun = run;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        } finally {
            commandRun = previous;
        }
    }

    /** Renews the hold on the chunks of every mob the runs are waiting on. */
    public void holdRunChunks() {
        for (DungeonInstance instance : activeInstances.values()) {
            instance.forEachTracked(TrackedMob::holdChunk);
        }
    }

    public void completeDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        DungeonConfig config = instance.getConfig();
        instance.setState(DungeonState.COMPLETED);

        long completionTime = instance.getElapsedSeconds();

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                instance.giveRewards(player, config.completionRewards);

                PlayerProgressManager.getInstance().recordCompletion(
                        playerId.toString(), player.getName().getString(), dungeonId, completionTime
                );
                WeeklyLeaderboard.getInstance().recordCompletion(
                        playerId.toString(), player.getName().getString()
                );

                if (config.teleportBackOnComplete) {
                    teleportBack(player);
                }

                // Per-dungeon cooldown
                playerCooldowns.put(playerId + ":" + dungeonId, System.currentTimeMillis());

                player.sendSystemMessage(Messages.get(player, "end.completed", "time", formatTime(completionTime)));
            }
        }

        if (config.availableEverySeconds > 0) {
            dungeonAvailability.put(dungeonId, System.currentTimeMillis());
            availabilityAnnounced.remove(dungeonId);
        }
        saveTimers();

        if (config.announceCompletion && server != null) {
            String playerNames = instance.getPlayerNames(server);
            broadcast(config.completionMessage, "announce.completion",
                    "player", playerNames, "dungeon", config.name, "id", config.id);
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
        ArcadiaDungeon.LOGGER.info("Dungeon {} completed", dungeonId);
    }

    public void failDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        DungeonConfig config = instance.getConfig();
        // A run that never started costs nothing: whoever opens and leaves a recruitment does not get
        // to close a daily dungeon for everyone.
        boolean started = instance.getState() != DungeonState.RECRUITING;
        instance.setState(DungeonState.FAILED);

        // Capture player names BEFORE teleporting them back
        String playerNames = instance.getPlayerNames(server);

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                if (config.teleportBackOnComplete) {
                    teleportBack(player);
                }
                player.sendSystemMessage(Messages.get(player, "end.failed"));
            }
        }

        if (started && config.availableEverySeconds > 0) {
            dungeonAvailability.put(dungeonId, System.currentTimeMillis());
            availabilityAnnounced.remove(dungeonId);
        }
        saveTimers();

        if (config.announceCompletion && server != null && !playerNames.isEmpty()) {
            broadcast(config.failMessage, "announce.fail",
                    "player", playerNames, "dungeon", config.name, "id", config.id);
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
    }

    public void stopDungeon(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                teleportBack(player);
                player.sendSystemMessage(Messages.get(player, "end.stopped"));
            }
        }

        for (UUID pid : instance.getPlayers()) playerToDungeon.remove(pid);
        for (UUID pid : instance.getPlayers()) clearAllowedBeneficialEffects(pid);
        instance.cleanup();
        activeInstances.remove(dungeonId);
    }

    public void stopAllDungeons() {
        for (String id : new ArrayList<>(activeInstances.keySet())) {
            stopDungeon(id);
        }
    }

    public void forceReset(String dungeonId) {
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null) return;

        for (UUID playerId : new ArrayList<>(instance.getPlayers())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                teleportBack(player);
                player.sendSystemMessage(Messages.get(player, "end.reset"));
            }
            playerToDungeon.remove(playerId);
        }

        instance.cleanup();
        for (UUID playerId : instance.getPlayers()) clearAllowedBeneficialEffects(playerId);
        activeInstances.remove(dungeonId);
        ArcadiaDungeon.LOGGER.info("Dungeon {} force reset", dungeonId);
    }

    public void scheduleFailDungeon(String dungeonId) {
        pendingFails.add(dungeonId);
    }

    private final Set<UUID> pendingPlayerRemovals = ConcurrentHashMap.newKeySet();

    public void schedulePlayerRemoval(UUID playerId) {
        pendingPlayerRemovals.add(playerId);
    }

    public boolean isPendingRemoval(UUID playerId) {
        return pendingPlayerRemovals.contains(playerId);
    }

    public void processPendingFails() {
        if (!pendingPlayerRemovals.isEmpty()) {
            List<UUID> removals = new ArrayList<>(pendingPlayerRemovals);
            pendingPlayerRemovals.clear();
            for (UUID id : removals) {
                removePlayerFromDungeon(id);
            }
        }
        if (!pendingFails.isEmpty()) {
            List<String> toProcess = new ArrayList<>(pendingFails);
            pendingFails.clear();
            for (String id : toProcess) {
                failDungeon(id);
            }
        }
    }

    public void removePlayerFromDungeon(UUID playerId) {
        String dungeonId = playerToDungeon.get(playerId);
        if (dungeonId == null) {
            return;
        }
        DungeonInstance instance = activeInstances.get(dungeonId);
        if (instance == null || !instance.getPlayers().contains(playerId)) {
            playerToDungeon.remove(playerId);
            clearAllowedBeneficialEffects(playerId);
            return;
        }

        String playerName = "";
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            playerName = player.getName().getString();
            for (BossInstance boss : instance.getActiveBosses().values()) {
                boss.removePlayerFromBossBar(player);
            }
            teleportBack(player);
        }

        instance.removePlayer(playerId);
        playerToDungeon.remove(playerId);
        clearAllowedBeneficialEffects(playerId);

        if (!instance.getPlayers().isEmpty() && !playerName.isEmpty()) {
            String finalName = playerName;
            for (UUID otherId : instance.getPlayers()) {
                ServerPlayer other = server.getPlayerList().getPlayer(otherId);
                if (other != null) {
                    other.sendSystemMessage(Messages.get(other, "run.player_left", "player", finalName, "count", instance.getPlayerCount()));
                }
            }
        }

        if (instance.getPlayers().isEmpty()) {
            DungeonConfig cfg = instance.getConfig();
            if (cfg.announceCompletion && !playerName.isEmpty() && instance.getState() != DungeonState.RECRUITING) {
                broadcast(cfg.failMessage, "announce.fail",
                        "player", playerName, "dungeon", cfg.name, "id", cfg.id);
            }
            scheduleFailDungeon(cfg.id);
        }
    }

    /**
     * Seconds left before this player may run this dungeon again, or 0 when they may now.
     *
     * <p>Read-only: {@link #startDungeon} still enforces the cooldown and tells the player
     * why it refused. This is here so a listing can show the wait instead of offering a run
     * that is going to be turned down.</p>
     */
    public long getCooldownRemainingSeconds(UUID playerId, String dungeonId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null || config.cooldownSeconds <= 0) return 0;
        Long lastRun = playerCooldowns.get(playerId + ":" + dungeonId);
        if (lastRun == null) return 0;
        long elapsed = (System.currentTimeMillis() - lastRun) / 1000;
        return Math.max(0, config.cooldownSeconds - elapsed);
    }

    /** Seconds until this dungeon opens again on its availability timer, or 0 when it is open. */
    public long getAvailabilityRemainingSeconds(String dungeonId) {
        DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
        if (config == null || config.availableEverySeconds <= 0) return 0;
        Long lastAvailable = dungeonAvailability.get(dungeonId);
        if (lastAvailable == null) return 0;
        long elapsed = (System.currentTimeMillis() - lastAvailable) / 1000;
        return Math.max(0, config.availableEverySeconds - elapsed);
    }

    public void clearCooldown(UUID playerId) {
        playerCooldowns.entrySet().removeIf(e -> e.getKey().startsWith(playerId.toString()));
        saveTimers();
    }

    public void clearAllCooldowns() {
        playerCooldowns.clear();
        saveTimers();
    }

    public void allowBeneficialEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int durationTicks) {
        if (player == null || effect == null || !effect.value().isBeneficial()) return;
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString() + "#" + amplifier;
        long expiresAt = System.currentTimeMillis() + ((long) durationTicks * 50L) + 3000L;
        allowedBeneficialEffects.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>()).put(effectId, expiresAt);
    }

    public void allowBeneficialEffect(ServerPlayer player, net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect> effect, int amplifier, int durationTicks) {
        allowBeneficialEffect(player, (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) effect, amplifier, durationTicks);
    }

    public boolean isBeneficialEffectAllowed(ServerPlayer player, net.minecraft.world.effect.MobEffectInstance effectInstance) {
        if (player == null || effectInstance == null || !effectInstance.getEffect().value().isBeneficial()) return true;
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect().value()).toString() + "#" + effectInstance.getAmplifier();
        Map<String, Long> allowed = allowedBeneficialEffects.get(player.getUUID());
        if (allowed == null) return false;
        Long expiry = allowed.get(effectId);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            allowed.remove(effectId);
            if (allowed.isEmpty()) {
                allowedBeneficialEffects.remove(player.getUUID());
            }
            return false;
        }
        return true;
    }

    public void clearAllowedBeneficialEffects(UUID playerId) {
        allowedBeneficialEffects.remove(playerId);
    }

    public DungeonInstance getInstance(String dungeonId) {
        return activeInstances.get(dungeonId);
    }

    public Map<String, DungeonInstance> getActiveInstances() {
        return Collections.unmodifiableMap(activeInstances);
    }

    public DungeonInstance getPlayerDungeon(UUID playerId) {
        String dungeonId = playerToDungeon.get(playerId);
        if (dungeonId != null) {
            DungeonInstance instance = activeInstances.get(dungeonId);
            if (instance != null && instance.getPlayers().contains(playerId)) {
                return instance;
            }
            // Stale entry
            playerToDungeon.remove(playerId);
        }
        return null;
    }

    public void saveReturnPoint(ServerPlayer player) {
        SpawnPointConfig returnPoint = new SpawnPointConfig(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );
        playerReturnPoints.put(player.getUUID(), returnPoint);
    }

    public void teleportBack(ServerPlayer player) {
        // A dead player cannot be moved: it would respawn at its bed with the way back used up.
        if (player.isDeadOrDying()) {
            if (playerReturnPoints.containsKey(player.getUUID())) returnOnRespawn.add(player.getUUID());
            return;
        }
        SpawnPointConfig returnPoint = playerReturnPoints.remove(player.getUUID());
        if (returnPoint != null) {
            teleportToSpawn(player, returnPoint);
        }
    }

    public void deferRewards(UUID playerId, List<RewardConfig> rewards) {
        rewardsOnRespawn.computeIfAbsent(playerId, id -> new ArrayList<>()).addAll(rewards);
    }

    /** What was waiting for this player to come back from the death screen. */
    public void onPlayerRespawned(ServerPlayer player) {
        if (returnOnRespawn.remove(player.getUUID())) teleportBack(player);
        List<RewardConfig> rewards = rewardsOnRespawn.remove(player.getUUID());
        if (rewards != null) DungeonInstance.grantRewards(server, player, rewards);
    }

    public void teleportToSpawn(ServerPlayer player, SpawnPointConfig spawn) {
        if (server == null || spawn == null) return;
        ResourceLocation dimLoc = ResourceLocation.tryParse(spawn.dimension);
        if (dimLoc == null) {
            ArcadiaDungeon.LOGGER.error("Invalid dimension: {}", spawn.dimension);
            return;
        }
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel level = server.getLevel(dimKey);
        if (level != null) {
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
    }
    }

    public void checkAvailabilityAnnouncements() {
        if (server == null) return;
        for (Map.Entry<String, Long> entry : dungeonAvailability.entrySet()) {
            String dungeonId = entry.getKey();
            if (availabilityAnnounced.contains(dungeonId)) continue;

            // Don't announce if this dungeon is currently active
            if (activeInstances.containsKey(dungeonId)) continue;

            DungeonConfig config = ConfigManager.getInstance().getDungeon(dungeonId);
            if (config == null || !config.announceAvailability || config.availableEverySeconds <= 0) continue;

            long elapsed = (System.currentTimeMillis() - entry.getValue()) / 1000;
            // Minimum 10s delay before announcing to avoid spam right after fail/complete
            if (elapsed >= config.availableEverySeconds && elapsed >= 10) {
                availabilityAnnounced.add(dungeonId);
                String cmd = "/arcadia_dungeon start " + config.id;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    // Don't send to players already in a dungeon
                    if (getPlayerDungeon(player.getUUID()) != null) continue;
                    player.sendSystemMessage(Messages.getOr(player, config.availabilityMessage, "announce.availability",
                                    "dungeon", config.name, "id", config.id)
                            .append(" ")
                            .append(button(Messages.get(player, "button.start"), ChatFormatting.GREEN, cmd,
                                    Messages.get(player, "hover.start", "dungeon", config.name)
                                            .withStyle(ChatFormatting.YELLOW))));
                }
            }
        }
    }

    /**
     * Prune expired cooldowns and availability entries to prevent memory leaks.
     * Called periodically from tick handler.
     */
    public void pruneExpiredData() {
        long now = System.currentTimeMillis();

        // Prune a player cooldown once its own dungeon cooldown is over (key = "uuid:dungeonId").
        // A fixed 24h cap used to drop the cooldown of dungeons set to 36h.
        playerCooldowns.entrySet().removeIf(e -> {
            String key = e.getKey();
            int sep = key.indexOf(':');
            DungeonConfig config = sep < 0 ? null : ConfigManager.getInstance().getDungeon(key.substring(sep + 1));
            long cooldown = config == null ? 0 : config.cooldownSeconds;
            return (now - e.getValue()) / 1000 > cooldown;
        });
        allowedBeneficialEffects.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(effect -> effect.getValue() < now);
            return entry.getValue().isEmpty();
        });

        // Prune availability entries for dungeons that no longer exist or already announced
        dungeonAvailability.entrySet().removeIf(e -> {
            DungeonConfig config = ConfigManager.getInstance().getDungeon(e.getKey());
            if (config == null) return true;
            long elapsed = (now - e.getValue()) / 1000;
            return elapsed > config.availableEverySeconds + 3600; // 1h grace after expiry
        });

        // Prune return points for players not in any dungeon
        playerReturnPoints.entrySet().removeIf(e -> getPlayerDungeon(e.getKey()) == null && !returnOnRespawn.contains(e.getKey()));
    }

    private void kickParasites(DungeonInstance instance) {
        if (server == null) return;
        DungeonConfig config = instance.getConfig();
        if (!config.hasArea()) return;

        Set<UUID> dungeonPlayers = instance.getPlayers();

        // Kick parasite players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (dungeonPlayers.contains(player.getUUID())) continue;
            if (player.isSpectator() || player.hasPermissions(2)) continue;
            try { if (net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(player, ArcadiaDungeon.BYPASS_ANTIPARASITE)) continue; } catch (Exception ignored) {}

            String playerDim = player.level().dimension().location().toString();
            if (config.isInArea(playerDim, player.getX(), player.getY(), player.getZ())) {
                ServerLevel overworld = server.overworld();
                net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Messages.get(player, "area.kicked_starting"));
                ArcadiaDungeon.LOGGER.info("Kicked parasite {} from dungeon area {}", player.getName().getString(), config.id);
            }
        }

        // Clear all existing mobs in the dungeon area
        clearMobsInArea(config);
    }

    private void clearMobsInArea(DungeonConfig config) {
        if (server == null || !config.hasArea()) return;

        ResourceLocation dimLoc = ResourceLocation.tryParse(config.areaPos1.dimension);
        if (dimLoc == null) return;
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) return;

        int minX = Math.min(config.areaPos1.x, config.areaPos2.x);
        int maxX = Math.max(config.areaPos1.x, config.areaPos2.x);
        int minY = Math.min(config.areaPos1.y, config.areaPos2.y);
        int maxY = Math.max(config.areaPos1.y, config.areaPos2.y);
        int minZ = Math.min(config.areaPos1.z, config.areaPos2.z);
        int maxZ = Math.max(config.areaPos1.z, config.areaPos2.z);

        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<net.minecraft.world.entity.Mob> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area);

        int cleared = 0;
        for (net.minecraft.world.entity.Mob mob : mobs) {
            // Monsters and what past runs left. Villagers, animals, somebody's pet and a named mob
            // somebody placed are part of the world.
            if (!ManagedEntities.isManaged(mob) && (!(mob instanceof net.minecraft.world.entity.monster.Enemy)
                    || mob.hasCustomName() || isOwned(mob))) continue;
            mob.discard();
            cleared++;
        }

        if (cleared > 0) {
            ArcadiaDungeon.LOGGER.info("Cleared {} mobs from dungeon area {}", cleared, config.id);
        }
    }

    private static boolean isOwned(net.minecraft.world.entity.Mob mob) {
        return mob instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerUUID() != null;
    }

    public void broadcastClickableJoin(DungeonConfig config, String playerName, long secondsLeft) {
        if (server == null) return;
        String cmd = "/arcadia_dungeon join " + config.id;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(recruitmentAnnouncement(player, config, playerName, secondsLeft)
                    .append(" ")
                    .append(button(Messages.get(player, "button.join_timed", "time", secondsLeft), ChatFormatting.GREEN, cmd,
                            Messages.get(player, "hover.join", "dungeon", config.name, "command", cmd)
                                    .withStyle(ChatFormatting.GREEN))));
        }
    }

    /**
     * The line that opens a recruitment, without its button. A dungeon's own
     * {@code recruitmentMessage} wins when it has one. It used to be written to the config, offered
     * by {@code setmessage recruitment} and then never read, so editing it changed nothing.
     */
    public static MutableComponent recruitmentAnnouncement(ServerPlayer player, DungeonConfig config,
                                                           String playerName, long secondsLeft) {
        return Messages.getOr(player, config.recruitmentMessage, "recruit.announce",
                "player", playerName, "dungeon", config.name, "id", config.id, "time", secondsLeft);
    }

    public void broadcastClickableReminder(DungeonConfig config, long secondsLeft) {
        if (server == null) return;
        String cmd = "/arcadia_dungeon join " + config.id;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Messages.get(player, "recruit.reminder", "dungeon", config.name, "time", secondsLeft)
                    .append(" ")
                    .append(button(Messages.get(player, "button.join"), ChatFormatting.GREEN, cmd,
                            Messages.get(player, "hover.join", "dungeon", config.name, "command", cmd)
                                    .withStyle(ChatFormatting.GREEN))));
        }
    }

    private static MutableComponent leaveButton(ServerPlayer player) {
        return button(Messages.get(player, "button.leave"), ChatFormatting.RED, "/arcadia_dungeon abandon",
                Messages.get(player, "hover.leave").withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent button(MutableComponent label, ChatFormatting color, String command, Component hover) {
        return label.withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /** Sends each online player the dungeon's own text if it has one, else the line in their language. */
    private void broadcast(String configured, String key, Object... args) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Messages.getOr(player, configured, key, args));
        }
    }

    /**
     * Turns {@code &} codes into styled text the way the game reads them: a format adds to the
     * current style and a colour starts a new one. Each code used to replace the one before it, so
     * {@code &c&l} came out bold and white.
     */
    public static MutableComponent parseColorCodes(String message) {
        MutableComponent result = Component.empty();
        String[] parts = message.split("&");
        Style style = Style.EMPTY;
        boolean first = true;
        for (String part : parts) {
            if (first) {
                result.append(Component.literal(part));
                first = false;
                continue;
            }
            if (part.isEmpty()) continue;
            ChatFormatting format = getFormatByCode(part.charAt(0));
            if (format == null) {
                result.append(Component.literal("&" + part).withStyle(style));
                continue;
            }
            if (format == ChatFormatting.RESET) style = Style.EMPTY;
            else style = (format.isColor() ? Style.EMPTY : style).applyFormat(format);
            result.append(Component.literal(part.substring(1)).withStyle(style));
        }
        return result;
    }

    private static ChatFormatting getFormatByCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'l' -> ChatFormatting.BOLD;
            case 'o' -> ChatFormatting.ITALIC;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }

    public static String formatTime(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
