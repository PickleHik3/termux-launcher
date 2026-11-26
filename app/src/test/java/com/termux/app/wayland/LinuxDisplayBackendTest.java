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

    /** Wayland Display is in every build now; a fresh install and an update still open X11 Display. */
    @Test
    public void theSharedDefaultOpensX11EvenInABuildWithTheHost() {
        String shared = com.termux.shared.termux.settings.preferences
            .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_LINUX_DISPLAY_BACKEND;
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(shared, true));
        // An update has no stored value: the getter falls back to the default.
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, true));
    }

    @Test
    public void appsAndDesktopsInThePrefixFollowWayland() {
        LinuxDisplayBackend w = LinuxDisplayBackend.WAYLAND;
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.forApp(w, false, false));
        // A whole desktop opens as one window in its own X server.
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.forApp(w, true, false));
        // An app in a proot-distro container opens as a window; its desktop keeps X11 Display.
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.forApp(w, false, true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.forApp(w, true, true));
        assertEquals(LinuxDisplayBackend.X11,
            LinuxDisplayBackend.forApp(LinuxDisplayBackend.X11, true, false));
        assertEquals(LinuxDisplayBackend.X11,
            LinuxDisplayBackend.forApp(LinuxDisplayBackend.X11, false, false));
    }
}
