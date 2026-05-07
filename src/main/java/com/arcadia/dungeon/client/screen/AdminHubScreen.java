package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.arcadiaui.ArcaModel;
import com.arcadia.dungeon.client.arcadiaui.ArcaPanel;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplate;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.ReloadRequestPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran admin — liste donjons en lecture seule + bouton reload (Story S6.4).
 *
 * <p>Requiert op2. La vérification client-side est indicative ; le serveur
 * valide lui-même dans {@code ServerPayloadHandler.handleReloadRequest}.
 */
public final class AdminHubScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 240;

    private ArcaPanel panel;
    private int lastKnownDungeonCount = -1;
    private boolean panelDirty = true;
    private final boolean accessDenied;

    public AdminHubScreen() {
        super(Component.literal("Admin — Donjons"));
        // Vérifie op2 côté client (indicatif uniquement) — effectué dans le constructeur
        // pour éviter d'appeler onClose() depuis init() (violation contrat Screen).
        this.accessDenied = Minecraft.getInstance().player != null
                && !Minecraft.getInstance().player.hasPermissions(2);
    }

    @Override
    protected void init() {
        super.init();
        if (accessDenied) {
            // Planifier la fermeture hors de init() pour respecter le contrat Screen
            Minecraft.getInstance().tell(this::onClose);
            return;
        }
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        panelDirty = true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        List<DungeonListPayload.DungeonSummary> current = DungeonListClient.get();
        if (current.size() != lastKnownDungeonCount) {
            lastKnownDungeonCount = current.size();
            panelDirty = true;
        }
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
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

        List<DungeonListPayload.DungeonSummary> dungeons = DungeonListClient.get();

        // ArcaForEach v-for="d in dungeon.count" → clés "d.id.i", "d.schema.i", etc.
        Map<String, String> modelData = new HashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        modelData.put("dungeon.count",  String.valueOf(dungeons.size()));
        modelData.put("admin.fallback", dungeons.isEmpty() ? "CHARGEMENT" : "");

        for (int i = 0; i < dungeons.size(); i++) {
            DungeonListPayload.DungeonSummary d = dungeons.get(i);
            boolean isWarn = d.schemaVersion() > 1;
            modelData.put("d.id." + i,         d.id());
            modelData.put("d.schema." + i,      String.valueOf(d.schemaVersion()));
            modelData.put("d.status." + i,      isWarn ? "⚠ v" + d.schemaVersion() : "✓ OK");
            modelData.put("d.status-class." + i, isWarn ? "status-warn" : "status-ok");
            modelData.put("d.badge-class." + i,  isWarn ? "badge-warn" : "badge-ok");
        }

        handlers.put("reload", this::onReload);
        handlers.put("close",  this::onClose);

        ArcaModel model = key -> modelData.getOrDefault(key, null);
        ArcaTemplate template = ArcaTemplate.load("arcadia_dungeon:ui/admin-hub");
        panel = ArcaTemplateRenderer.build(template, model, handlers, px, py, PANEL_W, PANEL_H);
    }

    private void onReload() {
        PacketDistributor.sendToServer(new ReloadRequestPayload());
    }
}
