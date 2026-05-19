package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraRenderContext;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Detail editor for one ordered wave.
 */
public final class AdminDungeonWaveDetailScreen extends TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 720;
    private static final int MAX_H = 390;

    private final String dungeonId;
    private final String dungeonName;
    private final int waveIndex;

    private TesseraPanel panel;
    private final TesseraRenderContext renderContext = new TesseraRenderContext();
    private boolean panelDirty = true;
    private String activeTab = "wave";
    private int selectedMobIndex = 0;

    public AdminDungeonWaveDetailScreen(String dungeonId, String dungeonName, int waveIndex) {
        super(Component.translatable("arcadia.admin.wave.screen.title", dungeonName, waveIndex + 1));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
        this.waveIndex = Math.max(0, waveIndex);
    }

    @Override protected void init() { super.init(); panelDirty = true; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        AdminUiFeedback.renderToasts(g, width, height);
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
        int panelW = Math.max(480, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(300, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();
        JsonArray waves = AdminDungeonWavesScreen.getGlobalWaves(cfg);
        JsonObject wave = ensureWave(waves, waveIndex);
        JsonArray mobs = mobList(wave);
        selectedMobIndex = Math.max(0, Math.min(selectedMobIndex, mobs.size() - 1));
        JsonObject mob = mobs.get(selectedMobIndex).getAsJsonObject();
        JsonObject spawn = spawnPoint(mob);
        JsonObject equipment = getObject(mob, "equipment");
        JsonObject combat = getObject(mob, "combat");
        JsonObject attrs = getObject(mob, "customAttributes");
        JsonObject area1 = getObjectOrNull(mob, "areaPos1");
        JsonObject area2 = getObjectOrNull(mob, "areaPos2");
        AdminDungeonWavesScreen.syncWaves(cfg, waves);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", I18n.get("arcadia.admin.wave.detail.title", dungeonName,
            AdminDungeonWavesScreen.strOr(wave, "name", I18n.get("arcadia.admin.wave.default_name", waveIndex + 1))));
        putTabs(modelData);
        modelData.put("s.entities", AdminUiSuggestions.ENTITIES);
        modelData.put("s.items", AdminUiSuggestions.ITEMS);
        modelData.put("s.dimensions", AdminUiSuggestions.DIMENSIONS);
        modelData.put("s.attributes", AdminUiSuggestions.ATTRIBUTES);

        modelData.put("v.waveName", AdminDungeonWavesScreen.strOr(wave, "name", I18n.get("arcadia.admin.wave.default_name", waveIndex + 1)));
        String triggerMode = triggerMode(wave);
        modelData.put("v.showOrdered", String.valueOf("ordered".equals(triggerMode)));
        modelData.put("v.showTicks", String.valueOf("ticks".equals(triggerMode)));
        modelData.put("v.triggerOrderedClass", "ordered".equals(triggerMode) ? "zone-mode-btn zone-mode-active" : "zone-mode-btn");
        modelData.put("v.triggerTicksClass", "ticks".equals(triggerMode) ? "zone-mode-btn zone-mode-active" : "zone-mode-btn");
        modelData.put("v.waveOrder", String.valueOf(waveIndex + 1));
        modelData.put("v.triggerNote", "ticks".equals(triggerMode)
            ? I18n.get("arcadia.admin.wave.trigger.note.ticks")
            : I18n.get("arcadia.admin.wave.trigger.note.ordered", waveIndex + 1, waves.size()));
        modelData.put("v.waveDelay", intOr(wave, "delayTicks", 20));
        modelData.put("v.waveMessage", strOr(wave, "startMessage", ""));
        modelData.put("v.waveGlowChecked", String.valueOf(boolOr(wave, "glowingAfterDelay", true)));
        modelData.put("v.waveGlowDelay", intOr(wave, "glowingDelaySeconds", 60));

        modelData.put("mob.count", String.valueOf(mobs.size()));
        modelData.put("mob.selected", I18n.get("arcadia.admin.wave.mob.selected", selectedMobIndex + 1, mobs.size()));
        modelData.put("mob.selectedName", selectedMobTitle(mob, selectedMobIndex));
        modelData.put("v.mobSummary", mobSummary(mob));
        modelData.put("v.mobType", strOr(mob, "mobType", "minecraft:zombie"));
        modelData.put("v.mobCount", intOr(mob, "count", 1));
        modelData.put("v.mobName", strOr(mob, "customName", ""));
        modelData.put("v.mobHp", doubleOr(mob, "health", 20.0));
        modelData.put("v.mobDmg", doubleOr(mob, "damage", 3.0));
        modelData.put("v.mobSpd", doubleOr(mob, "speed", 0.0));
        modelData.put("v.mobArmor", doubleOr(attrs, "minecraft:generic.armor", 0.0));
        modelData.put("v.spawnX", doubleOr(spawn, "x", 0.0));
        modelData.put("v.spawnY", doubleOr(spawn, "y", 64.0));
        modelData.put("v.spawnZ", doubleOr(spawn, "z", 0.0));
        modelData.put("v.spawnDim", strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
        modelData.put("v.areaStatus", area1 != null && area2 != null
            ? I18n.get("arcadia.admin.wave.area.configured")
            : I18n.get("arcadia.admin.wave.area.unset"));
        putArea(modelData, "a1", area1, spawn);
        putArea(modelData, "a2", area2, spawn);
        modelData.put("v.eqMain", strOr(equipment, "mainHand", ""));
        modelData.put("v.eqOff", strOr(equipment, "offHand", ""));
        modelData.put("v.eqHead", strOr(equipment, "helmet", ""));
        modelData.put("v.eqChest", strOr(equipment, "chestplate", ""));
        modelData.put("v.eqLegs", strOr(equipment, "leggings", ""));
        modelData.put("v.eqFeet", strOr(equipment, "boots", ""));
        modelData.put("v.combatAttackRange", doubleOr(combat, "attackRange", 0.0));
        modelData.put("v.combatAttackCooldown", intOr(combat, "attackCooldownMs", 0));
        modelData.put("v.combatAggroRange", doubleOr(combat, "aggroRange", 0.0));
        modelData.put("v.combatProjectileCooldown", intOr(combat, "projectileCooldownMs", 0));
        modelData.put("v.combatDodgeChance", doubleOr(combat, "dodgeChance", 0.0));
        modelData.put("v.combatDodgeCooldown", intOr(combat, "dodgeCooldownMs", 0));
        modelData.put("v.combatDodgeProjectilesOnly", String.valueOf(boolOr(combat, "dodgeProjectilesOnly", false)));
        modelData.put("v.combatDodgeMessage", strOr(combat, "dodgeMessage", ""));
        modelData.put("v.attrs", objectCsv(attrs));
        modelData.put("v.combat", objectCsv(combat));

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            AdminDungeonWavesScreen.syncWaves(cfg, waves);
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("deleteWave", () -> {
            if (waveIndex < waves.size()) waves.remove(waveIndex);
            if (waves.size() == 0) waves.add(AdminDungeonWavesScreen.newWave(0));
            AdminDungeonWavesScreen.syncWaves(cfg, waves);
            ArcadiaNavigator.back();
        });
        handlers.put("tabWave", () -> switchTab("wave"));
        handlers.put("tabMobs", () -> switchTab("mobs"));
        handlers.put("tabIdentity", () -> switchTab("identity"));
        handlers.put("tabSpawn", () -> switchTab("spawn"));
        handlers.put("tabStats", () -> switchTab("stats"));
        handlers.put("tabEquip", () -> switchTab("equip"));
        handlers.put("tabCombat", () -> switchTab("combat"));
        handlers.put("setTriggerOrdered", () -> {
            wave.addProperty("triggerMode", "ordered");
            panelDirty = true;
        });
        handlers.put("setTriggerTicks", () -> {
            wave.addProperty("triggerMode", "ticks");
            panelDirty = true;
        });
        handlers.put("captureSpawn", () -> {
            capturePlayerPosition(spawn);
            syncSpawnInputs(spawn);
            panelDirty = true;
        });
        handlers.put("captureArea1", () -> {
            JsonObject area = areaPoint(mob, "areaPos1", spawn);
            capturePlayerBlockPosition(area);
            syncAreaInputs("1", area);
            panelDirty = true;
        });
        handlers.put("captureArea2", () -> {
            JsonObject area = areaPoint(mob, "areaPos2", spawn);
            capturePlayerBlockPosition(area);
            syncAreaInputs("2", area);
            panelDirty = true;
        });
        handlers.put("clearArea", () -> {
            mob.remove("areaPos1");
            mob.remove("areaPos2");
            panelDirty = true;
        });
        handlers.put("addMob", () -> {
            mobs.add(AdminDungeonWavesScreen.newMob());
            selectedMobIndex = mobs.size() - 1;
            activeTab = "identity";
            clearDynamicInputStates();
            panelDirty = true;
        });

        for (int i = 0; i < mobs.size(); i++) {
            final int idx = i;
            JsonObject rowMob = mobs.get(i).getAsJsonObject();
            modelData.put("m.mobIndex." + i, String.valueOf(i + 1));
            modelData.put("m.mobType." + i, strOr(rowMob, "mobType", "minecraft:zombie"));
            modelData.put("m.mobName." + i, displayName(rowMob));
            modelData.put("m.mobCount." + i, I18n.get("arcadia.admin.wave.mob.count", intOr(rowMob, "count", 1)));
            modelData.put("m.mobSummary." + i, mobSummary(rowMob));
            modelData.put("m.rowClass." + i, idx == selectedMobIndex ? "wave-mob-row selected" : "wave-mob-row");
            modelData.put("m.selectKey." + i, "selectMob." + i);
            modelData.put("m.delKey." + i, "deleteMob." + i);
            handlers.put("selectMob." + i, () -> {
                selectedMobIndex = idx;
                clearDynamicInputStates();
                panelDirty = true;
            });
            handlers.put("deleteMob." + i, () -> {
                if (mobs.size() <= 1 || idx >= mobs.size()) return;
                mobs.remove(idx);
                selectedMobIndex = Math.max(0, Math.min(selectedMobIndex, mobs.size() - 1));
                clearDynamicInputStates();
                panelDirty = true;
            });
        }

        inputHandlers.put("onWaveName", v -> wave.addProperty("name", clean(v, I18n.get("arcadia.admin.wave.default_name", waveIndex + 1))));
        inputHandlers.put("onWaveOrder", v -> moveWaveFromInput(waves, v));
        inputHandlers.put("onWaveDelay", v -> setInt(wave, "delayTicks", v, 20, 0));
        inputHandlers.put("onWaveMessage", v -> setNullableString(wave, "startMessage", v));
        inputHandlers.put("onWaveGlow", v -> wave.addProperty("glowingAfterDelay", Boolean.parseBoolean(v)));
        inputHandlers.put("onWaveGlowDelay", v -> setInt(wave, "glowingDelaySeconds", v, 60, 0));
        inputHandlers.put("onWaveMobType", v -> mob.addProperty("mobType", clean(v, "minecraft:zombie")));
        inputHandlers.put("onWaveMobCount", v -> setInt(mob, "count", v, 1, 1));
        inputHandlers.put("onWaveMobName", v -> setNullableString(mob, "customName", v));
        inputHandlers.put("onWaveMobHp", v -> { setDoubleFromSlider(mob, "health", v, 20.0, 1.0); panelDirty = true; });
        inputHandlers.put("onWaveMobDmg", v -> { setDoubleFromSlider(mob, "damage", v, 3.0, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveMobSpd", v -> { setDoubleFromSlider(mob, "speed", v, 0.0, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveMobArmor", v -> { setCustomAttribute(mob, "minecraft:generic.armor", v, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveSpawnX", v -> setDouble(spawn, "x", v, 0.0));
        inputHandlers.put("onWaveSpawnY", v -> setDouble(spawn, "y", v, 64.0));
        inputHandlers.put("onWaveSpawnZ", v -> setDouble(spawn, "z", v, 0.0));
        inputHandlers.put("onWaveSpawnDim", v -> spawn.addProperty("dimension", clean(v, AdminUiSuggestions.DEFAULT_DIMENSION)));
        inputHandlers.put("onWaveArea1Dim", v -> areaPoint(mob, "areaPos1", spawn).addProperty("dimension", clean(v, AdminUiSuggestions.DEFAULT_DIMENSION)));
        inputHandlers.put("onWaveArea1X", v -> setInt(areaPoint(mob, "areaPos1", spawn), "x", v, 0, Integer.MIN_VALUE));
        inputHandlers.put("onWaveArea1Y", v -> setInt(areaPoint(mob, "areaPos1", spawn), "y", v, 64, Integer.MIN_VALUE));
        inputHandlers.put("onWaveArea1Z", v -> setInt(areaPoint(mob, "areaPos1", spawn), "z", v, 0, Integer.MIN_VALUE));
        inputHandlers.put("onWaveArea2Dim", v -> areaPoint(mob, "areaPos2", spawn).addProperty("dimension", clean(v, AdminUiSuggestions.DEFAULT_DIMENSION)));
        inputHandlers.put("onWaveArea2X", v -> setInt(areaPoint(mob, "areaPos2", spawn), "x", v, 0, Integer.MIN_VALUE));
        inputHandlers.put("onWaveArea2Y", v -> setInt(areaPoint(mob, "areaPos2", spawn), "y", v, 64, Integer.MIN_VALUE));
        inputHandlers.put("onWaveArea2Z", v -> setInt(areaPoint(mob, "areaPos2", spawn), "z", v, 0, Integer.MIN_VALUE));
        inputHandlers.put("onWaveEqMain", v -> setEquipmentSlot(mob, "mainHand", v));
        inputHandlers.put("onWaveEqOff", v -> setEquipmentSlot(mob, "offHand", v));
        inputHandlers.put("onWaveEqHead", v -> setEquipmentSlot(mob, "helmet", v));
        inputHandlers.put("onWaveEqChest", v -> setEquipmentSlot(mob, "chestplate", v));
        inputHandlers.put("onWaveEqLegs", v -> setEquipmentSlot(mob, "leggings", v));
        inputHandlers.put("onWaveEqFeet", v -> setEquipmentSlot(mob, "boots", v));
        inputHandlers.put("onWaveCombatAttackRange", v -> { setCombatDouble(mob, "attackRange", v, 0.0, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveCombatAttackCooldown", v -> setCombatInt(mob, "attackCooldownMs", v, 0, 0));
        inputHandlers.put("onWaveCombatAggroRange", v -> { setCombatDouble(mob, "aggroRange", v, 0.0, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveCombatProjectileCooldown", v -> setCombatInt(mob, "projectileCooldownMs", v, 0, 0));
        inputHandlers.put("onWaveCombatDodgeChance", v -> { setCombatDouble(mob, "dodgeChance", v, 0.0, 0.0); panelDirty = true; });
        inputHandlers.put("onWaveCombatDodgeCooldown", v -> setCombatInt(mob, "dodgeCooldownMs", v, 0, 0));
        inputHandlers.put("onWaveCombatDodgeProjectilesOnly", v -> getObject(mob, "combat").addProperty("dodgeProjectilesOnly", Boolean.parseBoolean(v)));
        inputHandlers.put("onWaveCombatDodgeMessage", v -> setCombatNullableString(mob, "dodgeMessage", v));
        inputHandlers.put("onWaveAttrs", v -> mob.add("customAttributes", parseDoubleObject(v)));
        inputHandlers.put("onWaveCombat", v -> mob.add("combat", parseMixedObject(v)));

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-wave-detail");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void putTabs(Map<String, String> modelData) {
        modelData.put("tab.wave", String.valueOf("wave".equals(activeTab)));
        modelData.put("tab.mobs", String.valueOf("mobs".equals(activeTab)));
        modelData.put("tab.identity", String.valueOf("identity".equals(activeTab)));
        modelData.put("tab.spawn", String.valueOf("spawn".equals(activeTab)));
        modelData.put("tab.stats", String.valueOf("stats".equals(activeTab)));
        modelData.put("tab.equip", String.valueOf("equip".equals(activeTab)));
        modelData.put("tab.combat", String.valueOf("combat".equals(activeTab)));
        modelData.put("tab.waveLabel", tabLabel("wave", I18n.get("arcadia.admin.wave.tab.wave")));
        modelData.put("tab.mobsLabel", tabLabel("mobs", I18n.get("arcadia.admin.wave.mobs")));
        modelData.put("tab.identityLabel", tabLabel("identity", I18n.get("arcadia.admin.common.identity")));
        modelData.put("tab.spawnLabel", tabLabel("spawn", I18n.get("arcadia.admin.common.spawn")));
        modelData.put("tab.statsLabel", tabLabel("stats", I18n.get("arcadia.admin.common.stats")));
        modelData.put("tab.equipLabel", tabLabel("equip", I18n.get("arcadia.admin.common.equipment")));
        modelData.put("tab.combatLabel", tabLabel("combat", I18n.get("arcadia.admin.common.combat")));
    }

    private void switchTab(String tab) {
        activeTab = tab;
        panelDirty = true;
    }

    private void clearDynamicInputStates() {
        renderContext.clearInputsWithPrefix("wave");
    }

    private void syncSpawnInputs(JsonObject spawn) {
        renderContext.setInputText("waveSpawnX", doubleOr(spawn, "x", 0.0));
        renderContext.setInputText("waveSpawnY", doubleOr(spawn, "y", 64.0));
        renderContext.setInputText("waveSpawnZ", doubleOr(spawn, "z", 0.0));
        renderContext.setInputText("waveSpawnDim", strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
    }

    private void syncAreaInputs(String suffix, JsonObject area) {
        renderContext.setInputText("waveArea" + suffix + "X", intOr(area, "x", 0));
        renderContext.setInputText("waveArea" + suffix + "Y", intOr(area, "y", 64));
        renderContext.setInputText("waveArea" + suffix + "Z", intOr(area, "z", 0));
        renderContext.setInputText("waveArea" + suffix + "Dim", strOr(area, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
    }

    private void moveWaveFromInput(JsonArray waves, String value) {
        int target;
        try { target = Math.max(0, Math.min(waves.size() - 1, Integer.parseInt(value.trim()) - 1)); }
        catch (Exception ignored) { return; }
        if (target == waveIndex) return;
        moveWave(waves, waveIndex, target);
        JsonObject cfg = DungeonEditClient.config();
        AdminDungeonWavesScreen.syncWaves(cfg, waves);
        Minecraft.getInstance().setScreen(new AdminDungeonWaveDetailScreen(dungeonId, dungeonName, target));
    }

    private String tabLabel(String tab, String label) {
        return tab.equals(activeTab) ? "> " + label : label;
    }

    private static JsonObject ensureWave(JsonArray waves, int index) {
        while (waves.size() <= index) {
            waves.add(AdminDungeonWavesScreen.newWave(waves.size()));
        }
        return waves.get(index).getAsJsonObject();
    }

    private static void moveWave(JsonArray waves, int from, int to) {
        if (from < 0 || from >= waves.size() || to < 0 || to >= waves.size() || from == to) return;
        JsonElement moved = waves.remove(from);
        JsonArray reordered = new JsonArray();
        for (int i = 0; i <= waves.size(); i++) {
            if (i == to) reordered.add(moved);
            if (i < waves.size()) reordered.add(waves.get(i));
        }
        while (waves.size() > 0) waves.remove(0);
        for (JsonElement element : reordered) waves.add(element);
    }

    private static String triggerMode(JsonObject wave) {
        return "ticks".equalsIgnoreCase(strOr(wave, "triggerMode", "ordered")) ? "ticks" : "ordered";
    }

    private static JsonArray mobList(JsonObject wave) {
        JsonArray mobs;
        try { mobs = wave.getAsJsonArray("mobs"); }
        catch (Exception ignored) { mobs = null; }
        if (mobs == null) {
            mobs = new JsonArray();
            wave.add("mobs", mobs);
        }
        if (mobs.size() == 0) mobs.add(AdminDungeonWavesScreen.newMob());
        return mobs;
    }

    private static JsonObject spawnPoint(JsonObject mob) {
        JsonObject spawn;
        try { spawn = mob.getAsJsonObject("spawnPoint"); }
        catch (Exception ignored) { spawn = null; }
        if (spawn == null) {
            spawn = new JsonObject();
            spawn.addProperty("dimension", AdminUiSuggestions.DEFAULT_DIMENSION);
            spawn.addProperty("x", 0.0);
            spawn.addProperty("y", 64.0);
            spawn.addProperty("z", 0.0);
            mob.add("spawnPoint", spawn);
        }
        return spawn;
    }

    private static JsonObject getObject(JsonObject owner, String key) {
        JsonObject object;
        try { object = owner.getAsJsonObject(key); }
        catch (Exception ignored) { object = null; }
        if (object == null) {
            object = new JsonObject();
            owner.add(key, object);
        }
        return object;
    }

    private static JsonObject getObjectOrNull(JsonObject owner, String key) {
        try { return owner.getAsJsonObject(key); }
        catch (Exception ignored) { return null; }
    }

    private static JsonObject areaPoint(JsonObject mob, String key, JsonObject fallbackSpawn) {
        JsonObject area = getObjectOrNull(mob, key);
        if (area == null) {
            area = new JsonObject();
            area.addProperty("dimension", strOr(fallbackSpawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
            area.addProperty("x", (int) Math.round(parseDouble(strOr(fallbackSpawn, "x", "0"), 0.0)));
            area.addProperty("y", (int) Math.round(parseDouble(strOr(fallbackSpawn, "y", "64"), 64.0)));
            area.addProperty("z", (int) Math.round(parseDouble(strOr(fallbackSpawn, "z", "0"), 0.0)));
            mob.add(key, area);
        }
        return area;
    }

    private static void putArea(Map<String, String> modelData, String prefix, JsonObject area, JsonObject spawn) {
        modelData.put("v." + prefix + "Dim", area != null ? strOr(area, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION)
            : strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
        modelData.put("v." + prefix + "X", area != null ? intOr(area, "x", 0) : String.valueOf((int) Math.round(parseDouble(strOr(spawn, "x", "0"), 0.0))));
        modelData.put("v." + prefix + "Y", area != null ? intOr(area, "y", 64) : String.valueOf((int) Math.round(parseDouble(strOr(spawn, "y", "64"), 64.0))));
        modelData.put("v." + prefix + "Z", area != null ? intOr(area, "z", 0) : String.valueOf((int) Math.round(parseDouble(strOr(spawn, "z", "0"), 0.0))));
    }

    private static void capturePlayerPosition(JsonObject target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        target.addProperty("dimension", mc.player.level().dimension().location().toString());
        target.addProperty("x", round2(mc.player.getX()));
        target.addProperty("y", round2(mc.player.getY()));
        target.addProperty("z", round2(mc.player.getZ()));
    }

    private static void capturePlayerBlockPosition(JsonObject target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        target.addProperty("dimension", mc.player.level().dimension().location().toString());
        target.addProperty("x", mc.player.blockPosition().getX());
        target.addProperty("y", mc.player.blockPosition().getY());
        target.addProperty("z", mc.player.blockPosition().getZ());
    }

    private static void setInt(JsonObject object, String key, String value, int fallback, int min) {
        try { object.addProperty(key, Math.max(min, Integer.parseInt(value.trim()))); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setDouble(JsonObject object, String key, String value, double fallback) {
        try { object.addProperty(key, Double.parseDouble(value.trim())); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setNullableString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value.trim());
    }

    private static void setDoubleFromSlider(JsonObject object, String key, String value, double fallback, double min) {
        try { object.addProperty(key, Math.max(min, Double.parseDouble(value.trim()))); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setCombatDouble(JsonObject mob, String key, String value, double fallback, double min) {
        JsonObject combat = getObject(mob, "combat");
        try { combat.addProperty(key, Math.max(min, Double.parseDouble(value.trim()))); }
        catch (Exception ignored) { combat.addProperty(key, fallback); }
    }

    private static void setCombatInt(JsonObject mob, String key, String value, int fallback, int min) {
        JsonObject combat = getObject(mob, "combat");
        setInt(combat, key, value, fallback, min);
    }

    private static void setCombatNullableString(JsonObject mob, String key, String value) {
        JsonObject combat = getObject(mob, "combat");
        setNullableString(combat, key, value);
    }

    private static void setEquipmentSlot(JsonObject mob, String slot, String value) {
        JsonObject equipment = getObject(mob, "equipment");
        if (value == null || value.isBlank()) equipment.remove(slot);
        else equipment.addProperty(slot, value.trim());
        if (equipment.size() == 0) mob.remove("equipment");
        else mob.add("equipment", equipment);
    }

    private static void setCustomAttribute(JsonObject mob, String attr, String value, double fallback) {
        JsonObject attrs = getObject(mob, "customAttributes");
        double parsed = fallback;
        try { parsed = Double.parseDouble(value.trim()); } catch (Exception ignored) {}
        if (parsed <= 0.0) attrs.remove(attr);
        else attrs.addProperty(attr, parsed);
        if (attrs.size() == 0) mob.remove("customAttributes");
        else mob.add("customAttributes", attrs);
    }

    private static JsonObject parseDoubleObject(String value) {
        JsonObject object = new JsonObject();
        if (value == null || value.isBlank()) return object;
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && s.contains("="))
            .forEach(entry -> {
                int sep = entry.indexOf('=');
                try {
                    object.addProperty(entry.substring(0, sep).trim(),
                        Double.parseDouble(entry.substring(sep + 1).trim()));
                } catch (NumberFormatException ignored) {}
            });
        return object;
    }

    private static JsonObject parseMixedObject(String value) {
        JsonObject object = new JsonObject();
        if (value == null || value.isBlank()) return object;
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && s.contains("="))
            .forEach(entry -> {
                int sep = entry.indexOf('=');
                String key = entry.substring(0, sep).trim();
                String raw = entry.substring(sep + 1).trim();
                if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                    object.addProperty(key, Boolean.parseBoolean(raw));
                    return;
                }
                try {
                    if (raw.matches("-?\\d+")) object.addProperty(key, Integer.parseInt(raw));
                    else object.addProperty(key, Double.parseDouble(raw));
                } catch (NumberFormatException e) {
                    object.addProperty(key, raw);
                }
            });
        return object;
    }

    private static String objectCsv(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        for (String key : object.keySet()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(key).append("=").append(object.get(key).getAsString());
        }
        return builder.toString();
    }

    private static String mobSummary(JsonObject mob) {
        JsonObject area1 = getObjectOrNull(mob, "areaPos1");
        JsonObject area2 = getObjectOrNull(mob, "areaPos2");
        if (area1 != null && area2 != null) {
            return I18n.get("arcadia.admin.wave.mob.summary.area",
                intOr(mob, "count", 1),
                strOr(area1, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
        }
        JsonObject spawn = spawnPoint(mob);
        return I18n.get("arcadia.admin.wave.mob.summary",
            intOr(mob, "count", 1),
            strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION),
            doubleOr(spawn, "x", 0.0),
            doubleOr(spawn, "y", 64.0),
            doubleOr(spawn, "z", 0.0));
    }

    private static String selectedMobTitle(JsonObject mob, int index) {
        return I18n.get("arcadia.admin.wave.mob.title", index + 1, displayName(mob));
    }

    private static String displayName(JsonObject mob) {
        String customName = strOr(mob, "customName", "");
        return customName.isBlank() ? strOr(mob, "mobType", "minecraft:zombie") : customName;
    }

    private static String clean(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static String strOr(JsonObject o, String k, String def) {
        try { return o.get(k).getAsString(); } catch (Exception e) { return def; }
    }

    private static boolean boolOr(JsonObject o, String k, boolean def) {
        try { return o.get(k).getAsBoolean(); } catch (Exception e) { return def; }
    }

    private static String intOr(JsonObject o, String k, int def) {
        try { return String.valueOf(o.get(k).getAsInt()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static String doubleOr(JsonObject o, String k, double def) {
        try { return String.valueOf(o.get(k).getAsDouble()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
