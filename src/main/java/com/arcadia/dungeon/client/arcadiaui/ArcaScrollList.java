package com.arcadia.dungeon.client.arcadiaui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class ArcaScrollList extends ArcaElement {

    private int scrollOffset = 0;
    private final int rowH;
    private final List<ArcadiaWidget> rows = new ArrayList<>();

    public ArcaScrollList(int x, int y, int w, int h, int rowH) {
        super(x, y, w, h);
        this.rowH = rowH;
    }

    public void setItems(List<? extends ArcadiaWidget> items) {
        rows.clear();
        rows.addAll(items);
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my) {
        g.enableScissor(x, y, x + width, y + height);
        int ry = y - scrollOffset;
        for (ArcadiaWidget row : rows) {
            if (ry + rowH >= y && ry < y + height) {
                row.setPosition(x, ry);
                row.setSize(width, rowH);
                row.render(g, mx, my);
            }
            ry += rowH;
        }
        g.disableScissor();
        renderScrollbar(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!isInBounds(mx, my)) return false;
        int ry = y - scrollOffset;
        for (ArcadiaWidget row : rows) {
            if (ry + rowH >= y && ry < y + height) {
                row.setPosition(x, ry);
                row.setSize(width, rowH);
                if (row.mouseClicked(mx, my, btn)) return true;
            }
            ry += rowH;
        }
        return false;
    }

    public boolean mouseScrolled(double dy) {
        scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) (dy * rowH)));
        return true;
    }

    private boolean isInBounds(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * rowH - height);
    }

    private void renderScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int barH = Math.max(10, height * height / (rows.size() * rowH));
        int barY = y + (int) ((long) scrollOffset * (height - barH) / max);
        int barX = x + width - 3;
        g.fill(barX, y, barX + 3, y + height, 0x20FFFFFF);
        g.fill(barX, barY, barX + 3, barY + barH, 0x80FFFFFF);
    }
}
