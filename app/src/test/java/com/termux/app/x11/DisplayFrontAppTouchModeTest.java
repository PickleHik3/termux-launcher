package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisplayFrontAppTouchModeTest {

    private static LinuxAppCatalog.LinuxApp app(String desktop, String exec, String wmClass) {
        return new LinuxAppCatalog.LinuxApp(desktop, desktop, exec, desktop, "", wmClass);
    }

    private final List<LinuxAppCatalog.LinuxApp> apps = Arrays.asList(
        app("firefox.desktop", "firefox", "Firefox"),
        app("gimp.desktop", "gimp", "Gimp"),
        app("arch:inkscape.desktop", "inkscape", "Inkscape"));

    private final Map<String, String> store = new HashMap<>();

    @Test public void appWithOverrideGivesIt() {
        store.put("firefox.desktop", "3");
        assertEquals("3", DisplayFrontAppTouchMode.decide(apps, "Firefox", store::get, false));
    }

    @Test public void aRunningDesktopIgnoresEveryAppOverride() {
        store.put("firefox.desktop", "3");
        assertNull(DisplayFrontAppTouchMode.decide(apps, "Firefox", store::get, true));
    }

    @Test public void pasteShortcutOverrideIsGivenForTheFrontApp() {
        store.put("firefox.desktop", "ctrl_shift_v");
        assertEquals("ctrl_shift_v",
            DisplayFrontAppTouchMode.decidePasteShortcut(apps, "Firefox", store::get, false));
        assertNull(DisplayFrontAppTouchMode.decidePasteShortcut(apps, "Gimp", store::get, false));
        assertNull(DisplayFrontAppTouchMode.decidePasteShortcut(apps, "Nothing", store::get, false));
    }

    @Test public void aRunningDesktopIgnoresPasteShortcutOverrides() {
        store.put("firefox.desktop", "ctrl_shift_v");
        assertNull(DisplayFrontAppTouchMode.decidePasteShortcut(apps, "Firefox", store::get, true));
    }

    @Test public void invalidStoredPasteShortcutFollowsDisplay() {
        store.put("firefox.desktop", "alt_v");
        assertNull(DisplayFrontAppTouchMode.decidePasteShortcut(apps, "Firefox", store::get, false));
    }

    @Test public void appWithoutOverrideFollowsDisplay() {
        store.put("firefox.desktop", "3");
        assertNull(DisplayFrontAppTouchMode.decide(apps, "Gimp", store::get, false));
    }

    @Test public void invalidStoredModeFollowsDisplay() {
        store.put("gimp.desktop", "9");
        assertNull(DisplayFrontAppTouchMode.decide(apps, "Gimp", store::get, false));
    }

    @Test public void unknownClassFollowsDisplay() {
        store.put("firefox.desktop", "2");
        assertNull(DisplayFrontAppTouchMode.decide(apps, "Nothing", store::get, false));
    }

    @Test public void emptyClassFollowsDisplay() {
        store.put("firefox.desktop", "2");
        assertNull(DisplayFrontAppTouchMode.decide(apps, "", store::get, false));
        assertNull(DisplayFrontAppTouchMode.decide(apps, null, store::get, false));
        assertNull(DisplayFrontAppTouchMode.decide(Collections.emptyList(), "Firefox", store::get, false));
    }

    @Test public void containerAppIdIsLookedUpWhole() {
        store.put("arch:inkscape.desktop", "2");
        assertEquals("2", DisplayFrontAppTouchMode.decide(apps, "Inkscape", store::get, false));
    }
}
