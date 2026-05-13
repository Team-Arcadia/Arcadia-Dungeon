package com.arcadia.dungeon.client.screen;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
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
 * Sous-écran — paramètres Arcadia (multiplicateur XP, niveau requis).
 */
public final class AdminDungeonArcadiaScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 280;
    private static final int MAX_H  = 180;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonArcadiaScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Arcadia — " + dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override protected void init() { super.init(); panelDirty = true; }

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
    public boolean charTyped(char c, int mods) {
        if (panel != null && panel.charTyped(c, mods)) return true;
        return super.charTyped(c, mods);
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

        JsonObject cfg = DungeonEditClient.config();

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",   I18n.get("arcadia.admin.arcadia.title", dungeonName));
        modelData.put("v.xpMulti",  doubleStr(cfg, "xpMultiplier",  "1.0"));
        modelData.put("v.reqLevel", intStr(cfg,    "requiredLevel", "0"));

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onXpMulti",  v -> setNullableDouble(cfg, "xpMultiplier",  v));
        inputHandlers.put("onReqLevel", v -> setNullableInt(cfg,    "requiredLevel", v));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-arcadia");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private static String doubleStr(JsonObject o, String key, String def) {
        try { return String.valueOf(o.get(key).getAsDouble()); } catch (Exception e) { return def; }
    }

    private static String intStr(JsonObject o, String key, String def) {
        try { return String.valueOf(o.get(key).getAsInt()); } catch (Exception e) { return def; }
    }

    private static void setNullableDouble(JsonObject o, String key, String v) {
        if (v == null || v.isBlank()) { o.add(key, JsonNull.INSTANCE); return; }
        try { o.addProperty(key, Double.parseDouble(v.trim())); } catch (NumberFormatException ignored) {}
    }

    private static void setNullableInt(JsonObject o, String key, String v) {
        if (v == null || v.isBlank()) { o.add(key, JsonNull.INSTANCE); return; }
        try { o.addProperty(key, Integer.parseInt(v.trim())); } catch (NumberFormatException ignored) {}
    }
}
