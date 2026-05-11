package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.network.AbandonRunPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran résultat de run — 3 modes : VICTORY, DEFEAT, DEATH (Story S6.3).
 *
 * <p>DEATH = mort intermédiaire (vies restantes > 0) : affiche un compte à rebours
 * et se ferme automatiquement au respawn.
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
    private final int respawnSeconds;
    private final String dungeonId;
    private final List<String> lootLines;

    // DEATH mode : deadline absolue pour le respawn
    private final long respawnDeadlineMs;
    private int lastSecondsShown = -1;
    private boolean panelDirty = false;

    private TesseraPanel panel;

    public ResultScreen(String result, long elapsedSeconds, long currencyEarned,
                        boolean newPb, long bestTimeSeconds, int respawnSeconds,
                        String dungeonId, List<String> lootLines) {
        super(Component.literal("Résultat"));
        this.result = result;
        this.elapsedSeconds = elapsedSeconds;
        this.currencyEarned = currencyEarned;
        this.newPb = newPb;
        this.bestTimeSeconds = bestTimeSeconds;
        this.respawnSeconds = respawnSeconds;
        this.dungeonId = dungeonId;
        this.lootLines = lootLines != null ? lootLines : List.of();
        this.respawnDeadlineMs = "DEATH".equals(result)
            ? System.currentTimeMillis() + respawnSeconds * 1000L
            : 0L;
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

        if ("DEATH".equals(result)) {
            long remaining = Math.max(0, (respawnDeadlineMs - System.currentTimeMillis() + 999) / 1000L);
            if (remaining == 0) { onClose(); return; }
            int sec = (int) remaining;
            if (sec != lastSecondsShown) {
                lastSecondsShown = sec;
                panelDirty = true;
            }
        }

        if (panelDirty) {
            rebuildPanel();
            panelDirty = false;
        }

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

        final TesseraModel model;
        final String templateId;
        final Map<String, Runnable> actions;

        if ("DEATH".equals(result)) {
            long remaining = Math.max(1, (respawnDeadlineMs - System.currentTimeMillis() + 999) / 1000L);
            model = TesseraModel.of(Map.of("t", remaining + "s"));
            templateId = "arcadia_dungeon:ui/result-screen-death";
            actions = Map.of("quit", () -> {
                PacketDistributor.sendToServer(new AbandonRunPayload());
                Minecraft.getInstance().setScreen(null);
            });
        } else {
            boolean isVictory = "VICTORY".equals(result);
            String resultClass   = isVictory ? "result-victory" : "result-defeat";
            String titleClass    = isVictory ? "title-victory"  : "title-defeat";
            String titleText     = isVictory ? "✦ VICTOIRE"     : "✗ DÉFAITE";

            String elapsedStr = formatTime(elapsedSeconds);
            String bestStr    = bestTimeSeconds > 0 ? formatTime(bestTimeSeconds) : "—";
            String pbHint     = newPb ? "★ Nouveau record !" : (bestTimeSeconds > 0 ? "Record : " + bestStr : "—");
            String pbHintClass = newPb ? "pb-hint-new" : "";
            String pbClass    = newPb ? "value-good" : "";

            Map<String, String> modelData = new HashMap<>();
            modelData.put("result.class",       resultClass);
            modelData.put("result.title",       titleText);
            modelData.put("result.title-class", titleClass);
            modelData.put("run.elapsed",        elapsedStr);
            modelData.put("run.pb",             bestStr);
            modelData.put("pb.class",           pbClass);
            modelData.put("pb.hint",            pbHint);
            modelData.put("pb.hint-class",      pbHintClass);
            modelData.put("reward.currency",    String.valueOf(currencyEarned));
            modelData.put("loot",               String.valueOf(lootLines.size()));
            for (int i = 0; i < lootLines.size(); i++) {
                modelData.put("item.name." + i, lootLines.get(i));
            }
            model = TesseraModel.of(modelData);
            templateId = "arcadia_dungeon:ui/result-screen";
            actions = Map.of(
                "hub",     () -> Minecraft.getInstance().setScreen(new PlayerHubScreen()),
                "rejouer", () -> Minecraft.getInstance().setScreen(new PlayerHubScreen())
            );
        }

        TesseraTemplate template = TesseraTemplate.load(templateId);
        panel = TesseraTemplateRenderer.build(template, model, actions, px, py, PANEL_W, PANEL_H);
    }

    private static String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
