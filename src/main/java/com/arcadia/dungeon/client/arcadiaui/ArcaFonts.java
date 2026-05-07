package com.arcadia.dungeon.client.arcadiaui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class ArcaFonts {

    public static final ResourceLocation FANTASY = ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "fantasy");
    public static final ResourceLocation MONO    = ResourceLocation.fromNamespaceAndPath("arcadia_dungeon", "mono");

    private ArcaFonts() {}

    /**
     * Returns the natural pixel height of the given font family — used as the
     * divisor when computing scale from a CSS-style fontSize. Default font is 7,
     * mono atlas is 8, fantasy atlas is 9.
     */
    public static float naturalPx(String fontFamily) {
        if ("fantasy".equals(fontFamily)) return 9f;
        if ("mono".equals(fontFamily))    return 8f;
        return 7f;
    }

    /** Wraps text in a Component with the given font family applied (regular weight). */
    public static Component component(String text, String fontFamily) {
        return component(text, fontFamily, 400);
    }

    /** Wraps text in a Component with font family + weight (>=600 → bold). */
    public static Component component(String text, String fontFamily, int fontWeight) {
        Style style = Style.EMPTY;
        if ("fantasy".equals(fontFamily)) style = style.withFont(FANTASY);
        else if ("mono".equals(fontFamily)) style = style.withFont(MONO);
        if (fontWeight >= 600) style = style.withBold(true);
        return Component.literal(text).withStyle(style);
    }
}
