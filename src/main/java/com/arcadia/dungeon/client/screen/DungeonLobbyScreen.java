package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.network.StartRunPayload;
import com.tesseraui.TesseraScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/**
 * Screen lobby pré-run (Story S6.1 — pilote).
 *
 * <p>Affiché après sélection d'un archétype. L'owner clique "Lancer !" pour
 * envoyer {@link StartRunPayload} et démarrer la run. Solo MVP uniquement.
 */
public final class DungeonLobbyScreen extends TesseraScreen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 260;

    private final String dungeonId;
    private final String dungeonName;
    private final String archetypeId;
    private final String archetypeName;

    private TesseraPanel panel;
    private boolean launching = false;

    public DungeonLobbyScreen(String dungeonId, String dungeonName,
                              String archetypeId, String archetypeName) {
        super(Component.translatable("arcadia.client.lobby.screen.title", dungeonName));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
        this.archetypeId = archetypeId;
        this.archetypeName = archetypeName;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        // Si en attente de réponse serveur, fermer quand la run passe IN_PROGRESS
        if (launching) {
            var state = com.arcadia.dungeon.client.state.RunStateClient.getState().orElse(null);
            if (state != null && "IN_PROGRESS".equals(state.phase())) {
                onClose();
                return;
            }
        }
        super.render(g, mx, my, partialTick);
        if (panel != null) panel.render(g, mx, my);
        renderTesseraOverlays(g, mx, my);
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

        String playerName = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getGameProfile().getName() : "???";

        // Tronquer le nom de donjon si trop long (AC5 — test texte long)
        String displayName = dungeonName.length() > 40
            ? dungeonName.substring(0, 37) + "…" : dungeonName;

        TesseraModel model = TesseraModel.of(Map.of(
            "dungeon.name",   displayName,
            "player.name",    playerName,
            "archetype.name", archetypeName,
            "launch.status",  launching ? "Démarrage..." : ""
        ));

        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/client/dungeon-lobby");
        panel = TesseraTemplateRenderer.build(template, model, Map.of(
            "launch", this::onLaunch,
            "close",  this::onClose
        ), px, py, PANEL_W, PANEL_H);
    }

    private void onLaunch() {
        if (launching) return; // Éviter double-clic
        PacketDistributor.sendToServer(new StartRunPayload(dungeonId, archetypeId));
        launching = true;
        rebuildPanel(); // Affiche "Démarrage..." et désactive le bouton
    }
}
