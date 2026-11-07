package com.vyrriox.arcadiadungeon.dungeon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * What marks a mob as belonging to a dungeon, and what keeps the ground under it loaded.
 *
 * <p>A chunk with nobody near it is written to disk within a few ticks, mobs included, and a run
 * cannot fight what is on disk. So a run holds the chunks its mobs stand in for as long as it needs
 * them. The ticket is short lived and renewed while the mob is there, which means a run that ends in
 * any way at all, a crash included, leaves nothing force loaded behind it.</p>
 *
 * @author vyrriox
 */
public final class ManagedEntities {
    public static final String MANAGED_TAG = "arcadia_managed";
    public static final String NO_LOOT_TAG = "arcadia_no_loot";

    /** Level 31 at the center: the mob's chunk ticks, the ones around it stay loaded. As an ender pearl. */
    private static final int HOLD_RADIUS = 2;
    private ManagedEntities() {}

    public static void hold(ServerLevel level, ChunkPos chunk) {
        // Ticket types are a registry here, and this one is what the mod needs: it loads, it ticks and
        // it runs out after forty ticks unless renewed. Runs renew it every twenty.
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, HOLD_RADIUS);
    }

    public static boolean isManaged(Entity entity) {
        return entity != null && entity.entityTags().contains(MANAGED_TAG);
    }
}
