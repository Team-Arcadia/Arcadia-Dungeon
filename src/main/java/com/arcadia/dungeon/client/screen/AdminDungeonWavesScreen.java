package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Global ordered wave editor. Each row represents one spawn step in run order.
 */
public final class AdminDungeonWavesScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 560;
    private static final int MAX_H = 340;

    private final String dungeonId;
    private final String dungeonName;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;
    private String activeTab = "base";

    public AdminDungeonWavesScreen(String dungeonId, String dungeonName) {
        super(Component.literal("Waves - " + dungeonName));
        this.dungeonId = dungeonId;
        this.dungeonName = dungeonName;
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
        int panelW = Math.max(360, Math.min(MAX_W, width - MARGIN * 2));
        int panelH = Math.max(220, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg = DungeonEditClient.config();
        JsonArray waves = getGlobalWaves(cfg);

        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title", "Vagues - " + dungeonName);
        modelData.put("waves.count", String.valueOf(waves.size()));
        modelData.put("tab.base", String.valueOf("base".equals(activeTab)));
        modelData.put("tab.spawn", String.valueOf("spawn".equals(activeTab)));
        modelData.put("tab.stats", String.valueOf("stats".equals(activeTab)));
        modelData.put("tab.advanced", String.valueOf("advanced".equals(activeTab)));
        modelData.put("tab.baseLabel", "base".equals(activeTab) ? "> Base" : "Base");
        modelData.put("tab.spawnLabel", "spawn".equals(activeTab) ? "> Spawn" : "Spawn");
        modelData.put("tab.statsLabel", "stats".equals(activeTab) ? "> Stats" : "Stats");
        modelData.put("tab.advancedLabel", "advanced".equals(activeTab) ? "> Avance" : "Avance");
        modelData.put("s.entities", AdminUiSuggestions.ENTITIES);
        modelData.put("s.items", AdminUiSuggestions.ITEMS);
        modelData.put("s.dimensions", AdminUiSuggestions.DIMENSIONS);
        modelData.put("s.attributes", AdminUiSuggestions.ATTRIBUTES);

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> AdminUiFeedback.saveDungeonConfig(dungeonId));
        handlers.put("tabBase", () -> switchTab("base"));
        handlers.put("tabSpawn", () -> switchTab("spawn"));
        handlers.put("tabStats", () -> switchTab("stats"));
        handlers.put("tabAdvanced", () -> switchTab("advanced"));
        handlers.put("addWave", () -> {
            JsonObject wave = new JsonObject();
            wave.addProperty("delayTicks", 20);
            JsonArray mobs = new JsonArray();
            mobs.add(newMobAtPlayer());
            wave.add("mobs", mobs);
            waves.add(wave);
            clearWaveInputStates();
            panelDirty = true;
        });

        for (int i = 0; i < waves.size(); i++) {
            final int idx = i;
            JsonObject wave = waves.get(i).getAsJsonObject();
            JsonObject mob = firstMob(wave);
            JsonObject spawn = spawnPoint(mob);
            JsonObject equipment = getObject(mob, "equipment");
            JsonObject combat = getObject(mob, "combat");

            modelData.put("w.waveIndex." + i, String.valueOf(i + 1));
            modelData.put("w.waveName." + i, strOr(wave, "name", "Vague " + (i + 1)));
            modelData.put("w.waveDelay." + i, intOr(wave, "delayTicks", 20));
            modelData.put("w.waveMessage." + i, strOr(wave, "startMessage", ""));
            modelData.put("w.waveGlowChecked." + i, String.valueOf(boolOr(wave, "glowingAfterDelay", true)));
            modelData.put("w.waveGlowDelay." + i, intOr(wave, "glowingDelaySeconds", 60));
            modelData.put("w.mobType." + i, strOr(mob, "mobType", "minecraft:zombie"));
            modelData.put("w.mobCount." + i, intOr(mob, "count", 1));
            modelData.put("w.mobName." + i, strOr(mob, "customName", ""));
            modelData.put("w.mobHp." + i, doubleOr(mob, "health", 20.0));
            modelData.put("w.mobDmg." + i, doubleOr(mob, "damage", 3.0));
            modelData.put("w.mobSpd." + i, doubleOr(mob, "speed", 0.0));
            modelData.put("w.spawnX." + i, doubleOr(spawn, "x", 0.0));
            modelData.put("w.spawnY." + i, doubleOr(spawn, "y", 64.0));
            modelData.put("w.spawnZ." + i, doubleOr(spawn, "z", 0.0));
            modelData.put("w.spawnDim." + i, strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
            JsonObject attrs = getObject(mob, "customAttributes");
            modelData.put("w.mobArmor." + i, doubleOr(attrs, "minecraft:generic.armor", 0.0));
            modelData.put("w.eqMain." + i, strOr(equipment, "mainHand", ""));
            modelData.put("w.eqOff." + i, strOr(equipment, "offHand", ""));
            modelData.put("w.eqHead." + i, strOr(equipment, "helmet", ""));
            modelData.put("w.eqChest." + i, strOr(equipment, "chestplate", ""));
            modelData.put("w.eqLegs." + i, strOr(equipment, "leggings", ""));
            modelData.put("w.eqFeet." + i, strOr(equipment, "boots", ""));
            modelData.put("w.attrs." + i, objectCsv(attrs));
            modelData.put("w.combat." + i, objectCsv(combat));
            modelData.put("w.entitySuggestions." + i, AdminUiSuggestions.ENTITIES);
            modelData.put("w.itemSuggestions." + i, AdminUiSuggestions.ITEMS);
            modelData.put("w.dimensionSuggestions." + i, AdminUiSuggestions.DIMENSIONS);
            modelData.put("w.attributeSuggestions." + i, AdminUiSuggestions.ATTRIBUTES);
            modelData.put("w.nameId." + i, "waveName_" + i);
            modelData.put("w.delayId." + i, "waveDelay_" + i);
            modelData.put("w.messageId." + i, "waveMessage_" + i);
            modelData.put("w.glowDelayId." + i, "waveGlowDelay_" + i);
            modelData.put("w.mobTypeId." + i, "waveMobType_" + i);
            modelData.put("w.mobCountId." + i, "waveMobCount_" + i);
            modelData.put("w.mobNameId." + i, "waveMobName_" + i);
            modelData.put("w.mobHpId." + i, "waveMobHp_" + i);
            modelData.put("w.mobDmgId." + i, "waveMobDmg_" + i);
            modelData.put("w.mobSpdId." + i, "waveMobSpd_" + i);
            modelData.put("w.spawnXId." + i, "waveSpawnX_" + i);
            modelData.put("w.spawnYId." + i, "waveSpawnY_" + i);
            modelData.put("w.spawnZId." + i, "waveSpawnZ_" + i);
            modelData.put("w.spawnDimId." + i, "waveSpawnDim_" + i);
            modelData.put("w.eqMainId." + i, "waveEqMain_" + i);
            modelData.put("w.eqOffId." + i, "waveEqOff_" + i);
            modelData.put("w.eqHeadId." + i, "waveEqHead_" + i);
            modelData.put("w.eqChestId." + i, "waveEqChest_" + i);
            modelData.put("w.eqLegsId." + i, "waveEqLegs_" + i);
            modelData.put("w.eqFeetId." + i, "waveEqFeet_" + i);
            modelData.put("w.attrsId." + i, "waveAttrs_" + i);
            modelData.put("w.combatId." + i, "waveCombat_" + i);
            modelData.put("w.nameKey." + i, "onWaveName." + i);
            modelData.put("w.delayKey." + i, "onWaveDelay." + i);
            modelData.put("w.messageKey." + i, "onWaveMessage." + i);
            modelData.put("w.glowDelayKey." + i, "onWaveGlowDelay." + i);
            modelData.put("w.mobTypeKey." + i, "onWaveMobType." + i);
            modelData.put("w.mobCountKey." + i, "onWaveMobCount." + i);
            modelData.put("w.mobNameKey." + i, "onWaveMobName." + i);
            modelData.put("w.mobHpKey." + i, "onWaveMobHp." + i);
            modelData.put("w.mobDmgKey." + i, "onWaveMobDmg." + i);
            modelData.put("w.mobSpdKey." + i, "onWaveMobSpd." + i);
            modelData.put("w.spawnXKey." + i, "onWaveSpawnX." + i);
            modelData.put("w.spawnYKey." + i, "onWaveSpawnY." + i);
            modelData.put("w.spawnZKey." + i, "onWaveSpawnZ." + i);
            modelData.put("w.spawnDimKey." + i, "onWaveSpawnDim." + i);
            modelData.put("w.armorKey." + i, "onWaveMobArmor." + i);
            modelData.put("w.eqMainKey." + i, "onWaveEqMain." + i);
            modelData.put("w.eqOffKey." + i, "onWaveEqOff." + i);
            modelData.put("w.eqHeadKey." + i, "onWaveEqHead." + i);
            modelData.put("w.eqChestKey." + i, "onWaveEqChest." + i);
            modelData.put("w.eqLegsKey." + i, "onWaveEqLegs." + i);
            modelData.put("w.eqFeetKey." + i, "onWaveEqFeet." + i);
            modelData.put("w.attrsKey." + i, "onWaveAttrs." + i);
            modelData.put("w.combatKey." + i, "onWaveCombat." + i);
            modelData.put("w.glowKey." + i, "onWaveGlow." + i);
            modelData.put("w.captureKey." + i, "captureWaveSpawn." + i);
            modelData.put("w.delKey." + i, "delWave." + i);

            inputHandlers.put("onWaveName." + i, v -> wave.addProperty("name", clean(v, "Vague " + (idx + 1))));
            inputHandlers.put("onWaveDelay." + i, v -> setInt(wave, "delayTicks", v, 20, 0));
            inputHandlers.put("onWaveMessage." + i, v -> setNullableString(wave, "startMessage", v));
            inputHandlers.put("onWaveGlow." + i, v -> wave.addProperty("glowingAfterDelay", Boolean.parseBoolean(v)));
            inputHandlers.put("onWaveGlowDelay." + i, v -> setInt(wave, "glowingDelaySeconds", v, 60, 0));
            inputHandlers.put("onWaveMobType." + i, v -> mob.addProperty("mobType", clean(v, "minecraft:zombie")));
            inputHandlers.put("onWaveMobCount." + i, v -> setInt(mob, "count", v, 1, 1));
            inputHandlers.put("onWaveMobName." + i, v -> setNullableString(mob, "customName", v));
            inputHandlers.put("onWaveMobHp." + i, v -> {
                setDoubleFromSlider(mob, "health", v, 20.0, 1.0);
                panelDirty = true;
            });
            inputHandlers.put("onWaveMobDmg." + i, v -> {
                setDoubleFromSlider(mob, "damage", v, 3.0, 0.0);
                panelDirty = true;
            });
            inputHandlers.put("onWaveMobSpd." + i, v -> {
                setDoubleFromSlider(mob, "speed", v, 0.0, 0.0);
                panelDirty = true;
            });
            inputHandlers.put("onWaveSpawnX." + i, v -> setDouble(spawn, "x", v, 0.0));
            inputHandlers.put("onWaveSpawnY." + i, v -> setDouble(spawn, "y", v, 64.0));
            inputHandlers.put("onWaveSpawnZ." + i, v -> setDouble(spawn, "z", v, 0.0));
            inputHandlers.put("onWaveSpawnDim." + i, v -> spawn.addProperty("dimension", clean(v, AdminUiSuggestions.DEFAULT_DIMENSION)));
            inputHandlers.put("onWaveMobArmor." + i, v -> {
                setCustomAttribute(mob, "minecraft:generic.armor", v, 0.0);
                panelDirty = true;
            });
            inputHandlers.put("onWaveEqMain." + i, v -> setEquipmentSlot(mob, "mainHand", v));
            inputHandlers.put("onWaveEqOff." + i, v -> setEquipmentSlot(mob, "offHand", v));
            inputHandlers.put("onWaveEqHead." + i, v -> setEquipmentSlot(mob, "helmet", v));
            inputHandlers.put("onWaveEqChest." + i, v -> setEquipmentSlot(mob, "chestplate", v));
            inputHandlers.put("onWaveEqLegs." + i, v -> setEquipmentSlot(mob, "leggings", v));
            inputHandlers.put("onWaveEqFeet." + i, v -> setEquipmentSlot(mob, "boots", v));
            inputHandlers.put("onWaveAttrs." + i, v -> mob.add("customAttributes", parseDoubleObject(v)));
            inputHandlers.put("onWaveCombat." + i, v -> mob.add("combat", parseMixedObject(v)));

            handlers.put("captureWaveSpawn." + i, () -> {
                capturePlayerPosition(spawn);
                panelDirty = true;
            });
            handlers.put("delWave." + i, () -> {
                if (idx < waves.size()) {
                    waves.remove(idx);
                    clearWaveInputStates();
                    panelDirty = true;
                }
            });
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-waves");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void switchTab(String tab) {
        this.activeTab = tab;
        this.panelDirty = true;
    }

    private static JsonArray getGlobalWaves(JsonObject cfg) {
        try {
            JsonArray waves = cfg.getAsJsonArray("waves");
            if (waves != null) return waves;
        } catch (Exception ignored) {}

        JsonArray waves = new JsonArray();
        try {
            JsonArray rooms = cfg.getAsJsonArray("rooms");
            if (rooms != null) {
                for (int r = 0; r < rooms.size(); r++) {
                    JsonArray roomWaves = rooms.get(r).getAsJsonObject().getAsJsonArray("waves");
                    if (roomWaves == null) continue;
                    for (int w = 0; w < roomWaves.size(); w++) waves.add(roomWaves.get(w));
                }
            }
        } catch (Exception ignored) {}
        cfg.add("waves", waves);
        return waves;
    }

    private static JsonObject firstMob(JsonObject wave) {
        JsonArray mobs;
        try { mobs = wave.getAsJsonArray("mobs"); }
        catch (Exception ignored) { mobs = null; }
        if (mobs == null) {
            mobs = new JsonArray();
            wave.add("mobs", mobs);
        }
        if (mobs.size() == 0) mobs.add(newMobAtPlayer());
        return mobs.get(0).getAsJsonObject();
    }

    private static JsonObject newMobAtPlayer() {
        JsonObject mob = new JsonObject();
        mob.addProperty("mobType", "minecraft:zombie");
        mob.addProperty("count", 1);
        JsonObject spawn = new JsonObject();
        capturePlayerPosition(spawn);
        mob.add("spawnPoint", spawn);
        return mob;
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

    private static void capturePlayerPosition(JsonObject spawn) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            spawn.addProperty("dimension", AdminUiSuggestions.DEFAULT_DIMENSION);
            spawn.addProperty("x", 0.0);
            spawn.addProperty("y", 64.0);
            spawn.addProperty("z", 0.0);
            return;
        }
        spawn.addProperty("dimension", mc.player.level().dimension().location().toString());
        spawn.addProperty("x", round2(mc.player.getX()));
        spawn.addProperty("y", round2(mc.player.getY()));
        spawn.addProperty("z", round2(mc.player.getZ()));
    }

    private void clearWaveInputStates() {
        renderContext.clearInputsWithPrefix("wave");
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
