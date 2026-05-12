package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.tesseraui.TesseraToast;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-ecran detail d'un boss: options, phases et drops propres au boss.
 */
public final class AdminDungeonBossDetailScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 560;
    private static final int MAX_H = 360;

    private final String dungeonId;
    private final String dungeonName;
    private final int bossIndex;

    private TesseraPanel panel;
    private final Map<String, com.tesseraui.TesseraInputState> inputStates = new HashMap<>();
    private boolean panelDirty = true;
    private String activeTab = "options";

    public AdminDungeonBossDetailScreen(String dungeonId, String dungeonName, int bossIndex) {
        super(Component.literal("Boss - " + dungeonName + " / " + (bossIndex + 1)));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
        this.bossIndex = Math.max(0, bossIndex);
    }

    @Override protected void init() { super.init(); panelDirty = true; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        TesseraToast.render(g, width, height);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean charTyped(char c, int m) {
        if (panel != null && panel.charTyped(c, m)) return true;
        return super.charTyped(c, m);
    }

    @Override public boolean keyPressed(int k, int s, int m) {
        if (panel != null && panel.keyPressed(k, s, m)) return true;
        return super.keyPressed(k, s, m);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (panel != null && panel.mouseScrolled(mx, my, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override protected TesseraPanel tesseraRoot() { return panel; }

    private void rebuildPanel() {
        int panelW = Math.max(330, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(220, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();
        JsonArray bosses = getBosses(cfg);
        JsonObject boss = ensureBoss(bosses, bossIndex);
        JsonArray phases = getArray(boss, "phases");
        JsonArray rewards = getArray(boss, "rewards");
        syncBosses(cfg, bosses);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", "Boss - " + dungeonName + " / " + strOr(boss, "id", "boss_" + (bossIndex + 1)));
        modelData.put("v.bossId", strOr(boss, "id", "boss_" + (bossIndex + 1)));
        modelData.put("v.bossType", strOr(boss, "type", "minecraft:wither_skeleton"));
        modelData.put("v.bossHp", intOr(boss, "hp", 100));
        modelData.put("v.bossChance", doubleOr(boss, "spawnChance", 1.0));
        modelData.put("v.bossOptional", boolOr(boss, "optional", false) ? "Optionnel" : "Fixe");
        modelData.put("v.bossRequired", boolOr(boss, "requiredKill", true) ? "Kill requis" : "Kill libre");
        modelData.put("phase.count", String.valueOf(phases.size()));
        modelData.put("reward.count", String.valueOf(rewards.size()));
        modelData.put("tab.options", String.valueOf("options".equals(activeTab)));
        modelData.put("tab.phases", String.valueOf("phases".equals(activeTab)));
        modelData.put("tab.drops", String.valueOf("drops".equals(activeTab)));
        modelData.put("tab.optionsLabel", "options".equals(activeTab) ? "> Options" : "Options");
        modelData.put("tab.phasesLabel", "phases".equals(activeTab) ? "> Phases" : "Phases");
        modelData.put("tab.dropsLabel", "drops".equals(activeTab) ? "> Drops" : "Drops");

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("tabOptions", () -> switchTab("options"));
        handlers.put("tabPhases", () -> switchTab("phases"));
        handlers.put("tabDrops", () -> switchTab("drops"));
        handlers.put("save", () -> {
            syncBosses(cfg, bosses);
            PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
            TesseraToast.success("Boss sauvegarde");
        });
        handlers.put("deleteBoss", () -> {
            if (bossIndex < bosses.size()) bosses.remove(bossIndex);
            if (bosses.size() == 0) bosses.add(newBoss(0));
            syncBosses(cfg, bosses);
            PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
            ArcadiaNavigator.back();
        });
        handlers.put("toggleOptional", () -> {
            boss.addProperty("optional", !boolOr(boss, "optional", false));
            panelDirty = true;
        });
        handlers.put("toggleRequired", () -> {
            boss.addProperty("requiredKill", !boolOr(boss, "requiredKill", true));
            panelDirty = true;
        });
        handlers.put("addPhase", () -> {
            JsonObject ph = new JsonObject();
            ph.addProperty("triggerHpPercent", 50);
            ph.addProperty("damageMultiplier", 1.0);
            ph.addProperty("speedMultiplier", 1.0);
            phases.add(ph);
            boss.add("phases", phases);
            activeTab = "phases";
            clearDynamicInputStates();
            panelDirty = true;
        });
        handlers.put("addReward", () -> {
            JsonObject reward = new JsonObject();
            reward.addProperty("item", "minecraft:diamond");
            reward.addProperty("min", 1);
            reward.addProperty("max", 1);
            reward.addProperty("chance", 1.0);
            rewards.add(reward);
            boss.add("rewards", rewards);
            activeTab = "drops";
            clearDynamicInputStates();
            panelDirty = true;
        });

        inputHandlers.put("onBossId", v -> boss.addProperty("id", clean(v, "boss_" + (bossIndex + 1))));
        inputHandlers.put("onBossType", v -> boss.addProperty("type", clean(v, "minecraft:wither_skeleton")));
        inputHandlers.put("onBossHp", v -> { try { boss.addProperty("hp", Math.max(1, Integer.parseInt(v.trim()))); } catch (Exception ignored) {} });
        inputHandlers.put("onBossChance", v -> { try { boss.addProperty("spawnChance", clamp01(Double.parseDouble(v.trim()))); } catch (Exception ignored) {} });

        fillPhaseRows(modelData, inputHandlers, handlers, phases, boss);
        fillRewardRows(modelData, inputHandlers, handlers, rewards, boss);

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-boss-detail");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, inputStates, px, py, panelW, panelH);
    }

    private void fillPhaseRows(Map<String, String> modelData,
                               Map<String, Consumer<String>> inputHandlers,
                               Map<String, Runnable> handlers,
                               JsonArray phases,
                               JsonObject boss) {
        for (int i = 0; i < phases.size(); i++) {
            final int idx = i;
            JsonObject ph = phases.get(i).getAsJsonObject();
            modelData.put("p.phaseIndex." + i, String.valueOf(i + 1));
            modelData.put("p.phaseHp." + i, intOr(ph, "triggerHpPercent", 50));
            modelData.put("p.phaseDmg." + i, doubleOr(ph, "damageMultiplier", 1.0));
            modelData.put("p.phaseSpd." + i, doubleOr(ph, "speedMultiplier", 1.0));
            modelData.put("p.phaseHpId." + i, "phaseHp_" + i);
            modelData.put("p.phaseDmgId." + i, "phaseDmg_" + i);
            modelData.put("p.phaseSpdId." + i, "phaseSpd_" + i);
            modelData.put("p.phaseHpKey." + i, "onPhaseHp." + i);
            modelData.put("p.phaseDmgKey." + i, "onPhaseDmg." + i);
            modelData.put("p.phaseSpdKey." + i, "onPhaseSpd." + i);
            modelData.put("p.phaseDelKey." + i, "delPhase." + i);

            inputHandlers.put("onPhaseHp." + i, v -> { try { ph.addProperty("triggerHpPercent", Integer.parseInt(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onPhaseDmg." + i, v -> { try { ph.addProperty("damageMultiplier", Double.parseDouble(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onPhaseSpd." + i, v -> { try { ph.addProperty("speedMultiplier", Double.parseDouble(v.trim())); } catch (Exception ignored) {} });
            handlers.put("delPhase." + i, () -> {
                if (idx < phases.size()) phases.remove(idx);
                boss.add("phases", phases);
                activeTab = "phases";
                clearDynamicInputStates();
                panelDirty = true;
            });
        }
    }

    private void fillRewardRows(Map<String, String> modelData,
                                Map<String, Consumer<String>> inputHandlers,
                                Map<String, Runnable> handlers,
                                JsonArray rewards,
                                JsonObject boss) {
        for (int i = 0; i < rewards.size(); i++) {
            final int idx = i;
            JsonObject reward = rewards.get(i).getAsJsonObject();
            modelData.put("r.rewardIndex." + i, String.valueOf(i + 1));
            modelData.put("r.rewardItem." + i, strOr(reward, "item", "minecraft:diamond"));
            modelData.put("r.rewardMin." + i, intOr(reward, "min", 1));
            modelData.put("r.rewardMax." + i, intOr(reward, "max", 1));
            modelData.put("r.rewardChance." + i, doubleOr(reward, "chance", 1.0));
            modelData.put("r.rewardItemId." + i, "rewardItem_" + i);
            modelData.put("r.rewardMinId." + i, "rewardMin_" + i);
            modelData.put("r.rewardMaxId." + i, "rewardMax_" + i);
            modelData.put("r.rewardChanceId." + i, "rewardChance_" + i);
            modelData.put("r.rewardItemKey." + i, "onRewardItem." + i);
            modelData.put("r.rewardMinKey." + i, "onRewardMin." + i);
            modelData.put("r.rewardMaxKey." + i, "onRewardMax." + i);
            modelData.put("r.rewardChanceKey." + i, "onRewardChance." + i);
            modelData.put("r.rewardDelKey." + i, "delReward." + i);

            inputHandlers.put("onRewardItem." + i, v -> reward.addProperty("item", clean(v, "minecraft:diamond")));
            inputHandlers.put("onRewardMin." + i, v -> { try { reward.addProperty("min", Integer.parseInt(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onRewardMax." + i, v -> { try { reward.addProperty("max", Integer.parseInt(v.trim())); } catch (Exception ignored) {} });
            inputHandlers.put("onRewardChance." + i, v -> { try { reward.addProperty("chance", clamp01(Double.parseDouble(v.trim()))); } catch (Exception ignored) {} });
            handlers.put("delReward." + i, () -> {
                if (idx < rewards.size()) rewards.remove(idx);
                boss.add("rewards", rewards);
                activeTab = "drops";
                clearDynamicInputStates();
                panelDirty = true;
            });
        }
    }

    private void switchTab(String tab) {
        activeTab = tab;
        panelDirty = true;
    }

    private void clearDynamicInputStates() {
        inputStates.keySet().removeIf(key ->
            key.startsWith("phaseHp_")
                || key.startsWith("phaseDmg_")
                || key.startsWith("phaseSpd_")
                || key.startsWith("rewardItem_")
                || key.startsWith("rewardMin_")
                || key.startsWith("rewardMax_")
                || key.startsWith("rewardChance_"));
    }

    private static JsonArray getBosses(JsonObject cfg) {
        JsonArray bosses;
        try { bosses = cfg.getAsJsonArray("bosses"); }
        catch (Exception ignored) { bosses = null; }
        if (bosses == null) {
            bosses = new JsonArray();
            cfg.add("bosses", bosses);
            cfg.remove("boss");
        }
        return bosses;
    }

    private static JsonObject ensureBoss(JsonArray bosses, int index) {
        while (bosses.size() <= index) {
            bosses.add(newBoss(bosses.size()));
        }
        return bosses.get(index).getAsJsonObject();
    }

    private static JsonArray getArray(JsonObject owner, String key) {
        JsonArray array;
        try { array = owner.getAsJsonArray(key); }
        catch (Exception ignored) { array = null; }
        if (array == null) {
            array = new JsonArray();
            owner.add(key, array);
        }
        return array;
    }

    private static JsonObject newBoss(int index) {
        JsonObject boss = new JsonObject();
        boss.addProperty("id", "boss_" + (index + 1));
        boss.addProperty("type", "minecraft:wither_skeleton");
        boss.addProperty("hp", 100);
        boss.add("phases", new JsonArray());
        boss.addProperty("optional", false);
        boss.addProperty("spawnChance", 1.0);
        boss.addProperty("requiredKill", true);
        boss.add("rewards", new JsonArray());
        return boss;
    }

    private static void syncBosses(JsonObject cfg, JsonArray bosses) {
        cfg.add("bosses", bosses);
        cfg.remove("boss");
    }

    private static String clean(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

    private static String intOr(JsonObject o, String k, int def) {
        try { return String.valueOf(o.get(k).getAsInt()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static String doubleOr(JsonObject o, String k, double def) {
        try { return String.valueOf(o.get(k).getAsDouble()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static boolean boolOr(JsonObject o, String k, boolean def) {
        try { return o.get(k).getAsBoolean(); } catch (Exception e) { return def; }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
