package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Sous-ecran boss: liste des boss du donjon.
 */
public final class AdminDungeonBossScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 520;
    private static final int MAX_H  = 300;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonBossScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Boss - " + dungeonName));
        this.dungeonId = dungeonId;
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
        int panelW = Math.max(280, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(190, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();
        JsonArray bosses = getBosses(cfg);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.boss.title", dungeonName));
        modelData.put("boss.count", String.valueOf(bosses.size()));

        Map<String, Runnable> handlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            syncBosses(cfg, bosses);
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("addBoss", () -> {
            bosses.add(newBoss(bosses.size()));
            syncBosses(cfg, bosses);
            ArcadiaNavigator.push(new AdminDungeonBossDetailScreen(dungeonId, dungeonName, bosses.size() - 1));
        });

        for (int i = 0; i < bosses.size(); i++) {
            final int idx = i;
            JsonObject boss = bosses.get(i).getAsJsonObject();
            modelData.put("b.bossIndex." + i, String.valueOf(i + 1));
            modelData.put("b.bossName." + i, strOr(boss, "id", "boss_" + (i + 1)));
            modelData.put("b.manageKey." + i, "manageBoss." + i);

            handlers.put("manageBoss." + i, () ->
                ArcadiaNavigator.push(new AdminDungeonBossDetailScreen(dungeonId, dungeonName, idx)));
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-boss");
        panel = TesseraTemplateRenderer.build(template, model, handlers, Map.of(), renderContext, px, py, panelW, panelH);
    }

    private static JsonArray getBosses(JsonObject cfg) {
        JsonArray bosses;
        try {
            bosses = cfg.getAsJsonArray("bosses");
            if (bosses != null && bosses.size() > 0) return bosses;
        } catch (Exception ignored) {}

        bosses = new JsonArray();
        if (bosses.size() == 0) bosses.add(newBoss(0));
        cfg.add("bosses", bosses);
        cfg.remove("boss");
        return bosses;
    }

    private static JsonObject newBoss(int index) {
        JsonObject boss = new JsonObject();
        boss.addProperty("id", "boss_" + (index + 1));
        boss.addProperty("type", "minecraft:wither_skeleton");
        boss.addProperty("hp", 100);
        boss.add("phases", new JsonArray());
        boss.addProperty("optional", false);
        boss.addProperty("spawnChance", 1.0);
        boss.addProperty("requiredKill", true);
        boss.add("rewards", new JsonArray());
        return boss;
    }

    private static void syncBosses(JsonObject cfg, JsonArray bosses) {
        cfg.add("bosses", bosses);
        cfg.remove("boss");
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

}
