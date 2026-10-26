package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.x11.LinuxAppCatalog;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * What puts Linux apps and the Display entry in the catalogue (ADR 0008): a build with the server
 * and Show Linux apps in drawer — on by default — and nothing else; no Display switch, no usage
 * mode, no running display. And a desktop file added after the listing shows as a change, which
 * is what lets the palette and launcherctl re-list rather than miss an app installed from a pane.
 */
public class LauncherAppDataProviderLinuxAppsTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void showLinuxAppsIsOnByDefault() {
        assertTrue(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_X11_DRAWER_APPS);
    }

    @Test public void linuxAppsAreListedOnABuildWithTheServerAndTheSettingOn() {
        assertTrue(LauncherAppDataProvider.listsLinuxApps(true, true));
    }

    @Test public void theSettingOffOrNoServerListsNone() {
        assertFalse(LauncherAppDataProvider.listsLinuxApps(true, false));
        assertFalse(LauncherAppDataProvider.listsLinuxApps(false, true));
        assertFalse(LauncherAppDataProvider.listsLinuxApps(false, false));
    }

    @Test public void aGtkDemoDesktopFileIsCatalogued() throws IOException {
        File dir = temp.newFolder("applications");
        write(new File(dir, "gtk3-demo.desktop"),
            "[Desktop Entry]\nName=GTK+ Demo\nComment=Program to demonstrate GTK+ functions\n"
                + "Exec=gtk3-demo\nIcon=gtk3-demo\nTerminal=false\nType=Application\n"
                + "StartupNotify=true\nCategories=GNOME;GTK;Development;\nOnlyShowIn=GNOME;\n");

        List<LinuxAppCatalog.LinuxApp> apps =
            LinuxAppCatalog.scan(LinuxAppCatalog.prefixRoots(Collections.singletonList(dir)));

        assertEquals(1, apps.size());
        assertEquals("GTK+ Demo", apps.get(0).name);
        assertEquals("gtk3-demo", apps.get(0).id);
    }

    @Test public void aDesktopFileAddedAfterTheListingIsAChange() throws IOException {
        File dir = temp.newFolder("applications");
        List<LinuxAppCatalog.Root> roots =
            LinuxAppCatalog.prefixRoots(Collections.singletonList(dir));
        long listed = LinuxAppCatalog.signature(roots);

        write(new File(dir, "gtk3-demo.desktop"),
            "[Desktop Entry]\nName=GTK+ Demo\nExec=gtk3-demo\nType=Application\n");

        assertNotEquals(listed, LinuxAppCatalog.signature(roots));
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
