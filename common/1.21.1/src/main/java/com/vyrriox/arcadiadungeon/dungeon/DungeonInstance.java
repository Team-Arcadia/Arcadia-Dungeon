package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.RewardConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class DungeonInstance {
    private final DungeonConfig config;
    private final MinecraftServer server;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<String, BossInstance> activeBosses = new LinkedHashMap<>();
    private DungeonState state = DungeonState.WAITING;
    private long startTime;
    private long recruitmentEndTime;
    private int currentBossIndex = 0;
    private final List<WaveInstance> waveInstances = new ArrayList<>();
    private int currentWaveIndex = 0;
    private boolean wavesCompleted = false;
    private final Map<UUID, Integer> playerDeaths = new LinkedHashMap<>();
    private boolean waitingForInterWaveBoss = false;

    /** Stamped on every mob of this run, so a mob read back from disk can say which run it is from. */
    private final String runTag = "arcadia_run_" + UUID.randomUUID();
    private long lastProgressMillis = System.currentTimeMillis();
    private final Set<Integer> announcedMilestones = new HashSet<>();
    /** Mobs this run let into its area without spawning them itself, removed when it ends. */
    private final List<LivingEntity> extras = new ArrayList<>();
    /** Mod ids of the mobs this dungeon is made of, minecraft excluded. */
    private Set<String> ownNamespaces;

    public DungeonInstance(DungeonConfig config, MinecraftServer server) {
        this.config = config;
        this.server = server;

        for (WaveConfig waveConfig : config.waves) {
            waveInstances.add(new WaveInstance(waveConfig));
        }
        if (config.waves.isEmpty()) {
            wavesCompleted = true;
        }
    }

    // === RECRUITMENT ===

    public void startRecruitment() {
        state = DungeonState.RECRUITING;
        recruitmentEndTime = System.currentTimeMillis() + (config.recruitmentDurationSeconds * 1000L);
    }

    public boolean isRecruitmentOver() {
        return System.currentTimeMillis() >= recruitmentEndTime;
    }

    public long getRecruitmentRemainingSeconds() {
        long remaining = (recruitmentEndTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void startDungeon() {
        state = DungeonState.ACTIVE;
        startTime = System.currentTimeMillis();
    }

    // === PLAYERS ===

    public void addPlayer(ServerPlayer player) {
        players.add(player.getUUID());
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public int getPlayerCount() {
        return players.size();
    }

    // === STATE ===

    public DungeonConfig getConfig() {
        return config;
    }

    public DungeonState getState() {
        return state;
    }

    public void setState(DungeonState state) {
        this.state = state;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedSeconds() {
        // No start time yet during recruitment: this used to read as fifty years.
        return startTime == 0 ? 0 : (System.currentTimeMillis() - startTime) / 1000;
    }

    public String getRunTag() {
        return runTag;
    }

    /** Something happened in the fight: a mob spawned, took a hit from a player, or died. */
    public void noteProgress() {
        lastProgressMillis = System.currentTimeMillis();
    }

    public long getLastProgressMillis() {
        return lastProgressMillis;
    }

    /** True the first time this milestone comes up in this run. */
    public boolean announceOnce(int milestone) {
        return announcedMilestones.add(milestone);
    }

    /** Every mob the run is waiting on right now: its bosses, their summons, and the current wave. */
    public void forEachTracked(Consumer<TrackedMob> action) {
        for (BossInstance boss : List.copyOf(activeBosses.values())) {
            if (boss.getTracked() != null) action.accept(boss.getTracked());
            boss.getTrackedSummons().forEach(action);
        }
        WaveInstance wave = getCurrentWave();
        if (wave != null) wave.getTrackedMobs().forEach(action);
    }

    /**
     * A modded boss usually spawns adds or parts of itself, of its own mod, by itself. Refusing them
     * at the door of the area breaks the boss without a word, so they are let in.
     */
    public boolean bringsOwnMobs(EntityType<?> type) {
        if (ownNamespaces == null) {
            ownNamespaces = new HashSet<>();
            for (var boss : config.bosses) {
                addNamespace(boss.entityType);
                for (var phase : boss.phases) {
                    for (var summon : phase.summonMobs) addNamespace(summon.entityType);
                }
            }
            for (WaveConfig wave : config.waves) {
                for (var mob : wave.mobs) addNamespace(mob.entityType);
            }
        }
        if (ownNamespaces.isEmpty()) return false;
        return ownNamespaces.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace());
    }

    private void addNamespace(String entityType) {
        int separator = entityType == null ? -1 : entityType.indexOf(':');
        if (separator > 0 && !"minecraft".equals(entityType.substring(0, separator))) {
            ownNamespaces.add(entityType.substring(0, separator));
        }
    }

    /** Takes in a mob the run did not spawn, so it leaves with the run. */
    public void adoptExtra(LivingEntity living) {
        living.addTag(ManagedEntities.MANAGED_TAG);
        living.addTag(runTag);
        extras.add(living);
    }

    public boolean isTimedOut() {
        if (state == DungeonState.RECRUITING) return false;
        return config.settings.timeLimitSeconds > 0 && getElapsedSeconds() >= config.settings.timeLimitSeconds;
    }

    // === BOSSES ===

    public Map<String, BossInstance> getActiveBosses() {
        return activeBosses;
    }

    public void addBossInstance(String bossId, BossInstance boss) {
        activeBosses.put(bossId, boss);
        // Only switch to BOSS_FIGHT for required bosses — optional bosses don't block waves
        var bossConfig = config.bosses.stream().filter(b -> b.id.equals(bossId)).findFirst().orElse(null);
        if (bossConfig == null || bossConfig.requiredKill) {
            state = DungeonState.BOSS_FIGHT;
        }
    }

    public void removeBossInstance(String bossId) {
        activeBosses.remove(bossId);
    }

    public boolean allBossesDefeated() {
        return activeBosses.isEmpty() && currentBossIndex >= config.bosses.size();
    }

    public int getCurrentBossIndex() {
        return currentBossIndex;
    }

    public void incrementBossIndex() {
        currentBossIndex++;
    }

    public boolean hasNextBoss() {
        return currentBossIndex < config.bosses.size();
    }

    /**
     * Check if all requiredKill bosses are dead.
     * Non-required bosses still alive don't block completion.
     */
    public boolean allRequiredBossesDefeated() {
        for (Map.Entry<String, BossInstance> entry : activeBosses.entrySet()) {
            // Find the config for this active boss
            for (var bossConfig : config.bosses) {
                if (bossConfig.id.equals(entry.getKey()) && bossConfig.requiredKill) {
                    return false; // A required boss is still alive
                }
            }
        }
        return true;
    }

    // === WAVES ===

    public List<WaveInstance> getWaveInstances() {
        return waveInstances;
    }

    public int getCurrentWaveIndex() {
        return currentWaveIndex;
    }

    public boolean areWavesCompleted() {
        return wavesCompleted;
    }

    public void setWavesCompleted(boolean completed) {
        this.wavesCompleted = completed;
    }

    public WaveInstance getCurrentWave() {
        if (currentWaveIndex < waveInstances.size()) {
            return waveInstances.get(currentWaveIndex);
        }
        return null;
    }

    public boolean advanceWave() {
        currentWaveIndex++;
        if (currentWaveIndex >= waveInstances.size()) {
            wavesCompleted = true;
            return false;
        }
        return true;
    }

    public boolean hasWaves() {
        return !waveInstances.isEmpty();
    }

    // === INTER-WAVE BOSS ===

    public boolean isWaitingForInterWaveBoss() {
        return waitingForInterWaveBoss;
    }

    public void setWaitingForInterWaveBoss(boolean waiting) {
        this.waitingForInterWaveBoss = waiting;
    }

    // === DEATHS ===

    public int addDeath(UUID playerId) {
        int deaths = playerDeaths.getOrDefault(playerId, 0) + 1;
        playerDeaths.put(playerId, deaths);
        return deaths;
    }

    public int getDeaths(UUID playerId) {
        return playerDeaths.getOrDefault(playerId, 0);
    }

    public int getRemainingLives(UUID playerId) {
        int max = config.settings.maxDeaths;
        if (max <= 0) return Integer.MAX_VALUE;
        return Math.max(0, max - getDeaths(playerId));
    }

    /** The lives to show a player. With no limit there is no number: this used to say 2147483647. */
    public String getRemainingLivesLabel(UUID playerId) {
        return config.settings.maxDeaths <= 0 ? "\u221E" : String.valueOf(getRemainingLives(playerId));
    }

    // === REWARDS ===

    public void giveRewards(ServerPlayer player, List<RewardConfig> rewards) {
        // Items given to a dead player are lost with the inventory it drops: the rewards wait for
        // the respawn. This hit whoever was on the death screen when a teammate finished the boss.
        if (player.isDeadOrDying()) {
            DungeonManager.getInstance().deferRewards(player.getUUID(), rewards);
            return;
        }
        grantRewards(server, player, rewards);
    }

    public static void grantRewards(MinecraftServer server, ServerPlayer player, List<RewardConfig> rewards) {
        for (RewardConfig reward : rewards) {
            if (server.overworld().getRandom().nextDouble() <= reward.chance) {
                if (reward.item != null && !reward.item.isEmpty()) {
                    ResourceLocation loc = ResourceLocation.tryParse(reward.item);
                    if (loc != null) {
                        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(loc);
                        itemOpt.ifPresent(item -> {
                            ItemStack stack = new ItemStack(item, reward.count);
                            if (!player.getInventory().add(stack)) {
                                player.drop(stack, false);
                            }
                        });
                    }
                }

                if (reward.experience > 0) {
                    player.giveExperiencePoints(reward.experience);
                }

                if (reward.command != null && !reward.command.isEmpty()) {
                    String cmd = reward.command.replace("%player%", player.getName().getString());
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(), cmd
                    );
                }
            }
        }
    }

    // === UTILS ===

    public String getPlayerNames(MinecraftServer server) {
        StringBuilder sb = new StringBuilder();
        for (UUID id : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(p.getName().getString());
            }
        }
        return sb.toString();
    }

    public void cleanup() {
        for (BossInstance boss : activeBosses.values()) {
            boss.cleanup();
        }
        activeBosses.clear();
        for (WaveInstance wave : waveInstances) {
            wave.cleanup();
        }
        for (LivingEntity extra : extras) {
            if (extra.isAlive()) extra.discard();
        }
        extras.clear();
    }
}
