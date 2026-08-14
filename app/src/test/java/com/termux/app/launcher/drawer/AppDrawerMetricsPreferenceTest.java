package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerMetricsPreferenceTest {
    @Test public void autoIsGoldenAndExplicitInputsStayIndependentAndSafe() {
        AppDrawerGridMetrics oldVertical = AppDrawerGridMetrics.resolve(1080, 3, 33);
        AppDrawerGridMetrics autoVertical = AppDrawerGridMetrics.resolve(1080, 3, 33, 0, 0);
        assertEquals(oldVertical.columns, autoVertical.columns);
        assertEquals(oldVertical.iconPx, autoVertical.iconPx, 0f);
        assertEquals(6, AppDrawerGridMetrics.resolve(1080, 3, 33, 6, 36).columns);
        assertTrue(AppDrawerGridMetrics.resolve(300, 4, 33, 6, 48).iconPx <= 48 * 4);

        AppDrawerHorizontalGridMetrics horizontal = AppDrawerHorizontalGridMetrics.resolve(
            1080, 1400, 3, 33, 5, 4, 40);
        assertEquals(5, horizontal.columns); assertEquals(4, horizontal.rows);
        AppDrawerCategoryGridMetrics categories = AppDrawerCategoryGridMetrics.resolve(
            1080, 1600, 3, 40, 33, 60, 8 * 1024 * 1024, 2, 44);
        assertEquals(2, categories.columns);
        assertTrue(categories.largeIconPx <= Math.round(AppDrawerCategoryGridMetrics.MAX_ICON_DP) * 3);
        assertEquals(AppDrawerCategoryGridMetrics.EXPANDED_COLUMNS, categories.expandedColumns);
    }
}
