package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerLayoutConfigTest {
    @Test public void defaultsExplicitValuesInvalidFallbackAndEquality() {
        assertEquals(new AppDrawerLayoutConfig(AppDrawerViewType.VERTICAL, 0, 0, 0, 0, 0),
            AppDrawerLayoutConfig.defaults());
        for (int icon : new int[]{0, 36, 40, 44, 48})
            assertEquals(icon, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
                icon, 4, 5, 6, 3).iconSizeDp);
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
