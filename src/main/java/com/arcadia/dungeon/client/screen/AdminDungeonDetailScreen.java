package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonDetailClient;
import com.arcadia.dungeon.network.DungeonDetailPayload;
import com.arcadia.dungeon.network.RequestDungeonDetailPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Écran admin — détail et lecture d'un donjon (Story 8.4).
 *
 * <p>Affiche les infos du donjon en quatre onglets : Core / Boss / Rooms / Rewards.
 * Les données arrivent via {@link DungeonDetailPayload} (S2C) et sont cachées
 * dans {@link DungeonDetailClient}.
 *
 * <p>Navigation via {@link ArcadiaNavigator#back()} pour retourner au hub.
 */
public final class AdminDungeonDetailScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 420;
    private static final int MAX_H  = 270;

    private final String dungeonId;

    private TesseraPanel panel;
    private DungeonDetailPayload lastData = null;
    private boolean panelDirty = true;

    public AdminDungeonDetailScreen(String dungeonId) {
        super(Component.literal("Admin — Détail donjon"));
        this.dungeonId = dungeonId;
    }

    @Override
    protected void init() {
        super.init();
        DungeonDetailClient.clear();
        PacketDistributor.sendToServer(new RequestDungeonDetailPayload(dungeonId));
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        DungeonDetailPayload current = DungeonDetailClient.get().orElse(null);
        if (current != lastData) {
            lastData = current;
            panelDirty = true;
        }
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
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int panelW = Math.max(260, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        Map<String, String> modelData = new HashMap<>();

        if (lastData == null) {
            // En attente de la réponse serveur
            modelData.put("loading",       "true");
            modelData.put("dungeon.id",    dungeonId);
            modelData.put("dungeon.name",  "…");
            modelData.put("dungeon.schema","—");
            modelData.put("dungeon.lives", "—");
            modelData.put("dungeon.struct","—");
            modelData.put("dungeon.dim",   "—");
            modelData.put("boss.type",     "—");
            modelData.put("boss.hp",       "—");
            modelData.put("boss.phases",   "—");
            modelData.put("boss.count",    "—");
            modelData.put("rooms.count",   "—");
            modelData.put("rooms.waves",   "—");
            modelData.put("rewards.cur",   "—");
            modelData.put("rewards.loot",  "—");
            modelData.put("archetypes",    "—");
        } else {
            modelData.put("loading",       "");
            modelData.put("dungeon.id",    lastData.id());
            modelData.put("dungeon.name",  lastData.name());
            modelData.put("dungeon.schema","v" + lastData.schemaVersion());
            modelData.put("dungeon.lives", String.valueOf(lastData.lives()));
            modelData.put("dungeon.struct",lastData.structureRef());
            modelData.put("dungeon.dim",   lastData.dimension());
            modelData.put("boss.type",     lastData.bossType());
            modelData.put("boss.hp",       String.valueOf(lastData.bossHp()) + " HP");
            modelData.put("boss.phases",   String.valueOf(lastData.phaseCount()) + " phase(s)");
            modelData.put("boss.count",    String.valueOf(lastData.bossCount()) + " boss");
            modelData.put("rooms.count",   String.valueOf(lastData.roomCount()) + " salle(s)");
            modelData.put("rooms.waves",   String.valueOf(lastData.totalWaveCount()) + " vague(s) au total");
            modelData.put("rewards.cur",   String.valueOf(lastData.rewardCurrency()) + " Arcadia");
            modelData.put("rewards.loot",  String.valueOf(lastData.lootCount()) + " entrée(s) loot");
            modelData.put("archetypes",    String.valueOf(lastData.archetypeCount()) + " archétype(s)");
        }

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back",    ArcadiaNavigator::back);
        handlers.put("refresh", () -> {
            DungeonDetailClient.clear();
            PacketDistributor.sendToServer(new RequestDungeonDetailPayload(dungeonId));
        });
        handlers.put("close",   ArcadiaNavigator::closeAll);

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-detail");
        panel = TesseraTemplateRenderer.build(template, model, handlers, new HashMap<>(), px, py, panelW, panelH);
    }
}
