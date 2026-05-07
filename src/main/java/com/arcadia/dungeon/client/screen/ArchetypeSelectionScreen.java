package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.arcadiaui.ArcaModel;
import com.arcadia.dungeon.client.arcadiaui.ArcaPanel;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplate;
import com.arcadia.dungeon.client.arcadiaui.ArcaTemplateRenderer;
import com.arcadia.dungeon.network.DungeonListPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran sélection d'archétype pour un donjon (Story S6.2).
 *
 * <p>Affiché après le {@link PlayerHubScreen}. L'archétype choisi ouvre
 * {@link DungeonLobbyScreen}.
 */
public final class ArchetypeSelectionScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 196;

    private final String dungeonId;
    private final String dungeonName;
    private final List<DungeonListPayload.ArchetypeSummary> archetypes;

    private ArcaPanel panel;

    public ArchetypeSelectionScreen(String dungeonId, String dungeonName,
                                    List<DungeonListPayload.ArchetypeSummary> archetypes) {
        super(Component.literal("Archétype — " + dungeonName));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
        this.archetypes = archetypes;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
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

        // Tronquer le nom du donjon pour le header (AC5 texte long)
        String displayName = dungeonName.length() > 38
            ? dungeonName.substring(0, 35) + "…" : dungeonName;

        // ArcaForEach v-for="a in archetype.count" → clés "a.name.i", "a.id.i", "a.onclick.i"
        Map<String, String> modelData = new HashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        modelData.put("dungeon.name",     displayName);
        modelData.put("archetype.count",  String.valueOf(archetypes.size()));

        for (int i = 0; i < archetypes.size(); i++) {
            DungeonListPayload.ArchetypeSummary a = archetypes.get(i);
            // Résolution nameKey → traduit si dispo, sinon clé brute
            String displayArchName = Component.translatable(a.nameKey()).getString();
            String handlerKey = "archetype.select." + i;
            modelData.put("a.name." + i,   displayArchName);
            modelData.put("a.id." + i,     a.id());
            modelData.put("a.onclick." + i, handlerKey);
            final int idx = i;
            handlers.put(handlerKey, () -> onSelectArchetype(archetypes.get(idx), displayArchName));
        }
        handlers.put("back", () -> Minecraft.getInstance().setScreen(new PlayerHubScreen()));

        ArcaModel model = key -> modelData.getOrDefault(key, null);
        ArcaTemplate template = ArcaTemplate.load("arcadia_dungeon:ui/archetype-selection");
        panel = ArcaTemplateRenderer.build(template, model, handlers, px, py, PANEL_W, PANEL_H);
    }

    private void onSelectArchetype(DungeonListPayload.ArchetypeSummary archetype, String displayName) {
        Minecraft.getInstance().setScreen(
            new DungeonLobbyScreen(dungeonId, dungeonName, archetype.id(), displayName));
    }
}
