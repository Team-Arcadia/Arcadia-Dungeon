package com.arcadia.dungeon.client.state;

import com.arcadia.dungeon.network.RunStatePayload;

import java.util.Optional;

/**
 * Cache client lecture-seule de l'état run courant (Story S2.4).
 *
 * <p>Mis à jour par {@code ClientPayloadHandler} à chaque {@link RunStatePayload} reçu.
 * Lu par les screens (S6.x) et le HUD overlay (S6.5).
 *
 * <p>Thread-safety : {@code volatile} suffit — les lectures/écritures sont atomiques
 * sur une référence, et les screens s'exécutent sur le render thread (pas de race
 * avec le handler qui s'exécute via {@code enqueueWork} sur le client tick thread).
 */
public final class RunStateClient {

    private static volatile RunStatePayload currentState = null;

    private RunStateClient() {}

    public static void update(RunStatePayload payload) {
        currentState = payload;
    }

    public static Optional<RunStatePayload> getState() {
        return Optional.ofNullable(currentState);
    }

    public static boolean isInRun() {
        RunStatePayload state = currentState;
        return state != null && !"ENDED".equals(state.phase());
    }

    /** Appelé quand le joueur quitte une run ou se déconnecte. */
    public static void clear() {
        currentState = null;
    }
}
