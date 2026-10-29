package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.AppEdition;

import org.junit.Test;

public class GuiAppsSetupUnattendedTest {

    private static String distro(GuiAppsSetup.Distro distro, String user, String password) {
        return GuiAppsSetup.command(GuiAppsSetup.Route.DISTRO, distro,
            GuiAppsSetup.defaultStarters(), AppEdition.TERMUX, user, password);
    }

    @Test
    public void nullUserKeepsTheInteractiveCommand() {
        String interactive = GuiAppsSetup.command(GuiAppsSetup.Route.DISTRO,
            GuiAppsSetup.Distro.DEBIAN, GuiAppsSetup.defaultStarters(), AppEdition.TERMUX);
        assertEquals(interactive, distro(GuiAppsSetup.Distro.DEBIAN, null, null));
        assertTrue(interactive.contains("read -r LUSER"));
        assertTrue(interactive.contains("passwd \"$LUSER\""));
    }

    @Test
    public void aUserAsksNothing() {
        for (GuiAppsSetup.Distro distro : GuiAppsSetup.Distro.values()) {
            String command = distro(distro, "alice", "s3cret");
            assertTrue(command.contains("LUSER='alice'\n"));
            assertTrue(command.contains("PASS='s3cret'\n"));
            assertFalse(command.contains("read -r"));
            assertFalse(command.contains("passwd \"$LUSER\""));
            assertTrue(command.contains("-e LUSER=\"$LUSER\" -e PASS=\"$PASS\" -- /bin/sh -c"));
            assertTrue(command.contains("| chpasswd"));
        }
    }

    @Test
    public void thePasswordIsQuotedForTheShell() {
        String command = distro(GuiAppsSetup.Distro.DEBIAN, "bob", "a'b\"c$d `e` \\f");
        assertTrue(command.contains("PASS='a'\\''b\"c$d `e` \\f'\n"));
    }

    @Test
    public void thePasswordAppearsOnlyInItsOwnAssignment() {
        String command = distro(GuiAppsSetup.Distro.ARCH, "bob", "hunter22");
        assertEquals(1, command.split("hunter22", -1).length - 1);
    }

    @Test
    public void theUserIsQuotedToo() {
        String command = distro(GuiAppsSetup.Distro.UBUNTU, "x'; rm -rf ~; '", "pass");
        assertTrue(command.contains("LUSER='x'\\''; rm -rf ~; '\\'''\n"));
    }

    @Test
    public void theX11RouteIgnoresTheAccount() {
        String plain = GuiAppsSetup.command(GuiAppsSetup.Route.X11_REPO,
            GuiAppsSetup.Distro.DEBIAN, GuiAppsSetup.defaultStarters(), AppEdition.TERMUX);
        assertEquals(plain, GuiAppsSetup.command(GuiAppsSetup.Route.X11_REPO,
            GuiAppsSetup.Distro.DEBIAN, GuiAppsSetup.defaultStarters(), AppEdition.TERMUX,
            "alice", "s3cret"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aUserWithoutAPasswordIsRefused() {
        distro(GuiAppsSetup.Distro.DEBIAN, "alice", null);
    }
}
