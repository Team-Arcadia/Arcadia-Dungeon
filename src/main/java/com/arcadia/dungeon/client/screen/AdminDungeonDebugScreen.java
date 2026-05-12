package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.ActiveRunsClient;
import com.arcadia.dungeon.network.KillDungeonRunsPayload;
import com.arcadia.dungeon.network.MonitorDataPayload;
import com.arcadia.dungeon.network.MonitorRefreshPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sous-écran Debug — affiche les runs actives du donjon et permet de les tuer.
 *
 * <p>Raffraîchit la liste des runs au montage.
 */
public final class AdminDungeonDebugScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 300;
    private static final int MAX_H  = 200;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private boolean panelDirty = true;

    public AdminDungeonDebugScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Debug — " + dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override
    protected void init() {
        super.init();
        // Demande une actualisation de la liste des runs
        PacketDistributor.sendToServer(new MonitorRefreshPayload());
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
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
    public boolean keyPressed(int k, int s, int mods) {
        if (panel != null && panel.keyPressed(k, s, mods)) return true;
        return super.keyPressed(k, s, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override protected TesseraPanel tesseraRoot() { return panel; }

    private void rebuildPanel() {
        int panelW = Math.max(200, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(140, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        List<MonitorDataPayload.RunSummary> allRuns = ActiveRunsClient.get();
        long count = allRuns.stream().filter(r -> dungeonId.equals(r.dungeonId())).count();

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",      I18n.get("arcadia.admin.debug.title", dungeonName));
        modelData.put("debug.empty",    count == 0 ? "true" : "");
        modelData.put("debug.summary",  count > 0  ? count + " run(s) active(s)" : "");

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back",    ArcadiaNavigator::back);
        handlers.put("killAll", () -> {
            PacketDistributor.sendToServer(new KillDungeonRunsPayload(dungeonId));
            // Laisser le temps au serveur de traiter, puis revenir
            ArcadiaNavigator.back();
        });

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-debug");
        panel = TesseraTemplateRenderer.build(template, model, handlers,
            new HashMap<>(), new HashMap<>(), px, py, panelW, panelH);
    }
}
