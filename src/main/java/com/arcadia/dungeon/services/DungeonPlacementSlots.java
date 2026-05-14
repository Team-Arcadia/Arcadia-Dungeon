package com.arcadia.dungeon.services;

import com.arcadia.dungeon.domain.config.DungeonConfig;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.OptionalInt;

/** Deterministic placement grid for template-backed dungeon instances. */
public final class DungeonPlacementSlots {

    public static final int DEFAULT_Y = 64;
    public static final int MIN_SLOT = 0;
    public static final int MAX_SLOT = 63;
    public static final int RUNTIME_MIN_SLOT = 64;
    public static final int RUNTIME_MAX_SLOT = 511;
    public static final int SLOTS_PER_ROW = 8;
    public static final int SLOT_SPACING = 1024;

    private DungeonPlacementSlots() {}

    public static int clampSlot(int slot) {
        return Math.max(MIN_SLOT, Math.min(MAX_SLOT, slot));
    }

    public static BlockPos originFor(int slot, int y) {
        int safeSlot = Math.max(MIN_SLOT, Math.min(RUNTIME_MAX_SLOT, slot));
        int column = safeSlot % SLOTS_PER_ROW;
        int row = safeSlot / SLOTS_PER_ROW;
        return new BlockPos(column * SLOT_SPACING, y, row * SLOT_SPACING);
    }

    public static boolean isOccupied(Collection<DungeonConfig> dungeons,
                                     String currentDungeonId,
                                     String dimension,
                                     int slot) {
        for (DungeonConfig dungeon : dungeons) {
            if (dungeon == null || dungeon.id() == null || dungeon.id().equals(currentDungeonId)) continue;
            if (dungeon.generatedSlot() == null || dungeon.generatedOrigin() == null) continue;
            if (dungeon.generatedSlot() == slot && dimension.equals(dungeon.generatedOrigin().dimension())) {
                return true;
            }
        }
        return false;
    }

    public static OptionalInt firstAvailable(Collection<DungeonConfig> dungeons,
                                             String currentDungeonId,
                                             String dimension) {
        for (int slot = MIN_SLOT; slot <= MAX_SLOT; slot++) {
            if (!isOccupied(dungeons, currentDungeonId, dimension, slot)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }
}
