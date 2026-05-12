package com.arcadia.dungeon.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.client.state.DungeonEditClient;
import com.arcadia.dungeon.network.SaveDungeonConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sous-écran — vagues d'une salle spécifique.
 *
 * <p>Les mobs sont édités en format CSV {@code type:count, type:count}.
 */
public final class AdminDungeonWavesScreen extends com.tesseraui.TesseraScreen {

    private static final int MARGIN = 8;
    private static final int MAX_W  = 360;
    private static final int MAX_H  = 270;

    private final String dungeonId;
    private final String dungeonName;
    private final int roomIndex;

    private TesseraPanel panel;
    private final Map<String, com.tesseraui.TesseraInputState> inputStates = new HashMap<>();
    private boolean panelDirty = true;

    public AdminDungeonWavesScreen(String dungeonId, String dungeonName, int roomIndex) {
        super(Component.literal("Waves — " + dungeonName + " / Salle " + (roomIndex + 1)));
        this.dungeonId   = dungeonId;
        this.dungeonName = dungeonName;
        this.roomIndex   = roomIndex;
    }

    @Override protected void init() { super.init(); panelDirty = true; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
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
        int panelW = Math.max(240, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width - panelW) / 2, py = (height - panelH) / 2;

        JsonObject cfg   = DungeonEditClient.config();
        JsonArray  rooms = getRooms(cfg);
        JsonObject room  = roomIndex < rooms.size()
            ? rooms.get(roomIndex).getAsJsonObject() : new JsonObject();
        JsonArray  waves = getWaves(room);

        String roomLabel = I18n.get("arcadia.admin.rooms.room", String.valueOf(roomIndex + 1));
        Map<String, String> modelData = new HashMap<>();
        modelData.put("cfg.title",   "Vagues — " + dungeonName + " / " + roomLabel);
        modelData.put("waves.count", String.valueOf(waves.size()));

        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> inputHandlers = new HashMap<>();

        handlers.put("back", ArcadiaNavigator::back);
        handlers.put("save", () -> {
            PacketDistributor.sendToServer(new SaveDungeonConfigPayload(dungeonId, DungeonEditClient.toJson()));
            ArcadiaNavigator.back();
        });
        handlers.put("addWave", () -> {
            JsonObject w = new JsonObject();
            w.add("mobs", new JsonArray());
            w.addProperty("delayTicks", 20);
            waves.add(w);
            room.add("waves", waves);
            panelDirty = true;
        });

        // v-for="w in waves.count" → varName="w" → all per-row keys prefixed with "w."
        for (int i = 0; i < waves.size(); i++) {
            final int wIdx = i;
            JsonObject wave = waves.get(i).getAsJsonObject();

            modelData.put("w.waveLabel."   + i, I18n.get("arcadia.admin.rooms.wave", String.valueOf(i + 1)));
            modelData.put("w.waveDelay."   + i, intOr(wave, "delayTicks", 20));
            modelData.put("w.waveMobs."    + i, mobsCsv(wave));
            modelData.put("w.waveDelayId." + i, "waveDelay_"  + i);
            modelData.put("w.waveMobsId."  + i, "waveMobs_"   + i);
            modelData.put("w.waveDelayKey."+ i, "onWaveDelay." + i);
            modelData.put("w.waveMobsKey." + i, "onWaveMobs."  + i);
            modelData.put("w.waveDelKey."  + i, "delWave."     + i);

            inputHandlers.put("onWaveDelay." + i, v -> {
                try { wave.addProperty("delayTicks", Integer.parseInt(v.trim())); } catch (Exception ignored) {}
            });
            inputHandlers.put("onWaveMobs." + i, v -> {
                JsonArray mobs = new JsonArray();
                if (v != null && !v.isBlank()) {
                    Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(entry -> {
                            String[] parts = entry.split(":");
                            // Format: namespace:type:count ou type:count
                            // Si 3 parties: namespace:type:count — join first two as mobType
                            JsonObject mob = new JsonObject();
                            if (parts.length >= 3) {
                                String mobType = parts[0] + ":" + parts[1];
                                int count = parseInt(parts[2], 1);
                                mob.addProperty("mobType", mobType);
                                mob.addProperty("count", count);
                            } else if (parts.length == 2) {
                                mob.addProperty("mobType", parts[0]);
                                mob.addProperty("count", parseInt(parts[1], 1));
                            } else {
                                mob.addProperty("mobType", entry);
                                mob.addProperty("count", 1);
                            }
                            mobs.add(mob);
                        });
                }
                wave.add("mobs", mobs);
            });
            handlers.put("delWave." + i, () -> {
                if (wIdx < waves.size()) { waves.remove(wIdx); room.add("waves", waves); panelDirty = true; }
            });
        }

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-waves");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, inputStates, px, py, panelW, panelH);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private static JsonArray getRooms(JsonObject cfg) {
        try { return cfg.getAsJsonArray("rooms"); } catch (Exception e) { return new JsonArray(); }
    }

    private static JsonArray getWaves(JsonObject room) {
        try { return room.getAsJsonArray("waves"); }
        catch (Exception e) { JsonArray a = new JsonArray(); room.add("waves", a); return a; }
    }

    private static String intOr(JsonObject o, String k, int def) {
        try { return String.valueOf(o.get(k).getAsInt()); } catch (Exception e) { return String.valueOf(def); }
    }

    private static String mobsCsv(JsonObject wave) {
        try {
            JsonArray mobs = wave.getAsJsonArray("mobs");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mobs.size(); i++) {
                if (i > 0) sb.append(", ");
                JsonObject m = mobs.get(i).getAsJsonObject();
                sb.append(m.get("mobType").getAsString())
                  .append(":")
                  .append(m.get("count").getAsInt());
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
