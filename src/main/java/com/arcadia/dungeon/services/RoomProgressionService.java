package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service progression des salles et spawn des waves (Story S2.3).
 *
 * <p>Doit être enregistré sur {@code NeoForge.EVENT_BUS} pour recevoir
 * {@link LivingDeathEvent} et détecter le nettoyage des waves.
 *
 * <p>Toutes les mutations de {@link Run} se font sur le SGT — les
 * {@link LivingDeathEvent} sont déclenchés sur le SGT par le jeu.
 */
public final class RoomProgressionService {

    private final DungeonRegistry dungeonRegistry;
    private final RunLifecycleService runLifecycleService;
    private final BossPhaseService bossPhaseService;

    /** runId → UUIDs des mobs vivants dans la wave courante. */
    private final Map<RunId, Set<UUID>> livingMobs = new ConcurrentHashMap<>();
    /** Lookup rapide mob UUID → runId pour éviter un parcours complet. */
    private final Map<UUID, RunId> mobToRun = new ConcurrentHashMap<>();
    /** Position de spawn mémorisée au démarrage de la run (position joueur MVP). */
    private final Map<RunId, Vec3> spawnPositions = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public RoomProgressionService(DungeonRegistry dungeonRegistry,
                                  RunLifecycleService runLifecycleService,
                                  BossPhaseService bossPhaseService) {
        this.dungeonRegistry = dungeonRegistry;
        this.runLifecycleService = runLifecycleService;
        this.bossPhaseService = bossPhaseService;
    }

