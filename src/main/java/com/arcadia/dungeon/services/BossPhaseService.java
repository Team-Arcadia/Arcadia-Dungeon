package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.BossState;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunPhase;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.ServerPayloadHandler;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service boss: spawn, transitions de phase, mort -> victoire.
 */
public final class BossPhaseService {

    private static final ResourceLocation PHASE_MOD_DMG =
        ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "boss_phase_dmg");
    private static final ResourceLocation PHASE_MOD_SPD =
        ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "boss_phase_spd");

    private final RunLifecycleService runLifecycleService;
    private final RewardDistributionService rewardService;
    private final DungeonRegistry dungeonRegistry;

    private final Map<UUID, BossRuntime> bossToRun = new ConcurrentHashMap<>();
    private final Map<RunId, Set<UUID>> runBosses = new ConcurrentHashMap<>();
    private final Map<UUID, ServerBossEvent> bossBars = new ConcurrentHashMap<>();

    public BossPhaseService(RunLifecycleService runLifecycleService,
                            RewardDistributionService rewardService,
                            DungeonRegistry dungeonRegistry) {
        this.runLifecycleService = runLifecycleService;
        this.rewardService = rewardService;
        this.dungeonRegistry = dungeonRegistry;
    }

    /**
     * Spawn tous les boss configures pour la run.
     */
    public void spawnBoss(Run run, ServerLevel level, Vec3 spawnPos) {
        if (run.phase() == RunPhase.ENDED) return;
        if (runBosses.containsKey(run.id())) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;

        List<DungeonConfig.BossDefinition> bossDefs = config.configuredBosses();
        if (bossDefs.isEmpty()) {
            completeVictory(run);
            return;
        }

        Set<UUID> spawned = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < bossDefs.size(); i++) {
            DungeonConfig.BossDefinition bossDef = bossDefs.get(i);
            if (bossDef.optionalOrDefault()
                && ThreadLocalRandom.current().nextDouble() > bossDef.spawnChanceOrDefault()) {
                ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=optional_skipped runId={} bossId={}",
                    run.id(), bossDef.idOrDefault(i));
                continue;
            }

            Entity entity = spawnOneBoss(run, level, offsetSpawn(spawnPos, i), bossDef, i);
            if (entity != null) {
                spawned.add(entity.getUUID());
            }
        }

        if (spawned.isEmpty()) {
            completeVictory(run);
            return;
        }

        runBosses.put(run.id(), spawned);
        if (!hasRequiredBossAlive(spawned)) {
            discardRemainingBosses(run.id(), level.getServer());
            runBosses.remove(run.id());
            completeVictory(run);
            return;
        }

        ServerPayloadHandler.broadcastRunState(run);
    }

    public void cleanupBoss(RunId runId) {
        Set<UUID> entityIds = runBosses.remove(runId);
        if (entityIds == null) {
            entityIds = ConcurrentHashMap.newKeySet();
            for (Map.Entry<UUID, BossRuntime> entry : bossToRun.entrySet()) {
                if (entry.getValue().runId().equals(runId)) {
                    entityIds.add(entry.getKey());
                }
            }
        }

        if (entityIds.isEmpty()) {
            return;
        }

        for (UUID entityId : entityIds) {
            bossToRun.remove(entityId);
            ServerBossEvent bar = bossBars.remove(entityId);
            if (bar != null) bar.removeAllPlayers();
        }
    }

    /**
     * Tue un boss de la run. Pour une run multi-boss, retient le premier boss vivant trouve.
     */
    public boolean forceKillBoss(RunId runId, MinecraftServer server) {
        Set<UUID> bossUuids = runBosses.get(runId);
        if (bossUuids == null || bossUuids.isEmpty()) return false;

        for (UUID bossUuid : bossUuids) {
            for (ServerLevel level : server.getAllLevels()) {
                var entity = level.getEntity(bossUuid);
                if (entity instanceof LivingEntity living) {
                    living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onBossDamage(LivingDamageEvent.Pre event) {
        UUID entityId = event.getEntity().getUUID();
        BossRuntime runtime = bossToRun.get(entityId);
        if (runtime == null) return;

        Run run = runLifecycleService.findById(runtime.runId()).orElse(null);
        if (run == null || run.bossState() == null) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;
        List<DungeonConfig.BossDefinition> bosses = config.configuredBosses();
        if (runtime.bossIndex() < 0 || runtime.bossIndex() >= bosses.size()) return;

        List<DungeonConfig.Phase> phases = bosses.get(runtime.bossIndex()).phasesOrEmpty();
        if (phases.isEmpty()) return;

        float hpAfter = event.getEntity().getHealth() - event.getNewDamage();
        float hpMax = event.getEntity().getMaxHealth();
        if (hpMax <= 0) return;
        int hpPercent = Math.max(0, (int) ((hpAfter / hpMax) * 100));

        BossState bossState = run.bossState();
        bossState.setHpCurrent(Math.round(hpAfter));

        ServerBossEvent bar = bossBars.get(entityId);
        if (bar != null) bar.setProgress(Math.max(0f, Math.min(1f, hpAfter / hpMax)));

        int currentIdx = bossState.currentPhaseIndex();
        if (currentIdx < phases.size()) {
            DungeonConfig.Phase nextPhase = phases.get(currentIdx);
            if (hpPercent <= nextPhase.triggerHpPercent()) {
                applyPhase(event.getEntity(), nextPhase);
                bossState.setCurrentPhaseIndex(currentIdx + 1);
                ServerPayloadHandler.broadcastRunState(run);
                ArcadiaDungeon.LOGGER.info(
                    "[Arcadia][BOSS] event=phase_transition bossIndex={} phase={} hp={}",
                    runtime.bossIndex(), currentIdx + 1, hpPercent);
            }
        }
    }

    @SubscribeEvent
    public void onBossDrops(LivingDropsEvent event) {
        if (bossToRun.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBossDeath(LivingDeathEvent event) {
        UUID entityId = event.getEntity().getUUID();
        BossRuntime runtime = bossToRun.remove(entityId);
        if (runtime == null) return;

        ServerBossEvent bar = bossBars.remove(entityId);
        if (bar != null) bar.removeAllPlayers();

        Run run = runLifecycleService.findById(runtime.runId()).orElse(null);
        if (run == null) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config != null && runtime.bossIndex() >= 0 && runtime.bossIndex() < config.configuredBosses().size()) {
            rewardService.distributeBossRewards(run, config.configuredBosses().get(runtime.bossIndex()));
        }

        Set<UUID> alive = runBosses.get(runtime.runId());
        if (alive != null) {
            alive.remove(entityId);
            if (!alive.isEmpty() && hasRequiredBossAlive(alive)) {
                ServerPayloadHandler.broadcastRunState(run);
                ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=boss_killed runId={} remaining={}",
                    run.id(), alive.size());
                return;
            }
            discardRemainingBosses(runtime.runId(), event.getEntity().level().getServer());
            runBosses.remove(runtime.runId());
        }

        completeVictory(run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=all_bosses_killed runId={}", run.id());
    }

    private boolean hasRequiredBossAlive(Set<UUID> alive) {
        for (UUID bossId : alive) {
            BossRuntime runtime = bossToRun.get(bossId);
            if (runtime != null && runtime.requiredKill()) return true;
        }
        return false;
    }

    private void discardRemainingBosses(RunId runId, MinecraftServer server) {
        Set<UUID> remaining = runBosses.get(runId);
        if (remaining == null || remaining.isEmpty() || server == null) return;

        for (UUID bossId : remaining) {
            bossToRun.remove(bossId);
            ServerBossEvent bar = bossBars.remove(bossId);
            if (bar != null) bar.removeAllPlayers();

            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(bossId);
                if (entity != null) {
                    entity.discard();
                    break;
                }
            }
        }
    }

    private Entity spawnOneBoss(Run run, ServerLevel level, Vec3 spawnPos,
                                DungeonConfig.BossDefinition bossDef, int bossIndex) {
        ResourceLocation rl = ResourceLocation.tryParse(bossDef.type());
        if (rl == null) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][BOSS] type invalide: {}", bossDef.type());
            return null;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (entityType == null) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][BOSS] entite inconnue: {}", bossDef.type());
            return null;
        }

        Entity entity = entityType.create(level);
        if (entity == null) return null;

        entity.moveTo(spawnPos.x(), spawnPos.y(), spawnPos.z(), 0f, 0f);

        if (entity instanceof Mob mob) {
            var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(bossDef.hp());
                mob.setHealth((float) bossDef.hp());
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                MobSpawnType.COMMAND, null);
        }

        level.addFreshEntity(entity);
        bossToRun.put(entity.getUUID(), new BossRuntime(run.id(), bossIndex, bossDef.requiredKillOrDefault()));
        run.setBossState(new BossState(bossDef.type(), bossDef.hp()));

        ServerBossEvent bossBar = new ServerBossEvent(
            entityType.getDescription(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(1.0f);
        for (UUID playerId : run.playerIds()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(playerId);
            if (p != null) bossBar.addPlayer(p);
        }
        bossBars.put(entity.getUUID(), bossBar);

        ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=spawn runId={} bossId={} type={} hp={} requiredKill={}",
            run.id(), bossDef.idOrDefault(bossIndex), bossDef.type(), bossDef.hp(), bossDef.requiredKillOrDefault());
        return entity;
    }

    private void completeVictory(Run run) {
        runLifecycleService.completeRun(run, RunResult.VICTORY);
        rewardService.distribute(run, RunResult.VICTORY);
        ServerPayloadHandler.broadcastRunState(run);
    }

    private static Vec3 offsetSpawn(Vec3 base, int index) {
        int xOffset = (index % 3) - 1;
        int zOffset = index / 3;
        return base.add(xOffset * 2.0, 0.0, zOffset * 2.0);
    }

    private void applyPhase(LivingEntity entity, DungeonConfig.Phase phase) {
        applyMultiplier(entity, Attributes.MOVEMENT_SPEED, PHASE_MOD_SPD, phase.speedMultiplier());
        applyMultiplier(entity, Attributes.ATTACK_DAMAGE,  PHASE_MOD_DMG, phase.damageMultiplier());
    }

    private static void applyMultiplier(LivingEntity entity,
                                        Holder<Attribute> attribute,
                                        ResourceLocation modId,
                                        double multiplier) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(modId);
        if (multiplier != 1.0) {
            instance.addTransientModifier(new AttributeModifier(
                modId, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private record BossRuntime(RunId runId, int bossIndex, boolean requiredKill) {}
}
