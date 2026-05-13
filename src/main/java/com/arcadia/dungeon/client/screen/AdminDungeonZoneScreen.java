package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.CaptureSpawnPayload;
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
    private static final int MAX_W  = 310;
    private static final int MAX_H  = 270;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;

    // Valeurs de saisie manuelle (fallback sur DungeonEditClient au premier build)
    private double manualX, manualY, manualZ;
    private String manualDim = AdminUiSuggestions.DEFAULT_DIMENSION;
    private boolean initialized = false;
    private int seenAreaVersion = -1;

    public AdminDungeonZoneScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Zone — " + dungeonName));
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
            initialized = true;
        }
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (seenAreaVersion != DungeonEditClient.areaVersion()) {
            seenAreaVersion = DungeonEditClient.areaVersion();
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

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",         I18n.get("arcadia.admin.zone.title", dungeonName));
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
        handlers.put("capture", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                manualX = mc.player.getX();
                manualY = mc.player.getY();
                manualZ = mc.player.getZ();
                manualDim = mc.player.level().dimension().location().toString();
                panelDirty = true;
            }
            PacketDistributor.sendToServer(new CaptureSpawnPayload(dungeonId));
            // DungeonEditClient sera mis à jour côté serveur; pas de round-trip S2C pour l'instant
            // On ferme et revient : l'admin peut rouvrir pour voir les coords mises à jour
            AdminUiFeedback.saveZone();
        });
        handlers.put("save", () -> {
            PacketDistributor.sendToServer(new SaveZonePayload(dungeonId, manualX, manualY, manualZ, manualDim));
            AdminUiFeedback.saveZone();
        });
        handlers.put("areaWand", () -> PacketDistributor.sendToServer(new RequestAreaWandPayload(dungeonId)));

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onX",   v -> { try { manualX = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onY",   v -> { try { manualY = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onZ",   v -> { try { manualZ = Double.parseDouble(v.trim()); } catch (Exception ignored) {} });
        inputHandlers.put("onDim", v -> { if (v != null && !v.isBlank()) manualDim = v.trim(); });

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-zone");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private static String fmt(double v) {
        return v == 0.0 ? "" : String.format("%.2f", v).replaceAll("\\.?0+$", "");
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
}
