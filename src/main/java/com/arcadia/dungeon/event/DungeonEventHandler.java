package com.arcadia.dungeon.event;

/**
 * @deprecated Refactored into focused sub-handlers:
 *   - {@link DungeonWandEventHandler}    — wand interactions, GUI chat capture
 *   - {@link DungeonWorldEventHandler}   — entity join / unmanaged entity blocking
 *   - {@link DungeonPlayerEventHandler}  — player lifecycle, effect & command blocking
 *   - {@link DungeonCombatEventHandler}  — death, damage, loot/XP suppression
 *   - {@link DungeonTickHandler}         — tick loop, waves, timers, containment, anti-fly
 *
 * Wand static maps previously on this class have moved to {@link DungeonWandEventHandler}.
 */
@Deprecated
public final class DungeonEventHandler {
    private DungeonEventHandler() {}
}
