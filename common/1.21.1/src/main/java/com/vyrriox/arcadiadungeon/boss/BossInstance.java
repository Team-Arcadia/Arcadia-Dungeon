package com.vyrriox.arcadiadungeon.boss;

import com.vyrriox.arcadiadungeon.config.Messages;
import com.vyrriox.arcadiadungeon.ArcadiaDungeon;
import com.vyrriox.arcadiadungeon.config.BossConfig;
import com.vyrriox.arcadiadungeon.config.MobSpawnConfig;
import com.vyrriox.arcadiadungeon.config.PhaseConfig;
import com.vyrriox.arcadiadungeon.config.SummonConfig;
import com.vyrriox.arcadiadungeon.dungeon.CombatTuning;
import com.vyrriox.arcadiadungeon.dungeon.DungeonInstance;
import com.vyrriox.arcadiadungeon.dungeon.DungeonManager;
import com.vyrriox.arcadiadungeon.dungeon.ManagedEntities;
import com.vyrriox.arcadiadungeon.dungeon.TrackedMob;
import com.vyrriox.arcadiadungeon.dungeon.DungeonStats;
import com.vyrriox.arcadiadungeon.dungeon.SpawnSafety;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.*;

public class BossInstance {
    private static final int SPAWN_PROTECTION_TICKS = 100; // 5 seconds, so nobody kills it before players arrive
    private static final int RESTORE_PROTECTION_TICKS = 40;
    private static final int MAX_RESTORES = 3;

    private final BossConfig config;
    private final ServerLevel level;
    private final DungeonInstance run;
    /** Null while the boss is not in the world because its last spawn was refused. */
    private TrackedMob tracked;
    private final List<TrackedMob> summons = new ArrayList<>();
    /** -1 for a boss whose first phase has a threshold of its own and has not been reached yet. */
    private int currentPhase = 0;
    private boolean transitioning = false;
    private int transitionTicks = 0;
    private ServerBossEvent bossBar;
    private final double scaledMaxHealth;
    private final double scaledDamage;
    private double originalBaseSpeed;
    private double armorScale = 1.0;
    private long lastSummonMessageTick = 0;
    private int spawnInvulnerabilityTicks = SPAWN_PROTECTION_TICKS;
    private boolean invulnerableApplied = false;
    private LivingEntity invulnerabilitySyncedOn = null;
    private boolean bossBarRevealed = false;
    private float lastHealthRatio = 1.0F;
    private int restoreAttempts = 0;
    private long summonLockSince = 0L;
    private boolean summonLockLifted = false;

