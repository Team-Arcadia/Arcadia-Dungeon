package com.arcadia.dungeon.client.screen;

import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-ecran detail d'un boss: options, phases et drops propres au boss.
 */
public final class AdminDungeonBossDetailScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W = 600;
    private static final int MAX_H = 360;

    private final String dungeonId;
    private final String dungeonName;
    private final int bossIndex;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();
    private boolean panelDirty = true;
    private String activeTab = "identity";

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
        modelData.put("v.bossName", strOr(boss, "customName", ""));
        modelData.put("v.bossType", strOr(boss, "type", "minecraft:wither_skeleton"));
        modelData.put("v.bossHp", intOr(boss, "hp", 100));
        modelData.put("v.bossDamage", doubleOr(boss, "baseDamage", 10.0));
        modelData.put("v.bossChance", doubleOr(boss, "spawnChance", 1.0));
        modelData.put("v.bossOptionalChecked", String.valueOf(boolOr(boss, "optional", false)));
        modelData.put("v.bossRequiredChecked", String.valueOf(boolOr(boss, "requiredKill", true)));
        modelData.put("v.spawnAtStartChecked", String.valueOf(boolOr(boss, "spawnAtStart", false)));
        modelData.put("v.showBossBarChecked", String.valueOf(boolOr(boss, "showBossBar", true)));
        modelData.put("v.spawnAfterWave", intOr(boss, "spawnAfterWave", 0));
        JsonObject spawn = getObject(boss, "spawnPoint");
        modelData.put("v.spawnX", doubleOr(spawn, "x", 0.0));
        modelData.put("v.spawnY", doubleOr(spawn, "y", 64.0));
        modelData.put("v.spawnZ", doubleOr(spawn, "z", 0.0));
        modelData.put("v.spawnDim", strOr(spawn, "dimension", AdminUiSuggestions.DEFAULT_DIMENSION));
        modelData.put("v.spawnMessage", strOr(boss, "spawnMessage", ""));
        modelData.put("v.skipMessage", strOr(boss, "skipMessage", ""));
        JsonObject equipment = getObject(boss, "equipment");
        JsonObject attrs = getObject(boss, "customAttributes");
        modelData.put("v.eqMain", strOr(equipment, "mainHand", ""));
        modelData.put("v.eqOff", strOr(equipment, "offHand", ""));
        modelData.put("v.eqHead", strOr(equipment, "helmet", ""));
        modelData.put("v.eqChest", strOr(equipment, "chestplate", ""));
        modelData.put("v.eqLegs", strOr(equipment, "leggings", ""));
        modelData.put("v.eqFeet", strOr(equipment, "boots", ""));
        modelData.put("v.bossArmor", doubleOr(attrs, "minecraft:generic.armor", 0.0));
        modelData.put("v.attrs", objectCsv(attrs));
        modelData.put("v.combat", objectCsv(getObject(boss, "combat")));
        modelData.put("phase.count", String.valueOf(phases.size()));
        modelData.put("reward.count", String.valueOf(rewards.size()));
        modelData.put("tab.identity", String.valueOf("identity".equals(activeTab)));
        modelData.put("tab.spawn", String.valueOf("spawn".equals(activeTab)));
        modelData.put("tab.combat", String.valueOf("combat".equals(activeTab)));
        modelData.put("tab.phases", String.valueOf("phases".equals(activeTab)));
        modelData.put("tab.drops", String.valueOf("drops".equals(activeTab)));
        modelData.put("tab.identityLabel", "identity".equals(activeTab) ? "> Identite" : "Identite");
        modelData.put("tab.spawnLabel", "spawn".equals(activeTab) ? "> Spawn" : "Spawn");
        modelData.put("tab.combatLabel", "combat".equals(activeTab) ? "> Combat" : "Combat");
        modelData.put("tab.phasesLabel", "phases".equals(activeTab) ? "> Phases" : "Phases");
        modelData.put("tab.dropsLabel", "drops".equals(activeTab) ? "> Drops" : "Drops");
        modelData.put("s.entities", AdminUiSuggestions.ENTITIES);
        modelData.put("s.items", AdminUiSuggestions.ITEMS);
        modelData.put("s.dimensions", AdminUiSuggestions.DIMENSIONS);
        modelData.put("s.attributes", AdminUiSuggestions.ATTRIBUTES);
        modelData.put("s.effects", AdminUiSuggestions.EFFECTS);

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("tabIdentity", () -> switchTab("identity"));
        handlers.put("tabSpawn", () -> switchTab("spawn"));
        handlers.put("tabCombat", () -> switchTab("combat"));
        handlers.put("tabPhases", () -> switchTab("phases"));
        handlers.put("tabDrops", () -> switchTab("drops"));
        handlers.put("save", () -> {
            syncBosses(cfg, bosses);
            AdminUiFeedback.saveDungeonConfig(dungeonId);
        });
        handlers.put("deleteBoss", () -> {
            if (bossIndex < bosses.size()) bosses.remove(bossIndex);
            if (bosses.size() == 0) bosses.add(newBoss(0));
            syncBosses(cfg, bosses);
            PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
            ArcadiaNavigator.back();
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
        inputHandlers.put("onBossName", v -> setNullableString(boss, "customName", v));
        inputHandlers.put("onBossType", v -> boss.addProperty("type", clean(v, "minecraft:wither_skeleton")));
        inputHandlers.put("onBossHp", v -> {
            setIntFromSlider(boss, "hp", v, 100, 1);
            panelDirty = true;
        });
        inputHandlers.put("onBossDamage", v -> {
            setDoubleFromSlider(boss, "baseDamage", v, 10.0, 0.0);
            panelDirty = true;
        });
        inputHandlers.put("onBossChance", v -> {
            try { boss.addProperty("spawnChance", clamp01(Double.parseDouble(v.trim()))); } catch (Exception ignored) {}
            panelDirty = true;
        });
        inputHandlers.put("onBossArmor", v -> {
            setCustomAttribute(boss, "minecraft:generic.armor", v, 0.0);
            panelDirty = true;
        });
        inputHandlers.put("onBossOptional", v -> boss.addProperty("optional", Boolean.parseBoolean(v)));
        inputHandlers.put("onBossRequired", v -> boss.addProperty("requiredKill", Boolean.parseBoolean(v)));
        inputHandlers.put("onSpawnAtStart", v -> boss.addProperty("spawnAtStart", Boolean.parseBoolean(v)));
        inputHandlers.put("onBossBar", v -> boss.addProperty("showBossBar", Boolean.parseBoolean(v)));
        inputHandlers.put("onSpawnAfterWave", v -> { try { boss.addProperty("spawnAfterWave", Math.max(0, Integer.parseInt(v.trim()))); } catch (Exception ignored) {} });
        inputHandlers.put("onSpawnX", v -> setDouble(spawn, "x", v, 0.0));
        inputHandlers.put("onSpawnY", v -> setDouble(spawn, "y", v, 64.0));
        inputHandlers.put("onSpawnZ", v -> setDouble(spawn, "z", v, 0.0));
        inputHandlers.put("onSpawnDim", v -> spawn.addProperty("dimension", clean(v, AdminUiSuggestions.DEFAULT_DIMENSION)));
        inputHandlers.put("onSpawnMessage", v -> setNullableString(boss, "spawnMessage", v));
        inputHandlers.put("onSkipMessage", v -> setNullableString(boss, "skipMessage", v));
        inputHandlers.put("onEqMain", v -> setEquipmentSlot(boss, "mainHand", v));
        inputHandlers.put("onEqOff", v -> setEquipmentSlot(boss, "offHand", v));
        inputHandlers.put("onEqHead", v -> setEquipmentSlot(boss, "helmet", v));
        inputHandlers.put("onEqChest", v -> setEquipmentSlot(boss, "chestplate", v));
        inputHandlers.put("onEqLegs", v -> setEquipmentSlot(boss, "leggings", v));
        inputHandlers.put("onEqFeet", v -> setEquipmentSlot(boss, "boots", v));
        inputHandlers.put("onAttrs", v -> boss.add("customAttributes", parseDoubleObject(v)));
        inputHandlers.put("onCombat", v -> boss.add("combat", parseMixedObject(v)));

        fillPhaseRows(modelData, inputHandlers, handlers, phases, boss);
        fillRewardRows(modelData, inputHandlers, handlers, rewards, boss);

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-boss-detail");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
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
            modelData.put("p.phaseMessage." + i, strOr(ph, "phaseStartMessage", ""));
            modelData.put("p.phaseSummons." + i, mobsCsv(getArray(ph, "summonMobs")));
            modelData.put("p.phaseEffects." + i, effectsCsv(getArray(ph, "playerEffects")));
            modelData.put("p.phaseCommands." + i, stringArrayCsv(getArray(ph, "phaseCommands"), "; "));
            modelData.put("p.entitySuggestions." + i, AdminUiSuggestions.ENTITIES);
            modelData.put("p.effectSuggestions." + i, AdminUiSuggestions.EFFECTS);
            modelData.put("p.phaseHpId." + i, "phaseHp_" + i);
            modelData.put("p.phaseDmgId." + i, "phaseDmg_" + i);
            modelData.put("p.phaseSpdId." + i, "phaseSpd_" + i);
            modelData.put("p.phaseMessageId." + i, "phaseMessage_" + i);
            modelData.put("p.phaseSummonsId." + i, "phaseSummons_" + i);
            modelData.put("p.phaseEffectsId." + i, "phaseEffects_" + i);
            modelData.put("p.phaseCommandsId." + i, "phaseCommands_" + i);
            modelData.put("p.phaseHpKey." + i, "onPhaseHp." + i);
            modelData.put("p.phaseDmgKey." + i, "onPhaseDmg." + i);
            modelData.put("p.phaseSpdKey." + i, "onPhaseSpd." + i);
            modelData.put("p.phaseMessageKey." + i, "onPhaseMessage." + i);
            modelData.put("p.phaseSummonsKey." + i, "onPhaseSummons." + i);
            modelData.put("p.phaseEffectsKey." + i, "onPhaseEffects." + i);
            modelData.put("p.phaseCommandsKey." + i, "onPhaseCommands." + i);
            modelData.put("p.phaseDelKey." + i, "delPhase." + i);

            inputHandlers.put("onPhaseHp." + i, v -> {
                setIntFromSlider(ph, "triggerHpPercent", v, 50, 1);
                panelDirty = true;
            });
            inputHandlers.put("onPhaseDmg." + i, v -> {
                setDoubleFromSlider(ph, "damageMultiplier", v, 1.0, 0.0);
                panelDirty = true;
            });
            inputHandlers.put("onPhaseSpd." + i, v -> {
                setDoubleFromSlider(ph, "speedMultiplier", v, 1.0, 0.0);
                panelDirty = true;
            });
            inputHandlers.put("onPhaseMessage." + i, v -> setNullableString(ph, "phaseStartMessage", v));
            inputHandlers.put("onPhaseSummons." + i, v -> ph.add("summonMobs", parseMobs(v)));
            inputHandlers.put("onPhaseEffects." + i, v -> ph.add("playerEffects", parseEffects(v)));
            inputHandlers.put("onPhaseCommands." + i, v -> ph.add("phaseCommands", parseStringArray(v, ";")));
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
            modelData.put("r.itemSuggestions." + i, AdminUiSuggestions.ITEMS);
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
        renderContext.clearInputsMatching(key ->
            key.startsWith("phase")
                || key.startsWith("reward"));
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

    private static String clean(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static void setNullableString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value.trim());
    }

    private static void setDouble(JsonObject object, String key, String value, double fallback) {
        try { object.addProperty(key, Double.parseDouble(value.trim())); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setIntFromSlider(JsonObject object, String key, String value, int fallback, int min) {
        try { object.addProperty(key, Math.max(min, Math.round(Float.parseFloat(value.trim())))); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setDoubleFromSlider(JsonObject object, String key, String value, double fallback, double min) {
        try { object.addProperty(key, Math.max(min, Double.parseDouble(value.trim()))); }
        catch (Exception ignored) { object.addProperty(key, fallback); }
    }

    private static void setEquipmentSlot(JsonObject owner, String slot, String value) {
        JsonObject equipment = getObject(owner, "equipment");
        if (value == null || value.isBlank()) equipment.remove(slot);
        else equipment.addProperty(slot, value.trim());
        if (equipment.size() == 0) owner.remove("equipment");
        else owner.add("equipment", equipment);
    }

    private static void setCustomAttribute(JsonObject owner, String attr, String value, double fallback) {
        JsonObject attrs = getObject(owner, "customAttributes");
        double parsed = fallback;
        try { parsed = Double.parseDouble(value.trim()); } catch (Exception ignored) {}
        if (parsed <= 0.0) attrs.remove(attr);
        else attrs.addProperty(attr, parsed);
        if (attrs.size() == 0) owner.remove("customAttributes");
        else owner.add("customAttributes", attrs);
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

    private static JsonArray parseMobs(String value) {
        JsonArray mobs = new JsonArray();
        if (value == null || value.isBlank()) return mobs;
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(entry -> {
                String[] parts = entry.split(":");
                JsonObject mob = new JsonObject();
                if (parts.length >= 3) {
                    mob.addProperty("mobType", parts[0] + ":" + parts[1]);
                    mob.addProperty("count", parseInt(parts[2], 1));
                } else if (parts.length == 2) {
                    mob.addProperty("mobType", parts[0]);
                    mob.addProperty("count", parseInt(parts[1], 1));
                } else {
                    mob.addProperty("mobType", entry);
                    mob.addProperty("count", 1);
                }
                mobs.add(mob);
            });
        return mobs;
    }

    private static JsonArray parseEffects(String value) {
        JsonArray effects = new JsonArray();
        if (value == null || value.isBlank()) return effects;
        Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(entry -> {
                String[] parts = entry.split(":");
                JsonObject effect = new JsonObject();
                if (parts.length >= 2) effect.addProperty("effect", parts[0] + ":" + parts[1]);
                else effect.addProperty("effect", entry);
                effect.addProperty("durationSeconds", parts.length >= 3 ? parseInt(parts[2], 10) : 10);
                effect.addProperty("amplifier", parts.length >= 4 ? parseInt(parts[3], 0) : 0);
                effects.add(effect);
            });
        return effects;
    }

    private static JsonArray parseStringArray(String value, String delimiter) {
        JsonArray array = new JsonArray();
        if (value == null || value.isBlank()) return array;
        Arrays.stream(value.split(delimiter))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(array::add);
        return array;
    }

    private static String objectCsv(JsonObject object) {
        StringBuilder builder = new StringBuilder();
        for (String key : object.keySet()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(key).append("=").append(object.get(key).getAsString());
        }
        return builder.toString();
    }

    private static String mobsCsv(JsonArray mobs) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mobs.size(); i++) {
            if (i > 0) builder.append(", ");
            JsonObject mob = mobs.get(i).getAsJsonObject();
            builder.append(strOr(mob, "mobType", "minecraft:zombie"))
                .append(":")
                .append(intOr(mob, "count", 1));
        }
        return builder.toString();
    }

    private static String effectsCsv(JsonArray effects) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) builder.append(", ");
            JsonObject effect = effects.get(i).getAsJsonObject();
            builder.append(strOr(effect, "effect", "minecraft:slowness"))
                .append(":")
                .append(intOr(effect, "durationSeconds", 10))
                .append(":")
                .append(intOr(effect, "amplifier", 0));
        }
        return builder.toString();
    }

    private static String stringArrayCsv(JsonArray values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(delimiter);
            builder.append(values.get(i).getAsString());
        }
        return builder.toString();
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

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
