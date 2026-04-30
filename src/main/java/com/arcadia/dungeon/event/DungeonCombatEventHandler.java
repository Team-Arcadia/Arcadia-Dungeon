package com.arcadia.dungeon.event;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.boss.BossInstance;
import com.arcadia.dungeon.boss.BossManager;
import com.arcadia.dungeon.dungeon.CombatTuning;
import com.arcadia.dungeon.dungeon.DungeonInstance;
import com.arcadia.dungeon.dungeon.DungeonManager;
import com.arcadia.dungeon.dungeon.WaveInstance;
import com.arcadia.dungeon.util.MessageUtil;
import com.arcadia.dungeon.util.SparkUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all combat-related events: entity death routing, damage interception (anti-PVP,
 * dodge, cooldowns, phase transitions), loot/XP suppression, and extended melee ticking.
 */
public class DungeonCombatEventHandler {

    // === DEATH ===

    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity.getTags().contains("arcadia_boss")) { handleBossDeath(entity); return; }
            if (entity.getTags().contains("arcadia_wave_mob")) return;
            if (entity instanceof ServerPlayer player) handlePlayerDeath(player, event);
        } catch (RuntimeException e) {
            DungeonEventUtil.logHandlerError("onEntityDeath", "entity=" + event.getEntity().getType(), e);
        }
    }

    // === DAMAGE ===

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityDamage(LivingIncomingDamageEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            long now = System.currentTimeMillis();

            // Anti-PVP
            if (entity instanceof ServerPlayer targetPlayer) {
                DungeonInstance targetDungeon = DungeonManager.getInstance().getPlayerDungeon(targetPlayer.getUUID());
                if (targetDungeon != null && !targetDungeon.getConfig().settings.pvp) {
                    if (event.getSource().getEntity() instanceof Player attacker) {
                        if (targetDungeon.getPlayers().contains(attacker.getUUID())) {
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }

            boolean sparkSectionStarted = SparkUtil.startSection("combat");
            try {
                if (entity.getTags().contains("arcadia_managed") && CombatTuning.shouldDodge(entity, event.getSource().getDirectEntity(), now)) {
                    sendDodgeMessage(entity, event.getSource().getEntity());
                    event.setCanceled(true);
                    return;
                }

                if (event.getSource().getEntity() instanceof LivingEntity attacker
                        && attacker.getTags().contains("arcadia_managed")
                        && CombatTuning.shouldCancelDirectMeleeForCooldown(attacker, event.getSource().getDirectEntity(), now)) {
                    event.setCanceled(true);
                    return;
                }
            } finally {
                if (sparkSectionStarted) {
                    SparkUtil.endSection(sparkSectionStarted);
                }
            }

            if (!entity.getTags().contains("arcadia_boss")) return;

            // Fix #7: direct lookup instead of O(D*B) scan
            for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
                for (BossInstance boss : instance.getActiveBosses().values()) {
                    if (boss.getBossEntity() != entity) continue;

                    if (boss.isTransitioning()) { event.setCanceled(true); return; }
                    if (boss.shouldBlockDamageWhileSummonsAlive()) {
                        event.setCanceled(true);
                        boss.sendSummonWarning(instance.getPlayers());
                        return;
                    }

                    // Reveal boss bar on first hit (optional bosses)
                    boss.revealBossBar();

                    // Anti-phase-skip
                    float currentHp = boss.getBossEntity().getHealth();
                    float maxHp = boss.getBossEntity().getMaxHealth();
                    float resultHp = currentHp - event.getAmount();
                    float floor = getHealthFloorForNextPhase(boss, maxHp);
                    if (floor > 0 && resultHp < floor) {
                        float targetHp = Math.max(Math.nextDown(floor), 0.5f);
                        float capped = currentHp - targetHp;
                        if (capped > 0) event.setAmount(capped); else event.setCanceled(true);
                        resultHp = currentHp - event.getAmount();
                    }

                    if (!event.isCanceled() && maxHp > 0) {
                        boss.checkPhaseTransition(resultHp / maxHp, instance.getPlayers());
                    }
                    return;
                }
            }
        } catch (RuntimeException e) {
            DungeonEventUtil.logHandlerError("onEntityDamage", "entity=" + event.getEntity().getType(), e);
        }
    }

    private float getHealthFloorForNextPhase(BossInstance boss, float maxHp) {
        var phases = boss.getConfig().phases;
        for (int i = boss.getCurrentPhase() + 1; i < phases.size(); i++) {
            return (float) (phases.get(i).healthThreshold * maxHp);
        }
        return 0;
    }

    private void sendDodgeMessage(LivingEntity entity, Entity attacker) {
        String dodgeMessage = CombatTuning.getDodgeMessage(entity);
        if (dodgeMessage == null || dodgeMessage.isEmpty()) return;

        if (attacker instanceof ServerPlayer player) {
            MessageUtil.send(player, dodgeMessage);
            return;
        }
        if (entity instanceof ServerPlayer player) {
            MessageUtil.send(player, dodgeMessage);
        }
    }

    // === LOOT / XP SUPPRESSION ===

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains("arcadia_no_loot")) event.setCanceled(true);
    }

    // === EXTENDED MELEE (called from DungeonTickHandler every 100 ms) ===

    void checkManagedCombat(long now) {
        for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
            for (BossInstance boss : instance.getActiveBosses().values()) {
                LivingEntity bossEntity = boss.getBossEntity();
                if (bossEntity != null && bossEntity.isAlive()) {
                    CombatTuning.tryExtendedMeleeAttack(bossEntity, now);
                }
                for (LivingEntity summon : boss.getSummonedMobs()) {
                    if (summon.isAlive()) {
                        CombatTuning.tryExtendedMeleeAttack(summon, now);
                    }
                }
            }
            for (WaveInstance wave : instance.getWaveInstances()) {
                for (LivingEntity mob : wave.getAliveMobs()) {
                    if (mob.isAlive()) {
                        CombatTuning.tryExtendedMeleeAttack(mob, now);
                    }
                }
            }
        }
    }

    // === DEATH HANDLERS ===

    private void handleBossDeath(LivingEntity entity) {
        for (Map.Entry<String, DungeonInstance> entry : new ArrayList<>(DungeonManager.getInstance().getActiveInstances().entrySet())) {
            DungeonInstance instance = entry.getValue();
            String dungeonId = entry.getKey();

            for (Map.Entry<String, BossInstance> bossEntry : new ArrayList<>(instance.getActiveBosses().entrySet())) {
                BossInstance boss = bossEntry.getValue();
                if (boss.getBossEntity() != entity) continue;

                String bossId = bossEntry.getKey();
                if (DungeonEventUtil.isDebugEnabled(instance.getConfig())) {
                    ArcadiaDungeon.LOGGER.debug("Se ha implementado la muerte del jefe para la mazmorra={} y el jefe={}", dungeonId, bossId);
                }

                for (UUID playerId : instance.getPlayers()) {
                    ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        instance.giveRewards(player, boss.getConfig().rewards);
                        String bossLabel = (boss.getConfig().customName == null || boss.getConfig().customName.isEmpty())
                                ? bossId
                                : boss.getConfig().customName;
                        Component bossName = DungeonManager.parseColorCodes(bossLabel);
                        player.sendSystemMessage(Component.literal("[Arcadia] Boss ").withStyle(ChatFormatting.GOLD)
                                .append(bossName)
                                .append(Component.literal(" derrotado!").withStyle(ChatFormatting.GOLD)));
                    }
                }

                // Fix #2: remove from map BEFORE cleanup to prevent re-entry issues
                instance.removeBossInstance(bossId);
                boss.cleanup();

                // Inter-wave boss: let checkWaveStates handle resume when required bosses dead
                if (instance.isWaitingForInterWaveBoss()) {
                    // checkWaveStates will detect allRequiredBossesDefeated and resume
                } else if (instance.hasNextBoss() && (!instance.hasWaves() || instance.areWavesCompleted())) {
                    boolean spawned = BossManager.getInstance().spawnNextBoss(instance);
                    if (spawned) {
                        for (UUID playerId : instance.getPlayers()) {
                            ServerPlayer player = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(playerId);
                            if (player != null) player.sendSystemMessage(Component.literal("[Arcadia] Se acerca el próximo jefe...").withStyle(ChatFormatting.YELLOW));
                        }
                    } else if (instance.allRequiredBossesDefeated()) {
                        if (!instance.hasWaves() || instance.areWavesCompleted()) {
                            for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                                remaining.cleanup();
                            }
                            instance.getActiveBosses().clear();
                            DungeonManager.getInstance().scheduleCompletion(dungeonId);
                        }
                    }
                } else if (instance.allRequiredBossesDefeated() && !instance.isWaitingForInterWaveBoss()) {
                    // All required bosses defeated — check if waves done too
                    if (!instance.hasWaves() || instance.areWavesCompleted()) {
                        for (BossInstance remaining : new ArrayList<>(instance.getActiveBosses().values())) {
                            remaining.cleanup();
                        }
                        instance.getActiveBosses().clear();
                        DungeonManager.getInstance().scheduleCompletion(dungeonId);
                    }
                }
                return;
            }
        }
    }

    private void handlePlayerDeath(ServerPlayer player, LivingDeathEvent event) {
        DungeonInstance instance = DungeonManager.getInstance().getPlayerDungeon(player.getUUID());
        if (instance == null) return;

        int deaths = instance.addDeath(player.getUUID());
        int maxDeaths = instance.getConfig().settings.maxDeaths;
        int remaining = instance.getRemainingLives(player.getUUID());
        if (DungeonEventUtil.isDebugEnabled(instance.getConfig())) {
            ArcadiaDungeon.LOGGER.debug("Muerte de un jugador en la mazmorra={} jugador={} muertes={} restantes={}",
                    instance.getConfig().id, player.getGameProfile().getName(), deaths, remaining);
        }

        if (maxDeaths > 0 && deaths >= maxDeaths) {
            player.sendSystemMessage(Component.literal("[Arcadia] ¡Se ha alcanzado el límite máximo de muertes (« + maxDeaths + »)! Quedas expulsado de la mazmorra.").withStyle(ChatFormatting.RED));
            for (UUID otherId : instance.getPlayers()) {
                if (!otherId.equals(player.getUUID())) {
                    ServerPlayer other = DungeonManager.getInstance().getServer().getPlayerList().getPlayer(otherId);
                    if (other != null) other.sendSystemMessage(Component.literal("[Arcadia] " + player.getName().getString() + " ha sido excluido (demasiadas muertes)!").withStyle(ChatFormatting.RED));
                }
            }
            DungeonManager.getInstance().schedulePlayerRemoval(player.getUUID());
        } else {
            player.sendSystemMessage(Component.literal("[Arcadia] ¡Muerto! " + remaining + " vida(s) restante(s).").withStyle(ChatFormatting.RED));
        }
    }
}
