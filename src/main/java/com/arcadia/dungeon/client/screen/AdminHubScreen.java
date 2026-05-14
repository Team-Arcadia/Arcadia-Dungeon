package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraContextMenu;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.domain.config.DungeonConfig;
import com.arcadia.dungeon.network.DeleteDungeonPayload;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.ReloadRequestPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
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

    /** Marge minimale de chaque côté (px GUI) — garantit la visibilité à GUI scale 4. */
    private static final int MARGIN = 8;
    private static final int MAX_W  = 460;
    private static final int MAX_H  = 290;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private List<DungeonListPayload.DungeonSummary> allDungeons = List.of();
    private DungeonListPayload.DungeonSummary clipboard = null;
    private double lastMx, lastMy;
    private List<DungeonListPayload.DungeonSummary> filtered    = List.of();
    private String filterText = "";
    private boolean panelDirty = true;

    public AdminHubScreen() {
        super(Component.translatable("arcadia.admin.hub.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        ArcadiaNavigator.reset();
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Rafraîchit si la liste change côté client (après réponse réseau)
        List<DungeonListPayload.DungeonSummary> current = DungeonListClient.get();
        if (!current.equals(allDungeons)) {
            allDungeons = current;
            applyFilter();
            panelDirty = true; // forcer le rebuild quand la liste arrive du réseau
        }
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        TesseraContextMenu.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (TesseraContextMenu.mouseClicked(mx, my, btn)) return true;
        lastMx = mx; lastMy = my;
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (panel != null && panel.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (panel != null && panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // panel.mouseScrolled prend 3 args (mx, my, dy) — propagé jusqu'à TesseraVirtualList
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int panelW = Math.max(260, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("dungeon.count",    String.valueOf(allDungeons.size()));
        modelData.put("dungeon.filtered", String.valueOf(filtered.size()));
        modelData.put("filter.hint",      filtered.isEmpty() && !filterText.isEmpty() ? I18n.get("arcadia.admin.hub.empty") : "");

        for (int i = 0; i < filtered.size(); i++) {
            DungeonListPayload.DungeonSummary d = filtered.get(i);
            boolean warn = d.schemaVersion() != DungeonConfig.CURRENT_SCHEMA_VERSION;
            modelData.put("d.name." + i,         d.name());
            modelData.put("d.status." + i,      warn ? "⚠" : "✓");
            modelData.put("d.statusClass." + i, warn ? "status-warn" : "status-ok");
            modelData.put("d.manageKey." + i,   "manage." + i);
        }

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("create",  () -> ArcadiaNavigator.push(new AdminDungeonCreateScreen()));
        handlers.put("classes", () -> ArcadiaNavigator.push(new AdminDungeonArchetypesScreen()));
        handlers.put("reload",  () -> PacketDistributor.sendToServer(new ReloadRequestPayload()));
        handlers.put("monitor", () -> ArcadiaNavigator.push(new AdminMonitorScreen()));
        handlers.put("debug",   () -> ArcadiaNavigator.push(new AdminDungeonDebugScreen()));
        handlers.put("close",   ArcadiaNavigator::closeAll);

        for (int i = 0; i < filtered.size(); i++) {
            final DungeonListPayload.DungeonSummary d = filtered.get(i);
            handlers.put("manage." + i, () -> ArcadiaNavigator.push(new AdminDungeonConfigScreen(d.id(), d.name())));
            handlers.put("ctx." + i, () -> {
                final boolean canPaste = clipboard != null;
                TesseraContextMenu.builder()
                    .item(I18n.get("arcadia.admin.context.copy"),   () -> clipboard = d)
                    .item(I18n.get("arcadia.admin.context.paste"),   () -> ArcadiaNavigator.push(new AdminDungeonCreateScreen(clipboard.name())), canPaste)
                    .separator()
                    .item(I18n.get("arcadia.admin.common.delete"), () -> {
                        PacketDistributor.sendToServer(new DeleteDungeonPayload(d.id()));
                        panelDirty = true;
                    })
                    .showAt((int) lastMx, (int) lastMy);
            });
            modelData.put("d.ctxKey." + i, "ctx." + i);
        }

        // Menu contextuel sur la zone vide de la liste (coller sans sélection)
        handlers.put("ctxList", () -> {
            if (clipboard == null) return;
            TesseraContextMenu.builder()
                .item(I18n.get("arcadia.admin.context.paste_named", clipboard.name()),
                    () -> ArcadiaNavigator.push(new AdminDungeonCreateScreen(clipboard.name())))
                .showAt((int) lastMx, (int) lastMy);
        });

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("filter", text -> {
            filterText = text != null ? text : "";
            applyFilter();
            panelDirty = true;
        });

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-hub");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
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
