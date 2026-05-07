package com.arcadia.dungeon.client.util;

/**
 * Palette Copper Patina — 14 variables CSS du design system.
 * Source : ux-v1/design-system.md §2.
 *
 * Format : ARGB int (0xAARRGGBB) compatible avec g.fill() et g.drawString().
 */
public final class ArcadiaPalette {
    private ArcadiaPalette() {}

    // Backgrounds
    public static final int BG0  = 0xFF17120D;
    public static final int BG1  = 0xFF1F1812;
    public static final int BG2  = 0xFF2A2019;
    public static final int BG3  = 0xFF362A20;

    // Copper accents
    public static final int COPPER_HI   = 0xFFF0B27A;
    public static final int COPPER      = 0xFFD89255;
    public static final int COPPER_LO   = 0xFFA0642C;
    public static final int COPPER_DEEP = 0xFF5A3A1C;

    // Text
    public static final int CREAM     = 0xFFF3E7D3;
    public static final int CREAM_DIM = 0xFFC2AD8E;
    public static final int TEXT_MUTE = 0xFF7A6A55;

    // Sémantique
    public static final int GOOD      = 0xFF8FB96B;
    public static final int WARN      = 0xFFE0A84A;
    public static final int DANGER    = 0xFFC9533D;
    public static final int VERDIGRIS = 0xFF6BA894;

    // Lignes séparatrices
    public static final int LINE        = 0xFF5A3A1C;
    public static final int LINE_STRONG = 0xFFA0642C;
}
