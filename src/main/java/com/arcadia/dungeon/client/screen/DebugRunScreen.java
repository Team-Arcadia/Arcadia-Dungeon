package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.arcadiaui.ArcaModel;
import com.arcadia.dungeon.client.arcadiaui.ArcaPanel;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplate;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplateRenderer;
import com.arcadia.dungeon.client.state.RunStateClient;
import com.arcadia.dungeon.network.RunStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Screen debug minimal — affiche l'état {@link RunStateClient} via le framework ArcadiaUI.
 *
 * <p>Story S2.6 (AC1, AC4, AC5). Ouvert par {@code /arcadia debug showscreen}.
 * Pas de pause serveur ({@link #isPauseScreen()} = false).
 */
public final class DebugRunScreen extends Screen {

    private static final int PANEL_W = 248;
    private static final int PANEL_H = 148;

    private ArcaPanel panel;

    public DebugRunScreen() {
        super(Component.literal("Arcadia Debug"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        // Overlay sombre simple — pas de Gaussian blur shader MC 1.21 (trop intrusif pour un debug screen)
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

        ArcaModel model = buildModel(RunStateClient.getState().orElse(null));
        ArcaTemplate template = ArcaTemplate.load("arcadia_dungeon:ui/debug-screen");
        panel = ArcaTemplateRenderer.build(template, model, Map.of("close", this::onClose),
            px, py, PANEL_W, PANEL_H);
    }

    private ArcaModel buildModel(RunStatePayload state) {
        if (state == null) {
            return ArcaModel.of(Map.of(
                "run.id",      "—",
                "run.phase",   "PAS DE RUN ACTIVE",
                "run.room",    "—",
                "run.wave",    "—",
                "run.lives",   "—",
                "run.elapsed", "—"
            ));
        }
        long elapsed = (System.currentTimeMillis() - state.startTimestampMs()) / 1000;
        return ArcaModel.of(Map.of(
            "run.id",      state.runId().substring(0, Math.min(16, state.runId().length())) + "…",
            "run.phase",   state.phase(),
            "run.room",    String.valueOf(state.currentRoomIndex()),
            "run.wave",    String.valueOf(state.currentWaveIndex()),
            "run.lives",   String.valueOf(state.livesRemaining()),
            "run.elapsed", String.format("%02d:%02d", elapsed / 60, elapsed % 60)
        ));
    }
}
