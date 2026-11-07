package com.vyrriox.arcadiadungeon.dungeon;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.boss.BossInstance;
import com.vyrriox.arcadiadungeon.config.DungeonSettings;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.WaveConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class WaveInstance {
    private final WaveConfig config;
    private final List<TrackedMob> mobs = new ArrayList<>();
    private DungeonInstance run;
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

    public boolean spawn(MinecraftServer server, DungeonInstance run) {
        if (spawned) return false;
        spawned = true;
        this.run = run;

        int playerCount = run.getPlayerCount();
        DungeonSettings settings = run.getConfig().settings;
        boolean adaptive = settings != null && settings.difficultyScaling && playerCount > 1;
        double healthScale = adaptive ? 1.0 + (settings.waveHealthMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        double damageScale = adaptive ? 1.0 + (settings.waveDamageMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        int refused = 0;

        for (MobSpawnConfig mobConfig : config.mobs) {
            Optional<EntityType<?>> typeOpt = EntityType.byString(mobConfig.entityType);
            if (typeOpt.isEmpty()) {
                ArcadiaDungeon.LOGGER.error("Wave {}: unknown entity type {}", config.waveNumber, mobConfig.entityType);
                continue;
            }

            Identifier dimLoc = Identifier.tryParse(mobConfig.spawnPoint.dimension);
            if (dimLoc == null) {
                ArcadiaDungeon.LOGGER.error("Wave {}: invalid dimension {}", config.waveNumber, mobConfig.spawnPoint.dimension);
                continue;
            }

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                ArcadiaDungeon.LOGGER.error("Wave {}: dimension {} does not exist on this server", config.waveNumber, mobConfig.spawnPoint.dimension);
                continue;
            }

            // Scale mob count based on player count
            double countScale = adaptive ? 1.0 + (settings.waveCountMultiplierPerPlayer * (playerCount - 1)) : 1.0;
            int scaledCount = (int) Math.round(mobConfig.count * countScale);
            if (scaledCount < mobConfig.count) scaledCount = mobConfig.count; // never less than base

            for (int i = 0; i < scaledCount; i++) {
                Entity entity;
                try {
                    entity = typeOpt.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                } catch (Exception e) {
                    ArcadiaDungeon.LOGGER.error("Failed to create wave mob: {}", mobConfig.entityType, e);
                    continue;
                }
                if (entity == null) continue;

                if (!(entity instanceof LivingEntity living)) {
                    entity.discard();
                    continue;
                }

                double offsetX = scaledCount > 1 ? (level.getRandom().nextDouble() - 0.5) * 3 : 0;
                double offsetZ = scaledCount > 1 ? (level.getRandom().nextDouble() - 0.5) * 3 : 0;
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

                // Custom attributes can raise the maximum after the health above was set.
                living.setHealth(living.getMaxHealth());

                // Prevent despawn
                if (living instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }

                // Tags for tracking and no-loot
                living.addTag("arcadia_wave_mob");
                living.addTag("arcadia_wave_" + config.waveNumber);
                living.addTag(ManagedEntities.MANAGED_TAG);
                living.addTag(ManagedEntities.NO_LOOT_TAG);
                living.addTag(run.getRunTag());

                DungeonStats.remember(living);
                // Another mod can cancel any spawn, and a mob that never entered the world can
                // never be killed. Waiting on it held the wave until the timer ran out.
                if (level.addFreshEntity(living)) {
                    mobs.add(new TrackedMob(living, level,
                            new Vec3(mobConfig.spawnPoint.x, mobConfig.spawnPoint.y, mobConfig.spawnPoint.z)));
                    successfulSpawns++;
                } else {
                    refused++;
                }
            }
        }

        if (refused > 0) {
            ArcadiaDungeon.LOGGER.error("Wave {}: {} mob(s) were refused by another mod and did not enter the world",
                    config.waveNumber, refused);
        }
        if (successfulSpawns == 0 && !config.mobs.isEmpty()) {
            ArcadiaDungeon.LOGGER.warn("Wave {} failed to spawn any mobs! Check entity types.", config.waveNumber);
            cleared = true;
        }

        spawnTimeMillis = System.currentTimeMillis();
        run.noteProgress();
        ArcadiaDungeon.LOGGER.info("Spawned wave {} with {} mobs", config.waveNumber, successfulSpawns);
        return true;
    }

    public void checkGlowing() {
        if (glowingApplied || !spawned || cleared) return;
        if (!config.glowingAfterDelay || config.glowingDelaySeconds <= 0) return;

        long elapsed = (System.currentTimeMillis() - spawnTimeMillis) / 1000;
        if (elapsed >= config.glowingDelaySeconds) {
            glowingApplied = true;
            for (LivingEntity mob : getAliveMobs()) {
                if (mob.isAlive()) {
                    mob.setGlowingTag(true);
                }
            }
        }
    }

    public boolean isCleared() {
        if (cleared) return true;
        if (!spawned) return false;
        prune();
        if (mobs.isEmpty()) {
            cleared = true;
        }
        return cleared;
    }

    /** Forgets the mobs that are dead or gone for good. A mob that is only unloaded stays counted. */
    private void prune() {
        long now = System.currentTimeMillis();
        int before = mobs.size();
        mobs.removeIf(mob -> {
            TrackedMob.Presence presence = mob.follow(now);
            return presence == TrackedMob.Presence.KILLED || presence == TrackedMob.Presence.REMOVED;
        });
        if (mobs.size() < before && run != null) run.noteProgress();
    }

    public boolean isSpawned() {
        return spawned;
    }

    public int getRemainingMobs() {
        prune();
        return mobs.size();
    }

    public WaveConfig getConfig() {
        return config;
    }

    /** The mobs of this wave that are in the world right now. */
    public List<LivingEntity> getAliveMobs() {
        List<LivingEntity> present = new ArrayList<>(mobs.size());
        for (TrackedMob mob : mobs) {
            if (!mob.entity().isRemoved()) present.add(mob.entity());
        }
        return present;
    }

    public List<TrackedMob> getTrackedMobs() {
        return mobs;
    }

    public void cleanup() {
        for (TrackedMob mob : mobs) {
            mob.discard();
        }
        mobs.clear();
    }
}