    public BossInstance(BossConfig config, ServerLevel level, DungeonInstance run) {
        this.config = config;
        this.level = level;
        this.run = run;

        int playerCount = run.getPlayerCount();
        double healthScale = config.adaptivePower ? 1.0 + (config.healthMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        double damageScale = config.adaptivePower ? 1.0 + (config.damageMultiplierPerPlayer * (playerCount - 1)) : 1.0;
        this.scaledMaxHealth = config.baseHealth * healthScale;
        this.scaledDamage = config.baseDamage * damageScale;
    }

    public boolean spawn() {
        LivingEntity living = createBossEntity();
        if (living == null) return false;

        enterWorld(living);

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

        enterFirstPhase();

        ArcadiaDungeon.LOGGER.info("Spawned boss {} at {},{},{}", config.id,
                config.spawnPoint.x, config.spawnPoint.y, config.spawnPoint.z);
        return true;
    }

    private LivingEntity createBossEntity() {
        Optional<EntityType<?>> typeOpt = EntityType.byString(config.entityType);
        if (typeOpt.isEmpty()) {
            ArcadiaDungeon.LOGGER.error("Unknown entity type: {}", config.entityType);
            return null;
        }

        Entity entity;
        try {
            entity = typeOpt.get().create(level);
        } catch (Exception e) {
            ArcadiaDungeon.LOGGER.error("Failed to create entity: {}", config.entityType, e);
            return null;
        }

        if (entity == null) {
            ArcadiaDungeon.LOGGER.error("Entity creation returned null: {}", config.entityType);
            return null;
        }

        if (!(entity instanceof LivingEntity living)) {
            ArcadiaDungeon.LOGGER.error("Entity type {} is not a LivingEntity", config.entityType);
            entity.discard();
            return null;
        }
        return living;
    }

    /**
     * Sets the mob up and puts it in the world. False when something else refused it: another mod
     * can cancel any spawn, and a boss that was refused is not there to be fought. The run finds out
     * through {@link #presence} and tries again, instead of waiting on a mob that does not exist.
     */
    private boolean enterWorld(LivingEntity living) {
        SpawnSafety.placeAtSafeSpawn(living, config.spawnPoint, config.spawnPoint.x, config.spawnPoint.y, config.spawnPoint.z, 4);

        if (config.customName != null && !config.customName.isEmpty()) {
            living.setCustomName(DungeonManager.parseColorCodes(config.customName));
            living.setCustomNameVisible(true);
        }

        if (living.getAttribute(Attributes.MAX_HEALTH) != null) {
            living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(scaledMaxHealth);
            living.setHealth((float) scaledMaxHealth);
        }

        if (living.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            living.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(scaledDamage);
        }

        // Boss equipment
        BossInstance.equipEntity(living, config.mainHand, config.offHand,
                config.helmet, config.chestplate, config.leggings, config.boots);

        // Custom attributes
        BossInstance.applyCustomAttributes(living, config.customAttributes);

        // Custom attributes can raise the maximum after the health above was set.
        living.setHealth(living.getMaxHealth());

        // Read after the custom attributes: this is what phase multipliers multiply.
        if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            originalBaseSpeed = living.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        }

        // A boss coming back in the middle of a fight comes back in the phase it was in.
        if (restoreAttempts > 0 || currentPhase > 0) {
            applyPhaseStats(living);
        }

        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        living.addTag("arcadia_boss");
        living.addTag("arcadia_boss_" + config.id);
        living.addTag(ManagedEntities.MANAGED_TAG);
        living.addTag(ManagedEntities.NO_LOOT_TAG);
        living.addTag(run.getRunTag());

        // Temporary invulnerability to prevent boss being killed before players arrive
        living.setInvulnerable(true);
        invulnerableApplied = true;
        invulnerabilitySyncedOn = living;

        DungeonStats.remember(living);
        if (!level.addFreshEntity(living)) {
            ArcadiaDungeon.LOGGER.error("Boss {} ({}) was refused by another mod and did not enter the world",
                    config.id, config.entityType);
            tracked = null;
            return false;
        }
        tracked = new TrackedMob(living, level, new Vec3(config.spawnPoint.x, config.spawnPoint.y, config.spawnPoint.z));
        run.noteProgress();
        return true;
    }

    /**
     * A first phase at full health is how the boss starts, so it applies from the first tick: its
     * multipliers, its message and its summons used to be ignored. A first phase below full health
     * is an ordinary phase with a threshold, and it used to be skipped altogether.
     */
    private void enterFirstPhase() {
        if (config.phases.isEmpty()) return;
        if (config.phases.get(0).healthThreshold >= 1.0) {
            if (tracked != null) transitionToPhase(0, run.getPlayers());
        } else {
            currentPhase = -1;
        }
    }

    /** Whether another spawn may be attempted for this boss after it left the world. */
    public boolean canRestore() {
        return restoreAttempts < MAX_RESTORES;
    }

    /** Brings back a boss that was removed by something else, at the health and phase it had. */
    public boolean restore() {
        restoreAttempts++;
        LivingEntity living = createBossEntity();
        if (living == null) return false;
        spawnInvulnerabilityTicks = RESTORE_PROTECTION_TICKS;
        if (!enterWorld(living)) return false;
        living.setHealth(Math.max(1.0F, lastHealthRatio * living.getMaxHealth()));
        return true;
    }

    public TrackedMob.Presence presence() {
        return tracked == null ? TrackedMob.Presence.REMOVED : tracked.follow(System.currentTimeMillis());
    }

    public TrackedMob getTracked() {
        return tracked;
    }

    public List<TrackedMob> getTrackedSummons() {
        return summons;
    }

