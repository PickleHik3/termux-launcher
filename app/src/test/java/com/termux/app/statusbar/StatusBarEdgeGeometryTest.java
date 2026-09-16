package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bar's frame on each edge, and what the content beside it gives up. */
public class StatusBarEdgeGeometryTest {

    private static final int W = 1080;
    private static final int H = 2400;

    @Test public void aRowSpansTheWidthAtTheEdgeItStandsOn() {
        assertEquals(new StatusBarEdgeGeometry.Frame(0, 0, W, 96),
            StatusBarEdgeGeometry.frame(Edge.TOP, W, H, 96, 0));
        assertEquals(new StatusBarEdgeGeometry.Frame(0, H - 96, W, H),
            StatusBarEdgeGeometry.frame(Edge.BOTTOM, W, H, 96, 0));
    }

    @Test public void aColumnSpansTheHeightPastWhateverAlreadyHoldsItsEdge() {
        // 44px of display cutout, then the bar's own 76px of width.
        assertEquals(new StatusBarEdgeGeometry.Frame(44, 0, 120, H),
            StatusBarEdgeGeometry.frame(Edge.LEFT, W, H, 76, 44));
        assertEquals(new StatusBarEdgeGeometry.Frame(W - 120, 0, W - 44, H),
            StatusBarEdgeGeometry.frame(Edge.RIGHT, W, H, 76, 44));
    }

    @Test public void aFrameNeverLeavesItsContainer() {
        StatusBarEdgeGeometry.Frame tall = StatusBarEdgeGeometry.frame(Edge.TOP, W, 40, 96, 0);
        assertEquals(40, tall.height());
        StatusBarEdgeGeometry.Frame wide = StatusBarEdgeGeometry.frame(Edge.LEFT, 60, H, 76, 44);
        assertEquals(60, wide.right);
        assertEquals(new StatusBarEdgeGeometry.Frame(0, 0, W, H),
            StatusBarEdgeGeometry.frame(Edge.BOTTOM, W, H, H * 2, 0));
    }

    @Test public void theOpenColumnIsWiderThanTheClosedOneAndTheRowIsTaller() {
        for (Edge edge : Edge.values()) {
            assertTrue(edge + " opens", StatusBarEdgeGeometry.thicknessDp(edge, false, false)
                > StatusBarEdgeGeometry.thicknessDp(edge, false, true));
            assertTrue(edge + " opens (capsule)",
                StatusBarEdgeGeometry.thicknessDp(edge, true, false)
                    > StatusBarEdgeGeometry.thicknessDp(edge, true, true));
        }
        // The row's numbers are the ones the top bar has always used, unchanged.
        assertEquals(32f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, true), 0f);
        assertEquals(30f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, true, true), 0f);
        assertEquals(96f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, false), 0f);
        assertEquals(100f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, true, false), 0f);
        assertEquals(StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, true),
            StatusBarEdgeGeometry.thicknessDp(Edge.BOTTOM, false, true), 0f);
        // 2.75 is the phone of record's density; the dp rounds to whole pixels.
        assertEquals(88, StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, true, 2.75f));
    }
}
