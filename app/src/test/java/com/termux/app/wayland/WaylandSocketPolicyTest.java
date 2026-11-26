package com.termux.app.wayland;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WaylandSocketPolicyTest {
    @Test
    public void socketIsOwnedExactlyWhenTheBuildHasWayland() {
        assertTrue(WaylandSocketPolicy.ownsSocket(true));
        assertFalse(WaylandSocketPolicy.ownsSocket(false));
    }

    @Test
    public void shellsGetTheDisplayOnlyWhileWaylandIsTheMainSetting() {
        assertTrue(WaylandSocketPolicy.publishesToShells(true, true, false));
        assertTrue(WaylandSocketPolicy.publishesToShells(true, false, true));
        assertFalse(WaylandSocketPolicy.publishesToShells(true, false, false));
        // X11 Display chosen: the socket stays owned and the host may run, shells follow the setting.
        assertFalse(WaylandSocketPolicy.publishesToShells(false, true, true));
        assertFalse(WaylandSocketPolicy.publishesToShells(false, false, true));
    }
}
