package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.tesseraui.TesseraScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
public final class ArchetypeSelectionScreen extends TesseraScreen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 220;

    private final String dungeonId;
    private final String dungeonName;
    private final List<DungeonListPayload.ArchetypeSummary> archetypes;

    private TesseraPanel panel;

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

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

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
            modelData.put("a.name." + i,    displayArchName);
            modelData.put("a.id." + i,      a.id());
            modelData.put("a.onclick." + i,  handlerKey);
            modelData.put("a.icon." + i,     archetypeIcon(a.id()));
            modelData.put("a.sub." + i,      ""); // Sous-titre non disponible dans le payload
            handlers.put(handlerKey, () -> onSelectArchetype(a, displayArchName));
        }
        handlers.put("back", () -> Minecraft.getInstance().setScreen(new PlayerHubScreen()));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/archetype-selection");
        panel = TesseraTemplateRenderer.build(template, model, handlers, px, py, PANEL_W, PANEL_H);
    }

    /** Retourne une icône par défaut selon l'id d'archétype (clé ou suffixe). */
    private static String archetypeIcon(String id) {
        if (id == null) return "?";
        String lower = id.toLowerCase();
        if (lower.contains("warrior") || lower.contains("guerrier")) return "⚔";
        if (lower.contains("mage")    || lower.contains("mago"))     return "✦";
        if (lower.contains("archer")  || lower.contains("rogue"))    return "➶";
        return "?";
    }

    private void onSelectArchetype(DungeonListPayload.ArchetypeSummary archetype, String displayName) {
        Minecraft.getInstance().setScreen(
            new DungeonLobbyScreen(dungeonId, dungeonName, archetype.id(), displayName));
    }
}
