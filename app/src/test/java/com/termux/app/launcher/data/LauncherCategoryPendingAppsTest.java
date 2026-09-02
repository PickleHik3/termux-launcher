package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Per package, against whatever the drawer would resolve a category from. */
public class LauncherCategoryPendingAppsTest {

    @Test
    public void countsPackagesNoSourceHasAnOpinionOn() {
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.sorted.one"), app("com.sorted.two"), app("com.new.one"), app("com.new.two"));
        Set<String> sorted = new HashSet<>(Arrays.asList("com.sorted.one", "com.sorted.two"));

        assertEquals(2, LauncherCategoryPendingApps.count(catalogue,
            pkg -> sorted.contains(pkg) ? "tools" : null));
    }

    @Test
    public void aWorkProfileTwinIsTheSameApp() {
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.new.one"), app("com.new.one"), app("com.new.two"));

        assertEquals(2, LauncherCategoryPendingApps.count(catalogue, pkg -> null));
    }

    @Test
    public void anEmptyCatalogueIsNothingPending() {
        assertEquals(0, LauncherCategoryPendingApps.count(
            java.util.Collections.<LauncherAppEntry>emptyList(), pkg -> null));
    }

    private static LauncherAppEntry app(String packageName) {
        return new LauncherAppEntry(new AppRef(packageName, ".Main"), packageName, null);
    }
}
