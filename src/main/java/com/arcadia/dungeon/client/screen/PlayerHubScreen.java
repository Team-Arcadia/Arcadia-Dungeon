package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran hub joueur — liste des donjons disponibles (Story S6.2).
 *
 * <p>Envoie {@link RequestDungeonListPayload} à l'init, se rafraîchit quand
 * {@link DungeonListClient} est mis à jour (détection changement count en render).
 */
public final class PlayerHubScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;

    private TesseraPanel panel;
    private int lastKnownDungeonCount = -1;
    private boolean panelDirty = true;

    public PlayerHubScreen() {
        super(Component.literal("Arcadia — Donjons"));
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        panelDirty = true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        List<DungeonListPayload.DungeonSummary> current = DungeonListClient.get();
        if (current.size() != lastKnownDungeonCount) {
            lastKnownDungeonCount = current.size();
            panelDirty = true;
        }
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        renderBackground(g, mx, my, partialTick);
        if (panel != null) panel.render(g, mx, my);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        List<DungeonListPayload.DungeonSummary> dungeons = DungeonListClient.get();

        // ArcaForEach avec v-for="d in dungeon.count" résout les clés comme :
        //   model.resolve("d." + k + "." + idx)
        // Donc les clés du modèle doivent être "d.name.0", "d.id.0", "d.onclick.0", etc.
        Map<String, String> modelData = new HashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        modelData.put("dungeon.count", String.valueOf(dungeons.size()));
        modelData.put("hint", dungeons.isEmpty() ? "Chargement..." : "");

        for (int i = 0; i < dungeons.size(); i++) {
            DungeonListPayload.DungeonSummary d = dungeons.get(i);
            String handlerKey = "dungeon.select." + i;
            modelData.put("d.name." + i,   net.minecraft.network.chat.Component.translatable(d.name()).getString());
            modelData.put("d.id." + i,     d.id());
            modelData.put("d.onclick." + i, handlerKey);
            handlers.put(handlerKey, () -> onSelectDungeon(d));
        }
        handlers.put("close", this::onClose);

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/player-hub");
        panel = TesseraTemplateRenderer.build(template, model, handlers, px, py, PANEL_W, PANEL_H);
    }

    private void onSelectDungeon(DungeonListPayload.DungeonSummary dungeon) {
        Minecraft.getInstance().setScreen(
            new ArchetypeSelectionScreen(dungeon.id(), dungeon.name(), dungeon.archetypes()));
    }
}