    /** Whether this entity is this boss, under any of the objects it has been. */
    public boolean matches(Entity entity) {
        return tracked != null && tracked.id().equals(entity.getUUID());
    }

    public void tick(Collection<UUID> dungeonPlayers) {
        // The countdowns run whatever became of the entity. They used to stop with it, so a boss
        // that unloaded during its spawn protection kept the flag for ever.
        if (spawnInvulnerabilityTicks > 0) spawnInvulnerabilityTicks--;
        if (transitioning && --transitionTicks <= 0) transitioning = false;

        if (presence() != TrackedMob.Presence.PRESENT) return;
        LivingEntity bossEntity = tracked.entity();

        syncInvulnerability(bossEntity);
        pruneSummons(); // Fix #6: prune in tick, not in damage events
        lastHealthRatio = Mth.clamp(bossEntity.getHealth() / bossEntity.getMaxHealth(), 0.0F, 1.0F);

        // Update boss bar
        if (bossBar != null) {
            bossBar.setProgress(lastHealthRatio);

            // A respawned player is a new object with the same id: drop the old one first.
            var playerList = level.getServer().getPlayerList();
            for (ServerPlayer barPlayer : List.copyOf(bossBar.getPlayers())) {
                if (!dungeonPlayers.contains(barPlayer.getUUID()) || playerList.getPlayer(barPlayer.getUUID()) != barPlayer) {
                    bossBar.removePlayer(barPlayer);
                }
            }
            // Fix #11: sync boss bar without allocating HashSet every tick
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = playerList.getPlayer(playerId);
                if (player != null && !bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            }
        }

        if (!transitioning) checkPhaseTransition(dungeonPlayers);
    }

    /**
     * The flag is written to disk with the mob, so a mob read back from disk is always set again.
     * Otherwise it is only touched when the run's own state changes, because a modded boss may use
     * this flag for itself.
     */
    private void syncInvulnerability(LivingEntity bossEntity) {
        boolean wanted = spawnInvulnerabilityTicks > 0 || transitioning;
        if (wanted != invulnerableApplied || bossEntity != invulnerabilitySyncedOn) {
            bossEntity.setInvulnerable(wanted);
            invulnerableApplied = wanted;
            invulnerabilitySyncedOn = bossEntity;
        }
    }

    private void checkPhaseTransition(Collection<UUID> dungeonPlayers) {
        if (config.phases.isEmpty() || tracked == null || !tracked.entity().isAlive()) return;

        LivingEntity bossEntity = tracked.entity();
        float healthPercent = bossEntity.getHealth() / bossEntity.getMaxHealth();
        checkPhaseTransition(healthPercent, dungeonPlayers);
    }

