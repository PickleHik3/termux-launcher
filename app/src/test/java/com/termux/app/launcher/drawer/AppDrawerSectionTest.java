package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerSectionTest {

    @Test public void persistedValuesAndIndicesAreExact() {
        assertEquals("apps", AppDrawerSection.APPS.preferenceValue);
        assertEquals("categories", AppDrawerSection.CATEGORIES.preferenceValue);
        assertEquals(0, AppDrawerSection.APPS.index());
        assertEquals(1, AppDrawerSection.CATEGORIES.index());
    }

    @Test public void unknownValuesReadAsApps() {
        assertEquals(AppDrawerSection.APPS, AppDrawerSection.fromPreference(null));
        assertEquals(AppDrawerSection.APPS, AppDrawerSection.fromPreference(""));
        assertEquals(AppDrawerSection.APPS, AppDrawerSection.fromPreference("future"));
    }

    @Test public void valuesRoundTrip() {
        for (AppDrawerSection section : AppDrawerSection.values())
            assertEquals(section, AppDrawerSection.fromPreference(section.preferenceValue));
    }
}
