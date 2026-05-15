package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.hud.RunOverlayHud;
import com.arcadia.dungeon.client.state.ActiveRunsClient;
import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.client.state.PlayerHubPreferences;
import com.arcadia.dungeon.network.AdminDebugActionPayload;
import com.arcadia.dungeon.network.ForceEndRunPayload;
import com.arcadia.dungeon.network.MonitorDataPayload;
import com.arcadia.dungeon.network.MonitorRefreshPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Global admin debug panel for runtime and player progression tools.
 */
public final class AdminDungeonDebugScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 560;
    private static final int MAX_H = 330;
    private static final long REFRESH_MS = 2_000L;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;
    private List<MonitorDataPayload.RunSummary> lastRuns = List.of();
    private long lastRefreshMs = 0L;

    private String targetPlayer = "";
    private String amount = "100";
    private String dungeonId = "";
    private String timeSeconds = "60";
    private String loadoutPoints = "3";
    private String activeTab = "player";

    public AdminDungeonDebugScreen() {
        super(Component.translatable("arcadia.admin.debug.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        targetPlayer = currentPlayerName();
        if (dungeonId.isBlank()) {
            dungeonId = DungeonListClient.get().stream().findFirst()
                .map(dungeon -> dungeon.id())
                .orElse("");
        }
        ActiveRunsClient.clear();
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        sendRefresh();
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= REFRESH_MS) {
            sendRefresh();
        }

        List<MonitorDataPayload.RunSummary> current = ActiveRunsClient.get();
        if (!current.equals(lastRuns)) {
            lastRuns = current;
            panelDirty = true;
        }
        if (panelDirty) {
            rebuildPanel();
            panelDirty = false;
        }
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
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (panel != null && panel.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int k, int s, int mods) {
        if (panel != null && panel.keyPressed(k, s, mods)) return true;
        return super.keyPressed(k, s, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return panel;
    }

    private void sendRefresh() {
        PacketDistributor.sendToServer(new MonitorRefreshPayload());
        lastRefreshMs = System.currentTimeMillis();
    }

    private void rebuildPanel() {
        int panelW = Math.max(360, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(240, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.debug.global.title"));
        modelData.put("debug.summary", I18n.get("arcadia.admin.debug.summary", lastRuns.size()));
        modelData.put("debug.empty", lastRuns.isEmpty() ? "true" : "");
        modelData.put("tab.player", String.valueOf("player".equals(activeTab)));
        modelData.put("tab.hud", String.valueOf("hud".equals(activeTab)));
        modelData.put("tab.runs", String.valueOf("runs".equals(activeTab)));
        modelData.put("tab.playerLabel", tabLabel("player", I18n.get("arcadia.admin.debug.tab.player")));
        modelData.put("tab.hudLabel", tabLabel("hud", I18n.get("arcadia.admin.debug.tab.hud")));
        modelData.put("tab.runsLabel", tabLabel("runs", I18n.get("arcadia.admin.debug.tab.runs")));
        modelData.put("v.player", targetPlayer);
        modelData.put("v.amount", amount);
        modelData.put("v.dungeon", dungeonId);
        modelData.put("v.time", timeSeconds);
        modelData.put("v.points", loadoutPoints);
        modelData.put("hud.state", RunOverlayHud.previewEnabled()
            ? I18n.get("arcadia.admin.debug.hud.preview_on")
            : I18n.get("arcadia.admin.debug.hud.preview_off"));
        modelData.put("hud.option", PlayerHubPreferences.hud()
            ? I18n.get("arcadia.player.option.on")
            : I18n.get("arcadia.player.option.off"));
        modelData.put("s.players", playerSuggestions());
        modelData.put("s.dungeons", dungeonSuggestions());

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("tabPlayer", () -> switchTab("player"));
        handlers.put("tabHud", () -> switchTab("hud"));
        handlers.put("tabRuns", () -> switchTab("runs"));
        handlers.put("sync", () -> sendAction("SYNC_PROGRESS", 0L, 0L));
        handlers.put("addCurrency", () -> sendAction("ADD_CURRENCY", parseLong(amount, 0L), 0L));
        handlers.put("setCurrency", () -> sendAction("SET_CURRENCY", parseLong(amount, 0L), 0L));
        handlers.put("resetProgress", () -> sendAction("RESET_PROGRESS", 0L, 0L));
        handlers.put("grantCompletion", () -> sendAction("GRANT_COMPLETION", 0L, parseLong(timeSeconds, 60L)));
        handlers.put("unlockBadges", () -> sendAction("UNLOCK_PROFILE_BADGES", 0L, parseLong(timeSeconds, 60L)));
        handlers.put("unlockCustom", () -> sendAction("UNLOCK_CUSTOM_LOADOUT", 0L, 0L));
        handlers.put("addLoadoutPoints", () -> sendAction("ADD_LOADOUT_POINTS", parseLong(loadoutPoints, 0L), 0L));
        handlers.put("showHud", () -> {
            PlayerHubPreferences.hud(true);
            RunOverlayHud.setPreviewEnabled(true);
            TesseraToast.show(I18n.get("arcadia.admin.debug.toast.hud_on"));
            panelDirty = true;
        });
        handlers.put("showHudInventory", () -> {
            PlayerHubPreferences.hud(true);
            RunOverlayHud.setPreviewEnabled(true);
            sendAction("PREVIEW_DUNGEON_INVENTORY", 0L, 0L);
            TesseraToast.show(I18n.get("arcadia.admin.debug.toast.hud_inventory_on"));
            panelDirty = true;
        });
        handlers.put("restoreDebugInventory", () -> {
            sendAction("RESTORE_DEBUG_INVENTORY", 0L, 0L);
            TesseraToast.show(I18n.get("arcadia.admin.debug.toast.inventory_restored"));
        });
        handlers.put("hideHud", () -> {
            RunOverlayHud.setPreviewEnabled(false);
            PlayerHubPreferences.hud(false);
            TesseraToast.show(I18n.get("arcadia.admin.debug.toast.hud_off"));
            panelDirty = true;
        });
        handlers.put("killAll", () -> {
            for (MonitorDataPayload.RunSummary run : lastRuns) {
                PacketDistributor.sendToServer(new ForceEndRunPayload(run.runId(), false));
            }
            ActiveRunsClient.clear();
            lastRuns = List.of();
            sendRefresh();
            panelDirty = true;
        });

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onPlayer", v -> targetPlayer = safe(v));
        inputHandlers.put("onAmount", v -> amount = safe(v));
        inputHandlers.put("onDungeon", v -> dungeonId = safe(v));
        inputHandlers.put("onTime", v -> timeSeconds = safe(v));
        inputHandlers.put("onPoints", v -> loadoutPoints = safe(v));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-debug");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers,
            renderContext, px, py, panelW, panelH);
    }

    private void switchTab(String tab) {
        activeTab = tab;
        panelDirty = true;
    }

    private String tabLabel(String tab, String label) {
        return tab.equals(activeTab) ? "> " + label : label;
    }

    private void sendAction(String action, long amountValue, long timeValue) {
        PacketDistributor.sendToServer(new AdminDebugActionPayload(
            action, targetPlayer, dungeonId, amountValue, timeValue));
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value != null ? value.trim() : "";
    }

    private static String currentPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getGameProfile().getName() : "";
    }

    private static String playerSuggestions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return currentPlayerName();
        return mc.getConnection().getOnlinePlayers().stream()
            .map(info -> info.getProfile().getName())
            .distinct()
            .collect(Collectors.joining(","));
    }

    private static String dungeonSuggestions() {
        return DungeonListClient.get().stream()
            .map(dungeon -> dungeon.id())
            .collect(Collectors.joining(","));
    }
}
