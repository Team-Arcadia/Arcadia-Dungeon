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
 * Sous-écran — messages diffusés en chat (start, victoire, défaite).
 */
public final class AdminDungeonMessagesScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 300;
    private static final int MAX_H  = 220;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final Map<String, com.tesseraui.TesseraInputState> inputStates = new HashMap<>();
    private boolean panelDirty = true;

    public AdminDungeonMessagesScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Messages — " + dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override protected void init() { super.init(); panelDirty = true; }

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
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.msg.title", dungeonName));
        modelData.put("v.start",   strOrEmpty(cfg, "startMessage"));
        modelData.put("v.victory", strOrEmpty(cfg, "victoryMessage"));
        modelData.put("v.fail",    strOrEmpty(cfg, "failMessage"));

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
            ArcadiaNavigator.back();
        });

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onStart",   v -> setNullable(cfg, "startMessage",   v));
        inputHandlers.put("onVictory", v -> setNullable(cfg, "victoryMessage", v));
        inputHandlers.put("onFail",    v -> setNullable(cfg, "failMessage",    v));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-messages");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, inputStates, px, py, panelW, panelH);
    }

    private static String strOrEmpty(JsonObject o, String key) {
        try { String s = o.get(key).getAsString(); return s != null ? s : ""; }
        catch (Exception e) { return ""; }
    }

    private static void setNullable(JsonObject o, String key, String val) {
        if (val == null || val.isBlank()) o.add(key, JsonNull.INSTANCE);
        else o.addProperty(key, val);
    }
}
