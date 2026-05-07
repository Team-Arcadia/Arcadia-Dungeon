package com.arcadia.dungeon.domain.room;

/**
 * Agrégat domain — template d'une salle.
 *
 * <p>Réutilisable entre donjons. Évolue indépendamment de
 * {@link com.arcadia.dungeon.domain.config.DungeonConfig}.
 *
 * <p>En MVP, seule la `structureRef` est utilisée (référence vers une
 * StructureTemplate Minecraft chargée from datapack). Les waves de mobs
 * sont définies par-donjon via {@code DungeonConfig.RoomRef.waves} et
 * non globalement dans le template (permet customisation par donjon).
 *
 * @param id           identifiant unique du template (ex: "arcadia_dungeon:rooms/entry_basic")
 * @param structureRef resourceLocation de la StructureTemplate à coller dans le monde
 *                     (ex: "arcadia_dungeon:structures/room_entry_basic")
 */
public record RoomTemplate(
    String id,
    String structureRef
) {
}
