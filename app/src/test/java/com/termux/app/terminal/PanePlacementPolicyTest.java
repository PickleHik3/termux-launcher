package com.termux.app.terminal;

import android.widget.LinearLayout;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PanePlacementPolicyTest {

    private static final float EPS = 1e-4f;

    @Test
    public void directions_mapToSidesAndBack() {
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, PanePlacementPolicy.sideFor("right"));
        assertEquals(DwindleTilingPolicy.SIDE_LEFT, PanePlacementPolicy.sideFor("Left"));
        assertEquals(DwindleTilingPolicy.SIDE_BOTTOM, PanePlacementPolicy.sideFor(" down "));
        assertEquals(DwindleTilingPolicy.SIDE_TOP, PanePlacementPolicy.sideFor("up"));
        assertEquals(PanePlacementPolicy.SIDE_UNSET, PanePlacementPolicy.sideFor(null));
        assertEquals(PanePlacementPolicy.SIDE_UNSET, PanePlacementPolicy.sideFor(""));
        assertEquals(PanePlacementPolicy.SIDE_INVALID, PanePlacementPolicy.sideFor("north"));
        for (String name : new String[]{"right", "left", "down", "up"}) {
            assertEquals(name, PanePlacementPolicy.directionName(PanePlacementPolicy.sideFor(name)));
        }
    }

    @Test
    public void shares_areBoundedToTenAndNinetyPercent() {
        assertTrue(PanePlacementPolicy.isValidShare(0.1));
        assertTrue(PanePlacementPolicy.isValidShare(0.9));
        assertTrue(PanePlacementPolicy.isValidShare(0.5));
        assertFalse(PanePlacementPolicy.isValidShare(0.05));
        assertFalse(PanePlacementPolicy.isValidShare(0.95));
        assertFalse(PanePlacementPolicy.isValidShare(Double.NaN));
        assertEquals(0.1f, PanePlacementPolicy.clampShare(0.01f), EPS);
        assertEquals(0.9f, PanePlacementPolicy.clampShare(3f), EPS);
        assertEquals(0.5f, PanePlacementPolicy.clampShare(Float.NaN), EPS);
    }

    @Test
    public void cells_becomeAShareOfTheExtentAndKeepBothSidesUsable() {
        assertEquals(0.4f, PanePlacementPolicy.shareForCells(40, 100), EPS);
        // Asking for more than the pane has leaves the other side its minimum.
        assertEquals(0.9f, PanePlacementPolicy.shareForCells(120, 100), EPS);
        assertEquals(0.1f, PanePlacementPolicy.shareForCells(1, 100), EPS);
        // An unmeasured extent cannot be divided: the split stays even.
        assertEquals(0.5f, PanePlacementPolicy.shareForCells(40, 0), EPS);
    }

    @Test
    public void side_followsTheCellCountWhenNoDirectionIsGiven() {
        int fallback = DwindleTilingPolicy.SIDE_BOTTOM;
        assertEquals(DwindleTilingPolicy.SIDE_LEFT,
            PanePlacementPolicy.resolveSide(DwindleTilingPolicy.SIDE_LEFT, false, true, fallback));
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT,
            PanePlacementPolicy.resolveSide(PanePlacementPolicy.SIDE_UNSET, true, false, fallback));
        assertEquals(DwindleTilingPolicy.SIDE_BOTTOM,
            PanePlacementPolicy.resolveSide(PanePlacementPolicy.SIDE_UNSET, false, true, DwindleTilingPolicy.SIDE_RIGHT));
        assertEquals(fallback, PanePlacementPolicy.resolveSide(PanePlacementPolicy.SIDE_UNSET, true, true, fallback));
        assertEquals(fallback, PanePlacementPolicy.resolveSide(PanePlacementPolicy.SIDE_UNSET, false, false, fallback));
    }

    @Test
    public void place_rightAtFortyPercentPutsTheNewPaneSecond() {
        PanePlacementPolicy.Placement p = PanePlacementPolicy.place(
            DwindleTilingPolicy.SIDE_RIGHT, 0.4f, 0, 0, 100, 40);
        assertEquals(LinearLayout.HORIZONTAL, p.orientation);
        assertFalse(p.newFirst);
        assertEquals(0.4f, p.newShare, EPS);
        assertEquals(1.2f, p.weightA(2f), EPS);
        assertEquals(0.8f, p.weightB(2f), EPS);
    }

    @Test
    public void place_upPutsTheNewPaneFirstAndTakesRowsAlongTheAxis() {
        PanePlacementPolicy.Placement p = PanePlacementPolicy.place(
            DwindleTilingPolicy.SIDE_TOP, Float.NaN, 30, 10, 100, 40);
        assertEquals(LinearLayout.VERTICAL, p.orientation);
        assertTrue(p.newFirst);
        // Rows count on a vertical split; the 30 columns say nothing about it.
        assertEquals(0.25f, p.newShare, EPS);
        assertEquals(0.5f, p.weightA(2f), EPS);
    }

    @Test
    public void place_columnsOnALeftSplitAndSizeWinsOverCells() {
        PanePlacementPolicy.Placement cells = PanePlacementPolicy.place(
            DwindleTilingPolicy.SIDE_LEFT, Float.NaN, 25, 0, 100, 40);
        assertTrue(cells.newFirst);
        assertEquals(0.25f, cells.newShare, EPS);
        PanePlacementPolicy.Placement sized = PanePlacementPolicy.place(
            DwindleTilingPolicy.SIDE_LEFT, 0.7f, 25, 0, 100, 40);
        assertEquals(0.7f, sized.newShare, EPS);
        // A cell count on the other axis is ignored: an even split.
        PanePlacementPolicy.Placement other = PanePlacementPolicy.place(
            DwindleTilingPolicy.SIDE_BOTTOM, Float.NaN, 25, 0, 100, 40);
        assertEquals(0.5f, other.newShare, EPS);
    }

    @Test
    public void resize_givesThePaneItsShareOnEitherSide() {
        assertEquals(0.3f, PanePlacementPolicy.resizeShare(0.3f, 0, 0), EPS);
        assertEquals(0.25f, PanePlacementPolicy.resizeShare(Float.NaN, 20, 80), EPS);
        assertEquals(0.9f, PanePlacementPolicy.resizeShare(Float.NaN, 200, 80), EPS);
        assertEquals(0.6f, PanePlacementPolicy.weightAForShare(2f, 0.3f, true), EPS);
        assertEquals(1.4f, PanePlacementPolicy.weightAForShare(2f, 0.3f, false), EPS);
        assertEquals(0.2f, PanePlacementPolicy.weightAForShare(2f, 0.01f, true), EPS);
    }

    @Test
    public void childFraction_cutsTheParentAlongTheSplitsAxis() {
        float[] whole = {0f, 0f, 1f, 1f};
        assertArrayEquals(new float[]{0f, 0f, 0.6f, 1f},
            PanePlacementPolicy.childFraction(whole, LinearLayout.HORIZONTAL, 1.2f, 0.8f, true), EPS);
        assertArrayEquals(new float[]{0.6f, 0f, 1f, 1f},
            PanePlacementPolicy.childFraction(whole, LinearLayout.HORIZONTAL, 1.2f, 0.8f, false), EPS);
        float[] right = {0.5f, 0f, 1f, 1f};
        assertArrayEquals(new float[]{0.5f, 0.25f, 1f, 1f},
            PanePlacementPolicy.childFraction(right, LinearLayout.VERTICAL, 1f, 3f, false), EPS);
        // Degenerate weights fall back to an even cut rather than dividing by zero.
        assertArrayEquals(new float[]{0f, 0f, 1f, 0.5f},
            PanePlacementPolicy.childFraction(whole, LinearLayout.VERTICAL, 0f, 0f, true), EPS);
    }

    @Test
    public void cellGeometry_roundsEdgesSoNeighboursShareTheirDivider() {
        int[] left = PanePlacementPolicy.cellGeometry(new float[]{0f, 0f, 1f / 3f, 1f}, 100, 40);
        int[] right = PanePlacementPolicy.cellGeometry(new float[]{1f / 3f, 0f, 1f, 1f}, 100, 40);
        assertArrayEquals(new int[]{0, 0, 33, 40}, left);
        assertArrayEquals(new int[]{33, 0, 67, 40}, right);
        assertEquals(100, left[2] + right[2]);
        assertNull(PanePlacementPolicy.cellGeometry(new float[]{0f, 0f, 1f, 1f}, 0, 40));
        assertEquals(10, PanePlacementPolicy.cellsIn(1005, 100));
        assertEquals(0, PanePlacementPolicy.cellsIn(1005, 0));
    }
}
