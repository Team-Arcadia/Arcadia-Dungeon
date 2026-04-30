package com.arcadia.dungeon.event;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.dungeon.CombatTuning;
import com.arcadia.dungeon.dungeon.DungeonInstance;
import com.arcadia.dungeon.dungeon.DungeonManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Handles world-level entity events: blocks unmanaged entities from entering active dungeon areas.
 */
public class DungeonWorldEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        try {
            long now = System.currentTimeMillis();
            Entity entity = event.getEntity();
            if (entity instanceof Player) return;

            if (entity instanceof Projectile projectile && CombatTuning.shouldCancelProjectileSpawn(projectile, now)) {
                event.setCanceled(true);
                return;
            }

            if (entity instanceof Vex vex && DungeonEventUtil.isArcadiaManaged(vex.getOwner())) {
                event.setCanceled(true);
                return;
            }

            if (!(entity instanceof LivingEntity living)) return;
            if (DungeonEventUtil.isArcadiaManaged(living)) return;

            for (DungeonInstance instance : DungeonManager.getInstance().getActiveInstances().values()) {
                var config = instance.getConfig();
                if (!config.hasArea()) continue;
                String dimension = living.level().dimension().location().toString();
                if (!config.isInArea(dimension, living.getX(), living.getY(), living.getZ())) continue;

                event.setCanceled(true);
                if (DungeonEventUtil.isDebugEnabled(config)) {
                    ArcadiaDungeon.LOGGER.debug("Entidad no gestionada {} bloqueada dentro de una mazmorra activa {}", entity.getType(), config.id);
                }
                return;
            }
        } catch (RuntimeException e) {
            DungeonEventUtil.logHandlerError("onEntityJoinLevel", "entity=" + event.getEntity().getType(), e);
        }
    }
}
