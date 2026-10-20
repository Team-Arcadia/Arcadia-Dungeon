package com.vyrriox.arcadiadungeon.dungeon;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps the health and damage a dungeon gives its mobs, even when another mod or a script caps
 * mob stats when they join the level (for example a KubeJS "max 10000 HP / 80 damage" rule).
 *
 * <p>The configured base values are stored in the entity's persistent data when the dungeon spawns
 * it. On every level join (first spawn and chunk reloads) the current health is read before anyone
 * else runs, then the stored stats and that health are put back after everyone else has run.
 *
 * @author vyrriox
 */
public final class DungeonStats {
    private static final String MANAGED_TAG = "arcadia_managed";
    private static final String ROOT = "ArcadiaStats";
    private static final String MAX_HEALTH = "MaxHealth";
    private static final String ATTACK_DAMAGE = "AttackDamage";
    private static final String JOIN_HEALTH = "JoinHealth";

    private DungeonStats() {}

    /** Call right before {@code addFreshEntity}, once health, damage and custom attributes are set. */
    public static void remember(LivingEntity living) {
        CompoundTag stats = new CompoundTag();
        AttributeInstance health = living.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) stats.putDouble(MAX_HEALTH, health.getBaseValue());
        AttributeInstance damage = living.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) stats.putDouble(ATTACK_DAMAGE, damage.getBaseValue());
        living.getPersistentData().put(ROOT, stats);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoinFirst(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!living.getTags().contains(MANAGED_TAG)) return;
        CompoundTag stats = living.getPersistentData().getCompound(ROOT);
        if (stats.isEmpty()) return;
        stats.putFloat(JOIN_HEALTH, living.getHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoinLast(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!living.getTags().contains(MANAGED_TAG)) return;
        CompoundTag stats = living.getPersistentData().getCompound(ROOT);
        if (stats.isEmpty()) return;

        restoreBase(living, Attributes.MAX_HEALTH, stats, MAX_HEALTH);
        restoreBase(living, Attributes.ATTACK_DAMAGE, stats, ATTACK_DAMAGE);

        if (stats.contains(JOIN_HEALTH)) {
            float health = Math.min(stats.getFloat(JOIN_HEALTH), living.getMaxHealth());
            stats.remove(JOIN_HEALTH);
            if (health > 0.0F && living.getHealth() != health) {
                living.setHealth(health);
            }
        }
    }

    private static void restoreBase(LivingEntity living, Holder<Attribute> attribute, CompoundTag stats, String key) {
        if (!stats.contains(key)) return;
        AttributeInstance instance = living.getAttribute(attribute);
        double target = stats.getDouble(key);
        if (instance != null && instance.getBaseValue() != target) {
            instance.setBaseValue(target);
        }
    }
}
