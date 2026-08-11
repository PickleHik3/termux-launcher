package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerViewTypeTest {

    @Test public void persistedValuesAreExact() {
        assertEquals("vertical", AppDrawerViewType.VERTICAL.preferenceValue);
        assertEquals("horizontal", AppDrawerViewType.HORIZONTAL.preferenceValue);
        assertEquals("categories", AppDrawerViewType.CATEGORIES.preferenceValue);
    }

    @Test public void invalidValuesPreserveVertical() {
        assertEquals(AppDrawerViewType.VERTICAL, AppDrawerViewType.fromPreference(null));
        assertEquals(AppDrawerViewType.VERTICAL, AppDrawerViewType.fromPreference(""));
        assertEquals(AppDrawerViewType.VERTICAL, AppDrawerViewType.fromPreference("future"));
    }

    @Test public void horizontalRoundTrips() {
        assertEquals(AppDrawerViewType.HORIZONTAL,
            AppDrawerViewType.fromPreference(AppDrawerViewType.HORIZONTAL.preferenceValue));
    }

    @Test public void categoriesRoundTrips() {
        assertEquals(AppDrawerViewType.CATEGORIES,
            AppDrawerViewType.fromPreference(AppDrawerViewType.CATEGORIES.preferenceValue));
    }
}
