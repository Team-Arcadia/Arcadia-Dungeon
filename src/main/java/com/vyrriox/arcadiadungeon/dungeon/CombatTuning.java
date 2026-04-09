package com.vyrriox.arcadiadungeon.dungeon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CombatTuning {
    public static final String KEY_ATTACK_RANGE = "arcadia:attack_range";
    public static final String KEY_ATTACK_COOLDOWN_MS = "arcadia:attack_cooldown_ms";
    public static final String KEY_AGGRO_RANGE = "arcadia:aggro_range";
    public static final String KEY_PROJECTILE_COOLDOWN_MS = "arcadia:projectile_cooldown_ms";
    public static final String KEY_DODGE_CHANCE = "arcadia:dodge_chance";
    public static final String KEY_DODGE_COOLDOWN_MS = "arcadia:dodge_cooldown_ms";
    public static final String KEY_DODGE_PROJECTILES_ONLY = "arcadia:dodge_vs_projectiles_only";

    public static final List<String> SPECIAL_KEYS = List.of(
            KEY_ATTACK_RANGE,
            KEY_ATTACK_COOLDOWN_MS,
            KEY_AGGRO_RANGE,
            KEY_PROJECTILE_COOLDOWN_MS,
            KEY_DODGE_CHANCE,
            KEY_DODGE_COOLDOWN_MS,
            KEY_DODGE_PROJECTILES_ONLY
    );

    private static final String ROOT_TAG = "ArcadiaCombat";
    private static final String TAG_ATTACK_RANGE = "AttackRange";
    private static final String TAG_ATTACK_COOLDOWN_MS = "AttackCooldownMs";
    private static final String TAG_AGGRO_RANGE = "AggroRange";
    private static final String TAG_PROJECTILE_COOLDOWN_MS = "ProjectileCooldownMs";
    private static final String TAG_DODGE_CHANCE = "DodgeChance";
    private static final String TAG_DODGE_COOLDOWN_MS = "DodgeCooldownMs";
    private static final String TAG_DODGE_PROJECTILES_ONLY = "DodgeProjectilesOnly";
    private static final String TAG_DODGE_MESSAGE = "DodgeMessage";
    private static final String TAG_LAST_MELEE_ATTACK_MS = "LastMeleeAttackMs";
    private static final String TAG_LAST_PROJECTILE_MS = "LastProjectileMs";
    private static final String TAG_LAST_DODGE_MS = "LastDodgeMs";

    private static final long DEFAULT_EXTENDED_MELEE_COOLDOWN_MS = 600L;

    private CombatTuning() {}

    public static boolean applySpecialAttribute(LivingEntity living, String key, double value) {
        CompoundTag data = getCombatData(living);
        switch (key) {
            case KEY_ATTACK_RANGE -> {
                data.putDouble(TAG_ATTACK_RANGE, Math.max(0.0D, value));
                return true;
            }
            case KEY_ATTACK_COOLDOWN_MS -> {
                data.putLong(TAG_ATTACK_COOLDOWN_MS, Math.max(0L, Math.round(value)));
                return true;
            }
            case KEY_AGGRO_RANGE -> {
                double range = Math.max(0.0D, value);
                data.putDouble(TAG_AGGRO_RANGE, range);
                var attr = living.getAttribute(Attributes.FOLLOW_RANGE);
                if (attr != null) {
                    attr.setBaseValue(range);
                }
                return true;
            }
            case KEY_PROJECTILE_COOLDOWN_MS -> {
                data.putLong(TAG_PROJECTILE_COOLDOWN_MS, Math.max(0L, Math.round(value)));
                return true;
            }
            case KEY_DODGE_CHANCE -> {
                data.putDouble(TAG_DODGE_CHANCE, Mth.clamp(value, 0.0D, 1.0D));
                return true;
            }
            case KEY_DODGE_COOLDOWN_MS -> {
                data.putLong(TAG_DODGE_COOLDOWN_MS, Math.max(0L, Math.round(value)));
                return true;
            }
            case KEY_DODGE_PROJECTILES_ONLY -> {
                data.putBoolean(TAG_DODGE_PROJECTILES_ONLY, value >= 0.5D);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void applyConfiguredCombat(LivingEntity living,
                                             double attackRange,
                                             int attackCooldownMs,
                                             double aggroRange,
                                             int projectileCooldownMs,
                                             double dodgeChance,
                                             int dodgeCooldownMs,
                                             boolean dodgeProjectilesOnly,
                                             String dodgeMessage) {
        CompoundTag data = getCombatData(living);

        if (attackRange > 0.0D) {
            data.putDouble(TAG_ATTACK_RANGE, attackRange);
        }
        if (attackCooldownMs > 0) {
            data.putLong(TAG_ATTACK_COOLDOWN_MS, attackCooldownMs);
        }
        if (aggroRange > 0.0D) {
            data.putDouble(TAG_AGGRO_RANGE, aggroRange);
            var attr = living.getAttribute(Attributes.FOLLOW_RANGE);
            if (attr != null) {
                attr.setBaseValue(aggroRange);
            }
        }
        if (projectileCooldownMs > 0) {
            data.putLong(TAG_PROJECTILE_COOLDOWN_MS, projectileCooldownMs);
        }
        if (dodgeChance > 0.0D) {
            data.putDouble(TAG_DODGE_CHANCE, Mth.clamp(dodgeChance, 0.0D, 1.0D));
        }
        if (dodgeCooldownMs > 0) {
            data.putLong(TAG_DODGE_COOLDOWN_MS, dodgeCooldownMs);
        }
        if (dodgeProjectilesOnly) {
            data.putBoolean(TAG_DODGE_PROJECTILES_ONLY, true);
        }
        if (dodgeMessage != null && !dodgeMessage.isEmpty()) {
            data.putString(TAG_DODGE_MESSAGE, dodgeMessage);
        }
    }

    public static void tryExtendedMeleeAttack(LivingEntity living, long now) {
        if (!(living instanceof Mob mob)) return;

        double attackRange = getCombatData(living).getDouble(TAG_ATTACK_RANGE);
        if (attackRange <= 0.0D) return;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || target.level() != mob.level()) return;
        if (!mob.getSensing().hasLineOfSight(target)) return;

        double distanceSq = mob.distanceToSqr(target);
        double vanillaReachSq = getVanillaMeleeReachSq(mob, target);
        double customReachSq = attackRange * attackRange;
        if (distanceSq <= vanillaReachSq || distanceSq > customReachSq) return;

        long cooldownMs = getAttackCooldownMs(living);
        if (cooldownMs <= 0L) cooldownMs = DEFAULT_EXTENDED_MELEE_COOLDOWN_MS;
        long lastAttackMs = getCombatData(living).getLong(TAG_LAST_MELEE_ATTACK_MS);
        if (lastAttackMs > 0L && now - lastAttackMs < cooldownMs) return;

        mob.lookAt(target, 30.0F, 30.0F);
        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.doHurtTarget(target)) {
            getCombatData(living).putLong(TAG_LAST_MELEE_ATTACK_MS, now);
        }
    }

    public static boolean shouldCancelDirectMeleeForCooldown(LivingEntity attacker, Entity directEntity, long now) {
        long cooldownMs = getAttackCooldownMs(attacker);
        if (cooldownMs <= 0L) return false;
        if (directEntity != attacker) return false;

        CompoundTag data = getCombatData(attacker);
        long lastAttackMs = data.getLong(TAG_LAST_MELEE_ATTACK_MS);
        if (lastAttackMs > 0L && now - lastAttackMs < cooldownMs) {
            return true;
        }

        data.putLong(TAG_LAST_MELEE_ATTACK_MS, now);
        return false;
    }

    public static boolean shouldCancelProjectileSpawn(Projectile projectile, long now) {
        Entity owner = projectile.getOwner();
        if (!(owner instanceof LivingEntity living)) return false;
        if (!living.getTags().contains("arcadia_managed")) return false;

        long cooldownMs = getProjectileCooldownMs(living);
        if (cooldownMs <= 0L) return false;

        CompoundTag data = getCombatData(living);
        long lastProjectileMs = data.getLong(TAG_LAST_PROJECTILE_MS);
        if (lastProjectileMs > 0L && now - lastProjectileMs < cooldownMs) {
            return true;
        }

        data.putLong(TAG_LAST_PROJECTILE_MS, now);
        return false;
    }

    public static boolean shouldDodge(LivingEntity defender, Entity directEntity, long now) {
        CompoundTag data = getCombatData(defender);
        double dodgeChance = data.getDouble(TAG_DODGE_CHANCE);
        if (dodgeChance <= 0.0D) return false;

        boolean projectileOnly = data.getBoolean(TAG_DODGE_PROJECTILES_ONLY);
        boolean isProjectile = directEntity instanceof Projectile;
        if (projectileOnly && !isProjectile) return false;
        if (!projectileOnly && directEntity == null) return false;

        long cooldownMs = data.getLong(TAG_DODGE_COOLDOWN_MS);
        long lastDodgeMs = data.getLong(TAG_LAST_DODGE_MS);
        if (cooldownMs > 0L && lastDodgeMs > 0L && now - lastDodgeMs < cooldownMs) return false;

        if (defender.getRandom().nextDouble() > dodgeChance) return false;

        data.putLong(TAG_LAST_DODGE_MS, now);
        applyDodgeMotion(defender);
        return true;
    }

    public static List<String> getSpecialKeys() {
        return SPECIAL_KEYS;
    }

    public static String getDodgeMessage(LivingEntity living) {
        CompoundTag data = getCombatData(living);
        return data.contains(TAG_DODGE_MESSAGE) ? data.getString(TAG_DODGE_MESSAGE) : "";
    }

    private static long getAttackCooldownMs(LivingEntity living) {
        return getCombatData(living).getLong(TAG_ATTACK_COOLDOWN_MS);
    }

    private static long getProjectileCooldownMs(LivingEntity living) {
        return getCombatData(living).getLong(TAG_PROJECTILE_COOLDOWN_MS);
    }

    private static double getVanillaMeleeReachSq(Mob mob, LivingEntity target) {
        double width = mob.getBbWidth() * 2.0F;
        return width * width + target.getBbWidth();
    }

    private static void applyDodgeMotion(LivingEntity defender) {
        Vec3 look = defender.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x);
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(defender.getRandom().nextBoolean() ? 1.0D : -1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
            if (defender.getRandom().nextBoolean()) {
                side = side.scale(-1.0D);
            }
        }
        double vertical = defender.onGround() ? 0.15D : 0.0D;
        defender.setDeltaMovement(defender.getDeltaMovement().add(side.x * 0.35D, vertical, side.z * 0.35D));
        defender.hurtMarked = true;
    }

    private static CompoundTag getCombatData(Entity entity) {
        CompoundTag persistent = entity.getPersistentData();
        CompoundTag data = persistent.getCompound(ROOT_TAG);
        persistent.put(ROOT_TAG, data);
        return data;
    }
}
