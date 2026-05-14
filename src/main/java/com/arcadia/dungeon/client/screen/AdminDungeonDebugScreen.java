package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.ActiveRunsClient;
import com.arcadia.dungeon.network.ForceEndRunPayload;
import com.arcadia.dungeon.network.MonitorDataPayload;
import com.arcadia.dungeon.network.MonitorRefreshPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ecran debug admin global - affiche les runs actives et permet de les terminer.
 */
public final class AdminDungeonDebugScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 300;
    private static final int MAX_H = 200;
    private static final long REFRESH_MS = 2_000L;

    private TesseraPanel panel;
    private boolean panelDirty = true;
    private List<MonitorDataPayload.RunSummary> lastRuns = List.of();
    private long lastRefreshMs = 0L;

    public AdminDungeonDebugScreen() {
        super(Component.translatable("arcadia.admin.debug.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        ActiveRunsClient.clear();
        sendRefresh();
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= REFRESH_MS) {
            sendRefresh();
        }

        List<MonitorDataPayload.RunSummary> current = ActiveRunsClient.get();
        if (!current.equals(lastRuns)) {
            lastRuns = current;
            panelDirty = true;
        }
        if (panelDirty) {
            rebuildPanel();
            panelDirty = false;
        }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int k, int s, int mods) {
        if (panel != null && panel.keyPressed(k, s, mods)) return true;
        return super.keyPressed(k, s, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return panel;
    }

    private void sendRefresh() {
        PacketDistributor.sendToServer(new MonitorRefreshPayload());
        lastRefreshMs = System.currentTimeMillis();
    }

    private void rebuildPanel() {
        int panelW = Math.max(200, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(140, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.debug.global.title"));
        modelData.put("debug.empty", lastRuns.isEmpty() ? "true" : "");
        modelData.put("debug.summary", lastRuns.isEmpty() ? "" : I18n.get("arcadia.admin.debug.summary", lastRuns.size()));

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("killAll", () -> {
            for (MonitorDataPayload.RunSummary run : lastRuns) {
                PacketDistributor.sendToServer(new ForceEndRunPayload(run.runId(), false));
            }
            ActiveRunsClient.clear();
            lastRuns = List.of();
            sendRefresh();
            panelDirty = true;
        });

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-debug");
        panel = TesseraTemplateRenderer.build(template, model, handlers,
            new HashMap<>(), new HashMap<>(), px, py, panelW, panelH);
    }
}
