package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LinuxDisplayBackendTest {

    @Test
    public void storedWaylandIsWaylandOnlyInABuildWithTheHost() {
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.resolve("wayland", true));
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.resolve(" wayland ", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("wayland", false));
    }

    @Test
    public void everythingElseIsX11() {
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("x11", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("Wayland2", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, false));
    }

    @Test
    public void storedValuesMatchTheSharedDefault() {
        assertEquals(LinuxDisplayBackend.VALUE_X11, com.termux.shared.termux.settings.preferences
            .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_LINUX_DISPLAY_BACKEND);
    }

    @Test
    public void onlyPlainAppsInThePrefixFollowWayland() {
        LinuxDisplayBackend w = LinuxDisplayBackend.WAYLAND;
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.forApp(w, false, false));
        // A whole desktop keeps X11 Display for now.
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.forApp(w, true, false));
        // So does an app in a proot-distro container.
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.forApp(w, false, true));
        assertEquals(LinuxDisplayBackend.X11,
            LinuxDisplayBackend.forApp(LinuxDisplayBackend.X11, false, false));
    }
}
