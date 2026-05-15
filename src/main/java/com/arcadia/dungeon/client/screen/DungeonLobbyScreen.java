package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.RunStateClient;
import com.arcadia.dungeon.network.RunStatePayload;
import com.arcadia.dungeon.network.StartRunPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.tesseraui.TesseraToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

/**
 * Pre-run lobby screen. First launch click creates or joins a STARTING lobby;
 * the leader can click again to start the run once the group is ready.
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
    private int renderedPlayerCount = -1;

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
        RunStatePayload state = RunStateClient.getState().orElse(null);
        if (launching) {
            if (state != null && "IN_PROGRESS".equals(state.phase())) {
                onClose();
                return;
            } else if (state != null && "STARTING".equals(state.phase())) {
                launching = false;
                rebuildPanel();
            }
        } else if (state != null && "STARTING".equals(state.phase())
            && state.playerNames().size() != renderedPlayerCount) {
            rebuildPanel();
        }
        super.render(g, mx, my, partialTick);
        if (panel != null) panel.render(g, mx, my);
        renderTesseraOverlays(g, mx, my);
        TesseraToast.render(g, width, height);
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
    protected TesseraPanel tesseraRoot() {
        return panel;
    }

    private void rebuildPanel() {
        int panelW = Math.max(1, Math.min(PANEL_W, width - 16));
        int panelH = Math.max(1, Math.min(PANEL_H, height - 16));
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;

        String playerName = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getGameProfile().getName() : "???";
        RunStatePayload state = RunStateClient.getState().orElse(null);
        List<String> playerNames = state != null ? state.playerNames() : List.of(playerName);
        renderedPlayerCount = playerNames.size();
        String leaderName = !playerNames.isEmpty() ? playerNames.getFirst() : playerName;
        String secondPlayerName = playerNames.size() > 1 ? playerNames.get(1)
            : Component.translatable("arcadia.client.lobby.slot_open").getString();
        String secondPlayerSub = playerNames.size() > 1 ? Component.translatable("arcadia.client.ready").getString()
            : Component.translatable("arcadia.client.lobby.waiting_player").getString();

        String displayName = dungeonName.length() > 40
            ? dungeonName.substring(0, 37) + "..." : dungeonName;

        TesseraModel model = TesseraModel.of(Map.of(
            "dungeon.name", displayName,
            "player.name", leaderName,
            "player.two.name", secondPlayerName,
            "player.two.sub", secondPlayerSub,
            "archetype.name", archetypeName,
            "launch.status", launching ? "Demarrage..." : ""
        ));

        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/client/dungeon-lobby");
        panel = TesseraTemplateRenderer.build(template, model, Map.of(
            "launch", this::onLaunch,
            "close", this::onClose
        ), px, py, panelW, panelH);
    }

    private void onLaunch() {
        RunStatePayload state = RunStateClient.getState().orElse(null);
        if (launching && (state == null || !"STARTING".equals(state.phase()))) return;
        PacketDistributor.sendToServer(new StartRunPayload(dungeonId, archetypeId));
        launching = true;
        rebuildPanel();
    }
}
