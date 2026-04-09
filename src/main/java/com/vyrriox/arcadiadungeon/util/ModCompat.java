package com.vyrriox.arcadiadungeon.util;

import net.neoforged.fml.ModList;

public final class ModCompat {
    private ModCompat() {}

    public static final boolean HAS_LUCKPERMS = ModList.get().isLoaded("luckperms");
    public static final boolean HAS_SPARK      = ModList.get().isLoaded("spark");
}
