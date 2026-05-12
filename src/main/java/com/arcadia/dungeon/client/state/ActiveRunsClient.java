package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.MonitorDataPayload;

import java.util.List;

/**
 * Cache client des runs actives pour l'AdminMonitorScreen (Story 8.5).
 *
 * <p>Mis à jour par {@code ClientPayloadHandler} à chaque {@link MonitorDataPayload} reçu.
 * Lu par {@code AdminMonitorScreen}.
 */
public final class ActiveRunsClient {

    private static volatile List<MonitorDataPayload.RunSummary> current = List.of();

    private ActiveRunsClient() {}

    public static void update(MonitorDataPayload payload) {
        current = List.copyOf(payload.runs());
    }

    public static List<MonitorDataPayload.RunSummary> get() {
        return current;
    }

    public static void clear() {
        current = List.of();
    }
}
