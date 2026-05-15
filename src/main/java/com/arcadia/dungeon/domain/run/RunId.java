package com.arcadia.dungeon.domain.run;

import java.util.UUID;

/**
 * Identifiant unique d'une run. Wrapper autour de UUID pour type safety.
 */
public record RunId(UUID value) {
    public static RunId generate() {
        return new RunId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
