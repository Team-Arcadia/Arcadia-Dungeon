package com.arcadia.dungeon.client.screen;

import com.google.gson.JsonArray;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-écran — archétypes du donjon (kits de départ).
 *
 * <p>Les items sont édités sous forme de liste CSV séparée par des virgules.
 */
public final class AdminDungeonArchetypesScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 360;
    private static final int MAX_H  = 270;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonArchetypesScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Archetypes — " + dungeonName));
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
        JsonArray archs = getArchetypes(cfg);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",  I18n.get("arcadia.admin.arch.title", dungeonName));
        modelData.put("arch.count", String.valueOf(archs.size()));
        modelData.put("s.items", AdminUiSuggestions.ITEMS);

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("addArch", () -> {
            JsonObject a = new JsonObject();
            a.addProperty("id",       "archetype_" + archs.size());
            a.addProperty("nameKey",  "");
            a.add("items", new JsonArray());
            archs.add(a);
            renderContext.clearInputsWithPrefix("arch");
            panelDirty = true;
        });

        // v-for="a in arch.count" → varName="a" → all per-row keys prefixed with "a."
        for (int i = 0; i < archs.size(); i++) {
            final int idx = i;
            JsonObject arch = archs.get(i).getAsJsonObject();
            String items = itemsCsv(arch);

            modelData.put("a.archId."      + i, strOr(arch, "id",      ""));
            modelData.put("a.archName."    + i, strOr(arch, "nameKey", ""));
            modelData.put("a.archItems."   + i, items);
            modelData.put("a.itemSuggestions." + i, AdminUiSuggestions.ITEMS);
            modelData.put("a.archIdId."    + i, "archId_"    + i);
            modelData.put("a.archNameId."  + i, "archName_"  + i);
            modelData.put("a.archItemsId." + i, "archItems_" + i);
            modelData.put("a.archIdKey."   + i, "onArchId."    + i);
            modelData.put("a.archNameKey." + i, "onArchName."  + i);
            modelData.put("a.archItemsKey."+ i, "onArchItems." + i);
            modelData.put("a.archDelKey."  + i, "delArch."     + i);

            inputHandlers.put("onArchId."    + i, v -> arch.addProperty("id",      v != null ? v.trim() : ""));
            inputHandlers.put("onArchName."  + i, v -> arch.addProperty("nameKey", v != null ? v.trim() : ""));
            inputHandlers.put("onArchItems." + i, v -> {
                JsonArray arr = new JsonArray();
                if (v != null && !v.isBlank()) {
                    Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(arr::add);
                }
                arch.add("items", arr);
            });
            handlers.put("delArch." + i, () -> {
                if (idx < archs.size()) {
                    archs.remove(idx);
                    renderContext.clearInputsWithPrefix("arch");
                    panelDirty = true;
                }
            });
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-archetypes");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private static JsonArray getArchetypes(JsonObject cfg) {
        try { return cfg.getAsJsonArray("archetypes"); }
        catch (Exception e) { JsonArray a = new JsonArray(); cfg.add("archetypes", a); return a; }
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

    private static String itemsCsv(JsonObject arch) {
        try {
            JsonArray arr = arch.getAsJsonArray("items");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arr.get(i).getAsString());
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
