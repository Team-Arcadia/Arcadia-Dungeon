package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonListClient;
import com.arcadia.dungeon.client.state.PlayerHubPreferences;
import com.arcadia.dungeon.client.state.PlayerProgressClient;
import com.arcadia.dungeon.network.DungeonListPayload;
import com.arcadia.dungeon.network.RequestDungeonListPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraRenderContext;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.tesseraui.TesseraToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Player command center: dungeon selection, planned loadout/shop/profile/options entry points.
 */
public final class PlayerHubScreen extends TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 640;
    private static final int MAX_H = 360;

    private TesseraPanel panel;
    private final TesseraRenderContext renderContext = new TesseraRenderContext();
    private long lastKnownDungeonListVersion = -1L;
    private long lastKnownProgressVersion = -1L;
    private boolean panelDirty = true;
    private int selectedDungeonIndex = 0;
    private String selectedClassId = "";
    private String activeTab = "dungeons";

    private boolean optionHud = true;
    private boolean optionFeed = true;
    private boolean optionToasts = true;
    private boolean optionSpectator = false;

    public PlayerHubScreen() {
        super(Component.translatable("arcadia.player.screen.title"));
        PlayerHubPreferences.load();
        selectedClassId = PlayerHubPreferences.selectedClassId();
        optionHud = PlayerHubPreferences.hud();
        optionFeed = PlayerHubPreferences.feed();
        optionToasts = PlayerHubPreferences.toasts();
        optionSpectator = PlayerHubPreferences.spectator();
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new RequestDungeonListPayload());
        panelDirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long currentVersion = DungeonListClient.version();
        if (currentVersion != lastKnownDungeonListVersion) {
            lastKnownDungeonListVersion = currentVersion;
            panelDirty = true;
        }
        long progressVersion = PlayerProgressClient.version();
        if (progressVersion != lastKnownProgressVersion) {
            lastKnownProgressVersion = progressVersion;
            panelDirty = true;
        }
        if (panelDirty) {
            rebuildPanel();
            panelDirty = false;
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
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
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
        int panelW = Math.max(1, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(1, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;

        List<DungeonListPayload.DungeonSummary> dungeons = DungeonListClient.get();
        if (dungeons.isEmpty()) {
            selectedDungeonIndex = 0;
        } else if (selectedDungeonIndex >= dungeons.size()) {
            selectedDungeonIndex = dungeons.size() - 1;
        }

        DungeonListPayload.DungeonSummary selected = selectedDungeon(dungeons);
        Map<String, String> modelData = new HashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        fillShellModel(modelData, dungeons, selected);
        fillDungeonRows(modelData, handlers, dungeons);
        fillClassRows(modelData, handlers, dungeons);
        fillShopRows(modelData, handlers);
        fillProfileRows(modelData);
        fillOptions(modelData, inputHandlers);
        fillHandlers(handlers, dungeons);

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/client/player-hub");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void fillShellModel(Map<String, String> modelData,
                                List<DungeonListPayload.DungeonSummary> dungeons,
                                DungeonListPayload.DungeonSummary selected) {
        String playerName = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getName().getString()
            : tr("arcadia.player.fallback.player");

        modelData.put("tab.dungeons", String.valueOf("dungeons".equals(activeTab)));
        modelData.put("tab.loadout", String.valueOf("loadout".equals(activeTab)));
        modelData.put("tab.shop", String.valueOf("shop".equals(activeTab)));
        modelData.put("tab.profile", String.valueOf("profile".equals(activeTab)));
        modelData.put("tab.options", String.valueOf("options".equals(activeTab)));
        modelData.put("tab.dungeonsLabel", tabLabel("dungeons", tr("arcadia.player.tab.dungeons")));
        modelData.put("tab.loadoutLabel", tabLabel("loadout", tr("arcadia.player.tab.loadout")));
        modelData.put("tab.shopLabel", tabLabel("shop", tr("arcadia.player.tab.shop")));
        modelData.put("tab.profileLabel", tabLabel("profile", tr("arcadia.player.tab.profile")));
        modelData.put("tab.optionsLabel", tabLabel("options", tr("arcadia.player.tab.options")));

        modelData.put("player.name", playerName);
        int totalRuns = PlayerProgressClient.totalRuns();
        modelData.put("player.rank", totalRuns >= 10 ? tr("arcadia.player.rank.veteran") :
            totalRuns > 0 ? tr("arcadia.player.rank.explorer") : tr("arcadia.player.rank.new"));
        modelData.put("player.currency", String.valueOf(PlayerProgressClient.currency()));
        modelData.put("player.currencyLabel", tr("arcadia.player.currency", PlayerProgressClient.currency()));
        modelData.put("player.level", String.valueOf(1 + totalRuns / 5));
        modelData.put("player.levelLabel", tr("arcadia.player.profile.level", 1 + totalRuns / 5));
        modelData.put("player.runs", String.valueOf(totalRuns));
        modelData.put("player.best", formatTime(PlayerProgressClient.bestTimeSeconds()));
        modelData.put("player.badge", totalRuns > 0 ? tr("arcadia.player.badge.active") : tr("arcadia.player.badge.ready"));
        modelData.put("dungeon.count", String.valueOf(dungeons.size()));
        modelData.put("hub.empty", String.valueOf(dungeons.isEmpty()));
        modelData.put("hub.hasDungeons", String.valueOf(!dungeons.isEmpty()));
        modelData.put("hub.status", dungeons.isEmpty()
            ? tr("arcadia.player.status.waiting")
            : tr("arcadia.player.status.loaded", dungeons.size()));

        boolean hasSelected = selected != null;
        modelData.put("selected.exists", String.valueOf(hasSelected));
        modelData.put("selected.missing", String.valueOf(!hasSelected));
        modelData.put("selected.name", hasSelected ? dungeonName(selected) : tr("arcadia.player.status.no_dungeon"));
        modelData.put("selected.id", hasSelected ? selected.id() : tr("arcadia.player.status.syncing"));
        modelData.put("selected.classes", tr("arcadia.player.status.global_classes"));
        modelData.put("selected.schema", hasSelected ? tr("arcadia.player.schema", selected.schemaVersion()) : tr("arcadia.player.schema.empty"));
        modelData.put("selected.state", hasSelected ? tr("arcadia.player.status.ready") : tr("arcadia.player.sync"));
        modelData.put("selected.stateClass", hasSelected ? "state-ready" : "state-warn");
        modelData.put("selected.loadout", selectedClass(dungeons).name());
        modelData.put("selected.modifiers", hasSelected ? suggestedModifiers(selected) : tr("arcadia.player.status.no_modifier"));

        FreeClass selectedClass = selectedClass(dungeons);
        modelData.put("loadout.name", selectedClass.name());
        modelData.put("loadout.id", selectedClass.id());
        modelData.put("loadout.role", selectedClass.role());
        modelData.put("loadout.power", String.valueOf(selectedClass.power()));
        modelData.put("loadout.powerLabel", tr("arcadia.player.power", selectedClass.power()));
        modelData.put("loadout.slot.main", itemDisplay(selectedClass.main()));
        modelData.put("loadout.slot.off", itemDisplay(selectedClass.off()));
        modelData.put("loadout.slot.utility", itemDisplay(selectedClass.utility()));
        modelData.put("loadout.item.main", selectedClass.main());
        modelData.put("loadout.item.off", selectedClass.off());
        modelData.put("loadout.item.utility", selectedClass.utility());
        modelData.put("loadout.locked", tr("arcadia.player.loadout.locked"));
        modelData.put("loadout.points", "0");
        modelData.put("loadout.pointsLabel", tr("arcadia.player.points", 0));
    }

    private void fillClassRows(Map<String, String> modelData,
                               Map<String, Runnable> handlers,
                               List<DungeonListPayload.DungeonSummary> dungeons) {
        List<FreeClass> classes = availableClasses(dungeons);
        modelData.put("class.count", String.valueOf(classes.size()));
        for (int i = 0; i < classes.size(); i++) {
            final int idx = i;
            FreeClass klass = classes.get(i);
            String key = "class.select." + i;
            modelData.put("c.name." + i, klass.name());
            modelData.put("c.role." + i, klass.role());
            modelData.put("c.power." + i, tr("arcadia.player.power", klass.power()));
            modelData.put("c.detail." + i, klass.detail());
            modelData.put("c.rowClass." + i, klass.id().equals(selectedClassId) ? "class-row selected" : "class-row");
            modelData.put("c.onclick." + i, key);
            handlers.put(key, () -> selectClass(idx));
        }
    }

    private void fillDungeonRows(Map<String, String> modelData,
                                 Map<String, Runnable> handlers,
                                 List<DungeonListPayload.DungeonSummary> dungeons) {
        for (int i = 0; i < dungeons.size(); i++) {
            final int idx = i;
            DungeonListPayload.DungeonSummary dungeon = dungeons.get(i);
            String selectKey = "dungeon.select." + i;
            String playKey = "dungeon.play." + i;

            modelData.put("d.index." + i, String.valueOf(i + 1));
            modelData.put("d.name." + i, dungeonName(dungeon));
            modelData.put("d.id." + i, dungeon.id());
            modelData.put("d.meta." + i, tr("arcadia.player.dungeon.meta", dungeon.id(), formatTime(PlayerProgressClient.bestTimeFor(dungeon.id()))));
            modelData.put("d.state." + i, tr("arcadia.player.status.ready"));
            modelData.put("d.stateClass." + i, "state-ready");
            modelData.put("d.rowClass." + i, idx == selectedDungeonIndex ? "dungeon-row selected" : "dungeon-row");
            modelData.put("d.select." + i, selectKey);
            modelData.put("d.play." + i, playKey);
            handlers.put(selectKey, () -> selectDungeon(idx));
            handlers.put(playKey, () -> openDungeon(dungeon));
        }
    }

    private void fillShopRows(Map<String, String> modelData, Map<String, Runnable> handlers) {
        modelData.put("shop.count", "3");
        long currency = PlayerProgressClient.currency();
        putShop(modelData, handlers, 0, "arcadia.player.shop.reroll", 75, currency);
        putShop(modelData, handlers, 1, "arcadia.player.shop.fortune", 120, currency);
        putShop(modelData, handlers, 2, "arcadia.player.shop.second_chance", 200, currency);
    }

    private void putShop(Map<String, String> modelData,
                         Map<String, Runnable> handlers,
                         int index,
                         String keyPrefix,
                         int cost,
                         long currency) {
        String key = "shop.buy." + index;
        modelData.put("s.name." + index, tr(keyPrefix + ".name"));
        modelData.put("s.desc." + index, tr(keyPrefix + ".desc"));
        modelData.put("s.cost." + index, tr("arcadia.player.currency", cost));
        modelData.put("s.state." + index, currency >= cost ? tr("arcadia.player.status.ready") : tr("arcadia.player.shop.missing"));
        modelData.put("s.onclick." + index, key);
        handlers.put(key, () -> TesseraToast.show(tr("arcadia.player.toast.shop_unavailable")));
    }

    private void fillProfileRows(Map<String, String> modelData) {
        int totalRuns = PlayerProgressClient.totalRuns();
        long bestTime = PlayerProgressClient.bestTimeSeconds();
        modelData.put("badge.count", "4");
        putBadge(modelData, 0, "arcadia.player.profile.badge.first_blood", totalRuns > 0, false);
        putBadge(modelData, 1, "arcadia.player.profile.badge.timer", bestTime > 0, false);
        putBadge(modelData, 2, "arcadia.player.profile.badge.collector", false, true);
        putBadge(modelData, 3, "arcadia.player.profile.badge.guardian", false, true);
    }

    private void putBadge(Map<String, String> modelData, int index, String keyPrefix,
                          boolean unlocked, boolean comingSoon) {
        modelData.put("b.name." + index, tr(keyPrefix + ".name"));
        modelData.put("b.desc." + index, tr(keyPrefix + ".desc"));
        modelData.put("b.rowClass." + index, unlocked ? "badge-row unlocked" : "badge-row locked");
        modelData.put("b.state." + index, unlocked ? tr("arcadia.player.badge.state.unlocked") :
            comingSoon ? tr("arcadia.player.badge.state.soon") : tr("arcadia.player.badge.state.locked"));
        modelData.put("b.stateClass." + index, unlocked ? "badge-state-unlocked" :
            comingSoon ? "badge-state-soon" : "badge-state-locked");
    }

    private void fillOptions(Map<String, String> modelData, Map<String, Consumer<String>> inputHandlers) {
        modelData.put("opt.hud", String.valueOf(optionHud));
        modelData.put("opt.feed", String.valueOf(optionFeed));
        modelData.put("opt.toasts", String.valueOf(optionToasts));
        modelData.put("opt.spectator", String.valueOf(optionSpectator));
        modelData.put("opt.hudState", optionState(optionHud));
        modelData.put("opt.feedState", optionState(optionFeed));
        modelData.put("opt.toastsState", optionState(optionToasts));
        modelData.put("opt.spectatorState", optionState(optionSpectator));

        inputHandlers.put("toggleHud", v -> toggleOption("hud", v));
        inputHandlers.put("toggleFeed", v -> toggleOption("feed", v));
        inputHandlers.put("toggleToasts", v -> toggleOption("toasts", v));
        inputHandlers.put("toggleSpectator", v -> toggleOption("spectator", v));
    }

    private void fillHandlers(Map<String, Runnable> handlers, List<DungeonListPayload.DungeonSummary> dungeons) {
        handlers.put("close", this::onClose);
        handlers.put("tabDungeons", () -> switchTab("dungeons"));
        handlers.put("tabLoadout", () -> switchTab("loadout"));
        handlers.put("tabShop", () -> switchTab("shop"));
        handlers.put("tabProfile", () -> switchTab("profile"));
        handlers.put("tabOptions", () -> switchTab("options"));
        handlers.put("playSelected", () -> {
            DungeonListPayload.DungeonSummary selected = selectedDungeon(dungeons);
            if (selected == null) {
                TesseraToast.error(tr("arcadia.player.toast.no_dungeon"));
                return;
            }
            openDungeon(selected);
        });
        handlers.put("editLoadout", () -> {
            TesseraToast.show(tr("arcadia.player.toast.edit_loadout"));
        });
        handlers.put("customizeLoadout", () -> TesseraToast.show(tr("arcadia.player.toast.customize_loadout")));
        handlers.put("quickQueue", () -> TesseraToast.show(tr("arcadia.player.toast.quick_queue")));
        handlers.put("refresh", () -> {
            PacketDistributor.sendToServer(new RequestDungeonListPayload());
            TesseraToast.show(tr("arcadia.player.toast.sync_requested"));
        });
    }

    private void switchTab(String tab) {
        activeTab = tab;
        panelDirty = true;
    }

    private void selectDungeon(int index) {
        selectedDungeonIndex = Math.max(0, index);
        panelDirty = true;
    }

    private void selectClass(int index) {
        List<FreeClass> classes = availableClasses(DungeonListClient.get());
        if (classes.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(index, classes.size() - 1));
        selectedClassId = classes.get(safeIndex).id();
        PlayerHubPreferences.selectedClassId(selectedClassId);
        panelDirty = true;
    }

    private void openDungeon(DungeonListPayload.DungeonSummary dungeon) {
        FreeClass selected = selectedClass(DungeonListClient.get());
        Minecraft.getInstance().setScreen(new DungeonLobbyScreen(
            dungeon.id(),
            dungeonName(dungeon),
            selected.id(),
            selected.name()));
    }

    private void toggleOption(String option, String value) {
        boolean enabled = Boolean.parseBoolean(value);
        switch (option) {
            case "hud" -> {
                optionHud = enabled;
                PlayerHubPreferences.hud(enabled);
            }
            case "feed" -> {
                optionFeed = enabled;
                PlayerHubPreferences.feed(enabled);
            }
            case "toasts" -> {
                optionToasts = enabled;
                PlayerHubPreferences.toasts(enabled);
            }
            case "spectator" -> {
                optionSpectator = enabled;
                PlayerHubPreferences.spectator(enabled);
            }
            default -> { return; }
        }
        TesseraToast.show(tr("arcadia.player.toast.option", option, optionState(enabled)));
        panelDirty = true;
    }

    private static String optionState(boolean enabled) {
        return tr(enabled ? "arcadia.player.option.on" : "arcadia.player.option.off");
    }

    private DungeonListPayload.DungeonSummary selectedDungeon(List<DungeonListPayload.DungeonSummary> dungeons) {
        if (dungeons.isEmpty()) return null;
        int index = Math.max(0, Math.min(selectedDungeonIndex, dungeons.size() - 1));
        return dungeons.get(index);
    }

    private String tabLabel(String tab, String label) {
        return tab.equals(activeTab) ? "> " + label : label;
    }

    private String dungeonName(DungeonListPayload.DungeonSummary dungeon) {
        return Component.translatable(dungeon.name()).getString();
    }

    private String suggestedModifiers(DungeonListPayload.DungeonSummary dungeon) {
        return tr("arcadia.player.status.modifiers_planned");
    }

    private FreeClass selectedClass(List<DungeonListPayload.DungeonSummary> dungeons) {
        List<FreeClass> classes = availableClasses(dungeons);
        for (FreeClass klass : classes) {
            if (klass.id().equals(selectedClassId)) {
                return klass;
            }
        }
        FreeClass fallback = classes.getFirst();
        selectedClassId = fallback.id();
        PlayerHubPreferences.selectedClassId(selectedClassId);
        return fallback;
    }

    private List<FreeClass> availableClasses(List<DungeonListPayload.DungeonSummary> dungeons) {
        List<FreeClass> classes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DungeonListPayload.ClassSummary klass : DungeonListClient.globalClasses()) {
            if (!seen.add(klass.id())) {
                continue;
            }
            List<String> items = klass.items();
            classes.add(new FreeClass(
                klass.id(),
                className(klass.id(), klass.nameKey()),
                classRole(klass.id()),
                itemOr(items, 0, "minecraft:book"),
                itemOr(items, 1, "minecraft:shield"),
                itemOr(items, 2, "minecraft:bread"),
                classPower(klass.id(), items.size()),
                ""));
        }
        if (classes.isEmpty()) {
            classes.add(new FreeClass("warrior", tr("arcadia.player.class.warrior"), tr("arcadia.player.class.role.frontline"),
                "minecraft:iron_sword", "minecraft:shield", "minecraft:bread", 18,
                ""));
        }
        return classes;
    }

    private static String itemDisplay(String itemId) {
        int idx = itemId.indexOf(':');
        return idx >= 0 ? itemId.substring(idx + 1) : itemId;
    }

    private static String className(String id, String nameKey) {
        String key = nameKey == null ? "" : nameKey.trim();
        if (key.isEmpty()) {
            return id;
        }
        return Component.translatable(key).getString();
    }

    private static String itemOr(List<String> items, int index, String fallback) {
        return items != null && index >= 0 && index < items.size() ? items.get(index) : fallback;
    }

    private static String classRole(String id) {
        return switch (id) {
            case "warrior" -> tr("arcadia.player.class.role.frontline");
            case "mage" -> tr("arcadia.player.class.role.burst");
            case "archer" -> tr("arcadia.player.class.role.distance");
            case "healer" -> tr("arcadia.player.class.role.support");
            default -> tr("arcadia.player.class.role.admin");
        };
    }

    private static int classPower(String id, int itemCount) {
        return switch (id) {
            case "warrior" -> 18;
            case "mage" -> 16;
            case "archer" -> 15;
            case "healer" -> 14;
            default -> Math.max(8, Math.min(18, 8 + itemCount * 2));
        };
    }

    private static String formatTime(long seconds) {
        if (seconds <= 0) return "--:--";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + ":" + (secs < 10 ? "0" : "") + secs;
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    private record FreeClass(String id, String name, String role, String main, String off, String utility,
                             int power, String detail) {}
}