    public void checkPhaseTransition(float healthPercent, Collection<UUID> dungeonPlayers) {
        if (config.phases.isEmpty() || tracked == null || !tracked.entity().isAlive()) return;
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
        LivingEntity bossEntity = tracked.entity();

        ArcadiaDungeon.LOGGER.info("Boss {} transitioning to phase {}", config.id, phase.phase);

        // A first phase left at 1.0 changes nothing, and leaves custom attributes where they are.
        if (phaseIndex > 0 || phase.damageMultiplier != 1.0 || phase.speedMultiplier != 1.0) {
            applyPhaseStats(bossEntity);
            DungeonStats.remember(bossEntity);
        }

        // Handle transition invulnerability (seconds -> ticks)
        if (phase.invulnerableDuringTransition && phase.transitionDurationSeconds > 0) {
            transitioning = true;
            transitionTicks = (int) (phase.transitionDurationSeconds * 20);
        }
        syncInvulnerability(bossEntity);

        // Summon mobs
        for (SummonConfig summon : phase.summonMobs) {
            spawnSummons(summon);
        }
        if (!phase.summonMobs.isEmpty()) {
            summonLockSince = System.currentTimeMillis();
            summonLockLifted = false;
        }

        // Send phase message to dungeon players
        if (phase.phaseStartMessage != null && !phase.phaseStartMessage.isEmpty()) {
            Component msg = DungeonManager.parseColorCodes(phase.phaseStartMessage);
            for (UUID playerId : dungeonPlayers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.sendSystemMessage(msg);
                }
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
                    DungeonManager.getInstance().runOwnCommand(run, resolved);
                }
            }
        }
    }

    private void spawnSummons(SummonConfig summon) {
        Optional<EntityType<?>> typeOpt = EntityType.byString(summon.entityType);
        if (typeOpt.isEmpty()) {
            ArcadiaDungeon.LOGGER.error("Boss {}: unknown entity type for a summon: {}", config.id, summon.entityType);
            return;
        }
        LivingEntity bossEntity = tracked.entity();

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
                // Summons should appear immediately on the boss to avoid delayed
                // "invincible boss" moments while a safe position is searched.
                living.setPos(bossEntity.getX(), bossEntity.getY(), bossEntity.getZ());

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

                if (living instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }

                living.addTag("arcadia_summon");
                living.addTag("arcadia_summon_" + config.id);
                living.addTag(ManagedEntities.MANAGED_TAG);
                living.addTag(ManagedEntities.NO_LOOT_TAG);
                living.addTag(run.getRunTag());

                // Custom attributes can raise the maximum after the health above was set.
                living.setHealth(living.getMaxHealth());

                DungeonStats.remember(living);
                // A summon that was refused can never be killed, so it must not hold the boss.
                if (level.addFreshEntity(living)) {
                    summons.add(new TrackedMob(living, level, bossEntity.position()));
                } else {
                    ArcadiaDungeon.LOGGER.error("Boss {}: a summon ({}) was refused by another mod", config.id, summon.entityType);
                }
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
            if (player != null) {
                player.sendSystemMessage(Messages.get(player, "boss.kill_summons", "count", summons.size()));
            }
        }
    }

    /** True while the boss exists, loaded or not. A boss on disk is alive and coming back. */
    public boolean isBossAlive() {
        TrackedMob.Presence presence = presence();
        return presence == TrackedMob.Presence.PRESENT || presence == TrackedMob.Presence.AWAY;
    }

    // Fix #6: lazy check without removeIf in hot path (damage events)
    public boolean areSummonsAlive() {
        long now = System.currentTimeMillis();
        for (TrackedMob summon : summons) {
            TrackedMob.Presence presence = summon.follow(now);
            if (presence == TrackedMob.Presence.PRESENT || presence == TrackedMob.Presence.AWAY) return true;
        }
        return false;
    }

    /**
     * Only a phase that asks for it makes the boss immune. {@code requiredAction} decided this until
     * a refactor let every summon block damage, whatever the phase said.
     */
    public boolean shouldBlockDamageWhileSummonsAlive() {
        return !summonLockLifted && requiresKillSummons();
    }

    // Prune dead summons (called from tick, not from damage events)
    public void pruneSummons() {
        long now = System.currentTimeMillis();
        summons.removeIf(summon -> {
            TrackedMob.Presence presence = summon.follow(now);
            return presence == TrackedMob.Presence.KILLED || presence == TrackedMob.Presence.REMOVED;
        });
    }

    public boolean requiresKillSummons() {
        if (currentPhase < 0 || currentPhase >= config.phases.size()) return false;
        PhaseConfig phase = config.phases.get(currentPhase);
        return "KILL_SUMMONS".equalsIgnoreCase(phase.requiredAction) && areSummonsAlive();
    }

    /** How long this boss has been waiting on its summons, for the rescue of the ones nobody reaches. */
    public long summonLockHeldFor(long now) {
        return now - summonLockSince;
    }

    /**
     * A summon that cannot be reached must not end a fight. After this long the boss can be hit with
     * its summons still standing, which is a better end to a run than the timer running out.
     */
    public void checkSummonLock(long now) {
        int maxSeconds = run.getConfig().settings.summonLockMaxSeconds;
        if (maxSeconds <= 0 || summonLockLifted || !requiresKillSummons()) return;
        if (summonLockHeldFor(now) < maxSeconds * 1000L) return;
        summonLockLifted = true;
        ArcadiaDungeon.LOGGER.info("Boss {}: summons still standing after {}s, the boss can be hit again", config.id, maxSeconds);
        for (UUID playerId : run.getPlayers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Messages.get(player, "boss.lock_lifted"));
        }
    }

    public void revealBossBar() {
        if (!bossBarRevealed && bossBar != null) {
            bossBarRevealed = true;
            bossBar.setVisible(true);
        }
    }

    /** The summons that are in the world right now. */
    public List<LivingEntity> getSummonedMobs() {
        List<LivingEntity> present = new ArrayList<>(summons.size());
        for (TrackedMob summon : summons) {
            if (!summon.entity().isRemoved()) present.add(summon.entity());
        }
        return present;
    }

    public void removePlayerFromBossBar(ServerPlayer player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    /** The boss as of the last look at it, or null while it is not in the world. */
    public LivingEntity getBossEntity() {
        return tracked == null ? null : tracked.entity();
    }

    /**
     * Damage comes from the armor players wear when the boss spawns. This keeps a boss brought back
     * in the same phase as strong as it was. A later phase replaces it, as it always has.
     */
    public void setArmorScale(double scale) {
        armorScale = scale;
        if (tracked == null) return;
        LivingEntity bossEntity = tracked.entity();
        applyPhaseStats(bossEntity);
        DungeonStats.remember(bossEntity);
    }

    /** Sets damage and speed from the current phase, always from the original values. */
    private void applyPhaseStats(LivingEntity bossEntity) {
        boolean inPhase = currentPhase >= 0 && currentPhase < config.phases.size();
        double damageMultiplier = inPhase ? config.phases.get(currentPhase).damageMultiplier : 1.0;
        double speedMultiplier = inPhase ? config.phases.get(currentPhase).speedMultiplier : 1.0;
        if (currentPhase <= 0) damageMultiplier *= armorScale;

        if (bossEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            bossEntity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(scaledDamage * damageMultiplier);
        }
        if (bossEntity.getAttribute(Attributes.MOVEMENT_SPEED) != null && originalBaseSpeed > 0) {
            bossEntity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(originalBaseSpeed * speedMultiplier);
        }
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
        if (tracked != null) tracked.discard();
        for (TrackedMob summon : summons) {
            summon.discard();
        }
        summons.clear();
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
        equipSlot(living, EquipmentSlot.MAINHAND, mainHand == null || mainHand.isEmpty() ? weaponItNeeds(living) : mainHand);
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

    /**
     * The weapon a mob cannot fight at all without. The game hands these out in finalizeSpawn, which
     * a dungeon does not call, so a pillager with no main hand configured stood there unarmed and
     * never attacked. Write {@code minecraft:air} in the config to really leave it empty.
     */
    private static String weaponItNeeds(LivingEntity living) {
        if (living.getType() == EntityType.PILLAGER) return "minecraft:crossbow";
        if (living.getType() == EntityType.ILLUSIONER) return "minecraft:bow";
        return "";
    }

    private static void equipSlot(LivingEntity living, EquipmentSlot slot, String itemId) {
        if (itemId == null || itemId.isEmpty()) return;
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) return;
        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(loc);
        itemOpt.ifPresent(item -> living.setItemSlot(slot, new ItemStack(item)));
    }

    /**
     * The attribute with this id, under the name this version of the game gives it. The vanilla ones
     * lost their prefix in 1.21.2 ({@code generic.armor} became {@code armor}), and a config written
     * for one version has to keep its armor on another.
     */
    public static Optional<Attribute> findAttribute(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) return Optional.empty();
        Optional<Attribute> exact = BuiltInRegistries.ATTRIBUTE.getOptional(id);
        if (exact.isPresent() || !"minecraft".equals(id.getNamespace())) return exact;

        String bare = id.getPath().substring(id.getPath().indexOf('.') + 1);
        for (String candidate : new String[]{bare, "generic." + bare, "player." + bare, "zombie." + bare}) {
            Optional<Attribute> found = BuiltInRegistries.ATTRIBUTE.getOptional(id.withPath(candidate));
            if (found.isPresent()) return found;
        }
        return Optional.empty();
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
            Optional<Attribute> attrOpt = findAttribute(entry.getKey());
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
