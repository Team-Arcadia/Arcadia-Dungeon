package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.RequestDungeonEditPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hub de navigation vers les 9 sous-écrans de configuration d'un donjon.
 *
 * <p>Demande la config au serveur à l'ouverture via {@link RequestDungeonEditPayload}.
 * Les sous-écrans lisent et modifient {@link DungeonEditClient} directement.
 */
public final class AdminDungeonConfigScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 320;
    private static final int MAX_H  = 260;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private boolean panelDirty = true;

    public AdminDungeonConfigScreen(String dungeonId, String dungeonName) {
        super(Component.translatable("arcadia.admin.config.title", dungeonName));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
    }

    @Override
    protected void init() {
        super.init();
        // Demander la config complète au serveur (peuple DungeonEditClient)
        PacketDistributor.sendToServer(new RequestDungeonEditPayload(dungeonId));
        panelDirty = true;
    }

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
        int panelW = Math.max(220, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        String title = I18n.get("arcadia.admin.config.title", dungeonName);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", title);

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back",       ArcadiaNavigator::back);
        handlers.put("close",      ArcadiaNavigator::closeAll);
        handlers.put("core",       () -> ArcadiaNavigator.push(new AdminDungeonCoreScreen(dungeonId, dungeonName)));
        handlers.put("boss",       () -> ArcadiaNavigator.push(new AdminDungeonBossScreen(dungeonId, dungeonName)));
        handlers.put("waves",      () -> ArcadiaNavigator.push(new AdminDungeonWavesScreen(dungeonId, dungeonName)));
        handlers.put("rewards",    () -> ArcadiaNavigator.push(new AdminDungeonRewardsScreen(dungeonId, dungeonName)));
        handlers.put("messages",   () -> ArcadiaNavigator.push(new AdminDungeonMessagesScreen(dungeonId, dungeonName)));
        handlers.put("zone",       () -> ArcadiaNavigator.push(new AdminDungeonZoneScreen(dungeonId, dungeonName)));
        handlers.put("arcadia",    () -> ArcadiaNavigator.push(new AdminDungeonArcadiaScreen(dungeonId, dungeonName)));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-config");
        panel = TesseraTemplateRenderer.build(template, model, handlers,
            new HashMap<>(), new HashMap<>(), px, py, panelW, panelH);
    }
}
