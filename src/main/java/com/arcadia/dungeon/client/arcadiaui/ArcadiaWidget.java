package com.arcadia.dungeon.client.arcadiaui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Core rendering contract for all ArcadiaUI widgets.
 *
 * <p>Implement this interface (or extend {@link ArcaElement}) to create custom widgets
 * that participate in {@link ArcaPanel} layout and receive mouse events.</p>
 */
public interface ArcadiaWidget {

    /** Renders this widget at the given mouse position. */
    void render(GuiGraphics g, int mx, int my);

    /**
     * Handles a mouse button press.
     *
     * @return {@code true} if this widget consumed the event
     */
    boolean mouseClicked(double mx, double my, int btn);

    /** Called when a previously pressed mouse button is released. */
    default void mouseReleased(double mx, double my, int btn) {}

    /** Returns the axis-aligned bounding box of this widget. */
    Rect bounds();

    /** Enables or disables this widget. Disabled widgets ignore input and render dimmed. */
    void setActive(boolean active);

    /** Returns {@code true} if this widget is currently enabled. */
    boolean isActive();

    /** Repositions this widget. Called by {@link ArcaPanel} during layout. */
    void setPosition(int x, int y);

    /** Resizes this widget. Called by {@link ArcaPanel} during layout for flex children. */
    void setSize(int w, int h);

    /** Returns the current pixel width. Used by {@link ArcaPanel} during layout. */
    int getWidth();

    /** Returns the current pixel height. Used by {@link ArcaPanel} during layout. */
    int getHeight();

    /** Handles a key press while focused. Returns {@code true} if consumed. */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** Handles a typed character while focused. Returns {@code true} if consumed. */
    default boolean charTyped(char c, int modifiers) { return false; }

    /** Sets the focus state of this widget. */
    default void setFocused(boolean focused) {}

    /** Returns {@code true} if this widget currently holds keyboard focus. */
    default boolean isFocused() { return false; }
}
