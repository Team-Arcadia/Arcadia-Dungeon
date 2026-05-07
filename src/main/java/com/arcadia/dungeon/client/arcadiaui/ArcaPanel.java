package com.arcadia.dungeon.client.arcadiaui;

import com.arcadia.dungeon.ArcadiaDungeon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class ArcaPanel implements ArcadiaWidget {

    public enum Mode { ROW, COLUMN, GRID }

    private record Entry(ArcadiaWidget widget, int flex, String alignSelf, boolean marginTopAuto) {}

    private final Mode mode;
    private final int cols;
    private int x, y, w, h;
    private int gap = 0;
    private int padLeft, padRight, padTop, padBottom;
    private boolean active = true;
    private boolean clip = false;
    private boolean wrap = false;
    private final List<Entry> children = new ArrayList<>();
    private Runnable onClickAction;
    private int background = 0;
    private int borderColor = 0;
    private int borderThickness = 0;

    private int borderTopColor    = 0;
    private int borderBottomColor = 0;
    private int borderLeftColor   = 0;
    private int borderRightColor  = 0;

    // Hover
    private int hoverBackground       = 0;
    private int hoverBorderColor      = 0;
    private int hoverBorderTopColor    = 0;
    private int hoverBorderBottomColor = 0;
    private int hoverBorderLeftColor   = 0;
    private int hoverBorderRightColor  = 0;

    // Décoration coins (pour .arc-panel)
    private int cornerDotSize  = 0;
    private int cornerDotColor = 0;

    private float opacity = 1f;

    private String[] gridTemplateColumns = null;

    private String justifyContent = "flex-start";
    private String alignItems = "center";

    private ArcaPanel(Mode mode, int cols, int x, int y, int w, int h) {
        this.mode = mode;
        this.cols = cols;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        if (mode == Mode.COLUMN) this.alignItems = "stretch";
    }

    // ── Factory methods ────────────────────────────────────────────────────

    public static ArcaPanel row(int x, int y, int w, int h)    { return new ArcaPanel(Mode.ROW, 0, x, y, w, h); }
    public static ArcaPanel column(int x, int y, int w, int h) { return new ArcaPanel(Mode.COLUMN, 0, x, y, w, h); }
    public static ArcaPanel grid(int cols, int x, int y, int w, int h) { return new ArcaPanel(Mode.GRID, cols, x, y, w, h); }

    // ── Builder fluent ─────────────────────────────────────────────────────

    public ArcaPanel add(ArcadiaWidget widget) {
        children.add(new Entry(widget, 0, null, false));
        return this;
    }

    public ArcaPanel add(ArcadiaWidget widget, int flex) {
        children.add(new Entry(widget, flex, null, false));
        return this;
    }

    public ArcaPanel add(ArcadiaWidget widget, int flex, String alignSelf, boolean marginTopAuto) {
        children.add(new Entry(widget, flex, alignSelf, marginTopAuto));
        return this;
    }

    public ArcaPanel gap(int gap) { this.gap = gap; return this; }

    public ArcaPanel padding(int all)                             { padLeft = padRight = padTop = padBottom = all; return this; }
    public ArcaPanel padding(int horizontal, int vertical)        { padLeft = padRight = horizontal; padTop = padBottom = vertical; return this; }
    public ArcaPanel padding(int top, int right, int bottom, int left) { padTop = top; padRight = right; padBottom = bottom; padLeft = left; return this; }

    public ArcaPanel clip(boolean clip)            { this.clip = clip; return this; }
    public ArcaPanel wrap(boolean wrap)            { this.wrap = wrap; return this; }
    public ArcaPanel onClick(Runnable action)      { this.onClickAction = action; return this; }
    public ArcaPanel background(int color)         { this.background = color; return this; }

    public ArcaPanel border(int thickness, int color) {
        this.borderThickness = thickness;
        this.borderColor = color;
        return this;
    }

    public ArcaPanel borderSide(String side, int color) {
        switch (side) {
            case "top"    -> borderTopColor    = color;
            case "bottom" -> borderBottomColor = color;
            case "left"   -> borderLeftColor   = color;
            case "right"  -> borderRightColor  = color;
        }
        return this;
    }

    public ArcaPanel hoverBackground(int color)       { this.hoverBackground = color; return this; }
    public ArcaPanel hoverBorder(int color)           { this.hoverBorderColor = color; return this; }
    public ArcaPanel hoverBorderSide(String side, int color) {
        switch (side) {
            case "top"    -> hoverBorderTopColor    = color;
            case "bottom" -> hoverBorderBottomColor = color;
            case "left"   -> hoverBorderLeftColor   = color;
            case "right"  -> hoverBorderRightColor  = color;
        }
        return this;
    }

    public ArcaPanel cornerDots(int size, int color) {
        this.cornerDotSize = size;
        this.cornerDotColor = color;
        return this;
    }

    public ArcaPanel opacity(float o)      { this.opacity = Math.max(0f, Math.min(1f, o)); return this; }

    public ArcaPanel gridTemplateColumns(String[] tpl) { this.gridTemplateColumns = tpl; return this; }

    public ArcaPanel justifyContent(String value) { if (value != null) this.justifyContent = value; return this; }
    public ArcaPanel alignItems(String value)     { if (value != null) this.alignItems = value; return this; }

    // ── Layout engine ──────────────────────────────────────────────────────

    public void layout() {
        if (children.isEmpty()) return;
        switch (mode) {
            case ROW    -> layoutRow();
            case COLUMN -> layoutColumn();
            case GRID   -> layoutGrid();
        }
    }

    private void layoutRow() {
        int availW = w - padLeft - padRight;
        int availH = h - padTop - padBottom;

        if (wrap) { layoutRowWrap(availW, availH); return; }

        int fixedTotal = 0;
        int totalFlex = 0;
        for (Entry e : children) {
            if (e.flex() == 0) fixedTotal += e.widget().getWidth();
            else totalFlex += e.flex();
        }
        int totalGaps = Math.max(0, children.size() - 1) * gap;
        int remainingForFlex = availW - fixedTotal - totalGaps;

        int startX;
        int extraGap = 0;
        if (totalFlex > 0) {
            startX = x + padLeft;
        } else {
            int contentW = fixedTotal + totalGaps;
            int free = availW - contentW;
            switch (justifyContent) {
                case "flex-end"      -> { startX = x + padLeft + free; }
                case "center"        -> { startX = x + padLeft + free / 2; }
                case "space-between" -> {
                    startX = x + padLeft;
                    if (children.size() > 1) extraGap = free / (children.size() - 1);
                }
                case "space-around" -> {
                    int perItem = children.size() > 0 ? free / children.size() : 0;
                    startX = x + padLeft + perItem / 2;
                    extraGap = perItem;
                }
                default -> { startX = x + padLeft; }
            }
        }

        int curX = startX;
        for (Entry e : children) {
            int childW = (e.flex() > 0 && totalFlex > 0)
                    ? e.flex() * remainingForFlex / totalFlex
                    : e.widget().getWidth();
            int childH = e.widget().getHeight();

            String align = e.alignSelf() != null ? e.alignSelf() : this.alignItems;
            int childY = switch (align) {
                case "flex-start" -> y + padTop;
                case "flex-end"   -> y + padTop + availH - childH;
                case "stretch"    -> { e.widget().setSize(childW, availH); yield y + padTop; }
                default           -> y + padTop + (availH - childH) / 2;
            };

            e.widget().setPosition(curX, childY);
            if (e.flex() > 0) e.widget().setSize(childW, e.widget().getHeight());
            curX += childW + gap + extraGap;
        }
    }

    private void layoutRowWrap(int availW, int availH) {
        int curX = x + padLeft;
        int curY = y + padTop;
        int lineH = 0;
        for (Entry e : children) {
            int childW = e.widget().getWidth();
            int childH = e.widget().getHeight();
            if (curX + childW > x + padLeft + availW && curX > x + padLeft) {
                curX = x + padLeft;
                curY += lineH + gap;
                lineH = 0;
            }
            e.widget().setPosition(curX, curY);
            curX += childW + gap;
            lineH = Math.max(lineH, childH);
        }
    }

    private void layoutColumn() {
        int availW = w - padLeft - padRight;
        int availH = h - padTop - padBottom;

        int fixedTotal = 0;
        int totalFlex = 0;
        for (Entry e : children) {
            if (e.flex() == 0) fixedTotal += e.widget().getHeight();
            else totalFlex += e.flex();
        }
        int totalGaps = Math.max(0, children.size() - 1) * gap;
        int remainingForFlex = availH - fixedTotal - totalGaps;

        int autoMarginIdx = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).marginTopAuto()) { autoMarginIdx = i; break; }
        }

        int startY;
        int extraGap = 0;
        if (totalFlex > 0 || autoMarginIdx >= 0) {
            startY = y + padTop;
        } else {
            int contentH = fixedTotal + totalGaps;
            int free = availH - contentH;
            switch (justifyContent) {
                case "flex-end"      -> { startY = y + padTop + free; }
                case "center"        -> { startY = y + padTop + free / 2; }
                case "space-between" -> {
                    startY = y + padTop;
                    if (children.size() > 1) extraGap = free / (children.size() - 1);
                }
                case "space-around" -> {
                    int perItem = children.size() > 0 ? free / children.size() : 0;
                    startY = y + padTop + perItem / 2;
                    extraGap = perItem;
                }
                default -> { startY = y + padTop; }
            }
        }

        int curY = startY;
        for (int i = 0; i < children.size(); i++) {
            Entry e = children.get(i);

            if (i == autoMarginIdx && totalFlex == 0) {
                int usedSoFar = 0;
                for (int j = 0; j < i; j++) usedSoFar += children.get(j).widget().getHeight() + gap;
                int freeSpace = availH - fixedTotal - totalGaps;
                if (freeSpace > 0) curY = y + padTop + usedSoFar + freeSpace;
            }

            int childH = (e.flex() > 0 && totalFlex > 0)
                    ? e.flex() * remainingForFlex / totalFlex
                    : e.widget().getHeight();

            String align = e.alignSelf() != null ? e.alignSelf() : this.alignItems;
            int childX = switch (align) {
                case "flex-end"   -> x + padLeft + availW - e.widget().getWidth();
                case "center"     -> x + padLeft + (availW - e.widget().getWidth()) / 2;
                case "stretch"    -> { e.widget().setSize(availW, childH); yield x + padLeft; }
                default           -> x + padLeft;
            };

            e.widget().setPosition(childX, curY);
            if (e.flex() > 0) e.widget().setSize(e.widget().getWidth(), childH);
            curY += childH + gap + extraGap;
        }
    }

    private void layoutGrid() {
        int availW = w - padLeft - padRight;

        int[] colWidths;
        int colsCount;
        if (gridTemplateColumns != null && gridTemplateColumns.length > 0) {
            colsCount = gridTemplateColumns.length;
            colWidths = computeGridTrackWidths(gridTemplateColumns, availW, gap);
        } else {
            if (cols <= 0) return;
            colsCount = cols;
            int cellW = (availW - gap * (cols - 1)) / cols;
            colWidths = new int[cols];
            for (int i = 0; i < cols; i++) colWidths[i] = cellW;
        }

        int numRows = (children.size() + colsCount - 1) / colsCount;
        int[] rowHeights = new int[Math.max(1, numRows)];
        for (int i = 0; i < children.size(); i++) {
            int row = i / colsCount;
            rowHeights[row] = Math.max(rowHeights[row], children.get(i).widget().getHeight());
        }

        int[] rowOffsets = new int[Math.max(1, numRows)];
        int cumY = 0;
        for (int r = 0; r < numRows; r++) {
            rowOffsets[r] = cumY;
            cumY += rowHeights[r] + gap;
        }

        int[] colOffsets = new int[colsCount];
        int cumX = 0;
        for (int c = 0; c < colsCount; c++) {
            colOffsets[c] = cumX;
            cumX += colWidths[c] + gap;
        }

        for (int i = 0; i < children.size(); i++) {
            int col = i % colsCount;
            int row = i / colsCount;
            int cx = x + padLeft + colOffsets[col];
            int cy = y + padTop + rowOffsets[row];
            Entry e = children.get(i);
            e.widget().setPosition(cx, cy);
            e.widget().setSize(colWidths[col], e.widget().getHeight());
        }
    }

    private static int[] computeGridTrackWidths(String[] tokens, int availW, int gap) {
        int n = tokens.length;
        int[] widths = new int[n];
        float[] frs = new float[n];
        int fixedPx = 0;
        float totalFr = 0f;
        for (int i = 0; i < n; i++) {
            String tok = tokens[i].trim().toLowerCase(java.util.Locale.ROOT);
            if (tok.endsWith("fr")) {
                try { frs[i] = Float.parseFloat(tok.substring(0, tok.length() - 2)); }
                catch (NumberFormatException e) { frs[i] = 1f; }
                totalFr += frs[i];
            } else if (tok.endsWith("px")) {
                try { widths[i] = Integer.parseInt(tok.substring(0, tok.length() - 2)); }
                catch (NumberFormatException e) { widths[i] = 0; }
                fixedPx += widths[i];
            } else if (tok.equals("auto")) {
                // treat auto as 0-fixed for now (renderer can post-process)
                widths[i] = 0;
            } else {
                try { widths[i] = Integer.parseInt(tok); fixedPx += widths[i]; }
                catch (NumberFormatException e) { widths[i] = 0; }
            }
        }
        int totalGaps = Math.max(0, n - 1) * gap;
        int free = Math.max(0, availW - fixedPx - totalGaps);
        for (int i = 0; i < n; i++) {
            if (frs[i] > 0 && totalFr > 0) widths[i] = (int) (free * frs[i] / totalFr);
        }
        return widths;
    }

    // ── ArcadiaWidget ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        layout();
        boolean hovered = bounds().contains(mx, my);

        int bg = (hovered && hoverBackground != 0) ? hoverBackground : background;
        int bColor = (hovered && hoverBorderColor != 0) ? hoverBorderColor : borderColor;
        int bTop    = (hovered && hoverBorderTopColor    != 0) ? hoverBorderTopColor    : borderTopColor;
        int bBottom = (hovered && hoverBorderBottomColor != 0) ? hoverBorderBottomColor : borderBottomColor;
        int bLeft   = (hovered && hoverBorderLeftColor   != 0) ? hoverBorderLeftColor   : borderLeftColor;
        int bRight  = (hovered && hoverBorderRightColor  != 0) ? hoverBorderRightColor  : borderRightColor;

        boolean useShader = opacity < 1f;
        if (useShader) RenderSystem.setShaderColor(1f, 1f, 1f, opacity);

        if (bg != 0) g.fill(x, y, x + w, y + h, bg);

        if (clip) {
            Matrix4f m = g.pose().last().pose();
            Vector4f tl = new Vector4f(x, y, 0f, 1f).mul(m);
            Vector4f br = new Vector4f(x + w, y + h, 0f, 1f).mul(m);
            g.enableScissor((int) tl.x, (int) tl.y, (int) br.x, (int) br.y);
        }
        for (Entry e : children) {
            e.widget().render(g, mx, my);
        }
        if (clip) g.disableScissor();

        // Borders et coins dessinés APRÈS les enfants pour ne pas être recouverts par leurs fonds
        if (borderThickness > 0 && bColor != 0) {
            int t = borderThickness;
            g.fill(x,         y,         x + w,     y + t,     bColor);
            g.fill(x,         y + h - t, x + w,     y + h,     bColor);
            g.fill(x,         y,         x + t,     y + h,     bColor);
            g.fill(x + w - t, y,         x + w,     y + h,     bColor);
        }

        if (bTop    != 0) g.fill(x,         y,         x + w, y + 1,     bTop);
        if (bBottom != 0) g.fill(x,         y + h - 1, x + w, y + h,     bBottom);
        if (bLeft   != 0) g.fill(x,         y,         x + 1, y + h,     bLeft);
        if (bRight  != 0) g.fill(x + w - 1, y,         x + w, y + h,     bRight);

        // Coins décoratifs
        if (cornerDotSize > 0 && cornerDotColor != 0) {
            int s = cornerDotSize;
            int c = cornerDotColor;
            g.fill(x + 1,         y + 1,         x + 1 + s,         y + 1 + s,         c);
            g.fill(x + w - 1 - s, y + 1,         x + w - 1,         y + 1 + s,         c);
            g.fill(x + 1,         y + h - 1 - s, x + 1 + s,         y + h - 1,         c);
            g.fill(x + w - 1 - s, y + h - 1 - s, x + w - 1,         y + h - 1,         c);
        }

        if (useShader) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && bounds().contains(mx, my) && onClickAction != null) {
            onClickAction.run();
            defocusAll();
            return true;
        }
        int consumedIdx = -1;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).widget().mouseClicked(mx, my, btn)) { consumedIdx = i; break; }
        }
        if (consumedIdx >= 0) {
            ArcadiaWidget hit = children.get(consumedIdx).widget();
            for (Entry e : children) {
                if (e.widget() != hit) e.widget().setFocused(false);
            }
            return true;
        }
        defocusAll();
        return false;
    }

    private void defocusAll() {
        for (Entry e : children) e.widget().setFocused(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Entry e : children) {
            if (e.widget().keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (Entry e : children) {
            if (e.widget().charTyped(c, modifiers)) return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) defocusAll();
    }

    @Override
    public void mouseReleased(double mx, double my, int btn) {
        for (Entry e : children) e.widget().mouseReleased(mx, my, btn);
    }

    @Override
    public Rect bounds() { return new Rect(x, y, w, h); }

    @Override
    public void setActive(boolean active) {
        this.active = active;
        for (Entry e : children) e.widget().setActive(active);
    }

    @Override
    public boolean isActive() { return active; }

    @Override
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    @Override
    public void setSize(int w, int h) { this.w = w; this.h = h; }

    @Override
    public int getWidth()  { return w; }

    @Override
    public int getHeight() { return h; }

    /**
     * Computes the natural content width: sum of children widths + gaps + horizontal paddings + borders.
     * Used to size shrink-to-fit containers (e.g. a row child of a row parent with no explicit width).
     */
    public int fitContentWidth() {
        int contentW = 0;
        if (mode == Mode.ROW) {
            for (int i = 0; i < children.size(); i++) {
                contentW += children.get(i).widget().getWidth();
                if (i < children.size() - 1) contentW += gap;
            }
        } else {
            for (Entry e : children) {
                contentW = Math.max(contentW, e.widget().getWidth());
            }
        }
        return contentW + padLeft + padRight + 2 * borderThickness;
    }
}
