package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerCategoryGridMetricsTest {
    private static final float EPS = 0.001f;
    private static final int BUDGET = 8 * 1024 * 1024;

    @Test public void columnBreakpointsAreTheApprovedPhysicalFit() {
        // Physical fit at the redesign's 12dp card gap: floor((w + 12) / (144 + 12)).
        assertEquals(1, metrics(299f, 600f).columns);
        assertEquals(2, metrics(300f, 600f).columns);
        assertEquals(2, metrics(455f, 600f).columns);
        assertEquals(3, metrics(456f, 600f).columns);
        // A 1200dp body would be 380dp cards at three columns, past MAX_TILE_DP: it adds columns.
        assertEquals(5, metrics(1200f, 600f).columns);
    }

    @Test public void phonesKeepTodaysColumnsTileIconAndSpacing() {
        // Values captured from the pre-change maths (density 1, so px == dp).
        AppDrawerCategoryGridMetrics p411 = metrics(411f, 800f);
        assertEquals(2, p411.columns);
        assertEquals(187.5f, p411.tileSidePx, EPS);
        assertEquals(72, p411.largeIconPx);
        assertEquals(14.5f, p411.innerPaddingPx, EPS);
        assertEquals(14.5f, p411.slotGapPx, EPS);
        AppDrawerCategoryGridMetrics p360 = metrics(360f, 640f);
        assertEquals(2, p360.columns);
        assertEquals(162f, p360.tileSidePx, EPS);
        assertEquals(63, p360.largeIconPx);
        assertEquals(12f, p360.innerPaddingPx, EPS);
        assertEquals(12f, p360.slotGapPx, EPS);
        // Top of the three-column regime (768dp body, 240dp cards) is untouched too.
        AppDrawerCategoryGridMetrics edge = metrics(768f, 1200f);
        assertEquals(3, edge.columns);
        assertEquals(240f, edge.tileSidePx, EPS);
        assertEquals(72, edge.largeIconPx);
        assertEquals(32f, edge.innerPaddingPx, EPS);
    }

    @Test public void wideTabletBodyGetsMoreSmallerRoughlySquareCards() {
        // 1368dp landscape tablet at density 2.0, 1824px (912dp) tall viewport.
        AppDrawerCategoryGridMetrics m = AppDrawerCategoryGridMetrics.resolve(
            1368f * 2f, 1824f, 2f, 16f * 2f, 16f * 2f, 80f, BUDGET);
        assertTrue(m.columns >= 5);
        assertTrue(m.tileSidePx / 2f <= AppDrawerCategoryGridMetrics.MAX_TILE_DP + EPS);
        assertTrue(m.innerPaddingPx / 2f <= AppDrawerCategoryGridMetrics.MAX_SPACING_DP + EPS);
        float cardH = m.tileSidePx + m.headingGapPx + m.headingHeightPx;
        float aspect = cardH / m.tileSidePx;
        assertTrue("aspect " + aspect, aspect >= 0.85f && aspect <= 1.25f);
    }

    @Test public void portraitTabletGetsAtLeastFourColumns() {
        assertTrue(metrics(912f, 1400f).columns >= 4);
    }

    @Test public void expandedGridIsTheRedesignsFixedThreeColumns() {
        assertEquals(3, metrics(360f, 640f).expandedColumns);
        assertEquals(3, metrics(1200f, 600f).expandedColumns);
    }

    @Test public void squareHeadingSlotsRadiusAndBudgetAreExact() {
        AppDrawerCategoryGridMetrics m = metrics(360f, 640f);
        assertEquals((360f - 2f * m.sidePaddingPx - m.itemGapPx) / 2f,
            m.spanWidthPx, EPS);
        assertEquals(m.spanWidthPx - 2f * m.tileHorizontalInsetPx, m.tileSidePx, EPS);
        assertEquals(m.tileSidePx + m.headingGapPx + m.headingHeightPx
            + m.itemBottomGapPx, m.itemHeightPx, EPS);
        assertEquals(m.largeSlotPx, m.smallCellPx * 2f + m.smallBlockGapPx, EPS);
        assertTrue(m.radiusPx <= m.tileSidePx / 2f);
        assertTrue(m.chargedPreviewBytes() <= Math.floor(BUDGET * 0.60d));
    }

    @Test public void oneSpacingRunsEveryGapInsideAndOutsideTheCard() {
        for (float[] input : new float[][] {{360f, 640f, 1f}, {1080f, 2100f, 2.625f},
            {1920f, 900f, 2f}}) {
            AppDrawerCategoryGridMetrics m = AppDrawerCategoryGridMetrics.resolve(
                input[0], input[1], input[2], 16f * input[2], 16f * input[2], 80f, BUDGET);
            String at = "w=" + input[0] + " d=" + input[2];
            // Outside: the plane-edge gap is the card-to-card gap, and the drawn card fills its span.
            assertEquals(at, m.sidePaddingPx, m.itemGapPx, EPS);
            assertEquals(at, 0f, m.tileHorizontalInsetPx, EPS);
            assertEquals(at, m.spanWidthPx, m.tileSidePx, EPS);
            // Inside: pad, icon, gap, icon, pad — one number, and it is the card's whole width.
            assertEquals(at, m.innerPaddingPx, m.slotGapPx, EPS);
            assertEquals(at, m.innerPaddingPx, m.headingGapPx, EPS);
            float used = 3f * m.innerPaddingPx + 2f * m.largeIconPx;
            if (m.innerPaddingPx < AppDrawerCategoryGridMetrics.MAX_SPACING_DP * input[2] - EPS) {
                assertEquals(at, m.tileSidePx, used, 1.5f);
            } else {
                // Wide layout: spacing is capped, so the card may carry a little slack.
                assertTrue(at, m.tileSidePx >= used - 1.5f);
            }
            // The icon fills its half of the card: slot and icon are the same size, so nothing
            // floats in a ring of dead space, and the spacing never drops below the 12dp rhythm.
            assertEquals(at, m.largeIconPx, m.largeSlotPx, EPS);
            assertTrue(at, m.innerPaddingPx >= AppDrawerCategoryGridMetrics.RHYTHM_DP * input[2]
                - EPS);
            assertTrue(at, m.chargedPreviewBytes() <= Math.floor(BUDGET * 0.60d));
        }
    }

    @Test public void bottomUpDetailGrowsFromBottomThenOverflows() {
        AppDrawerCategoryGridMetrics m = metrics(360f, 640f);
        AppDrawerCategoryGridMetrics.DetailLayout one = m.resolveDetail(1, 500f, 40f);
        assertEquals(500f, one.bottomPx, EPS);
        assertEquals(500f - one.listHeightPx, one.listTopPx, EPS);
        assertEquals(one.listTopPx - m.headerListGapPx, one.headerBottomPx, EPS);
        assertFalse(one.overflow);

        AppDrawerCategoryGridMetrics.DetailLayout many = m.resolveDetail(1000, 500f, 80f);
        assertTrue(many.overflow);
        assertEquals(500f - m.emptyTopMinPx - 80f - m.headerListGapPx,
            many.listHeightPx, EPS);
        assertTrue(many.headerBottomPx <= many.listTopPx);
    }

    @Test public void tileHeadingAndExpandedAppLabelHeightsRemainIndependent() {
        float tileHeadingHeight = 26f;
        float appLabelHeight = 22f;
        AppDrawerCategoryGridMetrics m = AppDrawerCategoryGridMetrics.resolve(
            360f, 640f, 1f, tileHeadingHeight, appLabelHeight, 80f, BUDGET);

        assertEquals(tileHeadingHeight, m.headingHeightPx, EPS);
        assertEquals(m.largeIconPx + AppDrawerGridMetrics.LABEL_GAP_DP + appLabelHeight
            + AppDrawerGridMetrics.ROW_BOTTOM_DP, m.expandedRowHeightPx, EPS);
    }

    @Test public void fontScaleShortAndDegenerateInputsRemainFiniteAndNonOverlapping() {
        AppDrawerCategoryGridMetrics m = AppDrawerCategoryGridMetrics.resolve(
            Float.NaN, Float.POSITIVE_INFINITY, 0f, Float.NaN, Float.NaN,
            Float.POSITIVE_INFINITY, 0);
        assertEquals(1, m.columns);
        assertEquals(0, m.largeIconPx);
        assertEquals(0L, m.chargedPreviewBytes());
        AppDrawerCategoryGridMetrics.DetailLayout shortLayout = m.resolveDetail(
            Integer.MAX_VALUE, 20f, 80f);
        for (float value : new float[] {m.tileSidePx, m.itemHeightPx, m.radiusPx,
            shortLayout.listHeightPx, shortLayout.headerTopPx, shortLayout.listTopPx}) {
            assertTrue(Float.isFinite(value));
            assertTrue(value >= 0f);
        }
        assertTrue(shortLayout.headerBottomPx <= shortLayout.listTopPx);
    }

    private static AppDrawerCategoryGridMetrics metrics(float width, float height) {
        return AppDrawerCategoryGridMetrics.resolve(width, height, 1f, 16f, 16f, 80f, BUDGET);
    }
}
