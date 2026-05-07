package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.arcadiaui.ArcaModel;
import com.arcadia.dungeon.client.arcadiaui.ArcaPanel;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplate;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Écran résultat de run — 2 modes : VICTORY et DEFEAT (Story S6.3).
 *
 * <p>Ouvert par {@code ClientPayloadHandler} à la réception de
 * {@link com.arcadia.dungeon.network.OpenResultScreenPayload}.
 */
public final class ResultScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 220;

    private final String result;
    private final long elapsedSeconds;
    private final long currencyEarned;
    private final boolean newPb;
    private final long bestTimeSeconds;

    private ArcaPanel panel;

    public ResultScreen(String result, long elapsedSeconds, long currencyEarned,
                        boolean newPb, long bestTimeSeconds) {
        super(Component.literal("Résultat"));
        this.result = result;
        this.elapsedSeconds = elapsedSeconds;
        this.currencyEarned = currencyEarned;
        this.newPb = newPb;
        this.bestTimeSeconds = bestTimeSeconds;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        if (panel != null) panel.render(g, mx, my);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        boolean isVictory = "VICTORY".equals(result);
        String resultClass   = isVictory ? "result-victory" : "result-defeat";
        String titleClass    = isVictory ? "title-victory"  : "title-defeat";
        String titleText     = isVictory ? "✦ VICTOIRE"     : "✗ DÉFAITE";

        String elapsedStr = formatTime(elapsedSeconds);
        String bestStr    = bestTimeSeconds > 0 ? formatTime(bestTimeSeconds) : "—";
        String pbHint     = newPb ? "★ Nouveau record !" : (bestTimeSeconds > 0 ? "Record : " + bestStr : "—");
        String pbHintClass = newPb ? "pb-hint-new" : "";
        String pbClass    = newPb ? "value-good" : "";

        ArcaModel model = ArcaModel.of(Map.of(
            "result.class",      resultClass,
            "result.title",      titleText,
            "result.title-class", titleClass,
            "run.elapsed",       elapsedStr,
            "run.pb",            bestStr,
            "pb.class",          pbClass,
            "pb.hint",           pbHint,
            "pb.hint-class",     pbHintClass,
            "reward.currency",   String.valueOf(currencyEarned)
        ));

        ArcaTemplate template = ArcaTemplate.load("arcadia_dungeon:ui/result-screen");
        panel = ArcaTemplateRenderer.build(template, model, Map.of(
            "hub", () -> Minecraft.getInstance().setScreen(new PlayerHubScreen())
        ), px, py, PANEL_W, PANEL_H);
    }

    private static String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
