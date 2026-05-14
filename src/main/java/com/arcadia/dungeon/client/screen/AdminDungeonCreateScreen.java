package com.arcadia.dungeon.client.screen;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import com.arcadia.dungeon.ArcadiaDungeon;
import com.arcadia.dungeon.network.CreateDungeonPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int MARGIN = 8;
    private static final int MAX_W  = 340;
    private static final int MAX_H  = 220;

    private TesseraPanel panel;
    private final com.tesseraui.TesseraRenderContext renderContext = new com.tesseraui.TesseraRenderContext();

    // Champs du formulaire
    private String fId       = "";
    private String fName     = "";
    private String fLives    = "3";

    // Erreurs de validation
    private final List<String> errors = new ArrayList<>();
    private boolean panelDirty = false;

    public AdminDungeonCreateScreen() {
        super(Component.translatable("arcadia.admin.create.screen.title"));
    }

    /** Constructeur avec préremplissage (utilisé par le coller du menu contextuel). */
    public AdminDungeonCreateScreen(String prefillName) {
        super(Component.translatable("arcadia.admin.create.screen.title"));
        this.fName = prefillName != null ? prefillName : "";
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanel();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (panelDirty) { rebuildPanel(); panelDirty = false; }
        super.render(g, mx, my, pt);
        if (panel != null) panel.render(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (panel != null && panel.mouseClicked(mx, my, btn)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (panel != null && panel.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (panel != null && panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected TesseraPanel tesseraRoot() { return panel; }

    // ── Internals ─────────────────────────────────────────────────────────

    private void rebuildPanel() {
        int panelW = Math.max(240, Math.min(MAX_W, width  - MARGIN * 2));
        int panelH = Math.max(160, Math.min(MAX_H, height - MARGIN * 2));
        int px = (width  - panelW) / 2;
        int py = (height - panelH) / 2;

        Map<String, String> modelData = new HashMap<>();
        modelData.put("err.id",    errors.contains("id")    ? I18n.get("arcadia.admin.create.err.id") : "");
        modelData.put("err.name",  errors.contains("name")  ? I18n.get("arcadia.admin.create.err.name") : "");
        modelData.put("err.lives", errors.contains("lives") ? I18n.get("arcadia.admin.create.err.lives") : "");
        modelData.put("err.class.id",    errors.contains("id")    ? "field-error" : "");
        modelData.put("err.class.name",  errors.contains("name")  ? "field-error" : "");
        modelData.put("err.class.lives", errors.contains("lives") ? "field-error" : "");
        modelData.put("prefill.name", fName);

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("create", this::attemptCreate);
        handlers.put("cancel", ArcadiaNavigator::back);

        Map<String, Consumer<String>> inputHandlers = new HashMap<>();
        inputHandlers.put("onId",    t -> fId    = t != null ? t : "");
        inputHandlers.put("onName",  t -> fName  = t != null ? t : "");
        inputHandlers.put("onLives", t -> fLives = t != null ? t : "");

        TesseraModel model = key -> modelData.getOrDefault(key, null);
        TesseraTemplate template = TesseraTemplate.load("arcadia_dungeon:ui/admin/admin-dungeon-create");
        panel = TesseraTemplateRenderer.build(template, model, handlers, inputHandlers, renderContext, px, py, panelW, panelH);
    }

    private void attemptCreate() {
        errors.clear();
        String id   = normalizeId(fId);
        String name = fName.trim();

        if (!isValidId(id))                              errors.add("id");
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

    private static String normalizeId(String raw) {
        String id = raw != null ? raw.trim() : "";
        if (id.isEmpty() || id.contains(":")) return id;
        return ArcadiaDungeon.MODID + ":" + id;
    }

    private static boolean isValidId(String id) {
        return id != null
            && !id.isBlank()
            && id.length() <= 64
            && ResourceLocation.tryParse(id) != null;
    }
}
