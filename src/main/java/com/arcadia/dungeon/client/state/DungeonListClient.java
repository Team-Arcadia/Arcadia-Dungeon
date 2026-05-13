package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.DungeonListPayload;

import java.util.List;

/**
 * Cache client de la liste des donjons disponibles (Story S6.2).
 *
 * <p>Mis à jour par {@code ClientPayloadHandler} à chaque {@link DungeonListPayload} reçu.
 * Lu par {@code PlayerHubScreen} et {@code AdminHubScreen}.
 */
public final class DungeonListClient {

    private static volatile List<DungeonListPayload.DungeonSummary> dungeons = List.of();
    private static volatile long version = 0L;

    private DungeonListClient() {}

    public static void update(List<DungeonListPayload.DungeonSummary> list) {
        dungeons = List.copyOf(list);
        version++;
    }

    public static List<DungeonListPayload.DungeonSummary> get() {
        return dungeons;
    }

    public static long version() {
        return version;
    }
}
