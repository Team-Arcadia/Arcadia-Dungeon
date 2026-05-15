package com.arcadia.dungeon.domain.run;

/**
 * Résultat d'une run terminée. Null si la run est encore active.
 */
public enum RunResult {
    /** Boss tué, run réussie. Rewards complets distribués. */
    VICTORY,

    /** Vies épuisées ou autre échec. Rewards partiels. */
    DEFEAT,

    /** Joueur(s) ont quitté volontairement (commande /abandon ou tous déconnectés). */
    ABANDONED,

    /** Serveur arrêté gracieusement pendant la run. */
    SERVER_SHUTDOWN
}
