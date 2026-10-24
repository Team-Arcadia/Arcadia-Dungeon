package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.DungeonConfig;
import com.vyrriox.arcadiadungeon.config.RewardConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

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
        return (System.currentTimeMillis() - startTime) / 1000;
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

    // === REWARDS ===

    public void giveRewards(ServerPlayer player, List<RewardConfig> rewards) {
        for (RewardConfig reward : rewards) {
            if (level_random() <= reward.chance) {
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

    private double level_random() {
        return server.overworld().getRandom().nextDouble();
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
    }
}
