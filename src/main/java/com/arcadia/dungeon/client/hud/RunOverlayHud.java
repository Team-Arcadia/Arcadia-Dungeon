package com.arcadia.dungeon.client.hud;

import com.arcadia.dungeon.client.state.PlayerHubPreferences;
import com.arcadia.dungeon.client.state.PlayerProgressClient;
import com.arcadia.dungeon.client.state.RunStateClient;
import com.arcadia.dungeon.network.RunStatePayload;
import com.tesseraui.TesseraPalette;
import com.tesseraui.TesseraToast;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * In-run overlay rendered directly on the Minecraft HUD layer.
 */
public final class RunOverlayHud implements LayeredDraw.Layer {

    public static final RunOverlayHud INSTANCE = new RunOverlayHud();

    private static final int RUN_X = 10;
    private static final int RUN_Y = 10;
    private static final int RUN_W = 132;
    private static final int RUN_H = 39;
    private static final int BOSS_MAX_W = 348;
    private static final int BOSS_H = 30;
    private static final int HOTBAR_W = 226;
    private static final int HOTBAR_H = 30;
    private static final int VITAL_W = 168;
    private static final int VITAL_MIN_W = 96;
    private static final int VITAL_H = 42;
    private static final int SLOT = 20;
    private static final int BG = 0xB8120D09;
    private static final int BG_SOFT = 0x8F1F1812;
    private static final int COPPER_GLASS = 0x90462B13;
    private static final int GOLD_SOFT = 0x55F0B27A;
    private static final int BAR_BG = 0xD00A0705;
    private static final int LOW_LIFE = 1;
    private static volatile boolean previewEnabled = false;
    private static volatile long previewStartMs = System.currentTimeMillis();

    private RunOverlayHud() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean preview = previewEnabled;
        if (minecraft.screen != null && !preview) return;

        if (PlayerHubPreferences.toasts()) {
            TesseraToast.render(graphics, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        }
        if (!PlayerHubPreferences.hud() && !preview) return;

        RunStatePayload state = RunStateClient.getState().orElse(null);
        Font font = minecraft.font;
        long now = System.currentTimeMillis();
        if (state == null || !"IN_PROGRESS".equals(state.phase())) {
            if (!preview) return;
            state = previewState(now);
        }

        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();

        drawRunPanel(graphics, font, state, now);
        if (state.hasBoss() && state.bossHpMax() > 0) {
            drawBossPanel(graphics, font, state, sw);
        }
        drawVitals(graphics, font, minecraft, sw, sh);
        drawRunHotbar(graphics, font, minecraft, state, sw, sh);
    }

    public static void setPreviewEnabled(boolean enabled) {
        previewEnabled = enabled;
        if (enabled) {
            previewStartMs = System.currentTimeMillis();
        }
    }

    public static boolean previewEnabled() {
        return previewEnabled;
    }

    public static boolean shouldReplaceVanillaHotbar() {
        if (previewEnabled) return true;
        if (!PlayerHubPreferences.hud()) return false;
        return RunStateClient.getState()
            .map(state -> "IN_PROGRESS".equals(state.phase()))
            .orElse(false);
    }

    public static boolean shouldReplaceVanillaSurvivalBars() {
        return shouldReplaceVanillaHotbar();
    }

    private static RunStatePayload previewState(long now) {
        int hp = 65 + (int) (20 * Math.sin(now / 450.0D));
        return new RunStatePayload(
            "debug-preview",
            "arcadia_dungeon:debug",
            "IN_PROGRESS",
            0,
            2,
            3,
            1,
            previewStartMs,
            true,
            "minecraft:wither_skeleton",
            Math.max(1, hp),
            100,
            1,
            List.of("Admin"),
            0L,
            now
        );
    }

    private static void drawRunPanel(GuiGraphics graphics, Font font, RunStatePayload state, long now) {
        int lives = state.livesRemaining();
        boolean danger = lives >= 0 && lives <= LOW_LIFE;
        boolean pulse = danger && (now % 800L) < 400L;
        int primaryColor = pulse ? TesseraPalette.DANGER : TesseraPalette.CREAM;

        String time = formatElapsed(now, state.startTimestampMs());
        String wave = I18n.get("arcadia.hud.wave", Math.max(1, state.currentWaveIndex() + 1));
        String players = formatPlayers(state.playerNames());
        String livesText = lives < 0
            ? I18n.get("arcadia.hud.lives", "\u221E")
            : I18n.get("arcadia.hud.lives", lives);

        drawFrame(graphics, RUN_X, RUN_Y, RUN_W, RUN_H, danger ? TesseraPalette.DANGER : TesseraPalette.COPPER);
        graphics.fill(RUN_X + 1, RUN_Y + 1, RUN_X + RUN_W - 1, RUN_Y + 10, 0x8A2A1A08);
        graphics.fill(RUN_X + 6, RUN_Y + 5, RUN_X + 19, RUN_Y + 6, TesseraPalette.COPPER_HI);
        drawClippedString(graphics, font, I18n.get("arcadia.hud.title"), RUN_X + 25, RUN_Y + 3,
            RUN_W - 33, TesseraPalette.COPPER_HI);

        graphics.drawString(font, time, RUN_X + 8, RUN_Y + 17, primaryColor, true);
        graphics.drawString(font, livesText, RUN_X + RUN_W - 8 - font.width(livesText), RUN_Y + 17,
            danger ? TesseraPalette.DANGER : TesseraPalette.CREAM, true);
        drawClippedString(graphics, font, wave, RUN_X + 8, RUN_Y + 29, 54, TesseraPalette.CREAM_DIM);
        if (!players.isBlank()) {
            drawClippedString(graphics, font, players, RUN_X + 68, RUN_Y + 29, RUN_W - 76, TesseraPalette.TEXT_MUTE);
        }
    }

