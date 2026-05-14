package com.arcadia.dungeon.domain.room;

/**
 * Domain aggregate for a reusable room template.
 *
 * <p>Only {@code structureRef} is currently used. Mob waves are configured
 * globally by dungeon through {@code DungeonConfig.waves}.
 *
 * @param id unique template id, for example {@code arcadia_dungeon:rooms/entry_basic}
 * @param structureRef StructureTemplate resource location to paste in the world
 */
public record RoomTemplate(
    String id,
    String structureRef
) {
}
