package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraRenderContext;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Wave list screen. Detailed editing lives in {@link AdminDungeonWaveDetailScreen}.
 */
public final class AdminDungeonWavesScreen extends TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 520;
    private static final int MAX_H = 300;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final TesseraRenderContext renderContext = new TesseraRenderContext();
    private boolean panelDirty = true;

    public AdminDungeonWavesScreen(String dungeonId, String dungeonName) {
        super(Component.translatable("arcadia.admin.waves.screen.title", dungeonName));
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
        JsonArray waves = getGlobalWaves(cfg);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.waves.screen.title", dungeonName));
        modelData.put("waves.count", String.valueOf(waves.size()));

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            syncWaves(cfg, waves);
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("addWave", () -> {
            waves.add(newWave(waves.size()));
            syncWaves(cfg, waves);
            ArcadiaNavigator.push(new AdminDungeonWaveDetailScreen(dungeonId, dungeonName, waves.size() - 1));
        });

        for (int i = 0; i < waves.size(); i++) {
            final int idx = i;
            JsonObject wave = waves.get(i).getAsJsonObject();
            JsonObject mob = firstMob(wave);
            modelData.put("w.waveIndex." + i, String.valueOf(i + 1));
            modelData.put("w.waveName." + i, strOr(wave, "name", I18n.get("arcadia.admin.wave.default_name", i + 1)));
            modelData.put("w.waveSummary." + i, summary(wave, mob));
            modelData.put("w.manageKey." + i, "manageWave." + i);
            handlers.put("manageWave." + i, () ->
                ArcadiaNavigator.push(new AdminDungeonWaveDetailScreen(dungeonId, dungeonName, idx)));
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-waves");
        panel = TesseraTemplateRenderer.build(template, model, handlers, Map.of(), renderContext, px, py, panelW, panelH);
    }

    static JsonArray getGlobalWaves(JsonObject cfg) {
        JsonArray waves;
        try { waves = cfg.getAsJsonArray("waves"); }
        catch (Exception ignored) { waves = null; }
        if (waves == null) {
            waves = new JsonArray();
            cfg.add("waves", waves);
        }
        return waves;
    }

    static void syncWaves(JsonObject cfg, JsonArray waves) {
        cfg.add("waves", waves);
    }

    static JsonObject newWave(int index) {
        JsonObject wave = new JsonObject();
        wave.addProperty("name", I18n.get("arcadia.admin.wave.default_name", index + 1));
        wave.addProperty("triggerMode", "ordered");
        wave.addProperty("delayTicks", 20);
        wave.addProperty("startMessage", "");
        wave.addProperty("glowingAfterDelay", true);
        wave.addProperty("glowingDelaySeconds", 60);
        JsonArray mobs = new JsonArray();
        mobs.add(newMob());
        wave.add("mobs", mobs);
        return wave;
    }

    static JsonObject firstMob(JsonObject wave) {
        JsonArray mobs;
        try { mobs = wave.getAsJsonArray("mobs"); }
        catch (Exception ignored) { mobs = null; }
        if (mobs == null) {
            mobs = new JsonArray();
            wave.add("mobs", mobs);
        }
        if (mobs.size() == 0) mobs.add(newMob());
        return mobs.get(0).getAsJsonObject();
    }

    static JsonObject newMob() {
        JsonObject mob = new JsonObject();
        mob.addProperty("mobType", "minecraft:zombie");
        mob.addProperty("count", 1);
        JsonObject spawn = new JsonObject();
        spawn.addProperty("dimension", AdminUiSuggestions.DEFAULT_DIMENSION);
        spawn.addProperty("x", 0.0);
        spawn.addProperty("y", 64.0);
        spawn.addProperty("z", 0.0);
        mob.add("spawnPoint", spawn);
        return mob;
    }

    private static String summary(JsonObject wave, JsonObject mob) {
        String trigger = "ticks".equalsIgnoreCase(strOr(wave, "triggerMode", "ordered"))
            ? intOr(wave, "delayTicks", 20) + " ticks"
            : I18n.get("arcadia.admin.wave.trigger.ordered");
        return strOr(mob, "mobType", "minecraft:zombie")
            + " x" + intOr(mob, "count", 1)
            + " / " + trigger;
    }

    static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

    static int intOr(JsonObject o, String k, int def) {
        try { return o.get(k).getAsInt(); } catch (Exception e) { return def; }
    }
}