    private static void drawBossPanel(GuiGraphics graphics, Font font, RunStatePayload state, int screenWidth) {
        int y = RUN_Y;
        int idealW = Math.max(230, Math.min(BOSS_MAX_W, screenWidth - 300));
        int idealX = (screenWidth - idealW) / 2;
        int minX = RUN_X + RUN_W + 14;
        int rightPadding = 10;
        int x;
        int w;
        if (idealX >= minX) {
            x = idealX;
            w = idealW;
        } else {
            x = minX;
            w = Math.max(190, Math.min(BOSS_MAX_W, screenWidth - x - rightPadding));
        }
        int hpCurrent = Math.max(0, state.bossHpCurrent());
        int hpMax = Math.max(1, state.bossHpMax());
        float ratio = Math.max(0.0F, Math.min(1.0F, hpCurrent / (float) hpMax));
        int fillW = Math.max(0, Math.round((w - 18) * ratio));
        String title = I18n.get("arcadia.hud.boss", cleanId(state.bossType()), state.bossPhaseIndex() + 1);
        String hp = I18n.get("arcadia.hud.boss_hp", hpCurrent, hpMax);

        drawFrame(graphics, x, y, w, BOSS_H, TesseraPalette.DANGER);
        drawClippedString(graphics, font, title, x + 10, y + 6, w - 106, TesseraPalette.CREAM);
        graphics.drawString(font, hp, x + w - 10 - font.width(hp), y + 6, TesseraPalette.CREAM_DIM, true);
        drawBar(graphics, x + 10, y + 21, w - 20, 5, ratio, TesseraPalette.DANGER, 0xFF7A2E28);
        graphics.fill(x + 10 + fillW, y + 19, x + 11 + fillW, y + 27, TesseraPalette.CREAM);
    }

