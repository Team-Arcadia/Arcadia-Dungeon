package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.network.CreateDungeonPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Formulaire de création d'un donjon (Story 8.3).
 *
 * <p>Validations client-side avant envoi du {@link CreateDungeonPayload}.
 * Le serveur re-valide côté OP2 + regex (Zero Trust).
 */
public final class AdminDungeonCreateScreen extends com.tesseraui.TesseraScreen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 220;

    private TesseraPanel panel;

    // Champs du formulaire
    private String fId       = "";
    private String fName     = "";
    private String fLives    = "3";

    // Erreurs de validation
    private final List<String> errors = new ArrayList<>();
    private boolean panelDirty = false;

    public AdminDungeonCreateScreen() {
        super(Component.literal("Admin — Créer un donjon"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        renderBackground(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("err.id",    errors.contains("id")    ? "ID requis (a-z, 0-9, _)" : "");
        modelData.put("err.name",  errors.contains("name")  ? "Nom requis"               : "");
        modelData.put("err.lives", errors.contains("lives") ? "Vies : 1–99"              : "");
        modelData.put("err.class.id",    errors.contains("id")    ? "field-error" : "");
        modelData.put("err.class.name",  errors.contains("name")  ? "field-error" : "");
        modelData.put("err.class.lives", errors.contains("lives") ? "field-error" : "");

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("create", this::attemptCreate);
        handlers.put("cancel", ArcadiaNavigator::back);

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onId",    t -> fId    = t != null ? t : "");
        inputHandlers.put("onName",  t -> fName  = t != null ? t : "");
        inputHandlers.put("onLives", t -> fLives = t != null ? t : "");

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin-dungeon-create");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, px, py, PANEL_W, PANEL_H);
    }

    private void attemptCreate() {
        errors.clear();
        String id   = fId.trim();
        String name = fName.trim();

        if (id.isEmpty() || !id.matches("[a-z0-9_]+"))  errors.add("id");
        if (name.isEmpty())                              errors.add("name");

        int lives = 3;
        try {
            lives = Integer.parseInt(fLives.trim());
            if (lives < 1 || lives > 99) errors.add("lives");
        } catch (NumberFormatException e) {
            errors.add("lives");
        }

        if (!errors.isEmpty()) { panelDirty = true; return; }

        PacketDistributor.sendToServer(new CreateDungeonPayload(id, name, lives));
        ArcadiaNavigator.back();
    }
}
