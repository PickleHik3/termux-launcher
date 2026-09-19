package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The shared collapse behind {@code LauncherCategorySortService.runSort},
 * {@code CategorySortDialogs.loadApps} and {@link LauncherCategoryPasteImporter#knownPackages}:
 * every Linux app shares the one reserved {@code x11:linux} package, so it must never be offered
 * to anything that treats the catalogue as one app per package.
 */
public class LauncherCategoryCatalogueTest {

    @Test public void theLinuxAppsPackageIsNeverOffered() {
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.notes", "Notes"),
            linuxApp("typora.desktop", "Typora"),
            linuxApp("distro:debian:gimp.desktop", "GIMP"));

        LinkedHashMap<String, String> labelByPackage =
            LauncherCategoryCatalogue.labelByPackage(catalogue);

        assertEquals(1, labelByPackage.size());
        assertTrue(labelByPackage.containsKey("com.example.notes"));
        assertFalse(labelByPackage.containsKey(X11Apps.PACKAGE));
    }

    @Test public void aWorkProfileTwinCollapsesToTheFirstLabelSeen() {
        List<LauncherAppEntry> catalogue = Arrays.asList(
            app("com.example.mail", "Mail (work)"), app("com.example.mail", "Mail"));

        LinkedHashMap<String, String> labelByPackage =
            LauncherCategoryCatalogue.labelByPackage(catalogue);

        assertEquals(1, labelByPackage.size());
        assertEquals("Mail (work)", labelByPackage.get("com.example.mail"));
    }

    @Test public void nullEntriesAndAnEmptyCatalogueAreHarmless() {
        assertTrue(LauncherCategoryCatalogue.labelByPackage(
            java.util.Collections.<LauncherAppEntry>emptyList()).isEmpty());
        assertTrue(LauncherCategoryCatalogue.labelByPackage(
            Arrays.asList((LauncherAppEntry) null)).isEmpty());
    }

    private static LauncherAppEntry app(String packageName, String label) {
        return new LauncherAppEntry(new AppRef(packageName, ".Main"), label, null);
    }

    private static LauncherAppEntry linuxApp(String desktopId, String label) {
        return new LauncherAppEntry(X11Apps.ref(desktopId), label, null);
    }
}
