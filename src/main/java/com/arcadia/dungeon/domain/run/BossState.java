package com.arcadia.dungeon.domain.run;

/**
 * État du boss au sein d'une {@link Run}.
 *
 * <p>N'est PAS un agrégat séparé (cf. architecture-v1 §4.1). Le boss n'a pas
 * de cycle de vie indépendant de la run.
 */
public final class BossState {

    private final String type;
    private final int hpMax;
    private int hpCurrent;
    private int currentPhaseIndex;

    public BossState(String type, int hpMax) {
        this.type = type;
        this.hpMax = hpMax;
        this.hpCurrent = hpMax;
        this.currentPhaseIndex = 0;
    }

    public String type() { return type; }
    public int hpMax() { return hpMax; }
    public int hpCurrent() { return hpCurrent; }
    public int currentPhaseIndex() { return currentPhaseIndex; }

    public void setHpCurrent(int hp) { this.hpCurrent = Math.max(0, Math.min(hpMax, hp)); }
    public void setCurrentPhaseIndex(int index) { this.currentPhaseIndex = index; }

    public boolean isDead() { return hpCurrent <= 0; }
}
