package com.arcadia.dungeon.client.arcadiaui;

import com.arcadia.dungeon.client.util.ArcadiaPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ArcaLabel extends ArcaElement {

    private String text;
    private int color = ArcadiaPalette.CREAM;
    private String textAlign = "left";
    private String fontFamily = null;
    private float fontSize = 7f;
    private int fontWeight = 400;
    private String textTransform = null;
    private boolean clipOverflow = false;
    private float opacity = 1f;

    public ArcaLabel(int x, int y, int width, int height, String text) {
        super(x, y, width, height);
        this.text = text;
    }

    public ArcaLabel text(String text) { this.text = text; return this; }
    public ArcaLabel color(int color)  { this.color = color; return this; }
    public ArcaLabel textAlign(String align) { this.textAlign = align; return this; }
    public ArcaLabel font(String fontFamily) { this.fontFamily = fontFamily; return this; }
    public ArcaLabel fontSize(float px) { if (px > 0) this.fontSize = px; return this; }
    public ArcaLabel fontWeight(int w) { if (w > 0) this.fontWeight = w; return this; }
    public ArcaLabel textTransform(String tt) { this.textTransform = tt; return this; }
    public ArcaLabel clipOverflow(boolean clip) { this.clipOverflow = clip; return this; }
    public ArcaLabel opacity(float o) { this.opacity = Math.max(0f, Math.min(1f, o)); return this; }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        var font = Minecraft.getInstance().font;
        String displayed = ArcaTextStyling.transform(text, textTransform);
        float scale = fontSize / ArcaFonts.naturalPx(fontFamily);

        if (clipOverflow && width > 0) {
            int rawW = (int) Math.ceil(font.width(Component.literal(displayed)) * scale);
            if (rawW > width) {
                String ellipsed = displayed;
                while (!ellipsed.isEmpty()
                        && font.width(Component.literal(ellipsed + "…")) * scale > width) {
                    ellipsed = ellipsed.substring(0, ellipsed.length() - 1);
                }
                displayed = ellipsed + "…";
            }
        }

        var comp = ArcaFonts.component(displayed, fontFamily, fontWeight);
        int drawColor = applyOpacity(color, opacity);

        int textX;
        int textW = (int) Math.ceil(font.width(comp) * scale);
        switch (textAlign) {
            case "center" -> textX = x + (width - textW) / 2;
            case "right"  -> textX = x + width - textW;
            default       -> textX = x;
        }
        int textY = y + (height - (int) Math.ceil(8 * scale)) / 2;

        if (Math.abs(scale - 1f) < 1e-3f) {
            g.drawString(font, comp, textX, textY, drawColor, false);
        } else {
            g.pose().pushPose();
            g.pose().translate(textX, textY, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, comp, 0, 0, drawColor, false);
            g.pose().popPose();
        }
        renderStateOverlays(g, mx, my);
    }

    static int applyOpacity(int color, float opacity) {
        if (opacity >= 1f) return color;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) a = 255;
        int newA = Math.max(0, Math.min(255, (int) (a * opacity)));
        return (newA << 24) | (color & 0xFFFFFF);
    }
}