    /**
     * Démarre les waves pour une run fraîchement créée.
     * Doit être appelé sur le SGT (run.startActivePhase() enforce via requireSGT).
     *
     * @param spawnPos position de spawn des mobs (position joueur en MVP)
     */
    public void startRunWaves(Run run, ServerLevel level, Vec3 spawnPos) {
        spawnPositions.put(run.id(), spawnPos);
        run.startActivePhase();
        bossPhaseService.spawnStartBosses(run, level, spawnPos);
        triggerCurrentWave(run, level);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=run_waves_start runId={} spawnPos={}",
            run.id(), spawnPos);
    }

    /**
     * Avance manuellement à la salle suivante (utilisé par debug commands).
     * Doit être appelé sur le SGT.
     */
    public void advanceToNextRoom(Run run, ServerLevel level) {
        cleanupWaveTracking(run.id());
        run.advanceToNextRoom();
        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;

        if (run.currentRoomIndex() > 0) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=all_rooms_cleared runId={}", run.id());
            return;
        }
        triggerCurrentWave(run, level);
        ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=advance runId={} room={}",
            run.id(), run.currentRoomIndex());
    }

    /** @return true si tous les mobs de la wave courante sont morts. */
    public boolean isCurrentRoomCleared(Run run) {
        Set<UUID> mobs = livingMobs.get(run.id());
        return mobs == null || mobs.isEmpty();
    }

    public java.util.Optional<Vec3> getSpawnPosition(RunId runId) {
        return java.util.Optional.ofNullable(spawnPositions.get(runId));
    }

    /** Nettoie les structures de tracking quand une run se termine. */
    public void cleanupRun(RunId runId) {
        cleanupWaveTracking(runId);
        spawnPositions.remove(runId);
        bossPhaseService.cleanupBoss(runId);
    }

    // ============================================================
    // Event listener SGT (LivingDeathEvent fire on game thread)
    // ============================================================

    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        if (mobToRun.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        UUID entityId = event.getEntity().getUUID();
        RunId runId = mobToRun.remove(entityId);
        if (runId == null) return;

        Set<UUID> mobs = livingMobs.get(runId);
        if (mobs == null) return;
        mobs.remove(entityId);

        if (mobs.isEmpty()) {
            runLifecycleService.findById(runId).ifPresent(run -> {
                if (event.getEntity().level() instanceof ServerLevel serverLevel) {
                    onWaveCleared(run, serverLevel);
                }
            });
        }
    }

    // ============================================================
    // Internals
    // ============================================================

    private void triggerCurrentWave(Run run, ServerLevel level) {
        long t0 = System.currentTimeMillis();
        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;

        List<DungeonConfig.Wave> waves = wavesFor(config, run.currentRoomIndex());

        if (run.currentWaveIndex() >= waves.size()) {
            // Salle sans waves (transition pure) → considérée clearée immédiatement
            onWaveCleared(run, level);
            return;
        }

        DungeonConfig.Wave wave = waves.get(run.currentWaveIndex());
        Vec3 spawnPos = spawnPositions.getOrDefault(run.id(), Vec3.ZERO);
        Set<UUID> mobs = ConcurrentHashMap.newKeySet();
        broadcastWaveMessage(run, level, wave.startMessage());

        List<DungeonConfig.MobSpawn> waveMobs = wave.mobs() != null ? wave.mobs() : List.of();
        for (DungeonConfig.MobSpawn mobSpawn : waveMobs) {
            ResourceLocation rl = ResourceLocation.tryParse(mobSpawn.mobType());
            if (rl == null) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][ROOM] invalid mob type: {}", mobSpawn.mobType());
                continue;
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (entityType == null) {
                ArcadiaDungeon.LOGGER.warn("[Arcadia][ROOM] unknown mob type: {}", mobSpawn.mobType());
                continue;
            }
            Vec3 mobSpawnPos = configuredSpawnOrDefault(mobSpawn, spawnPos);
            ServerLevel spawnLevel = configuredLevelOrDefault(level, mobSpawn);
            for (int i = 0; i < mobSpawn.count(); i++) {
                double scatter = mobSpawn.spawnPoint() == null ? 6.0 : 1.0;
                double ox = (random.nextDouble() - 0.5) * scatter;
                double oz = (random.nextDouble() - 0.5) * scatter;
                Entity entity = entityType.create(spawnLevel);
                if (entity == null) continue;
                entity.addTag(DungeonZoneProtectionService.MANAGED_ENTITY_TAG);
                entity.moveTo(mobSpawnPos.x() + ox, mobSpawnPos.y(), mobSpawnPos.z() + oz, 0f, 0f);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(spawnLevel, spawnLevel.getCurrentDifficultyAt(entity.blockPosition()),
                        MobSpawnType.COMMAND, null);
                    applyMobConfig(mob, mobSpawn);
                }
                if (Boolean.TRUE.equals(wave.glowingAfterDelay())) {
                    entity.setGlowingTag(true);
                }
                boolean added = spawnLevel.addFreshEntity(entity);
                if (!added) {
                    ArcadiaDungeon.LOGGER.warn("[Arcadia][ROOM] addFreshEntity failed — mob not tracked type={}", mobSpawn.mobType());
                    continue;
                }
                mobs.add(entity.getUUID());
                mobToRun.put(entity.getUUID(), run.id());
            }
        }

        if (mobs.isEmpty()) {
            onWaveCleared(run, level);
        } else {
            livingMobs.put(run.id(), mobs);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=wave_trigger runId={} room={} wave={} mobs={}",
                run.id(), run.currentRoomIndex(), run.currentWaveIndex(), mobs.size());
        }

        long elapsed = System.currentTimeMillis() - t0;
        if (elapsed > 5) {
            ArcadiaDungeon.LOGGER.info("[Arcadia][PERF] event=tick_run_ms duration={} runId={}",
                elapsed, run.id());
        }
    }

    private void onWaveCleared(Run run, ServerLevel level) {
        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;
        List<DungeonConfig.Wave> waves = wavesFor(config, run.currentRoomIndex());
        Vec3 spawnPos = spawnPositions.getOrDefault(run.id(), Vec3.ZERO);

        if (run.currentWaveIndex() < waves.size()) {
            bossPhaseService.spawnBossesAfterWave(run, level, spawnPos, run.currentWaveIndex() + 1);
        }

        if (run.currentWaveIndex() + 1 < waves.size()) {
            // Il reste des waves dans cette salle
            run.nextWave();
            triggerCurrentWave(run, level);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=wave_cleared_next runId={} room={} nextWave={}",
                run.id(), run.currentRoomIndex(), run.currentWaveIndex());
        } else {
            // Toutes les salles clearées → boss ou victoire directe
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=all_rooms_cleared runId={}", run.id());
            if (config.configuredBosses().isEmpty()) {
                runLifecycleService.completeRun(run, RunResult.VICTORY);
            } else {
                bossPhaseService.spawnBoss(run, level, spawnPos);
            }
        }
    }

    private void cleanupWaveTracking(RunId runId) {
        Set<UUID> mobs = livingMobs.remove(runId);
        if (mobs == null) return;
        mobs.forEach(mobToRun::remove);
        discardEntities(mobs);
    }

    private static List<DungeonConfig.Wave> wavesFor(DungeonConfig config, int roomIndex) {
        return roomIndex == 0 ? config.configuredWaves() : List.of();
    }

    private static Vec3 configuredSpawnOrDefault(DungeonConfig.MobSpawn mobSpawn, Vec3 fallback) {
        DungeonConfig.SpawnPoint spawnPoint = mobSpawn.spawnPoint();
        if (spawnPoint == null) return fallback;
        return new Vec3(spawnPoint.x(), spawnPoint.y(), spawnPoint.z());
    }

    private static ServerLevel configuredLevelOrDefault(ServerLevel fallback, DungeonConfig.MobSpawn mobSpawn) {
        DungeonConfig.SpawnPoint spawnPoint = mobSpawn.spawnPoint();
        if (spawnPoint == null || spawnPoint.dimension() == null || spawnPoint.dimension().isBlank()) {
            return fallback;
        }
        ResourceLocation dim = ResourceLocation.tryParse(spawnPoint.dimension());
        if (dim == null) return fallback;
        ServerLevel configured = fallback.getServer().getLevel(
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
        return configured != null ? configured : fallback;
    }

    private static void applyMobConfig(Mob mob, DungeonConfig.MobSpawn mobSpawn) {
        if (mobSpawn.customName() != null && !mobSpawn.customName().isBlank()) {
            mob.setCustomName(Component.literal(mobSpawn.customName()));
            mob.setCustomNameVisible(true);
        }
        applyDoubleBase(mob, Attributes.MAX_HEALTH, mobSpawn.health(), true);
        applyDoubleBase(mob, Attributes.ATTACK_DAMAGE, mobSpawn.damage(), false);
        applyDoubleBase(mob, Attributes.MOVEMENT_SPEED, mobSpawn.speed(), false);
        applyEquipment(mob, mobSpawn.equipment());
        applyCustomAttributes(mob, mobSpawn.customAttributes());
    }

    private static void applyDoubleBase(LivingEntity entity,
                                        net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                        Double value,
                                        boolean healToMax) {
        if (value == null || value <= 0.0) return;
        var instance = entity.getAttribute(attribute);
        if (instance == null) return;
        instance.setBaseValue(value);
        if (healToMax) entity.setHealth(value.floatValue());
    }

    private static void applyEquipment(Mob mob, DungeonConfig.Equipment equipment) {
        if (equipment == null) return;
        equip(mob, EquipmentSlot.MAINHAND, equipment.mainHand());
        equip(mob, EquipmentSlot.OFFHAND, equipment.offHand());
        equip(mob, EquipmentSlot.HEAD, equipment.helmet());
        equip(mob, EquipmentSlot.CHEST, equipment.chestplate());
        equip(mob, EquipmentSlot.LEGS, equipment.leggings());
        equip(mob, EquipmentSlot.FEET, equipment.boots());
    }

    private static void equip(Mob mob, EquipmentSlot slot, String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(itemId.trim());
        if (rl == null) return;
        BuiltInRegistries.ITEM.getOptional(rl).ifPresent(item ->
            mob.setItemSlot(slot, new ItemStack(item)));
    }

    private static void applyCustomAttributes(LivingEntity entity, Map<String, Double> attributes) {
        if (attributes == null || attributes.isEmpty()) return;
        attributes.forEach((key, value) -> {
            if (key == null || value == null) return;
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return;
            BuiltInRegistries.ATTRIBUTE.getHolder(rl).ifPresent(attribute -> {
                var instance = entity.getAttribute(attribute);
                if (instance != null) instance.setBaseValue(value);
            });
        });
    }

    private static void broadcastWaveMessage(Run run, ServerLevel level, String message) {
        if (message == null || message.isBlank()) return;
        for (UUID playerId : run.playerIds()) {
            var player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
    }

    private void discardEntities(Set<UUID> mobIds) {
        if (mobIds.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : mobIds) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
    }
}
