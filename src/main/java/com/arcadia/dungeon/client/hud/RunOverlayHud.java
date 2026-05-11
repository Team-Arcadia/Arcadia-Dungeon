package com.arcadia.dungeon.client.hud;

import com.arcadia.dungeon.client.state.RunStateClient;
import com.tesseraui.TesseraPalette;
import com.arcadia.dungeon.network.RunStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/**
 * Overlay HUD in-run — chrono + vies + salle (Story S6.5).
 *
 * <p>Enregistré via {@code RegisterGuiLayersEvent} dans {@code ArcadiaDungeon.ClientModEvents}.
 * Visible uniquement quand la phase est {@code IN_PROGRESS}.
 * Position : haut-gauche, ne recouvre pas le HUD vanilla.
 */
public final class RunOverlayHud implements LayeredDraw.Layer {

    public static final RunOverlayHud INSTANCE = new RunOverlayHud();

    private static final int PAD_X = 5;
    private static final int PAD_Y = 5;
    private static final int LINE_H = 10;
    private static final int BG_COLOR = 0x90000000;

    private RunOverlayHud() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (Minecraft.getInstance().screen != null) return;
        RunStatePayload state = RunStateClient.getState().orElse(null);
        if (state == null || !"IN_PROGRESS".equals(state.phase())) return;

        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();

        long elapsed = (now - state.startTimestampMs()) / 1000L;
        String chrono = String.format("%02d:%02d", elapsed / 60, elapsed % 60);

        int lives = state.livesRemaining();
        String livesStr = "♥ " + lives;
        String roomStr = "Salle " + (state.currentRoomIndex() + 1) + "/" + state.totalRooms();

        // Danger pulsing : 1 vie restante
        int chronoColor;
        if (lives <= 1) {
            boolean pulse = (now % 800L) < 400L;
            chronoColor = pulse ? TesseraPalette.DANGER : 0xFFA01A10;
        } else {
            chronoColor = TesseraPalette.CREAM;
        }
        int livesColor = lives <= 1 ? TesseraPalette.DANGER : TesseraPalette.CREAM;

        // Largeur dynamique — prend le max des 3 lignes + 4px de padding de chaque côté
        int w1 = font.width(chrono);
        int w2 = font.width(roomStr);
        int w3 = font.width(livesStr);
        int bgW = Math.max(w1, Math.max(w2, w3)) + 8;
        int bgH = PAD_Y + LINE_H * 3 + 2;
        graphics.fill(PAD_X - 2, PAD_Y - 2, PAD_X + bgW, PAD_Y + bgH, BG_COLOR);

        graphics.drawString(font, chrono,   PAD_X, PAD_Y,              chronoColor, false);
        graphics.drawString(font, livesStr, PAD_X, PAD_Y + LINE_H,     livesColor,  false);
        graphics.drawString(font, roomStr,  PAD_X, PAD_Y + LINE_H * 2, TesseraPalette.CREAM_DIM, false);
    }
}
