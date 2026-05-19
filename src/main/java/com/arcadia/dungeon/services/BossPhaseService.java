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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
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
import java.util.function.Predicate;

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
    private final Map<UUID, Long> bossHudBroadcastAt = new ConcurrentHashMap<>();
    private final Map<RunId, Set<Integer>> defeatedBosses = new ConcurrentHashMap<>();
    private final Map<RunId, Set<Integer>> skippedBosses = new ConcurrentHashMap<>();

    public BossPhaseService(RunLifecycleService runLifecycleService,
                            RewardDistributionService rewardService,
                            DungeonRegistry dungeonRegistry) {
        this.runLifecycleService = runLifecycleService;
        this.rewardService = rewardService;
        this.dungeonRegistry = dungeonRegistry;
    }

    /**
     * Spawn les boss finaux configures pour la run.
     */
    public void spawnBoss(Run run, ServerLevel level, Vec3 spawnPos) {
        spawnConfiguredBosses(run, level, spawnPos,
            bossDef -> isFinalBoss(bossDef) || bossBlocksCompletion(bossDef),
            true, true);
    }

    public void spawnStartBosses(Run run, ServerLevel level, Vec3 spawnPos) {
        spawnConfiguredBosses(run, level, spawnPos, DungeonConfig.BossDefinition::spawnAtStartOrDefault, false, false);
    }

    public void spawnBossesAfterWave(Run run, ServerLevel level, Vec3 spawnPos, int clearedWaveNumber) {
        spawnConfiguredBosses(run, level, spawnPos,
            bossDef -> bossDef.spawnAfterWaveOrDefault() == clearedWaveNumber, false, false);
    }

    private void spawnConfiguredBosses(Run run,
                                       ServerLevel level,
                                       Vec3 spawnPos,
                                       Predicate<DungeonConfig.BossDefinition> selector,
                                       boolean completesRun,
                                       boolean completeIfNone) {
        if (run.phase() == RunPhase.ENDED) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;

        List<DungeonConfig.BossDefinition> bossDefs = config.configuredBosses();
        if (bossDefs.stream().noneMatch(selector)) {
            if (completeIfNone) {
                completeVictoryIfNoRequiredBosses(run, config);
            }
            return;
        }

        if (bossDefs.isEmpty() && completeIfNone) {
            completeVictory(run);
            return;
        }

        Set<UUID> spawned = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < bossDefs.size(); i++) {
            DungeonConfig.BossDefinition bossDef = bossDefs.get(i);
            if (!selector.test(bossDef)) {
                continue;
            }
            if (isBossResolved(run.id(), i) || isBossIndexActive(run.id(), i)) {
                continue;
            }
            if (bossDef.optionalOrDefault()
                && ThreadLocalRandom.current().nextDouble() > bossDef.spawnChanceOrDefault()) {
                markSkipped(run.id(), i);
                if (bossDef.skipMessage() != null && !bossDef.skipMessage().isBlank()) {
                    broadcastRunMessage(run, level.getServer(), bossDef.skipMessage());
                }
                ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=optional_skipped runId={} bossId={}",
                    run.id(), bossDef.idOrDefault(i));
                continue;
            }

            Vec3 bossSpawn = configuredSpawnOrDefault(bossDef.spawnPoint(), offsetSpawn(spawnPos, i));
            ServerLevel bossLevel = configuredLevelOrDefault(level, bossDef.spawnPoint());
            Entity entity = spawnOneBoss(run, bossLevel, bossSpawn, bossDef, i, completesRun);
            if (entity != null) {
                spawned.add(entity.getUUID());
            }
        }

        if (spawned.isEmpty()) {
            if (completeIfNone) {
                completeVictoryIfNoRequiredBosses(run, config);
            }
            return;
        }

        runBosses.compute(run.id(), (runId, existing) -> {
            Set<UUID> tracked = existing != null ? existing : ConcurrentHashMap.newKeySet();
            tracked.addAll(spawned);
            return tracked;
        });

        if (completesRun && !hasBlockingBossAlive(run.id()) && !hasBlockingBossPending(run, config)) {
            discardBosses(spawned, level.getServer());
            removeTrackedBosses(run.id(), spawned);
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
        defeatedBosses.remove(runId);
        skippedBosses.remove(runId);
        bossHudBroadcastAt.keySet().removeAll(entityIds);

        if (entityIds.isEmpty()) {
            return;
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        for (UUID entityId : entityIds) {
            bossToRun.remove(entityId);
            bossHudBroadcastAt.remove(entityId);
            ServerBossEvent bar = bossBars.remove(entityId);
            if (bar != null) bar.removeAllPlayers();
            if (server != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(entityId);
                    if (entity != null) {
                        entity.discard();
                        break;
                    }
                }
            }
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
        if (run == null) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null) return;
        List<DungeonConfig.BossDefinition> bosses = config.configuredBosses();
        if (runtime.bossIndex() < 0 || runtime.bossIndex() >= bosses.size()) return;
        DungeonConfig.BossDefinition bossDef = bosses.get(runtime.bossIndex());

        float hpAfter = event.getEntity().getHealth() - event.getNewDamage();
        float hpMax = event.getEntity().getMaxHealth();
        if (hpMax <= 0) return;
        int hpPercent = Math.max(0, (int) ((hpAfter / hpMax) * 100));

        BossState bossState = run.bossState();
        int roundedMax = Math.max(1, Math.round(hpMax));
        if (bossState == null || !bossDef.type().equals(bossState.type()) || bossState.hpMax() != roundedMax) {
            bossState = new BossState(bossDef.type(), roundedMax);
            run.setBossState(bossState);
        }
        bossState.setHpCurrent(Math.max(0, Math.round(hpAfter)));

        ServerBossEvent bar = bossBars.get(entityId);
        if (bar != null) bar.setProgress(Math.max(0f, Math.min(1f, hpAfter / hpMax)));

        List<DungeonConfig.Phase> phases = bossDef.phasesOrEmpty();
        boolean broadcasted = false;
        int currentIdx = bossState.currentPhaseIndex();
        if (currentIdx < phases.size()) {
            DungeonConfig.Phase nextPhase = phases.get(currentIdx);
            if (hpPercent <= nextPhase.triggerHpPercent()) {
                if (event.getEntity().level() instanceof ServerLevel serverLevel) {
                    applyPhase(event.getEntity(), nextPhase, run, serverLevel);
                } else {
                    applyPhase(event.getEntity(), nextPhase, run, null);
                }
                bossState.setCurrentPhaseIndex(currentIdx + 1);
                ServerPayloadHandler.broadcastRunState(run);
                bossHudBroadcastAt.put(entityId, System.currentTimeMillis());
                broadcasted = true;
                ArcadiaDungeon.LOGGER.info(
                    "[Arcadia][BOSS] event=phase_transition bossIndex={} phase={} hp={}",
                    runtime.bossIndex(), currentIdx + 1, hpPercent);
            }
        }
        if (!broadcasted) {
            broadcastBossHud(run, entityId);
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
        bossHudBroadcastAt.remove(entityId);
        if (bar != null) bar.removeAllPlayers();
        markDefeated(runtime.runId(), runtime.bossIndex());

        Run run = runLifecycleService.findById(runtime.runId()).orElse(null);
        if (run == null) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config != null && runtime.bossIndex() >= 0 && runtime.bossIndex() < config.configuredBosses().size()) {
            rewardService.distributeBossRewards(run, config.configuredBosses().get(runtime.bossIndex()));
        }

        Set<UUID> alive = runBosses.get(runtime.runId());
        if (alive != null) {
            alive.remove(entityId);
            if (alive.isEmpty()) {
                runBosses.remove(runtime.runId());
            }
            refreshBossState(run, config, event.getEntity().level().getServer());
            if (!runtime.completesRun()) {
                if (config != null && shouldCompleteAfterBossDeath(run, config)) {
                    completeVictory(run);
                    ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=required_side_bosses_cleared runId={}", run.id());
                    return;
                }
                ServerPayloadHandler.broadcastRunState(run);
                ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=side_boss_killed runId={} remaining={}",
                    run.id(), alive.size());
                return;
            }
            if (hasBlockingBossAlive(runtime.runId()) || (config != null && hasBlockingBossPending(run, config))) {
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

    private boolean hasBlockingBossAlive(RunId runId) {
        Set<UUID> alive = runBosses.get(runId);
        if (alive == null || alive.isEmpty()) return false;
        for (UUID bossId : alive) {
            BossRuntime runtime = bossToRun.get(bossId);
            if (runtime != null && runtime.blocksCompletion()) return true;
        }
        return false;
    }

    private boolean hasBlockingBossPending(Run run, DungeonConfig config) {
        List<DungeonConfig.BossDefinition> bossDefs = config.configuredBosses();
        for (int i = 0; i < bossDefs.size(); i++) {
            DungeonConfig.BossDefinition bossDef = bossDefs.get(i);
            if (!bossBlocksCompletion(bossDef)) continue;
            if (!isBossResolved(run.id(), i) && !isBossIndexActive(run.id(), i)) return true;
        }
        return false;
    }

    private boolean shouldCompleteAfterBossDeath(Run run, DungeonConfig config) {
        if (run.phase() != RunPhase.IN_PROGRESS) return false;
        int waveCount = config.configuredWaves().size();
        boolean wavesDone = waveCount == 0 || run.currentWaveIndex() >= waveCount - 1;
        return wavesDone && !hasBlockingBossAlive(run.id()) && !hasBlockingBossPending(run, config);
    }

    private void completeVictoryIfNoRequiredBosses(Run run, DungeonConfig config) {
        if (!hasBlockingBossAlive(run.id()) && !hasBlockingBossPending(run, config)) {
            completeVictory(run);
        } else {
            ServerPayloadHandler.broadcastRunState(run);
        }
    }

    private void broadcastBossHud(Run run, UUID entityId) {
        long now = System.currentTimeMillis();
        long last = bossHudBroadcastAt.getOrDefault(entityId, 0L);
        if (now - last < 200L) return;
        bossHudBroadcastAt.put(entityId, now);
        ServerPayloadHandler.broadcastRunState(run);
    }

    private boolean isBossIndexActive(RunId runId, int bossIndex) {
        Set<UUID> alive = runBosses.get(runId);
        if (alive == null || alive.isEmpty()) return false;
        for (UUID bossId : alive) {
            BossRuntime runtime = bossToRun.get(bossId);
            if (runtime != null && runtime.bossIndex() == bossIndex) return true;
        }
        return false;
    }

    private boolean isBossResolved(RunId runId, int bossIndex) {
        Set<Integer> defeated = defeatedBosses.get(runId);
        if (defeated != null && defeated.contains(bossIndex)) return true;
        Set<Integer> skipped = skippedBosses.get(runId);
        return skipped != null && skipped.contains(bossIndex);
    }

    private void markDefeated(RunId runId, int bossIndex) {
        defeatedBosses.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet()).add(bossIndex);
    }

    private void markSkipped(RunId runId, int bossIndex) {
        skippedBosses.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet()).add(bossIndex);
    }

    private void refreshBossState(Run run, DungeonConfig config, MinecraftServer server) {
        if (config == null || server == null) {
            run.setBossState(null);
            return;
        }
        Set<UUID> alive = runBosses.get(run.id());
        if (alive == null || alive.isEmpty()) {
            run.setBossState(null);
            return;
        }
        for (UUID bossId : alive) {
            BossRuntime runtime = bossToRun.get(bossId);
            if (runtime == null || runtime.bossIndex() < 0 || runtime.bossIndex() >= config.configuredBosses().size()) {
                continue;
            }
            Entity entity = findEntity(server, bossId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                DungeonConfig.BossDefinition bossDef = config.configuredBosses().get(runtime.bossIndex());
                BossState state = new BossState(bossDef.type(), Math.max(1, Math.round(living.getMaxHealth())));
                state.setHpCurrent(Math.round(living.getHealth()));
                BossState previous = run.bossState();
                if (previous != null && previous.type().equals(bossDef.type())) {
                    state.setCurrentPhaseIndex(previous.currentPhaseIndex());
                }
                run.setBossState(state);
                return;
            }
        }
        run.setBossState(null);
    }

    private static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private void discardRemainingBosses(RunId runId, MinecraftServer server) {
        Set<UUID> remaining = runBosses.get(runId);
        if (remaining == null || remaining.isEmpty() || server == null) return;

        for (UUID bossId : remaining) {
            bossToRun.remove(bossId);
            bossHudBroadcastAt.remove(bossId);
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

    private void discardBosses(Set<UUID> bossIds, MinecraftServer server) {
        if (bossIds == null || bossIds.isEmpty() || server == null) return;

        for (UUID bossId : bossIds) {
            bossToRun.remove(bossId);
            bossHudBroadcastAt.remove(bossId);
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

    private void removeTrackedBosses(RunId runId, Set<UUID> bossIds) {
        Set<UUID> tracked = runBosses.get(runId);
        if (tracked == null) return;
        tracked.removeAll(bossIds);
        if (tracked.isEmpty()) {
            runBosses.remove(runId);
        }
    }

    private Entity spawnOneBoss(Run run, ServerLevel level, Vec3 spawnPos,
                                DungeonConfig.BossDefinition bossDef, int bossIndex, boolean completesRun) {
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

        entity.addTag(DungeonZoneProtectionService.MANAGED_ENTITY_TAG);
        entity.moveTo(spawnPos.x(), spawnPos.y(), spawnPos.z(), 0f, 0f);

        if (entity instanceof Mob mob) {
            var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(bossDef.hp());
                mob.setHealth((float) bossDef.hp());
            }
            if (bossDef.customName() != null && !bossDef.customName().isBlank()) {
                mob.setCustomName(Component.literal(bossDef.customName()));
                mob.setCustomNameVisible(true);
            }
            applyDoubleBase(mob, Attributes.ATTACK_DAMAGE, bossDef.baseDamage(), false);
            applyEquipment(mob, bossDef.equipment());
            applyCustomAttributes(mob, bossDef.customAttributes());
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                MobSpawnType.COMMAND, null);
        }

        level.addFreshEntity(entity);
        bossToRun.put(entity.getUUID(), new BossRuntime(run.id(), bossIndex,
            bossBlocksCompletion(bossDef), completesRun));
        run.setBossState(new BossState(bossDef.type(), bossDef.hp()));

        if (bossDef.spawnMessage() != null && !bossDef.spawnMessage().isBlank()) {
            broadcastRunMessage(run, level.getServer(), bossDef.spawnMessage());
        }

        if (bossDef.showBossBarOrDefault()) {
            ServerBossEvent bossBar = new ServerBossEvent(
                entity.getDisplayName(), bossBarColor(bossDef.bossBarColor()), BossEvent.BossBarOverlay.PROGRESS);
            bossBar.setProgress(1.0f);
            for (UUID playerId : run.playerIds()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(playerId);
                if (p != null) bossBar.addPlayer(p);
            }
            bossBars.put(entity.getUUID(), bossBar);
        }

        ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=spawn runId={} bossId={} type={} hp={} requiredKill={} completesRun={}",
            run.id(), bossDef.idOrDefault(bossIndex), bossDef.type(), bossDef.hp(), bossDef.requiredKillOrDefault(), completesRun);
        return entity;
    }

    private void completeVictory(Run run) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            discardRemainingBosses(run.id(), server);
        }
        Set<UUID> tracked = runBosses.remove(run.id());
        if (tracked != null) {
            bossHudBroadcastAt.keySet().removeAll(tracked);
        }
        runLifecycleService.completeRun(run, RunResult.VICTORY);
        defeatedBosses.remove(run.id());
        skippedBosses.remove(run.id());
        rewardService.distribute(run, RunResult.VICTORY);
        ServerPayloadHandler.broadcastRunState(run);
    }

    private static Vec3 configuredSpawnOrDefault(DungeonConfig.SpawnPoint spawnPoint, Vec3 fallback) {
        if (spawnPoint == null) return fallback;
        return new Vec3(spawnPoint.x(), spawnPoint.y(), spawnPoint.z());
    }

    private static ServerLevel configuredLevelOrDefault(ServerLevel fallback, DungeonConfig.SpawnPoint spawnPoint) {
        if (spawnPoint == null || spawnPoint.dimension() == null || spawnPoint.dimension().isBlank()) {
            return fallback;
        }
        ResourceLocation dim = ResourceLocation.tryParse(spawnPoint.dimension());
        if (dim == null) return fallback;
        ServerLevel configured = fallback.getServer().getLevel(
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
        return configured != null ? configured : fallback;
    }

    private static Vec3 offsetSpawn(Vec3 base, int index) {
        int xOffset = (index % 3) - 1;
        int zOffset = index / 3;
        return base.add(xOffset * 2.0, 0.0, zOffset * 2.0);
    }

    private boolean isFinalBoss(DungeonConfig.BossDefinition bossDef) {
        return !bossDef.spawnAtStartOrDefault() && bossDef.spawnAfterWaveOrDefault() == 0;
    }

    private boolean bossBlocksCompletion(DungeonConfig.BossDefinition bossDef) {
        return bossDef.requiredKillOrDefault() || !bossDef.optionalOrDefault();
    }

    private void applyPhase(LivingEntity entity, DungeonConfig.Phase phase, Run run, ServerLevel level) {
        applyMultiplier(entity, Attributes.MOVEMENT_SPEED, PHASE_MOD_SPD, phase.speedMultiplier());
        applyMultiplier(entity, Attributes.ATTACK_DAMAGE,  PHASE_MOD_DMG, phase.damageMultiplier());
        if (level != null) {
            if (phase.phaseStartMessage() != null && !phase.phaseStartMessage().isBlank()) {
                broadcastRunMessage(run, level.getServer(), phase.phaseStartMessage());
            }
            applyPhaseEffects(run, level, phase);
            runPhaseCommands(run, level, phase);
            spawnPhaseSummons(run, level, entity.position(), phase);
        }
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

    private void spawnPhaseSummons(Run run, ServerLevel fallbackLevel, Vec3 fallbackSpawn, DungeonConfig.Phase phase) {
        for (DungeonConfig.MobSpawn summon : phase.summonsOrEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(summon.mobType());
            if (rl == null) continue;
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (entityType == null) continue;
            ServerLevel level = configuredLevelOrDefault(fallbackLevel, summon.spawnPoint());
            Vec3 spawnPos = configuredSpawnOrDefault(summon.spawnPoint(), fallbackSpawn);
            for (int i = 0; i < Math.max(1, summon.count()); i++) {
                Entity entity = entityType.create(level);
                if (entity == null) continue;
                entity.addTag(DungeonZoneProtectionService.MANAGED_ENTITY_TAG);
                entity.moveTo(spawnPos.x(), spawnPos.y(), spawnPos.z(), 0f, 0f);
                if (entity instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                        MobSpawnType.COMMAND, null);
                    applyMobConfig(mob, summon);
                }
                level.addFreshEntity(entity);
            }
        }
    }

    private static void applyPhaseEffects(Run run, ServerLevel level, DungeonConfig.Phase phase) {
        for (DungeonConfig.PhaseEffect effect : phase.effectsOrEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(effect.effect());
            if (rl == null) continue;
            BuiltInRegistries.MOB_EFFECT.getHolder(rl).ifPresent(holder -> {
                for (UUID playerId : run.playerIds()) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.addEffect(new MobEffectInstance(holder, effect.durationSeconds() * 20, effect.amplifier()));
                    }
                }
            });
        }
    }

    private static void runPhaseCommands(Run run, ServerLevel level, DungeonConfig.Phase phase) {
        for (String rawCommand : phase.commandsOrEmpty()) {
            if (rawCommand == null || rawCommand.isBlank()) continue;
            for (UUID playerId : run.playerIds()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player == null) continue;
                String command = rawCommand.replace("%player%", player.getGameProfile().getName());
                if (command.startsWith("/")) command = command.substring(1);
                level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack(), command);
            }
        }
    }

    private static void broadcastRunMessage(Run run, MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) return;
        for (UUID playerId : run.playerIds()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
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
                                        Holder<Attribute> attribute,
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

    private static BossEvent.BossBarColor bossBarColor(String raw) {
        if (raw == null || raw.isBlank()) return BossEvent.BossBarColor.RED;
        try { return BossEvent.BossBarColor.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return BossEvent.BossBarColor.RED; }
    }

    private record BossRuntime(RunId runId, int bossIndex, boolean blocksCompletion, boolean completesRun) {}
}
