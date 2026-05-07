package com.arcadia.dungeon.services;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.domain.run.BossState;
import com.arcadia.dungeon.domain.run.Run;
import com.arcadia.dungeon.domain.run.RunId;
import com.arcadia.dungeon.domain.run.RunResult;
import com.arcadia.dungeon.network.ServerPayloadHandler;
import com.arcadia.dungeon.persistence.DungeonRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service boss : spawn (S4.1), transitions de phase (S4.2), mort → victoire (S4.4).
 *
 * <p>Doit être enregistré sur {@code NeoForge.EVENT_BUS}.
 * Toutes les mutations de Run se font sur le SGT — LivingDamageEvent et
 * LivingDeathEvent s'exécutent sur le SGT par le jeu.
 */
public final class BossPhaseService {

    private static final ResourceLocation PHASE_MOD_DMG =
        ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "boss_phase_dmg");
    private static final ResourceLocation PHASE_MOD_SPD =
        ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "boss_phase_spd");

    private final RunLifecycleService runLifecycleService;
    private final RewardDistributionService rewardService;
    private final DungeonRegistry dungeonRegistry;

    /** Boss entity UUID → RunId pour lookup rapide. */
    private final Map<UUID, RunId> bossToRun = new ConcurrentHashMap<>();

    public BossPhaseService(RunLifecycleService runLifecycleService,
                            RewardDistributionService rewardService,
                            DungeonRegistry dungeonRegistry) {
        this.runLifecycleService = runLifecycleService;
        this.rewardService = rewardService;
        this.dungeonRegistry = dungeonRegistry;
    }

    // ── S4.1 — Spawn ──────────────────────────────────────────────────────

    /**
     * Spawn le boss à la position donnée et initialise {@link BossState} sur la run.
     * Doit être appelé sur le SGT.
     */
    public void spawnBoss(Run run, ServerLevel level, Vec3 spawnPos) {
        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null || config.boss() == null) return;

        DungeonConfig.BossDefinition bossDef = config.boss();
        ResourceLocation rl = ResourceLocation.tryParse(bossDef.type());
        if (rl == null) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][BOSS] type invalide: {}", bossDef.type());
            return;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        if (entityType == null) {
            ArcadiaDungeon.LOGGER.error("[Arcadia][BOSS] entité inconnue: {}", bossDef.type());
            return;
        }

        Entity entity = entityType.create(level);
        if (entity == null) return;

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
        bossToRun.put(entity.getUUID(), run.id());
        run.setBossState(new BossState(bossDef.type(), bossDef.hp()));

        ServerPayloadHandler.broadcastRunState(run);
        ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=spawn runId={} type={} hp={}",
            run.id(), bossDef.type(), bossDef.hp());
    }

    public void cleanupBoss(RunId runId) {
        bossToRun.values().removeIf(id -> id.equals(runId));
    }

    /**
     * Tue l'entité boss en jeu via {@code hurt(genericKill, MAX_VALUE)}, ce qui
     * déclenche {@link LivingDeathEvent} → {@link #onBossDeath} → completeRun VICTORY.
     * Retourne {@code false} si aucun boss n'est enregistré pour cette run ou
     * si l'entité n'est plus présente dans aucun level.
     *
     * <p>Appelé par {@code /arcadia debug killboss <runId>} (S7.1 AC3).
     */
    public boolean forceKillBoss(RunId runId, MinecraftServer server) {
        UUID bossUuid = bossToRun.entrySet().stream()
            .filter(e -> e.getValue().equals(runId))
            .map(Map.Entry::getKey)
            .findFirst().orElse(null);
        if (bossUuid == null) return false;

        for (ServerLevel level : server.getAllLevels()) {
            var entity = level.getEntity(bossUuid);
            if (entity instanceof LivingEntity living) {
                living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                return true;
            }
        }
        return false;
    }

    // ── S4.2 — Transitions de phase ───────────────────────────────────────

    @SubscribeEvent
    public void onBossDamage(LivingDamageEvent.Pre event) {
        UUID entityId = event.getEntity().getUUID();
        RunId runId = bossToRun.get(entityId);
        if (runId == null) return;

        Run run = runLifecycleService.findById(runId).orElse(null);
        if (run == null || run.bossState() == null) return;

        DungeonConfig config = dungeonRegistry.get(run.dungeonId()).orElse(null);
        if (config == null || config.boss() == null || config.boss().phases().isEmpty()) return;

        float hpAfter = event.getEntity().getHealth() - event.getNewDamage();
        float hpMax = event.getEntity().getMaxHealth();
        if (hpMax <= 0) return;
        int hpPercent = Math.max(0, (int) ((hpAfter / hpMax) * 100));

        BossState bossState = run.bossState();
        bossState.setHpCurrent(Math.round(hpAfter));

        List<DungeonConfig.Phase> phases = config.boss().phases();
        int currentIdx = bossState.currentPhaseIndex();
        if (currentIdx < phases.size()) {
            DungeonConfig.Phase nextPhase = phases.get(currentIdx);
            if (hpPercent <= nextPhase.triggerHpPercent()) {
                applyPhase(event.getEntity(), nextPhase);
                bossState.setCurrentPhaseIndex(currentIdx + 1);
                ServerPayloadHandler.broadcastRunState(run);
                ArcadiaDungeon.LOGGER.info(
                    "[Arcadia][BOSS] event=phase_transition phase={} hp={}",
                    currentIdx + 1, hpPercent);
            }
        }
    }

    // ── S4.4 — Boss mort → VICTORY ────────────────────────────────────────

    @SubscribeEvent
    public void onBossDeath(LivingDeathEvent event) {
        UUID entityId = event.getEntity().getUUID();
        RunId runId = bossToRun.remove(entityId);
        if (runId == null) return;

        Run run = runLifecycleService.findById(runId).orElse(null);
        if (run == null) return;

        runLifecycleService.completeRun(run, RunResult.VICTORY);
        rewardService.distribute(run, RunResult.VICTORY);
        ServerPayloadHandler.broadcastRunState(run);

        ArcadiaDungeon.LOGGER.info("[Arcadia][BOSS] event=boss_killed runId={}", run.id());
    }

    // ── Internals ──────────────────────────────────────────────────────────

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
}
