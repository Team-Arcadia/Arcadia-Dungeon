package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.StructurePlacementStatusPayload;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Client-side cache of the latest async structure placement status per dungeon. */
public final class StructurePlacementClient {

    private static final ConcurrentMap<String, StructurePlacementStatusPayload> STATUSES = new ConcurrentHashMap<>();

    private StructurePlacementClient() {}

    public static void update(StructurePlacementStatusPayload payload) {
        if (payload == null || payload.dungeonId() == null || payload.dungeonId().isBlank()) return;
        STATUSES.put(payload.dungeonId(), payload);
    }

    public static Optional<StructurePlacementStatusPayload> get(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank()) return Optional.empty();
        return Optional.ofNullable(STATUSES.get(dungeonId));
    }

    public static void clear(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank()) return;
        STATUSES.remove(dungeonId);
    }
}
