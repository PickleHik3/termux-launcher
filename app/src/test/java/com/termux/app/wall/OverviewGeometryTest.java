package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverviewGeometryTest {

    @Test public void uncoveredPagesAreCopiedFromTheWindow() {
        assertEquals(OverviewGeometry.Capture.WINDOW_COPY, OverviewGeometry.captureFor(false, false));
        assertEquals(OverviewGeometry.Capture.WINDOW_COPY, OverviewGeometry.captureFor(false, true));
    }

    @Test public void coveredPagesNeverUseTheWindow() {
        assertEquals(OverviewGeometry.Capture.SUBTREE_DRAW, OverviewGeometry.captureFor(true, false));
        assertEquals(OverviewGeometry.Capture.TEXT, OverviewGeometry.captureFor(true, true));
    }

    @Test public void framePoseIsSeen() {
        assertFalse(OverviewGeometry.framePosed(1f, 1f, 0f, 0f));
        assertTrue(OverviewGeometry.framePosed(0.6f, 0.6f, 0f, 0f));
        assertTrue(OverviewGeometry.framePosed(1f, 1f, 0f, 120f));
    }

    @Test public void onlyTheCentredCardZooms() {
        assertTrue(OverviewGeometry.tapZooms(2, 2f));
        assertTrue(OverviewGeometry.tapZooms(2, 2.2f));
        assertFalse(OverviewGeometry.tapZooms(3, 2f));
        assertFalse(OverviewGeometry.tapZooms(1, 2f));
    }

    @Test public void chipTextShrinksToFit() {
        // Width is 10 px per size unit: 100 px available fits size 10.
        assertEquals(10f, OverviewGeometry.fitTextSize(14f, 8f, 100f, s -> s * 10f), 0.01f);
        assertEquals(14f, OverviewGeometry.fitTextSize(14f, 8f, 500f, s -> s * 10f), 0.01f);
        assertEquals(8f, OverviewGeometry.fitTextSize(14f, 8f, 10f, s -> s * 10f), 0.01f);
    }
}
