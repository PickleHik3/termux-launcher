package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerCategoryGridMetricsTest {
    private static final float EPS = 0.001f;
    private static final int BUDGET = 8 * 1024 * 1024;

    @Test public void columnBreakpointsAreTheApprovedPhysicalFit() {
        assertEquals(1, metrics(295f, 600f).columns);
        assertEquals(2, metrics(296f, 600f).columns);
        assertEquals(2, metrics(447f, 600f).columns);
        assertEquals(3, metrics(448f, 600f).columns);
        assertEquals(3, metrics(1200f, 600f).columns);
    }

    @Test public void squareHeadingSlotsRadiusAndBudgetAreExact() {
        AppDrawerCategoryGridMetrics m = metrics(360f, 640f);
        assertEquals((360f - 2f * m.sidePaddingPx - m.itemGapPx) / 2f,
            m.spanWidthPx, EPS);
        assertEquals(m.spanWidthPx - 2f * m.tileHorizontalInsetPx, m.tileSidePx, EPS);
        assertEquals(m.tileSidePx + m.headingGapPx + m.headingHeightPx
            + m.itemBottomGapPx, m.itemHeightPx, EPS);
        assertEquals(m.largeSlotPx, m.smallCellPx * 2f, EPS);
        assertTrue(m.radiusPx <= m.tileSidePx / 2f);
        assertTrue(m.chargedPreviewBytes() <= Math.floor(BUDGET * 0.60d));
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
