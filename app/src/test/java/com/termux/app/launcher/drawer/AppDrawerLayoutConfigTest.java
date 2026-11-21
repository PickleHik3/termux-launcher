package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerLayoutConfigTest {
    @Test public void defaultsExplicitValuesInvalidFallbackAndEquality() {
        assertEquals(new AppDrawerLayoutConfig(AppDrawerViewType.VERTICAL, 0, 0, 0, 0, 0),
            AppDrawerLayoutConfig.defaults());
        for (int icon : new int[]{0, 48, 54, 60})
            assertEquals(icon, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
                icon, 4, 5, 6, 3).iconSizeDp);
        for (int icon : new int[]{36, 40, 44, 50, 72})
            assertEquals(0, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
                icon, 4, 5, 6, 3).iconSizeDp);
        for (int rows : new int[]{3, 4, 5, 6})
            assertEquals(rows, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
                0, 4, 5, rows, 3).horizontalRows);
        assertEquals(0, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
            0, 4, 5, 2, 3).horizontalRows);
        AppDrawerLayoutConfig invalid = new AppDrawerLayoutConfig(AppDrawerViewType.CATEGORIES,
            37, 3, 7, 1, 4);
        assertEquals(0, invalid.iconSizeDp);
        assertEquals(0, invalid.verticalColumns);
        assertEquals(0, invalid.horizontalColumns);
        assertEquals(0, invalid.horizontalRows);
        assertEquals(0, invalid.categoryColumns);
        assertEquals(AppDrawerViewType.VERTICAL, AppDrawerViewType.fromPreference("unknown"));
        assertNotEquals(AppDrawerLayoutConfig.defaults(), invalid);
    }
}
