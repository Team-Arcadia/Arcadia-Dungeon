package com.arcadia.dungeon.client.screen;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-écran — paramètres principaux d'un donjon (vies, structure, dimension, Y).
 *
 * <p>Modifie directement {@link DungeonEditClient#config()} et envoie la sauvegarde
 * via {@link SaveDungeonConfigPayload}.
 */
public final class AdminDungeonCoreScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 330;
    private static final int MAX_H  = 300;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonCoreScreen(String dungeonId, String dungeonName) {
        super(Component.translatable("arcadia.admin.core.title", dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override
    protected void init() {
        super.init();
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        AdminUiFeedback.renderToasts(g, width, height);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
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
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int panelW = Math.max(200, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.core.title", dungeonName));
        modelData.put("v.lives",      str(cfg, "lives", "3"));
        modelData.put("v.lobbyCountdown", str(cfg, "lobbyCountdownSeconds", "3"));
        modelData.put("v.minPlayers", str(cfg, "minPlayers", "1"));
        modelData.put("v.maxPlayers", str(cfg, "maxPlayers", "2"));
        modelData.put("v.structure",  str(cfg, "structureRef", ""));
        modelData.put("v.dimension",  str(cfg, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
        modelData.put("v.placementY", str(cfg, "placementY", ""));
        modelData.put("s.dimensions", AdminUiSuggestions.DIMENSIONS);

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", this::doSave);

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onLives",      v -> set(cfg, "lives", intOr(v, 3)));
        inputHandlers.put("onLobbyCountdown", v -> set(cfg, "lobbyCountdownSeconds", clamp(intOr(v, 3), 0, 120)));
        inputHandlers.put("onMinPlayers", v -> {
            int minPlayers = clamp(intOr(v, 1), 1, 8);
            int maxPlayers = clamp(jsonIntOr(cfg, "maxPlayers", 2), 1, 8);
            set(cfg, "minPlayers", minPlayers);
            if (maxPlayers < minPlayers) set(cfg, "maxPlayers", minPlayers);
        });
        inputHandlers.put("onMaxPlayers", v -> {
            int maxPlayers = clamp(intOr(v, 2), 1, 8);
            int minPlayers = clamp(jsonIntOr(cfg, "minPlayers", 1), 1, 8);
            set(cfg, "maxPlayers", maxPlayers);
            if (minPlayers > maxPlayers) set(cfg, "minPlayers", maxPlayers);
        });
        inputHandlers.put("onStructure",  v -> setStr(cfg, "structureRef", v));
        inputHandlers.put("onDimension",  v -> setStr(cfg, "dimension", v));
        inputHandlers.put("onPlacementY", v -> setNullableInt(cfg, "placementY", v));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-core");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void doSave() {
        AdminUiFeedback.saveDungeonConfig(dungeonId);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private static String str(JsonObject o, String key, String def) {
        try { return o.get(key).getAsString(); } catch (Exception e) { return def; }
    }

    private static void set(JsonObject o, String key, int val) {
        o.addProperty(key, val);
    }

    private static void setStr(JsonObject o, String key, String val) {
        if (val == null || val.isBlank()) o.add(key, JsonNull.INSTANCE);
        else o.addProperty(key, val.trim());
    }

    private static void setNullableInt(JsonObject o, String key, String val) {
        if (val == null || val.isBlank()) { o.add(key, JsonNull.INSTANCE); return; }
        try { o.addProperty(key, Integer.parseInt(val.trim())); }
        catch (NumberFormatException ignored) {}
    }

    private static int intOr(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static int jsonIntOr(JsonObject o, String key, int def) {
        try { return o.get(key).getAsInt(); } catch (Exception e) { return def; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
