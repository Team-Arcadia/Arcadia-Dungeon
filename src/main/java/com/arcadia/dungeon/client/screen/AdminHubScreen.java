package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.ReloadRequestPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Hub admin — liste, recherche et création de donjons (Stories 8.1–8.2).
 *
 * <p>Accessible via {@code /arcadia admin} (op2 requis côté serveur).
 * Utilise {@link ArcadiaNavigator} pour la navigation vers les sous-écrans.
 */
public final class AdminHubScreen extends com.tesseraui.TesseraScreen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 280;

    private TesseraPanel panel;
    private List<DungeonListPayload.DungeonSummary> allDungeons = List.of();
    private List<DungeonListPayload.DungeonSummary> filtered    = List.of();
    private String filterText = "";
    private boolean panelDirty = true;

    public AdminHubScreen() {
        super(Component.literal("Admin — Arcadia Dungeon"));
    }

    @Override
    protected void init() {
        super.init();
        ArcadiaNavigator.reset();
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        panelDirty = true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Rafraîchit si la liste change côté client (après réponse réseau)
        List<DungeonListPayload.DungeonSummary> current = DungeonListClient.get();
        if (!current.equals(allDungeons)) {
            allDungeons = current;
            applyFilter();
        }
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        renderBackground(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("dungeon.count",    String.valueOf(allDungeons.size()));
        modelData.put("dungeon.filtered", String.valueOf(filtered.size()));
        modelData.put("filter.hint",      filtered.isEmpty() && !filterText.isEmpty() ? "Aucun résultat" : "");

        for (int i = 0; i < filtered.size(); i++) {
            DungeonListPayload.DungeonSummary d = filtered.get(i);
            boolean warn = d.schemaVersion() > 1;
            modelData.put("d.name." + i,         d.name());
            modelData.put("d.id." + i,           d.id());
            modelData.put("d.schema." + i,       "v" + d.schemaVersion());
            modelData.put("d.status." + i,       warn ? "⚠" : "✓");
            modelData.put("d.status-class." + i, warn ? "status-warn" : "status-ok");
            modelData.put("d.manage-key." + i,   "manage." + i);
        }

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("create",  () -> ArcadiaNavigator.push(new AdminDungeonCreateScreen()));
        handlers.put("reload",  () -> PacketDistributor.sendToServer(new ReloadRequestPayload()));
        handlers.put("close",   ArcadiaNavigator::closeAll);

        for (int i = 0; i < filtered.size(); i++) {
            final DungeonListPayload.DungeonSummary d = filtered.get(i);
            handlers.put("manage." + i, () -> {
                // Story 8.4 : AdminDungeonDetailScreen(d.id())
                // Placeholder : refresh pour l'instant
                PacketDistributor.sendToServer(new RequestDungeonListPayload());
            });
        }

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("filter", text -> {
            filterText = text != null ? text : "";
            applyFilter();
            panelDirty = true;
        });

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-hub");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, px, py, PANEL_W, PANEL_H);
    }

    private void applyFilter() {
        if (filterText.isBlank()) {
            filtered = allDungeons;
        } else {
            String q = filterText.toLowerCase(Locale.ROOT);
            filtered = allDungeons.stream()
                .filter(d -> d.name().toLowerCase(Locale.ROOT).contains(q)
                          || d.id().toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toList());
        }
    }
}
