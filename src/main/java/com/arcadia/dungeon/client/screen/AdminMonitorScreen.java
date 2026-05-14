package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.ActiveRunsClient;
import com.arcadia.dungeon.network.ForceEndRunPayload;
import com.arcadia.dungeon.network.MonitorDataPayload;
import com.arcadia.dungeon.network.MonitorRefreshPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran admin — monitoring des runs actives (Story 8.5).
 *
 * <p>Affiche en temps (quasi-)réel la liste des runs en cours : dunjon, joueurs,
 * salle courante, timer, vies, état. Actions disponibles : forcer la victoire
 * ou la défaite d'une run.
 *
 * <p>Auto-refresh toutes les 2 s via {@link MonitorRefreshPayload} C2S.
 * Données cachées dans {@link ActiveRunsClient}.
 */
public final class AdminMonitorScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN          = 8;
    private static final int MAX_W           = 500;
    private static final int MAX_H           = 290;
    private static final long REFRESH_MS     = 2_000L;

    private TesseraPanel panel;
    private List<MonitorDataPayload.RunSummary> lastRuns = List.of();
    private boolean panelDirty = true;
    private long lastRefreshMs = 0L;

    public AdminMonitorScreen() {
        super(Component.translatable("arcadia.admin.monitor.title"));
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
        // Auto-refresh toutes les 2 s
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= REFRESH_MS) {
            sendRefresh();
        }

        List<MonitorDataPayload.RunSummary> current = ActiveRunsClient.get();
        if (!current.equals(lastRuns)) {
            lastRuns = current;
            panelDirty = true;
        }

        if (panelDirty) { rebuildPanel(); panelDirty = false; }

        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void sendRefresh() {
        PacketDistributor.sendToServer(new MonitorRefreshPayload());
        lastRefreshMs = System.currentTimeMillis();
    }

    private void rebuildPanel() {
        int panelW = Math.max(300, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(180, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        int totalPlayers = lastRuns.stream().mapToInt(MonitorDataPayload.RunSummary::playerCount).sum();

        Map<String, String> modelData = new HashMap<>();
        modelData.put("runs",          String.valueOf(lastRuns.size()));
        modelData.put("playerCount",   String.valueOf(totalPlayers));
        modelData.put("monitor.empty", lastRuns.isEmpty() ? "true" : "");

        for (int i = 0; i < lastRuns.size(); i++) {
            MonitorDataPayload.RunSummary r = lastRuns.get(i);

            // Identifiant court : 8 premiers chars de l'UUID
            String shortId = r.runId().length() >= 8 ? r.runId().substring(0, 8) : r.runId();

            long min = r.elapsedSeconds() / 60;
            long sec = r.elapsedSeconds() % 60;
            String timer = min + ":" + String.format("%02d", sec);

            String room = (r.currentRoomIndex() + 1) + "/" + r.totalRooms();

            modelData.put("r.shortId." + i, shortId);
            modelData.put("r.dungeon." + i, r.dungeonName());
            modelData.put("r.players." + i, String.valueOf(r.playerCount()));
            modelData.put("r.room."    + i, room);
            modelData.put("r.timer."   + i, timer);
            modelData.put("r.lives."   + i, String.valueOf(r.livesRemaining()));
            modelData.put("r.state."   + i, r.phase());
            modelData.put("r.stateBadge." + i, phaseBadge(r.phase()));
            modelData.put("r.winKey."  + i, "winRun."  + i);
            modelData.put("r.stopKey." + i, "stopRun." + i);
        }

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back",    ArcadiaNavigator::back);
        handlers.put("refresh", this::sendRefresh);
        handlers.put("close",   ArcadiaNavigator::closeAll);

        for (int i = 0; i < lastRuns.size(); i++) {
            final String runId = lastRuns.get(i).runId();
            handlers.put("winRun."  + i, () ->
                PacketDistributor.sendToServer(new ForceEndRunPayload(runId, true)));
            handlers.put("stopRun." + i, () ->
                PacketDistributor.sendToServer(new ForceEndRunPayload(runId, false)));
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-monitor");
        panel = TesseraTemplateRenderer.build(template, model, handlers, new HashMap<>(), px, py, panelW, panelH);
    }

    /** Classe CSS du badge selon la phase de la run. */
    private static String phaseBadge(String phase) {
        return switch (phase) {
            case "IN_PROGRESS" -> "good";
            case "STARTING"    -> "warn";
            case "ENDED"       -> "danger";
            default            -> "";
        };
    }
}
