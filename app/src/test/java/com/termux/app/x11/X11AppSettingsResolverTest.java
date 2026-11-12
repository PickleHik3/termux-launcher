package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class X11AppSettingsResolverTest {

    private static LinuxAppCatalog.LinuxApp prefixApp(String desktop, String exec, String wmClass) {
        return new LinuxAppCatalog.LinuxApp(desktop, desktop, exec, desktop, "", wmClass);
    }

    @Test public void overrideWins() {
        assertEquals("3", X11AppSettingsResolver.effectiveTouchMode("1", "3"));
    }

    @Test public void invalidOrMissingOverrideFollowsGlobal() {
        assertEquals("2", X11AppSettingsResolver.effectiveTouchMode("2", null));
        assertEquals("2", X11AppSettingsResolver.effectiveTouchMode("2", "9"));
        assertEquals("weird", X11AppSettingsResolver.effectiveTouchMode("weird", ""));
    }

    @Test public void windowClassFindsTheApp() {
        LinuxAppCatalog.LinuxApp firefox = prefixApp("firefox.desktop", "firefox", "");
        List<LinuxAppCatalog.LinuxApp> apps = Arrays.asList(
            prefixApp("gimp.desktop", "gimp", ""), firefox);
        assertEquals(firefox.id, X11AppSettingsResolver.appIdForWindowClass(apps, "Firefox"));
    }

    @Test public void windowClassFindsAContainerAppId() {
        ProotDistro.Container debian = new ProotDistro.Container(
            "debian", new File("/tmp/debian"), "root", "/root");
        LinuxAppCatalog.LinuxApp app = new LinuxAppCatalog.LinuxApp(
            debian, "gimp.desktop", "GIMP", "gimp", "gimp", "", "", false);
        String id = X11AppSettingsResolver.appIdForWindowClass(Collections.singletonList(app), "gimp");
        assertEquals(app.id, id);
        assertEquals("distro:debian:gimp.desktop", id);
    }

    @Test public void noMatchAndEmptyClassAreNull() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(prefixApp("gimp.desktop", "gimp", ""));
        assertNull(X11AppSettingsResolver.appIdForWindowClass(apps, "inkscape"));
        assertNull(X11AppSettingsResolver.appIdForWindowClass(apps, ""));
        assertNull(X11AppSettingsResolver.appIdForWindowClass(apps, null));
    }
}
