package com.arcadia.dungeon.dungeon;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.boss.BossInstance;
import com.arcadia.dungeon.config.DungeonSettings;
import com.arcadia.dungeon.config.MobSpawnConfig;
import com.arcadia.dungeon.config.WaveConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.*;

public class WaveInstance {
    private final WaveConfig config;
    private final List<LivingEntity> aliveMobs = new ArrayList<>();
    private boolean spawned = false;
    private boolean cleared = false;
    private int successfulSpawns = 0;
    private long nextSpawnAtMillis = -1L;
    private long spawnTimeMillis = 0;
    private boolean glowingApplied = false;

    public WaveInstance(WaveConfig config) {
        this.config = config;
    }

    public boolean tickDelay() {
        long now = System.currentTimeMillis();
        if (nextSpawnAtMillis < 0) {
            nextSpawnAtMillis = now + (config.delayBeforeSeconds * 1000L);
        }
        return now >= nextSpawnAtMillis;
    }

    public boolean spawn(MinecraftServer server) {
        return spawn(server, 1, null);
    }

    public boolean spawn(MinecraftServer server, int playerCount, DungeonSettings settings) {
        if (spawned) return false;
        spawned = true;

        boolean adaptive = settings != null && settings.difficultyScaling && playerCount > 1;
        double healthScale = adaptive ? 1.0 + (settings.waveHealthMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        double damageScale = adaptive ? 1.0 + (settings.waveDamageMultiplierPerPlayer * (playerCount - 1)) : 1.0;

        for (MobSpawnConfig mobConfig : config.mobs) {
            Optional<EntityType<?>> typeOpt = EntityType.byString(mobConfig.entityType);
            if (typeOpt.isEmpty()) {
                ArcadiaDungeon.LOGGER.error("Unknown entity type for wave mob: {}", mobConfig.entityType);
                continue;
            }

            ResourceLocation dimLoc = ResourceLocation.tryParse(mobConfig.spawnPoint.dimension);
            if (dimLoc == null) {
                ArcadiaDungeon.LOGGER.error("Invalid dimension: {}", mobConfig.spawnPoint.dimension);
                continue;
            }

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) continue;

            // Scale mob count based on player count
            double countScale = adaptive ? 1.0 + (settings.waveCountMultiplierPerPlayer * (playerCount - 1)) : 1.0;
            int scaledCount = (int) Math.round(mobConfig.count * countScale);
            if (scaledCount < mobConfig.count) scaledCount = mobConfig.count; // never less than base

            for (int i = 0; i < scaledCount; i++) {
                Entity entity;
                try {
                    entity = typeOpt.get().create(level);
                } catch (Exception e) {
                    ArcadiaDungeon.LOGGER.error("Failed to create wave mob: {}", mobConfig.entityType, e);
                    continue;
                }
                if (entity == null) continue;

                if (!(entity instanceof LivingEntity living)) {
                    entity.discard();
                    continue;
                }

                double offsetX = mobConfig.count > 1 ? (level.getRandom().nextDouble() - 0.5) * 3 : 0;
                double offsetZ = mobConfig.count > 1 ? (level.getRandom().nextDouble() - 0.5) * 3 : 0;
                SpawnSafety.placeAtSafeSpawn(
                        living,
                        mobConfig.spawnPoint,
                        mobConfig.spawnPoint.x + offsetX,
                        mobConfig.spawnPoint.y,
                        mobConfig.spawnPoint.z + offsetZ,
                        4
                );

                // Custom name
                if (mobConfig.customName != null && !mobConfig.customName.isEmpty()) {
                    living.setCustomName(DungeonManager.parseColorCodes(mobConfig.customName));
                    living.setCustomNameVisible(true);
                }

                // Health (with adaptive scaling)
                if (living.getAttribute(Attributes.MAX_HEALTH) != null && mobConfig.health > 0) {
                    double scaledHealth = mobConfig.health * healthScale;
                    living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(scaledHealth);
                    living.setHealth((float) scaledHealth);
                }

                // Damage (with adaptive scaling)
                if (living.getAttribute(Attributes.ATTACK_DAMAGE) != null && mobConfig.damage > 0) {
                    living.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(mobConfig.damage * damageScale);
                }

                // Speed
                if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null && mobConfig.speed > 0) {
                    living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(mobConfig.speed);
                }

                // Equipment
                BossInstance.equipEntity(living, mobConfig.mainHand, mobConfig.offHand,
                        mobConfig.helmet, mobConfig.chestplate, mobConfig.leggings, mobConfig.boots);

                // Custom attributes
                BossInstance.applyCustomAttributes(living, mobConfig.customAttributes);
                CombatTuning.applyConfiguredCombat(
                        living,
                        mobConfig.attackRange,
                        mobConfig.attackCooldownMs,
                        mobConfig.aggroRange,
                        mobConfig.projectileCooldownMs,
                        mobConfig.dodgeChance,
                        mobConfig.dodgeCooldownMs,
                        mobConfig.dodgeProjectilesOnly,
                        mobConfig.dodgeMessage
                );

                // Prevent despawn
                if (living instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }

                // Tags for tracking and no-loot
                living.addTag("arcadia_wave_mob");
                living.addTag("arcadia_wave_" + config.waveNumber);
                living.addTag("arcadia_managed");
                living.addTag("arcadia_no_loot");

                level.addFreshEntity(living);
                aliveMobs.add(living);
                successfulSpawns++;
            }
        }

        if (successfulSpawns == 0 && !config.mobs.isEmpty()) {
            ArcadiaDungeon.LOGGER.warn("Wave {} failed to spawn any mobs! Check entity types.", config.waveNumber);
            cleared = true;
        }

        spawnTimeMillis = System.currentTimeMillis();
        ArcadiaDungeon.LOGGER.info("Spawned wave {} with {} mobs", config.waveNumber, successfulSpawns);
        return true;
    }

    public void checkContainmentArea(com.arcadia.dungeon.config.DungeonConfig dungeonConfig) {
        // Wave mobs are no longer forcibly recentered. Spawn validation handles bad points up front.
    }

    public void checkGlowing() {
        if (glowingApplied || !spawned || cleared) return;
        if (!config.glowingAfterDelay || config.glowingDelaySeconds <= 0) return;

        long elapsed = (System.currentTimeMillis() - spawnTimeMillis) / 1000;
        if (elapsed >= config.glowingDelaySeconds) {
            glowingApplied = true;
            for (LivingEntity mob : aliveMobs) {
                if (mob.isAlive()) {
                    mob.setGlowingTag(true);
                }
            }
        }
    }

    public boolean isCleared() {
        if (cleared) return true;
        aliveMobs.removeIf(mob -> !mob.isAlive());
        if (aliveMobs.isEmpty() && spawned) {
            cleared = true;
        }
        return cleared;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public int getRemainingMobs() {
        aliveMobs.removeIf(mob -> !mob.isAlive());
        return aliveMobs.size();
    }

    public WaveConfig getConfig() {
        return config;
    }

    public List<LivingEntity> getAliveMobs() {
        return aliveMobs;
    }

    public void cleanup() {
        for (LivingEntity mob : aliveMobs) {
            if (mob.isAlive()) {
                mob.discard();
            }
        }
        aliveMobs.clear();
    }
}