    private static void drawRunHotbar(GuiGraphics graphics, Font font, Minecraft minecraft, RunStatePayload state,
                                      int sw, int sh) {
        int x = (sw - HOTBAR_W) / 2;
        int y = sh - HOTBAR_H - 5;
        Player player = minecraft.player;
        int selected = player != null ? player.getInventory().selected : 0;
        drawFrame(graphics, x, y, HOTBAR_W, HOTBAR_H, TesseraPalette.COPPER_DEEP);
        graphics.fill(x + 1, y + 1, x + HOTBAR_W - 1, y + HOTBAR_H - 1, 0x9B110C08);
        graphics.fill(x + 8, y + 4, x + HOTBAR_W - 8, y + 5, COPPER_GLASS);

        int slotsX = x + 6;
        int slotsY = y + 6;
        for (int i = 0; i < 9; i++) {
            int sx = slotsX + i * (SLOT + 4);
            boolean active = i == selected;
            int border = active ? TesseraPalette.COPPER_HI : TesseraPalette.COPPER_DEEP;
            if (active) {
                graphics.fill(sx - 2, slotsY - 3, sx + SLOT + 2, slotsY + SLOT + 3, GOLD_SOFT);
                graphics.renderOutline(sx - 2, slotsY - 3, SLOT + 4, SLOT + 6, TesseraPalette.COPPER_HI);
            }
            graphics.fill(sx, slotsY, sx + SLOT, slotsY + SLOT, active ? 0xE03A2510 : 0xB00E0B07);
            graphics.renderOutline(sx, slotsY, SLOT, SLOT, border);
            ItemStack stack = hotbarStack(player, i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 2, slotsY + 2);
                graphics.renderItemDecorations(font, stack, sx + 2, slotsY + 2);
            }
        }
    }

    private static void drawVitals(GuiGraphics graphics, Font font, Minecraft minecraft, int sw, int sh) {
        Player player = minecraft.player;
        if (player == null) return;

        int hotbarX = (sw - HOTBAR_W) / 2;
        int x = 10;
        int y = sh - VITAL_H - 8;
        int availableW = Math.max(VITAL_MIN_W, hotbarX - x - 12);
        int panelW = Math.max(VITAL_MIN_W, Math.min(VITAL_W, availableW));

        float healthMax = Math.max(1.0F, player.getMaxHealth());
        float health = Math.max(0.0F, Math.min(healthMax, player.getHealth()));
        float absorption = Math.max(0.0F, player.getAbsorptionAmount());
        float healthRatio = Math.max(0.0F, Math.min(1.0F, health / healthMax));
        float absorptionRatio = Math.max(0.0F, Math.min(1.0F, absorption / healthMax));

        int food = player.getFoodData().getFoodLevel();
        float saturation = player.getFoodData().getSaturationLevel();
        float foodRatio = Math.max(0.0F, Math.min(1.0F, food / 20.0F));
        float saturationRatio = Math.max(0.0F, Math.min(1.0F, saturation / 20.0F));

        drawFrame(graphics, x, y, panelW, VITAL_H, TesseraPalette.COPPER_DEEP);
        graphics.fill(x + 1, y + 1, x + panelW - 1, y + VITAL_H - 1, 0x76110C08);

        int barW = Math.max(72, panelW - 20);
        drawLabeledBar(graphics, font, x + 10, y + 12, barW, 5,
            I18n.get("arcadia.hud.vitals.health"),
            Math.round(health) + "/" + Math.round(healthMax),
            healthRatio,
            health <= healthMax * 0.3F ? TesseraPalette.DANGER : 0xFFE44848,
            0xFF8D2323);
        if (absorptionRatio > 0.0F) {
            int absorbW = Math.round(barW * absorptionRatio);
            graphics.fill(x + 10, y + 10, x + 10 + absorbW, y + 11, TesseraPalette.WARN);
        }

        drawLabeledBar(graphics, font, x + 10, y + 29, barW, 5,
            I18n.get("arcadia.hud.vitals.food"),
            food + "/20",
            foodRatio,
            TesseraPalette.WARN,
            0xFF9C6D18);
        if (saturationRatio > 0.0F) {
            int satW = Math.round(barW * saturationRatio);
            graphics.fill(x + 10, y + 33, x + 10 + satW, y + 34, TesseraPalette.CREAM_DIM);
        }
    }

    private static void drawLabeledBar(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                       String label, String value, float ratio, int fill, int fillDark) {
        drawClippedString(graphics, font, label, x, y - 8, 58, TesseraPalette.TEXT_MUTE);
        graphics.drawString(font, value, x + w - font.width(value), y - 8, TesseraPalette.CREAM_DIM, true);
        drawBar(graphics, x, y, w, h, ratio, fill, fillDark);
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int w, int h, int borderColor) {
        graphics.fill(x, y, x + w, y + h, BG);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, TesseraPalette.COPPER_DEEP);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, TesseraPalette.COPPER_DEEP);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG_SOFT);
        graphics.fill(x + 3, y + 3, x + 12, y + 4, borderColor);
        graphics.fill(x + 3, y + 3, x + 4, y + 12, borderColor);
        graphics.fill(x + w - 12, y + h - 4, x + w - 3, y + h - 3, TesseraPalette.COPPER_DEEP);
        graphics.fill(x + w - 4, y + h - 12, x + w - 3, y + h - 3, TesseraPalette.COPPER_DEEP);
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio, int fill, int fillDark) {
        graphics.fill(x, y, x + w, y + h, BAR_BG);
        int fillW = Math.max(0, Math.min(w, Math.round(w * ratio)));
        graphics.fill(x, y, x + fillW, y + h, fillDark);
        graphics.fill(x, y, x + fillW, y + Math.max(1, h - 2), fill);
        for (int i = 1; i < 6; i++) {
            int tickX = x + i * w / 6;
            graphics.fill(tickX, y, tickX + 1, y + h, 0x66120D09);
        }
    }

    private static void drawClippedString(GuiGraphics graphics, Font font, String text, int x, int y, int maxWidth, int color) {
        String value = fit(font, text, maxWidth);
        graphics.drawString(font, value, x, y, color, false);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(builder.toString() + c) + suffixWidth > maxWidth) break;
            builder.append(c);
        }
        return builder + suffix;
    }

    private static String formatElapsed(long nowMs, long startMs) {
        long elapsed = Math.max(0L, (nowMs - startMs) / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", elapsed / 60L, elapsed % 60L);
    }

    private static String formatPlayers(List<String> playerNames) {
        if (playerNames == null || playerNames.isEmpty()) return "";
        if (playerNames.size() == 1) return playerNames.getFirst();
        int extra = playerNames.size() - 1;
        return I18n.get("arcadia.hud.players", playerNames.getFirst(), extra);
    }

    private static ItemStack hotbarStack(Player player, int slot) {
        if (player != null) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.isEmpty()) return stack;
        }
        if (!previewEnabled) return ItemStack.EMPTY;
        return switch (slot) {
            case 0 -> stackFromId(PlayerProgressClient.customMainItem());
            case 1 -> stackFromId(PlayerProgressClient.customOffItem());
            case 2 -> stackFromId(PlayerProgressClient.customUtilityItem());
            default -> ItemStack.EMPTY;
        };
    }

    private static ItemStack stackFromId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id != null ? id : "");
        if (location == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private static String cleanId(String id) {
        if (id == null || id.isBlank()) return I18n.get("arcadia.hud.boss.unknown");
        int separator = id.indexOf(':');
        String value = separator >= 0 ? id.substring(separator + 1) : id;
        return value.replace('_', ' ');
    }
}
