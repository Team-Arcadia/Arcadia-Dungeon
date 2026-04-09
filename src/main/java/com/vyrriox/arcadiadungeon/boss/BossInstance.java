package com.vyrriox.arcadiadungeon.boss;

import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.config.SummonConfig;
import com.vyrriox.arcadiadungeon.dungeon.CombatTuning;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.SpawnSafety;
import com.vyrriox.arcadiadungeon.util.MessageUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.*;

public class BossInstance {
    private final BossConfig config;
    private final ServerLevel level;
    private LivingEntity bossEntity;
    private final List<LivingEntity> summonedMobs = new ArrayList<>();
    private int currentPhase = 0;
    private boolean transitioning = false;
    private int transitionTicks = 0;
    private ServerBossEvent bossBar;
    private double scaledMaxHealth;
    private double scaledDamage;
    private double originalBaseSpeed;
    private long lastSummonMessageTick = 0;
    private int spawnInvulnerabilityTicks = 100; // 5 seconds of invulnerability after spawn
    private boolean bossBarRevealed = false;

    public BossInstance(BossConfig config, ServerLevel level, int playerCount) {
        this.config = config;
        this.level = level;

        double healthScale = config.adaptivePower ? 1.0 + (config.healthMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        double damageScale = config.adaptivePower ? 1.0 + (config.damageMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        this.scaledMaxHealth = config.baseHealth * healthScale;
        this.scaledDamage = config.baseDamage * damageScale;
    }

    public boolean spawn() {
        Optional<EntityType<?>> typeOpt = EntityType.byString(config.entityType);
        if (typeOpt.isEmpty()) {
            ArcadiaDungeon.LOGGER.error("Unknown entity type: {}", config.entityType);
            return false;
        }

        Entity entity;
        try {
            entity = typeOpt.get().create(level);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to create entity: {}", config.entityType, e);
            return false;
        }

        if (entity == null) {
            ArcadiaDungeon.LOGGER.error("Entity creation returned null: {}", config.entityType);
            return false;
        }

        if (!(entity instanceof LivingEntity living)) {
            ArcadiaDungeon.LOGGER.error("Entity type {} is not a LivingEntity", config.entityType);
            entity.discard();
            return false;
        }

        bossEntity = living;
        SpawnSafety.placeAtSafeSpawn(bossEntity, config.spawnPoint, config.spawnPoint.x, config.spawnPoint.y, config.spawnPoint.z, 4);

        if (config.customName != null && !config.customName.isEmpty()) {
            bossEntity.setCustomName(DungeonManager.parseColorCodes(config.customName));
            bossEntity.setCustomNameVisible(true);
        }

        if (bossEntity.getAttribute(Attributes.MAX_HEALTH) != null) {
            bossEntity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(scaledMaxHealth);
            bossEntity.setHealth((float) scaledMaxHealth);
        }

        if (bossEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            bossEntity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(scaledDamage);
        }

        // Store original speed for phase multipliers
        if (bossEntity.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            originalBaseSpeed = bossEntity.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        }

        // Boss equipment
        BossInstance.equipEntity(bossEntity, config.mainHand, config.offHand,
                config.helmet, config.chestplate, config.leggings, config.boots);

        // Custom attributes
        BossInstance.applyCustomAttributes(bossEntity, config.customAttributes);
        CombatTuning.applyConfiguredCombat(
                bossEntity,
                config.attackRange,
                config.attackCooldownMs,
                config.aggroRange,
                config.projectileCooldownMs,
                config.dodgeChance,
                config.dodgeCooldownMs,
                config.dodgeProjectilesOnly,
                config.dodgeMessage
        );

        if (bossEntity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        bossEntity.addTag("arcadia_boss");
        bossEntity.addTag("arcadia_boss_" + config.id);
        bossEntity.addTag("arcadia_managed");
        bossEntity.addTag("arcadia_no_loot");

        // Temporary invulnerability to prevent boss being killed before players arrive
        bossEntity.setInvulnerable(true);

        level.addFreshEntity(bossEntity);

        if (config.showBossBar) {
            BossEvent.BossBarColor color = getBossBarColor(config.bossBarColor);
            bossBar = new ServerBossEvent(
                    DungeonManager.parseColorCodes(config.customName != null ? config.customName : "Boss"),
                    color,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            // Optional bosses: hide boss bar until first hit
            if (config.optional) {
                bossBar.setVisible(false);
                bossBarRevealed = false;
            } else {
                bossBar.setVisible(true);
                bossBarRevealed = true;
            }
        }

        if (!config.phases.isEmpty()) {
            currentPhase = 0;
        }

        ArcadiaDungeon.LOGGER.info("Spawned boss {} at {},{},{}", config.id,
                config.spawnPoint.x, config.spawnPoint.y, config.spawnPoint.z);
        return true;
    }

    public void tick(Collection<UUID> dungeonPlayers) {
        if (bossEntity == null || !bossEntity.isAlive()) return;
        pruneSummons(); // Fix #6: prune in tick, not in damage events

        // Spawn invulnerability countdown
        if (spawnInvulnerabilityTicks > 0) {
            spawnInvulnerabilityTicks--;
            if (spawnInvulnerabilityTicks <= 0 && !transitioning) {
                bossEntity.setInvulnerable(false);
            }
        }

        // Update boss bar
        if (bossBar != null) {
            float healthPercent = bossEntity.getHealth() / bossEntity.getMaxHealth();
            bossBar.setProgress(healthPercent);

            // Fix #11: sync boss bar without allocating HashSet every tick
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && !bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
            for (ServerPlayer barPlayer : List.copyOf(bossBar.getPlayers())) {
                if (!dungeonPlayers.contains(barPlayer.getUUID())) {
                    bossBar.removePlayer(barPlayer);
                }
            }
        }

        // Handle phase transition countdown
        if (transitioning) {
            transitionTicks--;
            if (transitionTicks <= 0) {
                transitioning = false;
                bossEntity.setInvulnerable(false);
                // Check if we skipped phases during invulnerability
                checkPhaseTransition(dungeonPlayers);
            }
            return;
        }

        checkPhaseTransition(dungeonPlayers);
    }

    private void checkPhaseTransition(Collection<UUID> dungeonPlayers) {
        if (config.phases.isEmpty() || bossEntity == null || !bossEntity.isAlive()) return;

        float healthPercent = bossEntity.getHealth() / bossEntity.getMaxHealth();
        checkPhaseTransition(healthPercent, dungeonPlayers);
    }

    public void checkPhaseTransition(float healthPercent, Collection<UUID> dungeonPlayers) {
        if (config.phases.isEmpty() || bossEntity == null || !bossEntity.isAlive()) return;
        if (transitioning) return;

        // Find the LOWEST phase we should be at (skip intermediate phases if HP dropped fast)
        int targetPhase = -1;
        for (int i = currentPhase + 1; i < config.phases.size(); i++) {
            if (healthPercent <= config.phases.get(i).healthThreshold) {
                targetPhase = i;
            }
        }

        if (targetPhase >= 0) {
            transitionToPhase(targetPhase, dungeonPlayers);
        }
    }

    private void transitionToPhase(int phaseIndex, Collection<UUID> dungeonPlayers) {
        PhaseConfig phase = config.phases.get(phaseIndex);
        currentPhase = phaseIndex;

        ArcadiaDungeon.LOGGER.info("Boss {} transitioning to phase {}", config.id, phase.phase);
        String dungeonId = DungeonManager.getInstance().findDungeonIdByBossEntity(bossEntity);
        if (dungeonId != null) {
            DungeonManager.getInstance().triggerScriptedWalls(dungeonId, "PHASE_START:" + phase.phase);
        }

        // Apply phase multipliers using ORIGINAL values (not stacking)
        if (bossEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            bossEntity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(scaledDamage * phase.damageMultiplier);
        }
        if (bossEntity.getAttribute(Attributes.MOVEMENT_SPEED) != null && originalBaseSpeed > 0) {
            bossEntity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(originalBaseSpeed * phase.speedMultiplier);
        }

        // Handle transition invulnerability (seconds -> ticks)
        double immunityDuration = phase.immunityDuration > 0 ? phase.immunityDuration
                : (phase.invulnerableDuringTransition ? phase.transitionDurationSeconds : 0.0);
        if (immunityDuration > 0) {
            transitioning = true;
            transitionTicks = (int) Math.round(immunityDuration * 20.0);
            bossEntity.setInvulnerable(true);
        }

        // Summon mobs
        for (SummonConfig summon : phase.summonMobs) {
            spawnSummons(summon);
        }

        // Send phase message to dungeon players
        if (phase.phaseStartMessage != null && !phase.phaseStartMessage.isEmpty()) {
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                MessageUtil.send(player, phase.phaseStartMessage);
            }
        }

        // Apply potion effects to players
        for (PhaseConfig.PhaseEffect fx : phase.playerEffects) {
            net.minecraft.resources.ResourceLocation effectLoc = net.minecraft.resources.ResourceLocation.tryParse(fx.effect);
            if (effectLoc == null) continue;
            var effectOpt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(effectLoc);
            if (effectOpt.isEmpty()) continue;
            var holder = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effectOpt.get());
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    DungeonManager.getInstance().allowBeneficialEffect(player, holder, fx.amplifier, fx.durationSeconds * 20);
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, fx.durationSeconds * 20, fx.amplifier, false, true, true));
                }
            }
        }

        // Execute phase commands
        for (String cmd : phase.phaseCommands) {
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    String resolved = cmd.replace("%player%", player.getName().getString());
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), resolved);
                }
            }
        }
    }

    private void spawnSummons(SummonConfig summon) {
        Optional<EntityType<?>> typeOpt = EntityType.byString(summon.entityType);
        if (typeOpt.isEmpty()) return;

        for (int i = 0; i < summon.count; i++) {
            Entity entity;
            try {
                entity = typeOpt.get().create(level);
            } catch (Exception e) {
                ArcadiaDungeon.LOGGER.error("Failed to create summon entity: {}", summon.entityType, e);
                continue;
            }
            if (entity == null) continue;

            if (entity instanceof LivingEntity living) {
                if (summon.spawnPoint != null) {
                    SpawnSafety.placeAtSafeSpawn(
                            living,
                            summon.spawnPoint,
                            summon.spawnPoint.x,
                            summon.spawnPoint.y,
                            summon.spawnPoint.z,
                            4
                    );
                } else {
                    // Legacy fallback when no summon spawn is configured.
                    living.setPos(bossEntity.getX(), bossEntity.getY(), bossEntity.getZ());
                }

                if (summon.customName != null && !summon.customName.isEmpty()) {
                    living.setCustomName(DungeonManager.parseColorCodes(summon.customName));
                    living.setCustomNameVisible(true);
                }

                if (living.getAttribute(Attributes.MAX_HEALTH) != null && summon.health > 0) {
                    living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(summon.health);
                    living.setHealth((float) summon.health);
                }

                if (living.getAttribute(Attributes.ATTACK_DAMAGE) != null && summon.damage > 0) {
                    living.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(summon.damage);
                }

                // Equipment
                BossInstance.equipEntity(living, summon.mainHand, summon.offHand,
                        summon.helmet, summon.chestplate, summon.leggings, summon.boots);

                // Custom attributes
                BossInstance.applyCustomAttributes(living, summon.customAttributes);
                CombatTuning.applyConfiguredCombat(
                        living,
                        summon.attackRange,
                        summon.attackCooldownMs,
                        summon.aggroRange,
                        summon.projectileCooldownMs,
                        summon.dodgeChance,
                        summon.dodgeCooldownMs,
                        summon.dodgeProjectilesOnly,
                        summon.dodgeMessage
                );

                if (living instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }

                living.addTag("arcadia_summon");
                living.addTag("arcadia_summon_" + config.id);
                living.addTag("arcadia_managed");
                living.addTag("arcadia_no_loot");

                level.addFreshEntity(living);
                summonedMobs.add(living);
            } else {
                entity.discard();
            }
        }
    }

    public void sendSummonWarning(Collection<UUID> dungeonPlayers) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - lastSummonMessageTick < 40) return; // 2s cooldown
        lastSummonMessageTick = currentTick;

        for (UUID playerId : dungeonPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            MessageUtil.send(player, "&c[Boss] Eliminez les sbires d'abord!");
        }
    }

    public boolean isBossAlive() {
        return bossEntity != null && bossEntity.isAlive();
    }

    // Fix #6: lazy check without removeIf in hot path (damage events)
    public boolean areSummonsAlive() {
        for (LivingEntity mob : summonedMobs) {
            if (mob.isAlive()) return true;
        }
        return false;
    }

    public boolean shouldBlockDamageWhileSummonsAlive() {
        return areSummonsAlive();
    }

    // Prune dead summons (called from tick, not from damage events)
    public void pruneSummons() {
        summonedMobs.removeIf(mob -> !mob.isAlive());
    }

    public boolean requiresKillSummons() {
        if (currentPhase >= config.phases.size()) return false;
        PhaseConfig phase = config.phases.get(currentPhase);
        return "KILL_SUMMONS".equals(phase.requiredAction) && areSummonsAlive();
    }

    public void revealBossBar() {
        if (!bossBarRevealed && bossBar != null) {
            bossBarRevealed = true;
            bossBar.setVisible(true);
        }
    }

    public List<LivingEntity> getSummonedMobs() {
        return summonedMobs;
    }

    public void removePlayerFromBossBar(ServerPlayer player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public LivingEntity getBossEntity() {
        return bossEntity;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public BossConfig getConfig() {
        return config;
    }

    public boolean isTransitioning() {
        return transitioning;
    }

    public void cleanup() {
        if (bossEntity != null && bossEntity.isAlive()) {
            bossEntity.discard();
        }
        for (LivingEntity mob : summonedMobs) {
            if (mob.isAlive()) {
                mob.discard();
            }
        }
        summonedMobs.clear();
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
        }
    }

    private BossEvent.BossBarColor getBossBarColor(String color) {
        if (color == null) return BossEvent.BossBarColor.RED;
        return switch (color.toUpperCase()) {
            case "BLUE" -> BossEvent.BossBarColor.BLUE;
            case "GREEN" -> BossEvent.BossBarColor.GREEN;
            case "YELLOW" -> BossEvent.BossBarColor.YELLOW;
            case "PURPLE" -> BossEvent.BossBarColor.PURPLE;
            case "WHITE" -> BossEvent.BossBarColor.WHITE;
            case "PINK" -> BossEvent.BossBarColor.PINK;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    // Helper to equip from MobSpawnConfig-style fields
    public static void equipEntity(LivingEntity living, String mainHand, String offHand,
                                    String helmet, String chestplate, String leggings, String boots) {
        equipSlot(living, EquipmentSlot.MAINHAND, mainHand);
        equipSlot(living, EquipmentSlot.OFFHAND, offHand);
        equipSlot(living, EquipmentSlot.HEAD, helmet);
        equipSlot(living, EquipmentSlot.CHEST, chestplate);
        equipSlot(living, EquipmentSlot.LEGS, leggings);
        equipSlot(living, EquipmentSlot.FEET, boots);

        // Prevent equipment drops
        if (living instanceof Mob mob) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.0f);
            }
        }
    }

    private static void equipSlot(LivingEntity living, EquipmentSlot slot, String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) return;
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(loc);
        itemOpt.ifPresent(item -> living.setItemSlot(slot, new ItemStack(item)));
    }

    /**
     * Apply custom attributes from a map (e.g. "minecraft:generic.armor" -> 10.0).
     * Works like /attribute for any registered attribute.
     */
    public static void applyCustomAttributes(LivingEntity living, Map<String, Double> customAttributes) {
        if (customAttributes == null || customAttributes.isEmpty()) return;
        for (Map.Entry<String, Double> entry : customAttributes.entrySet()) {
            if (CombatTuning.applySpecialAttribute(living, entry.getKey(), entry.getValue())) {
                continue;
            }
            ResourceLocation attrLoc = ResourceLocation.tryParse(entry.getKey());
            if (attrLoc == null) {
                ArcadiaDungeon.LOGGER.warn("Invalid attribute key: {}", entry.getKey());
                continue;
            }
            Optional<Attribute> attrOpt = BuiltInRegistries.ATTRIBUTE.getOptional(attrLoc);
            if (attrOpt.isEmpty()) {
                ArcadiaDungeon.LOGGER.warn("Unknown attribute: {}", entry.getKey());
                continue;
            }
            var attrInstance = living.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attrOpt.get()));
            if (attrInstance != null) {
                attrInstance.setBaseValue(entry.getValue());
            } else {
                ArcadiaDungeon.LOGGER.warn("Entity {} does not have attribute: {}", living.getType().toShortString(), entry.getKey());
            }
        }
    }
}
