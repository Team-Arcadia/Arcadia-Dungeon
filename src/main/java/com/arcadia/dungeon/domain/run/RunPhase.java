package com.arcadia.dungeon.domain.run;

/**
 * Phase d'une run. Lifecycle simple en MVP.
 */
public enum RunPhase {
    /** Run créée, en attente de joueurs (recrutement) ou de démarrage. */
    STARTING,

    /** Run active : joueurs dans le donjon, combats en cours. */
    IN_PROGRESS,

    /** Run terminée (succès, échec ou abandon). Voir {@link RunResult}. */
    ENDED
}
