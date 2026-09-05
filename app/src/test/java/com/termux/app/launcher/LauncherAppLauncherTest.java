package com.termux.app.launcher;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

/** The process-wide Linux app runner hook, and who may take it out. */
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
}
