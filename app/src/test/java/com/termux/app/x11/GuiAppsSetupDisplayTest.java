package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;
import com.termux.app.wayland.LinuxDisplayBackend;
import com.termux.app.x11.GuiAppsSetup.Distro;
import com.termux.app.x11.GuiAppsSetup.Route;

import org.junit.Test;

import java.util.Collections;

/** Wayland Display adds Xwayland to set-up; X11 Display changes nothing. */
public class GuiAppsSetupDisplayTest {

    private static String command(Route route, AppEdition edition, LinuxDisplayBackend display) {
        return GuiAppsSetup.command(route, Distro.DEBIAN, GuiAppsSetup.defaultStarters(), edition,
            null, null, false, display);
    }

    @Test
    public void packagesFollowTheChosenDisplay() {
        assertEquals(Collections.singletonList("xwayland"),
            GuiAppsSetup.displayPackages(LinuxDisplayBackend.WAYLAND));
        assertTrue(GuiAppsSetup.displayPackages(LinuxDisplayBackend.X11).isEmpty());
    }

    @Test
    public void waylandAddsXwaylandOnEveryRoute() {
        assertTrue(command(Route.X11_REPO, AppEdition.TERMUX, LinuxDisplayBackend.WAYLAND).contains(" xwayland"));
        assertTrue(command(Route.X11_REPO, AppEdition.VAJ, LinuxDisplayBackend.WAYLAND).contains("xwayland"));
        assertTrue(command(Route.DISTRO, AppEdition.TERMUX, LinuxDisplayBackend.WAYLAND)
            .contains("pkg install -y xwayland"));
    }

    @Test
    public void x11DisplayLeavesTheCommandsAsTheyWere() {
        for (Route route : Route.values()) {
            for (AppEdition edition : new AppEdition[] {AppEdition.TERMUX, AppEdition.VAJ}) {
                String x11 = command(route, edition, LinuxDisplayBackend.X11);
                assertFalse(x11.contains("xwayland"));
                assertEquals(GuiAppsSetup.command(route, Distro.DEBIAN, GuiAppsSetup.defaultStarters(),
                    edition, null, null, false), x11);
            }
        }
    }
}
