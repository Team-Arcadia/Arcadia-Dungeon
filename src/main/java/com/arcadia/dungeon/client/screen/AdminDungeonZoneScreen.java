package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.CaptureSpawnPayload;
import com.arcadia.dungeon.network.GenerateDungeonTemplatePayload;
import com.arcadia.dungeon.network.RequestAreaWandPayload;
import com.arcadia.dungeon.network.SaveZonePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-écran — coordonnées spawn du donjon.
 *
 * <p>Deux modes de saisie :
 * <ol>
 *   <li><b>Capture</b> — envoie {@link CaptureSpawnPayload} au serveur qui lit les coords du joueur.</li>
 *   <li><b>Manuel</b> — champs X/Y/Z/Dimension éditables, envoie {@link SaveZonePayload}.</li>
 * </ol>
 */
public final class AdminDungeonZoneScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 420;
    private static final int MAX_H  = 330;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    // Valeurs de saisie manuelle (fallback sur DungeonEditClient au premier build)
    private double manualX, manualY, manualZ;
    private String manualDim = AdminUiSuggestions.DEFAULT_DIMENSION;
    private int originX, originY = 64, originZ;
    private String templateRef = "arcadia_dungeon:chateau_defaut";
    private String templateDim = AdminUiSuggestions.DEFAULT_DIMENSION;
    private String activeTab = "template";
    private boolean initialized = false;
    private int seenAreaVersion = -1;

    public AdminDungeonZoneScreen(String dungeonId, String dungeonName) {
        super(Component.translatable("arcadia.admin.zone.title", dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override
    protected void init() {
        super.init();
        if (!initialized) {
            manualX   = DungeonEditClient.spawnX();
            manualY   = DungeonEditClient.spawnY();
            manualZ   = DungeonEditClient.spawnZ();
            manualDim = DungeonEditClient.spawnDim().isBlank()
                ? AdminUiSuggestions.DEFAULT_DIMENSION : DungeonEditClient.spawnDim();
            loadTemplateFieldsFromConfig();
            syncTemplateInputStates();
            syncSpawnInputStates();
            initialized = true;
        }
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (seenAreaVersion != DungeonEditClient.areaVersion()) {
            seenAreaVersion = DungeonEditClient.areaVersion();
            loadTemplateFieldsFromConfig();
            syncTemplateInputStates();
            panelDirty = true;
        }
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
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        boolean spawnSet = DungeonEditClient.spawnSet();
        JsonObject cfg = DungeonEditClient.config();
        String mode = str(cfg, "generationMode", "custom");
        boolean templateMode = "template".equalsIgnoreCase(mode);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",         I18n.get("arcadia.admin.zone.title", dungeonName));
        modelData.put("tab.template",      String.valueOf("template".equals(activeTab)));
        modelData.put("tab.area",          String.valueOf("area".equals(activeTab)));
        modelData.put("tab.spawn",         String.valueOf("spawn".equals(activeTab)));
        modelData.put("tab.templateLabel", "template".equals(activeTab) ? "> Template" : "Template");
        modelData.put("tab.areaLabel",     "area".equals(activeTab) ? "> Zone" : "Zone");
        modelData.put("tab.spawnLabel",    "spawn".equals(activeTab) ? "> Spawn" : "Spawn");
        modelData.put("mode.templateClass", templateMode ? "zone-mode-btn zone-mode-active" : "zone-mode-btn");
        modelData.put("mode.customClass",   templateMode ? "zone-mode-btn" : "zone-mode-btn zone-mode-active");
        modelData.put("mode.templateLabel", templateMode ? "> Template NBT" : "Template NBT");
        modelData.put("mode.customLabel",   templateMode ? "Custom existant" : "> Custom existant");
        modelData.put("template.status",    templateStatus(cfg));
        modelData.put("template.ref",       templateRef);
        modelData.put("template.dim",       templateDim);
        modelData.put("template.originX",   String.valueOf(originX));
        modelData.put("template.originY",   String.valueOf(originY));
        modelData.put("template.originZ",   String.valueOf(originZ));
        modelData.put("s.structures",       AdminUiSuggestions.STRUCTURES);
        modelData.put("zone.statusKey",    spawnSet ? "arcadia.admin.zone.status.set" : "arcadia.admin.zone.status.unset");
        modelData.put("zone.status",       I18n.get(spawnSet ? "arcadia.admin.zone.status.set" : "arcadia.admin.zone.status.unset"));
        modelData.put("zone.statusClass",  spawnSet ? "status-ok" : "status-unset");
        modelData.put("area.status",       areaStatus());
        modelData.put("area.statusClass",  DungeonEditClient.areaSet()
            ? "status-ok" : (DungeonEditClient.areaSelecting() ? "status-warn" : "status-unset"));
        modelData.put("area.pos1",         DungeonEditClient.areaPos1Set() ? areaPos(true) : "Pos1: -");
        modelData.put("area.pos2",         DungeonEditClient.areaPos2Set() ? areaPos(false) : "Pos2: -");
        modelData.put("v.x",   fmt(manualX));
        modelData.put("v.y",   fmt(manualY));
        modelData.put("v.z",   fmt(manualZ));
        modelData.put("v.dim", manualDim);
        modelData.put("s.dimensions", AdminUiSuggestions.DIMENSIONS);

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back",    ArcadiaNavigator::back);
        handlers.put("tabTemplate", () -> setTab("template"));
        handlers.put("tabArea",     () -> setTab("area"));
        handlers.put("tabSpawn",    () -> setTab("spawn"));
        handlers.put("modeTemplate", () -> {
            cfg.addProperty("generationMode", "template");
            panelDirty = true;
        });
        handlers.put("modeCustom", () -> {
            cfg.addProperty("generationMode", "custom");
            panelDirty = true;
        });
        handlers.put("useOrigin", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                originX = (int) Math.floor(mc.player.getX());
                originY = (int) Math.floor(mc.player.getY());
                originZ = (int) Math.floor(mc.player.getZ());
                templateDim = mc.player.level().dimension().location().toString();
                setStr(cfg, "dimension", templateDim);
                setNullableInt(cfg, "placementY", String.valueOf(originY));
                syncTemplateInputStates();
                panelDirty = true;
            }
        });
        handlers.put("generateTemplate", () -> generateTemplate(false));
        handlers.put("resetTemplate", () -> generateTemplate(true));
        handlers.put("capture", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                manualX = mc.player.getX();
                manualY = mc.player.getY();
                manualZ = mc.player.getZ();
                manualDim = mc.player.level().dimension().location().toString();
                syncSpawnInputStates();
                panelDirty = true;
            }
            PacketDistributor.sendToServer(new CaptureSpawnPayload(dungeonId));
            // DungeonEditClient sera mis à jour côté serveur; pas de round-trip S2C pour l'instant
            // On ferme et revient : l'admin peut rouvrir pour voir les coords mises à jour
            AdminUiFeedback.saveZone();
        });
        handlers.put("save", () -> {
            PacketDistributor.sendToServer(new SaveZonePayload(dungeonId, manualX, manualY, manualZ, manualDim));
            AdminUiFeedback.saveZoneConfig(dungeonId);
        });
        handlers.put("areaWand", () -> PacketDistributor.sendToServer(new RequestAreaWandPayload(dungeonId)));

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onX",   v -> { try { manualX = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onY",   v -> { try { manualY = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onZ",   v -> { try { manualZ = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onDim", v -> { if (v != null && !v.isBlank()) manualDim = v.trim(); });
        inputHandlers.put("onTemplateRef", v -> { templateRef = clean(v, ""); setStr(cfg, "structureRef", templateRef); });
        inputHandlers.put("onTemplateDim", v -> { templateDim = clean(v, AdminUiSuggestions.DEFAULT_DIMENSION); setStr(cfg, "dimension", templateDim); });
        inputHandlers.put("onOriginX", v -> originX = intOr(v, originX));
        inputHandlers.put("onOriginY", v -> { originY = intOr(v, originY); setNullableInt(cfg, "placementY", String.valueOf(originY)); });
        inputHandlers.put("onOriginZ", v -> originZ = intOr(v, originZ));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-zone");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void setTab(String tab) {
        this.activeTab = tab;
        panelDirty = true;
    }

    private void loadTemplateFieldsFromConfig() {
        JsonObject cfg = DungeonEditClient.config();
        templateRef = str(cfg, "structureRef", templateRef);
        templateDim = str(cfg, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION);
        originY = intOr(str(cfg, "placementY", String.valueOf(originY)), originY);
        JsonObject origin = object(cfg, "generatedOrigin");
        if (origin != null) {
            originX = intOr(str(origin, "x", String.valueOf(originX)), originX);
            originY = intOr(str(origin, "y", String.valueOf(originY)), originY);
            originZ = intOr(str(origin, "z", String.valueOf(originZ)), originZ);
            templateDim = str(origin, "dimension", templateDim);
        }
    }

    private void syncTemplateInputStates() {
        setInputText("templateRef", templateRef);
        setInputText("templateDim", templateDim);
        setInputText("originX", String.valueOf(originX));
        setInputText("originY", String.valueOf(originY));
        setInputText("originZ", String.valueOf(originZ));
    }

    private void syncSpawnInputStates() {
        setInputText("zx", fmt(manualX));
        setInputText("zy", fmt(manualY));
        setInputText("zz", fmt(manualZ));
        setInputText("zdim", manualDim);
    }

    private void setInputText(String id, String value) {
        var state = renderContext.inputState(id);
        state.text = value == null ? "" : value;
        state.cursor = state.text.length();
        state.selStart = state.cursor;
        state.scrollX = 0;
    }

    private static String fmt(double v) {
        return v == 0.0 ? "" : String.format("%.2f", v).replaceAll("\\.?0+$", "");
    }

    private void generateTemplate(boolean reset) {
        JsonObject cfg = DungeonEditClient.config();
        cfg.addProperty("generationMode", "template");
        setStr(cfg, "structureRef", templateRef);
        setStr(cfg, "dimension", templateDim);
        setNullableInt(cfg, "placementY", String.valueOf(originY));
        PacketDistributor.sendToServer(new GenerateDungeonTemplatePayload(
            dungeonId,
            templateRef,
            templateDim,
            originX,
            originY,
            originZ,
            reset
        ));
        AdminUiFeedback.templateGenerationSent(reset);
    }

    private static String templateStatus(JsonObject cfg) {
        JsonObject origin = object(cfg, "generatedOrigin");
        JsonObject size = object(cfg, "generatedSize");
        if (origin == null || size == null) return "NBT non genere";
        return "Genere @ " + str(origin, "dimension", "?")
            + " " + str(origin, "x", "0") + "/" + str(origin, "y", "0") + "/" + str(origin, "z", "0")
            + " size " + str(size, "x", "0") + "x" + str(size, "y", "0") + "x" + str(size, "z", "0");
    }

    private static String areaStatus() {
        if (DungeonEditClient.areaSet()) {
            return "Zone definie dans " + DungeonEditClient.areaDim();
        }
        if (DungeonEditClient.areaSelecting()) {
            if (DungeonEditClient.areaPos1Set() && !DungeonEditClient.areaPos2Set()) return "Selection wand: Pos2 attendu";
            if (!DungeonEditClient.areaPos1Set() && DungeonEditClient.areaPos2Set()) return "Selection wand: Pos1 attendu";
            return "Selection wand en cours";
        }
        return "Zone globale non definie";
    }

    private static String areaPos(boolean first) {
        if (first) {
            return "Pos1: " + DungeonEditClient.areaX1() + " / " + DungeonEditClient.areaY1() + " / " + DungeonEditClient.areaZ1();
        }
        return "Pos2: " + DungeonEditClient.areaX2() + " / " + DungeonEditClient.areaY2() + " / " + DungeonEditClient.areaZ2();
    }

    private static String str(JsonObject o, String key, String def) {
        try { return o.get(key).getAsString(); } catch (Exception e) { return def; }
    }

    private static JsonObject object(JsonObject o, String key) {
        try { return o.getAsJsonObject(key); } catch (Exception e) { return null; }
    }

    private static int intOr(String value, int def) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return def; }
    }

    private static String clean(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim();
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
}
