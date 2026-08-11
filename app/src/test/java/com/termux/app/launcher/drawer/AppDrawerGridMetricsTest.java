package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Grid sizing.
 *
 * <p>Reference geometry is the 1080px panel at 3x the rest of the drawer suite uses, so a full-width
 * plane is 360dp and the cap on the icon is the binding constraint rather than the cell — which is
 * the case that matters, since the cap is what bounds what a long scroll can push into the shared
 * icon cache.
 */
public class AppDrawerGridMetricsTest {

    private static final float EPS = 1e-4f;

    private static final float DENSITY = 3f;
    /** A single-line 11sp label at 3x. */
    private static final float LABEL_HEIGHT = 33f;

    @Test
    public void columnCountRoundsToTheTargetCellAndClampsAtBothEnds() {
        assertEquals(4, AppDrawerGridMetrics.resolveColumns(360f));
        assertEquals(5, AppDrawerGridMetrics.resolveColumns(380f));
        assertEquals(5, AppDrawerGridMetrics.resolveColumns(460f));
        assertEquals(6, AppDrawerGridMetrics.resolveColumns(500f));
        // A narrow plane would round to three columns, a tablet-width one to eight.
        assertEquals(AppDrawerGridMetrics.MIN_COLUMNS, AppDrawerGridMetrics.resolveColumns(200f));
        assertEquals(AppDrawerGridMetrics.MAX_COLUMNS, AppDrawerGridMetrics.resolveColumns(700f));
        assertEquals(AppDrawerGridMetrics.MIN_COLUMNS, AppDrawerGridMetrics.resolveColumns(0f));
    }

    @Test
    public void iconIsCappedAtItsDpCeilingOnAFullWidthPlane() {
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1080f, DENSITY, LABEL_HEIGHT);
        assertEquals(4, m.columns);
        assertEquals(270f, m.cellWidthPx, EPS);
        // 0.58 of the cell would be 156.6px; the 48dp ceiling wins.
        assertEquals(AppDrawerGridMetrics.MAX_ICON_DP * DENSITY, m.iconPx, EPS);
        assertTrue(m.iconPx < m.cellWidthPx * AppDrawerGridMetrics.ICON_CELL_FRACTION);
    }

    @Test
    public void iconFollowsTheCellWhenTheCellIsTheTighterConstraint() {
        // 240dp of content still gets the four-column floor, so the cells go narrow instead.
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(720f, DENSITY, LABEL_HEIGHT);
        assertEquals(4, m.columns);
        assertEquals(180f, m.cellWidthPx, EPS);
        assertEquals(180f * AppDrawerGridMetrics.ICON_CELL_FRACTION, m.iconPx, EPS);
        assertTrue(m.iconPx < AppDrawerGridMetrics.MAX_ICON_DP * DENSITY);
    }

    @Test
    public void rowHeightIsIconGapLabelAndBottomPadding() {
        AppDrawerGridMetrics m = AppDrawerGridMetrics.resolve(1080f, DENSITY, LABEL_HEIGHT);
        float expected = m.iconPx
            + (AppDrawerGridMetrics.LABEL_GAP_DP * DENSITY)
            + LABEL_HEIGHT
            + (AppDrawerGridMetrics.ROW_BOTTOM_DP * DENSITY);
        assertEquals(expected, m.rowHeightPx, EPS);
        assertEquals(225f, m.rowHeightPx, EPS);
        // A taller label pushes the row and nothing else.
        AppDrawerGridMetrics taller = AppDrawerGridMetrics.resolve(1080f, DENSITY, LABEL_HEIGHT * 2f);
        assertEquals(m.iconPx, taller.iconPx, EPS);
        assertEquals(m.rowHeightPx + LABEL_HEIGHT, taller.rowHeightPx, EPS);
    }

    @Test
    public void degenerateInputsDegradeRatherThanPoisonTheLayout() {
        // A frame measured before layout, and a density that would divide by zero.
        AppDrawerGridMetrics empty = AppDrawerGridMetrics.resolve(0f, DENSITY, LABEL_HEIGHT);
        assertEquals(AppDrawerGridMetrics.MIN_COLUMNS, empty.columns);
        assertEquals(0f, empty.cellWidthPx, EPS);
        assertEquals(0f, empty.iconPx, EPS);
        assertEquals(81f, empty.rowHeightPx, EPS);

        AppDrawerGridMetrics noDensity = AppDrawerGridMetrics.resolve(1080f, 0f, LABEL_HEIGHT);
        assertFalse(Float.isNaN(noDensity.iconPx));
        assertFalse(Float.isNaN(noDensity.rowHeightPx));
        assertTrue(noDensity.columns >= AppDrawerGridMetrics.MIN_COLUMNS
            && noDensity.columns <= AppDrawerGridMetrics.MAX_COLUMNS);

        AppDrawerGridMetrics unmeasuredLabel =
            AppDrawerGridMetrics.resolve(1080f, DENSITY, -20f);
        assertEquals(192f, unmeasuredLabel.rowHeightPx, EPS);
    }
}
