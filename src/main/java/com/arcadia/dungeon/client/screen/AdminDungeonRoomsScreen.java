package com.arcadia.dungeon.client.screen;

import com.google.gson.JsonArray;
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
 * Sous-écran — liste des salles d'un donjon.
 *
 * <p>Affiche chaque salle avec son template et le nombre de vagues.
 * Le bouton "Gérer les vagues" pousse {@link AdminDungeonWavesScreen} pour une salle spécifique.
 */
public final class AdminDungeonRoomsScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 360;
    private static final int MAX_H  = 270;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonRoomsScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Rooms — " + dungeonName));
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

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean charTyped(char c, int m) {
        if (panel != null && panel.charTyped(c, m)) return true;
        return super.charTyped(c, m);
    }

    @Override public boolean keyPressed(int k, int s, int m) {
        if (panel != null && panel.keyPressed(k, s, m)) return true;
        return super.keyPressed(k, s, m);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override protected TesseraPanel tesseraRoot() { return panel; }

    private void rebuildPanel() {
        int panelW = Math.max(240, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();
        JsonArray rooms = getRooms(cfg);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",   I18n.get("arcadia.admin.rooms.title", dungeonName));
        modelData.put("rooms.count", String.valueOf(rooms.size()));

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("addRoom", () -> {
            JsonObject room = new JsonObject();
            room.addProperty("id",          "room_" + (rooms.size() + 1));
            room.addProperty("templateRef", "");
            room.add("waves", new JsonArray());
            rooms.add(room);
            renderContext.clearInputsWithPrefix("room");
            panelDirty = true;
        });

        // v-for="r in rooms.count" → varName="r" → all per-row keys prefixed with "r."
        for (int i = 0; i < rooms.size(); i++) {
            final int idx = i;
            JsonObject room = rooms.get(i).getAsJsonObject();
            String label = I18n.get("arcadia.admin.rooms.room", String.valueOf(i + 1)) +
                " (" + strOr(room, "id", "room_" + (i + 1)) + ")";

            modelData.put("r.roomLabel."      + i, label);
            modelData.put("r.roomTemplate."   + i, strOr(room, "templateRef", ""));
            modelData.put("r.roomTemplateId." + i, "roomTemplate_"  + i);
            modelData.put("r.roomTemplateKey."+ i, "onRoomTemplate." + i);
            modelData.put("r.roomDelKey."     + i, "delRoom."        + i);

            inputHandlers.put("onRoomTemplate." + i,
                v -> room.addProperty("templateRef", v != null ? v.trim() : ""));
            handlers.put("delRoom." + i, () -> {
                if (idx < rooms.size()) {
                    rooms.remove(idx);
                    renderContext.clearInputsWithPrefix("room");
                    panelDirty = true;
                }
            });
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-rooms");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private static JsonArray getRooms(JsonObject cfg) {
        try { return cfg.getAsJsonArray("rooms"); }
        catch (Exception e) { JsonArray a = new JsonArray(); cfg.add("rooms", a); return a; }
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }
}
