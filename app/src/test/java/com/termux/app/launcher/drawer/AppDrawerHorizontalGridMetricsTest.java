package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerHorizontalGridMetricsTest {

    private static final float EPS = 0.001f;

    @Test public void autoAndExplicitDimensionsResolveIndependentCapacity() {
        AppDrawerHorizontalGridMetrics auto = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 900f, 3f, 33f, 0, 0);
        assertEquals(4, auto.columns);
        assertEquals(4, auto.rows);
        assertEquals(16, auto.itemsPerPage);

        AppDrawerHorizontalGridMetrics explicit = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 900f, 3f, 33f, 6, 3);
        assertEquals(6, explicit.columns);
        assertEquals(3, explicit.rows);
        assertEquals(18, explicit.itemsPerPage);
    }

    @Test public void fontScaleAndShortHeightCapRowsToWhatPhysicallyFits() {
        AppDrawerHorizontalGridMetrics tallLabel = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 500f, 3f, 120f, 4, 6);
        assertTrue(tallLabel.rows < 6);
        assertTrue(tallLabel.rowHeightPx * tallLabel.rows <= 500.01f);

        AppDrawerHorizontalGridMetrics shortPage = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 40f, 3f, 33f, 4, 6);
        assertEquals(1, shortPage.rows);
        assertTrue(shortPage.rowHeightPx <= 40f);
    }

    @Test public void degenerateDimensionsStayFiniteAndRetainCapacity() {
        AppDrawerHorizontalGridMetrics metrics = AppDrawerHorizontalGridMetrics.resolve(
            Float.NaN, Float.NEGATIVE_INFINITY, 0f, -5f, 0, 0);
        assertTrue(metrics.itemsPerPage >= 1);
        assertFalse(Float.isNaN(metrics.cellWidthPx));
        assertFalse(Float.isNaN(metrics.iconPx));
        assertFalse(Float.isNaN(metrics.rowHeightPx));
    }

    @Test public void horizontalPreferencesDoNotChangeVerticalResolution() {
        AppDrawerGridMetrics vertical = AppDrawerGridMetrics.resolve(1080f, 3f, 33f, 5);
        AppDrawerHorizontalGridMetrics horizontal = AppDrawerHorizontalGridMetrics.resolve(
            1080f, 900f, 3f, 33f, 6, 2);
        assertEquals(5, vertical.columns);
        assertEquals(6, horizontal.columns);
        assertEquals(270f, AppDrawerGridMetrics.resolve(1080f, 3f, 33f, 0).cellWidthPx, EPS);
    }
}
