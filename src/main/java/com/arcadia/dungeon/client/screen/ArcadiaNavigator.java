package com.arcadia.dungeon.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Navigateur de screens admin Arcadia — pile de navigation push/back.
 *
 * <p>Remplace le pattern parent-field dans tous les admin screens.
 * Utiliser {@link #push(Screen)} pour naviguer vers un sous-écran et
 * {@link #back()} pour revenir à l'écran précédent.
 */
public final class ArcadiaNavigator {

    private ArcadiaNavigator() {}

    private static final Deque<Screen> STACK = new ArrayDeque<>();

    /** Pousse l'écran courant dans la pile et ouvre {@code next}. */
    public static void push(Screen next) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (current != null) STACK.push(current);
        mc.setScreen(next);
    }

    /** Revient à l'écran précédent. Ferme tout si la pile est vide. */
    public static void back() {
        Minecraft mc = Minecraft.getInstance();
        if (!STACK.isEmpty()) mc.setScreen(STACK.pop());
        else mc.setScreen(null);
    }

    /** Ferme tous les screens Arcadia et vide la pile. */
    public static void closeAll() {
        STACK.clear();
        Minecraft.getInstance().setScreen(null);
    }

    /** Vide la pile sans changer l'écran courant (utile au disconnect). */
    public static void reset() {
        STACK.clear();
    }
}
