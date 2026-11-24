package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxAppsRoutesTest {

    @Test
    public void onlyTheTwoLinuxAppRoutesAreHandled() {
        assertTrue(LinuxAppsRoutes.handles("GET", "/v1/linux-apps"));
        assertTrue(LinuxAppsRoutes.handles("POST", "/v1/linux-apps/open"));
        assertFalse(LinuxAppsRoutes.handles("POST", "/v1/linux-apps"));
        assertFalse(LinuxAppsRoutes.handles("GET", "/v1/linux-apps/open"));
        assertFalse(LinuxAppsRoutes.handles("GET", "/v1/linux-apps/x"));
    }

    @Test
    public void aLineIsTabSeparatedWithDesktopAsAFourthColumn() {
        assertEquals("distro:arch:firefox\tFirefox\tarch",
            LinuxAppsRoutes.line("distro:arch:firefox", "Firefox", "arch", false));
        assertEquals("session:termux:xfce\tXfce\ttermux\tdesktop",
            LinuxAppsRoutes.line("session:termux:xfce", "Xfce", "termux", true));
    }

    @Test
    public void aNameNeverBreaksTheColumnsOrTheLines() {
        assertEquals("i\tA B C\ttermux",
            LinuxAppsRoutes.line("i", "A\tB\nC", "termux", false));
    }
}
