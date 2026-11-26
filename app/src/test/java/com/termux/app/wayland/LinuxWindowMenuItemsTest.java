package com.termux.app.wayland;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A desktop's rim tab offers Full screen; a Wayland desktop has no Scale, an X11 one keeps it (#400). */
public class LinuxWindowMenuItemsTest {

    @Test
    public void aWaylandDesktopHasFullScreenAndNoScale() {
        LinuxWindowMenuItems items = LinuxWindowMenuItems.of(LinuxWindowMenuItems.Kind.WAYLAND_DESKTOP);
        assertTrue(items.fullScreen);
        assertFalse(items.scale);
    }

    @Test
    public void anX11DesktopHasFullScreenAndKeepsScale() {
        LinuxWindowMenuItems items = LinuxWindowMenuItems.of(LinuxWindowMenuItems.Kind.X11_DESKTOP);
        assertTrue(items.fullScreen);
        assertTrue(items.scale);
    }

    @Test
    public void anAppWindowKeepsScaleAndHasNoFullScreen() {
        LinuxWindowMenuItems items = LinuxWindowMenuItems.of(LinuxWindowMenuItems.Kind.APP);
        assertFalse(items.fullScreen);
        assertTrue(items.scale);
    }
}
