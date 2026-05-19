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
 * Sous-écran — récompenses du donjon (currency + table de loot).
 */
public final class AdminDungeonRewardsScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 430;
    private static final int MAX_H  = 270;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonRewardsScreen(String dungeonId, String dungeonName) {
        super(Component.translatable("arcadia.admin.rewards.title", dungeonName));
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
        JsonObject rewards = getRewards(cfg);
        JsonArray loot     = getLoot(rewards);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",   I18n.get("arcadia.admin.rewards.title", dungeonName));
        modelData.put("v.currency",  longOr(rewards, "currency", 0));
        modelData.put("loot.count",  String.valueOf(loot.size()));
        modelData.put("s.items", AdminUiSuggestions.ITEMS);

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("addLoot", () -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", "minecraft:diamond");
            entry.addProperty("min", 1);
            entry.addProperty("max", 1);
            entry.addProperty("chance", 1.0);
            loot.add(entry);
            rewards.add("loot", loot);
            renderContext.clearInputsWithPrefix("loot");
            panelDirty = true;
        });

        inputHandlers.put("onCurrency", v -> {
            try { rewards.addProperty("currency", Long.parseLong(v.trim())); } catch (Exception ignored) {}
        });

        // v-for="l in loot.count" → varName="l" → all per-row keys prefixed with "l."
        for (int i = 0; i < loot.size(); i++) {
            final int idx = i;
            JsonObject entry = loot.get(i).getAsJsonObject();

            modelData.put("l.lootIndex."  + i, String.valueOf(i + 1));
            modelData.put("l.lootItem."   + i, strOr(entry, "item", ""));
            modelData.put("l.lootMin."    + i, intOr(entry, "min", 1));
            modelData.put("l.lootMax."    + i, intOr(entry, "max", 1));
            modelData.put("l.lootChance." + i, doubleOr(entry, "chance", 1.0));
            modelData.put("l.itemSuggestions." + i, AdminUiSuggestions.ITEMS);
            modelData.put("l.lootItemId." + i, "lootItem_"  + i);
            modelData.put("l.lootMinId."  + i, "lootMin_"   + i);
            modelData.put("l.lootMaxId."  + i, "lootMax_"   + i);
            modelData.put("l.lootChanceId." + i, "lootChance_" + i);
            modelData.put("l.lootItemKey."+ i, "onLootItem." + i);
            modelData.put("l.lootMinKey." + i, "onLootMin."  + i);
            modelData.put("l.lootMaxKey." + i, "onLootMax."  + i);
            modelData.put("l.lootChanceKey." + i, "onLootChance." + i);
            modelData.put("l.lootDelKey." + i, "delLoot."    + i);

            inputHandlers.put("onLootItem." + i, v -> entry.addProperty("item", v != null ? v : ""));
            inputHandlers.put("onLootMin."  + i, v -> { try { entry.addProperty("min", Integer.parseInt(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onLootMax."  + i, v -> { try { entry.addProperty("max", Integer.parseInt(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onLootChance." + i, v -> { try { entry.addProperty("chance", clamp01(Double.parseDouble(v.trim()))); } catch (Exception ignored) {} });
            handlers.put("delLoot." + i, () -> {
                if (idx < loot.size()) {
                    loot.remove(idx);
                    rewards.add("loot", loot);
                    renderContext.clearInputsWithPrefix("loot");
                    panelDirty = true;
                }
            });
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-rewards");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private static JsonObject getRewards(JsonObject cfg) {
        try { return cfg.getAsJsonObject("rewards"); }
        catch (Exception e) { JsonObject o = new JsonObject(); cfg.add("rewards", o); return o; }
    }

    private static JsonArray getLoot(JsonObject rewards) {
        try { return rewards.getAsJsonArray("loot"); }
        catch (Exception e) { JsonArray a = new JsonArray(); rewards.add("loot", a); return a; }
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

    private static String intOr(JsonObject o, String k, int def) {
        try { return String.valueOf(o.get(k).getAsInt()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static String longOr(JsonObject o, String k, long def) {
        try { return String.valueOf(o.get(k).getAsLong()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static String doubleOr(JsonObject o, String k, double def) {
        try {
            double value = o.get(k).getAsDouble();
            if (value == Math.floor(value)) return String.valueOf((long) value);
            return String.valueOf(value);
        } catch (Exception e) {
            return String.valueOf(def);
        }
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
