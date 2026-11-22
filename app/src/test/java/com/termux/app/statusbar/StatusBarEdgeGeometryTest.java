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

    private static final float[] DENSITIES = {1f, 2.625f, 3.5f};

    @Test public void rowGeometryReproducesTheRowArithmetic() {
        for (float d : DENSITIES) for (Edge edge : Edge.values())
            for (boolean capsule : new boolean[] {false, true})
                for (boolean compact : new boolean[] {false, true})
                    for (boolean collapsed : new boolean[] {false, true}) {
                        StatusBarEdgeGeometry.RowGeometry g = StatusBarEdgeGeometry.rowGeometry(
                            edge, capsule, compact, collapsed, d);
                        assertEquals(StatusBarEdgeGeometry.thicknessPx(edge, capsule, compact, d),
                            g.thickness);
                        assertEquals(Math.round((capsule ? 69f : 65f) * d), g.slot);
                        assertEquals(collapsed ? 0
                            : Math.round((capsule ? 3f : 2f) * d), g.foot);
                        assertEquals(Math.round((collapsed && capsule ? 22f : 24f) * d),
                            g.rowHeight);
                    }
    }

    @Test public void theCollapsedCapsuleRowUsesItsNamedConstant() {
        assertEquals(22f, StatusBarEdgeGeometry.STATUS_ROW_COLLAPSED_CAPSULE_DP, 0f);
        assertEquals(Math.round(StatusBarEdgeGeometry.STATUS_ROW_COLLAPSED_CAPSULE_DP * 2.625f),
            StatusBarEdgeGeometry.rowGeometry(Edge.TOP, true, true, true, 2.625f).rowHeight);
    }

    @Test public void theFoldPassInputsAreTheRestPassAnswers() {
        for (float d : DENSITIES) for (Edge edge : new Edge[] {Edge.TOP, Edge.BOTTOM})
            for (boolean capsule : new boolean[] {false, true}) {
                StatusBarEdgeGeometry.RowGeometry folded =
                    StatusBarEdgeGeometry.rowGeometry(edge, capsule, true, true, d);
                StatusBarEdgeGeometry.RowGeometry open =
                    StatusBarEdgeGeometry.rowGeometry(edge, capsule, false, false, d);
                assertEquals(Math.round((capsule ? 22f : 24f) * d), folded.rowHeight);
                assertEquals(Math.round(24f * d), open.rowHeight);
                assertEquals(Math.round((capsule ? 3f : 2f) * d), open.foot);
                assertEquals(0, folded.foot);
            }
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
        // The compact row keeps the numbers the top bar has always used; the open row is its
        // slot, the 24dp status row and the foot, with nothing between the slot and the row.
        assertEquals(32f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, true), 0f);
        assertEquals(30f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, true, true), 0f);
        assertEquals(91f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, false), 0f);
        assertEquals(96f, StatusBarEdgeGeometry.thicknessDp(Edge.TOP, true, false), 0f);
        assertEquals(StatusBarEdgeGeometry.thicknessDp(Edge.TOP, false, true),
            StatusBarEdgeGeometry.thicknessDp(Edge.BOTTOM, false, true), 0f);
        // 2.75 is the phone of record's density; the dp rounds to whole pixels.
        assertEquals(88, StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, true, 2.75f));
    }
}
