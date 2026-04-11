package com.arcadia.dungeon.boss;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.util.MessageUtil;
import com.arcadia.dungeon.util.SparkUtil;
import com.arcadia.dungeon.config.BossConfig;
import com.arcadia.dungeon.dungeon.DungeonInstance;
import com.arcadia.dungeon.dungeon.DungeonManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class BossManager {
    private static final BossManager INSTANCE = new BossManager();

    private BossManager() {}

    public static BossManager getInstance() {
        return INSTANCE;
    }

    public boolean spawnNextBoss(DungeonInstance instance) {
        boolean sparkSectionStarted = SparkUtil.startSection("boss.spawn");
        try {
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
                        String skipMsg = (bossConfig.skipMessage != null && !bossConfig.skipMessage.isEmpty())
                                ? bossConfig.skipMessage.replace("%boss%", bossConfig.customName)
                                : "[Arcadia] Le " + bossConfig.customName + " n'est pas apparu cette fois...";
                        MessageUtil.broadcast(instance, skipMsg);
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

                BossInstance bossInstance = new BossInstance(bossConfig, level, instance.getPlayerCount());
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
                    MessageUtil.broadcast(instance, spawnMsg);
                }

                return true;
            }

            // All remaining bosses were skipped (optional)
            return false;
        } finally {
            if (sparkSectionStarted) {
                SparkUtil.endSection(sparkSectionStarted);
            }
        }
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

        if (boss.getBossEntity().getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double currentDamage = boss.getBossEntity().getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();
            boss.getBossEntity().getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(currentDamage * armorMultiplier);
        }

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
                    String skipMsg = (bossConfig.skipMessage != null && !bossConfig.skipMessage.isEmpty())
                            ? bossConfig.skipMessage.replace("%boss%", bossConfig.customName)
                            : "[Arcadia] Le " + bossConfig.customName + " n'est pas apparu cette fois...";
                    MessageUtil.broadcast(instance, skipMsg);
                    continue;
                }
            }

            ResourceLocation dimLoc = ResourceLocation.tryParse(bossConfig.spawnPoint.dimension);
            if (dimLoc == null) continue;
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;

            BossInstance bossInstance = new BossInstance(bossConfig, level, instance.getPlayerCount());
            if (!bossInstance.spawn()) continue;

            if (instance.getConfig().settings.difficultyScaling) {
                adaptBossDamageToArmor(bossInstance, instance, server);
            }

            instance.addBossInstance(bossConfig.id, bossInstance);
            spawned = true;

            // Notify players with custom or default spawn message
            if (bossConfig.spawnMessage != null && !bossConfig.spawnMessage.isEmpty()) {
                String spawnMsg = bossConfig.spawnMessage.replace("%boss%", bossConfig.customName);
                MessageUtil.broadcast(instance, spawnMsg);
            }
        }
        return spawned;
    }

    public void tickAllBosses() {
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            if (instance.getActiveBosses().isEmpty()) continue; // Fix #19: skip when no bosses
            for (BossInstance boss : instance.getActiveBosses().values()) {
                boss.tick(instance.getPlayers());
            }
        }
    }
}
