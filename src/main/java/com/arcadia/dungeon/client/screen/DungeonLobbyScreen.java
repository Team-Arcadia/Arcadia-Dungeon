package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.RunStateClient;
import com.arcadia.dungeon.client.state.StructurePlacementClient;
import com.arcadia.dungeon.network.RunStatePayload;
import com.arcadia.dungeon.network.StartRunPayload;
import com.arcadia.dungeon.network.StructurePlacementStatusPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.tesseraui.TesseraToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-run lobby screen. First launch click creates or joins a STARTING lobby;
 * the leader can click again to start the run once the group is ready.
 */
public final class DungeonLobbyScreen extends TesseraScreen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;

    private final String dungeonId;
    private final String dungeonName;
    private final String archetypeId;
    private final String archetypeName;
    private final int minPlayers;
    private final int maxPlayers;

    private TesseraPanel panel;
    private boolean launching = false;
    private int renderedPlayerCount = -1;
    private int renderedCountdownSeconds = -1;
    private boolean renderedCountdownVisible = false;
    private String renderedGenerationKey = "";

    public DungeonLobbyScreen(String dungeonId, String dungeonName,
                              String archetypeId, String archetypeName) {
        this(dungeonId, dungeonName, archetypeId, archetypeName,
            com.arcadia.dungeon.domain.config.DungeonConfig.DEFAULT_MIN_PLAYERS,
            com.arcadia.dungeon.domain.config.DungeonConfig.DEFAULT_MAX_PLAYERS);
    }

    public DungeonLobbyScreen(String dungeonId, String dungeonName,
                              String archetypeId, String archetypeName,
                              int minPlayers, int maxPlayers) {
        super(Component.translatable("arcadia.client.lobby.screen.title", dungeonName));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
        this.archetypeId = archetypeId;
        this.archetypeName = archetypeName;
        this.maxPlayers = Math.max(1, Math.min(8, maxPlayers));
        this.minPlayers = Math.max(1, Math.min(this.maxPlayers, minPlayers));
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        RunStatePayload state = currentLobbyState();
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
        int countdownSeconds = countdownSeconds(state);
        boolean countdownVisible = countdownVisible(state);
        String generationKey = generationKey();
        if (countdownSeconds != renderedCountdownSeconds
            || countdownVisible != renderedCountdownVisible
            || !generationKey.equals(renderedGenerationKey)) {
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
        RunStatePayload state = currentLobbyState();
        List<String> playerNames = state != null ? state.playerNames() : List.of(playerName);
        renderedPlayerCount = playerNames.size();
        renderedCountdownSeconds = countdownSeconds(state);
        renderedCountdownVisible = countdownVisible(state);
        renderedGenerationKey = generationKey();
        StructurePlacementStatusPayload generation = StructurePlacementClient.get(dungeonId).orElse(null);

        String displayName = dungeonName.length() > 40
            ? dungeonName.substring(0, 37) + "..." : dungeonName;
        boolean waitingForLaunch = renderedCountdownVisible;
        String lobbyState = waitingForLaunch
            ? I18n.get("arcadia.client.lobby.state.countdown")
            : I18n.get("arcadia.client.ready");
        String launchLabel = waitingForLaunch
            ? I18n.get("arcadia.client.lobby.launching")
            : I18n.get("arcadia.client.launch");
        String launchStatus = waitingForLaunch
            ? renderedCountdownSeconds > 0
                ? I18n.get("arcadia.client.lobby.status.starting")
                : I18n.get("arcadia.client.lobby.status.generating")
            : launching ? I18n.get("arcadia.client.lobby.status.creating") : "";

        Map<String, String> modelData = new HashMap<>();
        modelData.put("dungeon.name", displayName);
        modelData.put("lobby.state", lobbyState);
        modelData.put("archetype.name", archetypeName);
        modelData.put("lobby.players", I18n.get("arcadia.client.lobby.players", playerNames.size(), minPlayers, maxPlayers));
        modelData.put("launch.status", launchStatus);
        modelData.put("launch.label", launchLabel);
        modelData.put("countdown.visible", String.valueOf(waitingForLaunch));
        modelData.put("countdown.number", waitingForLaunch
            ? (renderedCountdownSeconds > 0 ? String.valueOf(renderedCountdownSeconds) : "...")
            : "");
        modelData.put("countdown.label", renderedCountdownSeconds > 0
            ? I18n.get("arcadia.client.lobby.countdown.label")
            : I18n.get("arcadia.client.lobby.countdown.regenerating"));
        boolean generationVisible = generation != null
            && waitingForLaunch
            && (!generation.done() || "complete".equals(generation.stage()) || "error".equals(generation.stage()));
        int generationPercent = generationPercent(generation);
        modelData.put("generation.visible", String.valueOf(generationVisible));
        modelData.put("generation.status", generationStatus(generation, generationPercent));
        modelData.put("generation.percent", generationVisible ? generationPercent + "%" : "");
        for (int i = 1; i <= 10; i++) {
            modelData.put("generation.seg" + i, generationPercent >= i * 10 ? "generation-segment active" : "generation-segment");
        }
        modelData.put("player.count", String.valueOf(maxPlayers));
        for (int i = 0; i < maxPlayers; i++) {
            boolean filled = i < playerNames.size();
            modelData.put("p.mark." + i, String.valueOf(i + 1));
            modelData.put("p.name." + i, filled ? playerNames.get(i) : I18n.get("arcadia.client.lobby.slot_open"));
            modelData.put("p.sub." + i, filled
                ? (i == 0 ? I18n.get("arcadia.client.lobby.leader") : I18n.get("arcadia.client.ready"))
                : I18n.get("arcadia.client.lobby.waiting_player"));
            modelData.put("p.nameClass." + i, filled ? "player-name" : "player-name muted");
            modelData.put("p.readyVisible." + i, String.valueOf(filled));
        }
        TesseraModel model = TesseraModel.of(modelData);

        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/client/dungeon-lobby");
        panel = TesseraTemplateRenderer.build(template, model, Map.of(
            "launch", this::onLaunch,
            "close", this::onClose
        ), px, py, panelW, panelH);
    }

    private void onLaunch() {
        RunStatePayload state = currentLobbyState();
        if (launching && (state == null || !"STARTING".equals(state.phase()))) return;
        if (state == null || !"STARTING".equals(state.phase())) {
            StructurePlacementClient.clear(dungeonId);
        }
        PacketDistributor.sendToServer(new StartRunPayload(dungeonId, archetypeId));
        launching = true;
        rebuildPanel();
    }

    private static int countdownSeconds(RunStatePayload state) {
        if (state == null || !"STARTING".equals(state.phase()) || state.launchCountdownEndMs() <= 0L) {
            return 0;
        }
        long remainingMs = state.launchCountdownEndMs() - RunStateClient.serverNowMs();
        return Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
    }

    private RunStatePayload currentLobbyState() {
        return RunStateClient.getState()
            .filter(state -> dungeonId.equals(state.dungeonId()))
            .orElse(null);
    }

    private static boolean countdownVisible(RunStatePayload state) {
        return state != null && "STARTING".equals(state.phase()) && state.launchCountdownEndMs() > 0L;
    }

    private String generationKey() {
        return StructurePlacementClient.get(dungeonId)
            .map(status -> status.stage() + ":" + status.processed() + "/" + status.total() + ":" + status.done() + ":" + status.success())
            .orElse("");
    }

    private static int generationPercent(StructurePlacementStatusPayload status) {
        if (status == null) return 0;
        if (status.done()) return status.success() ? 100 : 0;
        int total = Math.max(1, status.total());
        return Math.max(0, Math.min(100, status.processed() * 100 / total));
    }

    private static String generationStatus(StructurePlacementStatusPayload status, int percent) {
        if (status == null) return "";
        if (status.done()) {
            return status.success()
                ? I18n.get("arcadia.client.lobby.generation.complete")
                : I18n.get("arcadia.client.lobby.generation.error", status.message());
        }
        if ("prepare".equals(status.stage())) return I18n.get("arcadia.client.lobby.generation.prepare");
        return I18n.get("arcadia.client.lobby.generation.progress", generationStageLabel(status.stage()), percent);
    }

    private static String generationStageLabel(String stage) {
        return switch (stage) {
            case "clear" -> I18n.get("arcadia.client.lobby.generation.stage.clear");
            case "place" -> I18n.get("arcadia.client.lobby.generation.stage.place");
            case "entities" -> I18n.get("arcadia.client.lobby.generation.stage.entities");
            default -> I18n.get("arcadia.client.lobby.generation.stage.prepare");
        };
    }
}
