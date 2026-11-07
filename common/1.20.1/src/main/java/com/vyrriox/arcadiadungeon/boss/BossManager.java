package com.vyrriox.arcadiadungeon.boss;

import com.vyrriox.arcadiadungeon.config.Messages;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.DungeonState;
import com.vyrriox.arcadiadungeon.dungeon.TrackedMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BossManager {
    private static final BossManager INSTANCE = new BossManager();

    private BossManager() {}

    public static BossManager getInstance() {
        return INSTANCE;
    }

    public boolean spawnNextBoss(DungeonInstance instance) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return false;

        // Find next end-of-waves boss to spawn, skipping inter-wave bosses and optional misses
        while (instance.hasNextBoss()) {
            int bossIndex = instance.getCurrentBossIndex();
            BossConfig bossConfig = instance.getConfig().bosses.get(bossIndex);

            // Skip inter-wave bosses (handled by spawnBossesForWave)
            if (bossConfig.spawnAfterWave > 0) {
                instance.incrementBossIndex();
                continue;
            }

            // Skip bosses that spawn at dungeon start (already spawned)
            if (bossConfig.spawnAtStart) {
                instance.incrementBossIndex();
                continue;
            }

            // Optional boss: roll for spawn chance
            if (bossConfig.optional && bossConfig.spawnChance < 1.0) {
                double roll = server.overworld().getRandom().nextDouble();
                if (roll > bossConfig.spawnChance) {
                    ArcadiaDungeon.LOGGER.info("Optional boss {} skipped (roll: {}, chance: {})",
                            bossConfig.id, String.format("%.2f", roll), bossConfig.spawnChance);
                    instance.incrementBossIndex();

                    // Notify players with custom or default skip message
                    String skipMsg = bossConfig.skipMessage;
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Messages.getOr(player, skipMsg, "boss.skipped", "boss", bossConfig.customName));
                    }
                    continue;
                }
            }

            // Spawn this boss
            ResourceLocation dimLoc = ResourceLocation.tryParse(bossConfig.spawnPoint.dimension);
            if (dimLoc == null) {
                ArcadiaDungeon.LOGGER.error("Invalid dimension: {}", bossConfig.spawnPoint.dimension);
                instance.incrementBossIndex();
                continue;
            }

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                ArcadiaDungeon.LOGGER.error("Could not find dimension: {}", bossConfig.spawnPoint.dimension);
                instance.incrementBossIndex();
                continue;
            }

            BossInstance bossInstance = new BossInstance(bossConfig, level, instance);
            if (!bossInstance.spawn()) {
                instance.incrementBossIndex();
                continue;
            }

            // Armor analysis
            if (instance.getConfig().settings.difficultyScaling) {
                adaptBossDamageToArmor(bossInstance, instance, server);
            }

            instance.addBossInstance(bossConfig.id, bossInstance);
            instance.incrementBossIndex();

            // Notify players with custom or default spawn message
            if (bossConfig.spawnMessage != null && !bossConfig.spawnMessage.isEmpty()) {
                String spawnMsg = bossConfig.spawnMessage.replace("%boss%", bossConfig.customName);
                net.minecraft.network.chat.Component spawnComponent = DungeonManager.parseColorCodes(spawnMsg);
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(spawnComponent);
                }
            }

            return true;
        }

        // All remaining bosses were skipped (optional)
        return false;
    }

    private void adaptBossDamageToArmor(BossInstance boss, DungeonInstance instance, MinecraftServer server) {
        if (boss.getBossEntity() == null) return;

        double totalArmor = 0;
        int count = 0;
        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.getAttribute(Attributes.ARMOR) != null) {
                totalArmor += player.getAttributeValue(Attributes.ARMOR);
                count++;
            }
        }

        if (count == 0) return;
        double avgArmor = totalArmor / count;

        // Scale: 0 armor = x1.0, 10 armor = x1.2, 20 armor = x1.5, 30+ armor = x1.8
        double armorMultiplier = 1.0 + (Math.min(avgArmor, 30) / 30.0) * 0.8;

        boss.setArmorScale(armorMultiplier);

        ArcadiaDungeon.LOGGER.info("Boss damage adapted: avg armor={}, multiplier=x{}",
                String.format("%.1f", avgArmor), String.format("%.2f", armorMultiplier));
    }

    /**
     * Spawn bosses that are configured to appear after a specific wave.
     * Returns true if at least one boss was spawned.
     */
    public boolean spawnBossesForWave(DungeonInstance instance, int waveNumber) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        if (server == null) return false;

        boolean spawned = false;
        for (BossConfig bossConfig : instance.getConfig().bosses) {
            if (bossConfig.spawnAfterWave != waveNumber) continue;

            // Optional boss: roll for spawn chance
            if (bossConfig.optional && bossConfig.spawnChance < 1.0) {
                double roll = server.overworld().getRandom().nextDouble();
                if (roll > bossConfig.spawnChance) {
                    ArcadiaDungeon.LOGGER.info("Optional inter-wave boss {} skipped (roll: {}, chance: {})",
                            bossConfig.id, String.format("%.2f", roll), bossConfig.spawnChance);
                    String skipMsg = bossConfig.skipMessage;
                    for (UUID playerId : instance.getPlayers()) {
                        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                        if (player != null) player.sendSystemMessage(Messages.getOr(player, skipMsg, "boss.skipped", "boss", bossConfig.customName));
                    }
                    continue;
                }
            }

            ResourceLocation dimLoc = ResourceLocation.tryParse(bossConfig.spawnPoint.dimension);
            if (dimLoc == null) {
                ArcadiaDungeon.LOGGER.error("Boss {}: invalid dimension {}", bossConfig.id, bossConfig.spawnPoint.dimension);
                continue;
            }
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                ArcadiaDungeon.LOGGER.error("Boss {}: dimension {} does not exist on this server", bossConfig.id, bossConfig.spawnPoint.dimension);
                continue;
            }

            BossInstance bossInstance = new BossInstance(bossConfig, level, instance);
            if (!bossInstance.spawn()) continue;

            if (instance.getConfig().settings.difficultyScaling) {
                adaptBossDamageToArmor(bossInstance, instance, server);
            }

            instance.addBossInstance(bossConfig.id, bossInstance);
            spawned = true;

            // Notify players with custom or default spawn message
            if (bossConfig.spawnMessage != null && !bossConfig.spawnMessage.isEmpty()) {
                String spawnMsg = bossConfig.spawnMessage.replace("%boss%", bossConfig.customName);
                net.minecraft.network.chat.Component spawnComponent = DungeonManager.parseColorCodes(spawnMsg);
                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) player.sendSystemMessage(spawnComponent);
                }
            }
        }
        return spawned;
    }

    public void tickAllBosses() {
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getActiveBosses().isEmpty()) continue; // Fix #19: skip when no bosses
            // A copy: a phase command or thorns can end a boss in the middle of this loop.
            for (BossInstance boss : List.copyOf(instance.getActiveBosses().values())) {
                boss.tick(instance.getPlayers());
            }
        }
    }

    /** A boss entity died. Finds its run by id, since the object may have changed along the way. */
    public void onBossEntityDied(LivingEntity entity) {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            for (Map.Entry<String, BossInstance> bossEntry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
                if (!bossEntry.getValue().matches(entity)) continue;
                onBossDefeated(instance, entry.getKey(), bossEntry.getKey(), bossEntry.getValue());
                return;
            }
        }
    }

    private void onBossDefeated(DungeonInstance instance, String dungeonId, String bossId, BossInstance boss) {
        MinecraftServer server = DungeonManager.getInstance().getServer();
        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                instance.giveRewards(player, boss.getConfig().rewards);
                String bossLabel = (boss.getConfig().customName == null || boss.getConfig().customName.isEmpty())
                        ? bossId
                        : boss.getConfig().customName;
                player.sendSystemMessage(Messages.get(player, "boss.defeated", "boss", bossLabel));
            }
        }

        // Fix #2: remove from map BEFORE cleanup to prevent re-entry issues
        instance.removeBossInstance(bossId);
        boss.cleanup();
        instance.noteProgress();
        continueAfterBoss(instance, dungeonId);
    }

    private void continueAfterBoss(DungeonInstance instance, String dungeonId) {
        MinecraftServer server = DungeonManager.getInstance().getServer();

        // Inter-wave boss: the wave check sees the required bosses are dead and resumes.
        if (instance.isWaitingForInterWaveBoss()) return;

        // The bosses meant for the end wait for the end. A boss that came with the start of the run
        // used to bring the final boss as soon as it died, in the middle of the waves.
        if (instance.hasWaves() && !instance.areWavesCompleted()) {
            if (instance.allRequiredBossesDefeated()) instance.setState(DungeonState.ACTIVE);
            return;
        }

        if (instance.hasNextBoss() && spawnNextBoss(instance)) {
            for (UUID playerId : instance.getPlayers()) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) player.sendSystemMessage(Messages.get(player, "boss.next"));
            }
            return;
        }

        if (instance.allRequiredBossesDefeated()) {
            // Cleanup non-required bosses still alive
            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                remaining.cleanup();
            }
            instance.getActiveBosses().clear();
            DungeonManager.getInstance().completeDungeon(dungeonId);
        }
    }

    /**
     * Notices a boss that left the world without a death event. The ender dragon and some modded
     * bosses die that way, and another mod can simply remove a mob. Both left the run waiting for a
     * boss that was gone until the timer ran out.
     */
    public void checkBossesStillThere(DungeonInstance instance, String dungeonId) {
        for (Map.Entry<String, BossInstance> entry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
            BossInstance boss = entry.getValue();
            TrackedMob.Presence presence = boss.presence();
            if (presence == TrackedMob.Presence.KILLED) {
                onBossDefeated(instance, dungeonId, entry.getKey(), boss);
            } else if (presence == TrackedMob.Presence.REMOVED) {
                onBossRemoved(instance, dungeonId, entry.getKey(), boss);
            }
        }
    }

    private void onBossRemoved(DungeonInstance instance, String dungeonId, String bossId, BossInstance boss) {
        if (!boss.getConfig().requiredKill) {
            ArcadiaDungeon.LOGGER.warn("Optional boss {} of {} left the world, the run goes on without it", bossId, dungeonId);
            instance.removeBossInstance(bossId);
            boss.cleanup();
            continueAfterBoss(instance, dungeonId);
            return;
        }
        if (boss.canRestore()) {
            boolean back = boss.restore();
            ArcadiaDungeon.LOGGER.warn("Boss {} of {} was removed from the world by something else: {}",
                    bossId, dungeonId, back ? "brought it back" : "could not bring it back yet");
            return;
        }
        // Peaceful difficulty, spawn-animals=false or a mod that removes this type of mob: no run can
        // be won here. Saying so beats a boss bar over nothing until the timer runs out.
        ArcadiaDungeon.LOGGER.error("Boss {} of {} cannot stay in the world (entity type {}). Failing the run. "
                        + "Check the difficulty, spawn-animals and spawn-npcs, and mods that remove mobs.",
                bossId, dungeonId, boss.getConfig().entityType);
        MinecraftServer server = DungeonManager.getInstance().getServer();
        for (UUID playerId : instance.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Messages.get(player, "boss.lost", "boss", boss.getConfig().customName));
            }
        }
        instance.removeBossInstance(bossId);
        boss.cleanup();
        DungeonManager.getInstance().scheduleFailDungeon(dungeonId);
    }
}
