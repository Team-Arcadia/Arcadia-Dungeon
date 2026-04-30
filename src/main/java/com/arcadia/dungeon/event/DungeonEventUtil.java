package com.arcadia.dungeon.event;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.config.DungeonConfig;
import net.minecraft.world.entity.Entity;

/**
 * Package-private static helpers shared across Dungeon event handler classes.
 */
class DungeonEventUtil {

    private DungeonEventUtil() {}

    static boolean isDebugEnabled(DungeonConfig config) {
        return config != null && config.debugMode && ArcadiaDungeon.LOGGER.isDebugEnabled();
    }

    static void logHandlerError(String handlerName, RuntimeException e) {
        ArcadiaDungeon.LOGGER.error("Arcadia: error inesperado en  {}", handlerName, e);
    }

    static void logHandlerError(String handlerName, String context, RuntimeException e) {
        ArcadiaDungeon.LOGGER.error("Arcadia: error inesperado en {} [{}]", handlerName, context, e);
    }

    static boolean isArcadiaManaged(Entity entity) {
        return entity != null && entity.getTags().contains("arcadia_managed");
    }
}
