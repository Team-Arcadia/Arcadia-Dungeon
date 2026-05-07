package com.arcadia.dungeon.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket simple : 1 requête par intervalle par joueur (Story S3.4).
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}. L'entrée persiste en mémoire
 * tant que le serveur tourne — volume faible (1 entrée par joueur connecté max).
 */
public final class PacketRateLimiter {

    private final long intervalMs;
    private final Map<UUID, Long> lastGranted = new ConcurrentHashMap<>();

    public PacketRateLimiter(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /**
     * @return {@code true} si la requête est autorisée (et consomme le token),
     *         {@code false} si le rate limit est atteint.
     *
     * <p>Utilise {@link java.util.concurrent.ConcurrentHashMap#compute} pour
     * garantir l'atomicité du get+put (pas de race condition entre threads).
     */
    public boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        long[] granted = { 1 }; // 1 = autorisé
        lastGranted.compute(playerId, (k, last) -> {
            if (last != null && now - last < intervalMs) {
                granted[0] = 0; // refusé
                return last;
            }
            return now;
        });
        return granted[0] == 1;
    }

    public void evict(UUID playerId) {
        lastGranted.remove(playerId);
    }
}
