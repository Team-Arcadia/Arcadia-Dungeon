package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
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

        if (run.currentRoomIndex() >= config.rooms().size()) {
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

        if (run.currentRoomIndex() >= config.rooms().size()) return;
        DungeonConfig.RoomRef currentRoom = config.rooms().get(run.currentRoomIndex());

        if (run.currentWaveIndex() >= currentRoom.waves().size()) {
            // Salle sans waves (transition pure) → considérée clearée immédiatement
            onWaveCleared(run, level);
            return;
        }

        DungeonConfig.Wave wave = currentRoom.waves().get(run.currentWaveIndex());
        Vec3 spawnPos = spawnPositions.getOrDefault(run.id(), Vec3.ZERO);
        Set<UUID> mobs = ConcurrentHashMap.newKeySet();

        for (DungeonConfig.MobSpawn mobSpawn : wave.mobs()) {
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
            for (int i = 0; i < mobSpawn.count(); i++) {
                double ox = (random.nextDouble() - 0.5) * 6;
                double oz = (random.nextDouble() - 0.5) * 6;
                Entity entity = entityType.create(level);
                if (entity == null) continue;
                entity.moveTo(spawnPos.x() + ox, spawnPos.y(), spawnPos.z() + oz, 0f, 0f);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                        MobSpawnType.COMMAND, null);
                }
                boolean added = level.addFreshEntity(entity);
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
        DungeonConfig.RoomRef currentRoom = config.rooms().get(run.currentRoomIndex());

        if (run.currentWaveIndex() + 1 < currentRoom.waves().size()) {
            // Il reste des waves dans cette salle
            run.nextWave();
            triggerCurrentWave(run, level);
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=wave_cleared_next runId={} room={} nextWave={}",
                run.id(), run.currentRoomIndex(), run.currentWaveIndex());
        } else if (run.currentRoomIndex() + 1 < config.rooms().size()) {
            // Toutes les waves de la salle sont clearées → avance à la suivante
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=cleared runId={} room={}",
                run.id(), run.currentRoomIndex());
            cleanupWaveTracking(run.id());
            run.advanceToNextRoom();
            triggerCurrentWave(run, level);
        } else {
            // Toutes les salles clearées → boss ou victoire directe
            ArcadiaDungeon.LOGGER.info("[Arcadia][ROOM] event=all_rooms_cleared runId={}", run.id());
            if (config.boss() == null) {
                runLifecycleService.completeRun(run, RunResult.VICTORY);
            } else {
                Vec3 spawnPos = spawnPositions.getOrDefault(run.id(), Vec3.ZERO);
                bossPhaseService.spawnBoss(run, level, spawnPos);
            }
        }
    }

    private void cleanupWaveTracking(RunId runId) {
        Set<UUID> mobs = livingMobs.remove(runId);
        if (mobs != null) mobs.forEach(mobToRun::remove);
    }
}
