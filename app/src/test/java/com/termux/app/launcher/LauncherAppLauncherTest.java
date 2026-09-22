package com.termux.app.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.X11Apps;

import org.junit.After;
import org.junit.Test;

/** The process-wide Linux app runner hooks, and who may take them out. */
public class LauncherAppLauncherTest {

    private final LauncherAppLauncher.LinuxAppRunner first = entry -> true;
    private final LauncherAppLauncher.LinuxAppRunner second = entry -> true;

    @After public void reset() {
        LauncherAppLauncher.clearLinuxAppRunner(first);
        LauncherAppLauncher.clearLinuxAppRunner(second);
    }

    @Test public void aDyingInstanceOnlyRemovesTheRunnerItInstalled() {
        // A second activity instance installs its runner before the first is destroyed.
        LauncherAppLauncher.setLinuxAppRunner(first);
        LauncherAppLauncher.setLinuxAppRunner(second);

        LauncherAppLauncher.clearLinuxAppRunner(first);

        assertSame("the live instance's runner survives the dead one's teardown",
            second, LauncherAppLauncher.linuxAppRunner());
    }

    @Test public void theInstalledRunnerIsRemovedByItsOwner() {
        LauncherAppLauncher.setLinuxAppRunner(second);
        LauncherAppLauncher.clearLinuxAppRunner(second);
        assertNull(LauncherAppLauncher.linuxAppRunner());
    }

    // --- D5: a Terminal=true entry forks to the terminal runner, not the display ---------------

    private LauncherAppEntry linuxAppEntry(String desktopId) {
        return new LauncherAppEntry(X11Apps.ref(desktopId), desktopId, null);
    }

    @Test public void aTerminalAppGoesToTheTerminalRunnerNotTheDisplay() {
        boolean[] displayCalled = {false};
        boolean[] terminalCalled = {false};
        LauncherAppLauncher.LinuxAppRunner display = entry -> { displayCalled[0] = true; return true; };
        LauncherAppLauncher.TerminalAppRunner terminal = new LauncherAppLauncher.TerminalAppRunner() {
            @Override public boolean handles(LauncherAppEntry entry) { return true; }
            @Override public boolean run(LauncherAppEntry entry) { terminalCalled[0] = true; return true; }
        };
        LauncherAppLauncher.setLinuxAppRunner(display);
        LauncherAppLauncher.setTerminalAppRunner(terminal);
        try {
            assertTrue(LauncherAppLauncher.launchEntry(null, linuxAppEntry("htop")));
            assertTrue("the terminal runner ran the tap", terminalCalled[0]);
            assertFalse("the display runner never saw a Terminal=true tap", displayCalled[0]);
        } finally {
            LauncherAppLauncher.clearLinuxAppRunner(display);
            LauncherAppLauncher.clearTerminalAppRunner(terminal);
        }
    }

    @Test public void anOrdinaryLinuxAppStillGoesToTheDisplay() {
        boolean[] displayCalled = {false};
        boolean[] terminalCalled = {false};
        LauncherAppLauncher.LinuxAppRunner display = entry -> { displayCalled[0] = true; return true; };
        LauncherAppLauncher.TerminalAppRunner terminal = new LauncherAppLauncher.TerminalAppRunner() {
            @Override public boolean handles(LauncherAppEntry entry) { return false; }
            @Override public boolean run(LauncherAppEntry entry) { terminalCalled[0] = true; return true; }
        };
        LauncherAppLauncher.setLinuxAppRunner(display);
        LauncherAppLauncher.setTerminalAppRunner(terminal);
        try {
            assertTrue(LauncherAppLauncher.launchEntry(null, linuxAppEntry("firefox")));
            assertTrue("a non-terminal entry still runs on the display", displayCalled[0]);
            assertFalse("the terminal runner was asked but declined, so it never ran", terminalCalled[0]);
        } finally {
            LauncherAppLauncher.clearLinuxAppRunner(display);
            LauncherAppLauncher.clearTerminalAppRunner(terminal);
        }
    }

    @Test public void withNoTerminalRunnerInstalledEveryLinuxAppGoesToTheDisplay() {
        boolean[] displayCalled = {false};
        LauncherAppLauncher.LinuxAppRunner display = entry -> { displayCalled[0] = true; return true; };
        LauncherAppLauncher.setLinuxAppRunner(display);
        try {
            assertTrue(LauncherAppLauncher.launchEntry(null, linuxAppEntry("htop")));
            assertTrue(displayCalled[0]);
        } finally {
            LauncherAppLauncher.clearLinuxAppRunner(display);
        }
    }
}
