package com.arcadia.dungeon.client.arcadiaui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcaPanelLayoutTest {

    @Test
    void columnLayoutPlacesChildrenVertically() {
        ArcaLabel a = new ArcaLabel(0, 0, 50, 10, "A");
        ArcaLabel b = new ArcaLabel(0, 0, 50, 10, "B");

        ArcaPanel panel = ArcaPanel.column(0, 0, 200, 100).gap(2).add(a).add(b);
        panel.layout();

        assertEquals(0, a.getX());
        assertEquals(0, a.getY());
        assertEquals(0, b.getX());
        assertTrue(b.getY() > a.getY(),
                "second child should be placed below the first (b.y=" + b.getY() + ", a.y=" + a.getY() + ")");
    }
}
