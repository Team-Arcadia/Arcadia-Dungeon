package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.DungeonDetailPayload;

import java.util.Optional;

/**
 * Cache client du détail d'un donjon (Story 8.4).
 *
 * <p>Mis à jour par {@code ClientPayloadHandler} à chaque {@link DungeonDetailPayload} reçu.
 * Lu par {@code AdminDungeonDetailScreen}.
 *
 * <p>Cleared dès qu'un nouvel écran détail s'ouvre — évite d'afficher
 * les données du donjon précédent pendant le chargement.
 */
public final class DungeonDetailClient {

    private static volatile DungeonDetailPayload current = null;

    private DungeonDetailClient() {}

    public static void update(DungeonDetailPayload payload) {
        current = payload;
    }

    public static Optional<DungeonDetailPayload> get() {
        return Optional.ofNullable(current);
    }

    /** Appelé à l'ouverture d'un nouvel écran détail pour forcer le rechargement. */
    public static void clear() {
        current = null;
    }
}
