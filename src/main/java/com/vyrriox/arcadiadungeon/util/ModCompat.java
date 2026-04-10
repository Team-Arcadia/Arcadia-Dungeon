package com.vyrriox.arcadiadungeon.util;

public final class ModCompat {
    private ModCompat() {}

    public static final boolean HAS_LUCKPERMS = com.arcadia.core.compat.ModCompat.LUCKPERMS.isLoaded();
    public static final boolean HAS_SPARK      = com.arcadia.core.compat.ModCompat.SPARK.isLoaded();
    public static final boolean HAS_EASY_NPC   = com.arcadia.core.compat.ModCompat.EASY_NPC.isLoaded();
    public static final boolean HAS_NUMISMATICS = com.arcadia.core.compat.ModCompat.NUMISMATICS.isLoaded();

    public static boolean isLoaded(String modId) {
        return com.arcadia.core.compat.ModCompat.isLoaded(modId);
    }
}
