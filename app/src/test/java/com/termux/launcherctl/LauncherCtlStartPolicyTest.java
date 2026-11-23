package com.termux.launcherctl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherCtlStartPolicyTest {

    @Test
    public void mainProcessStarts() {
        assertTrue(LauncherCtlStartPolicy.startsAtApplicationStart("com.termux", "com.termux"));
    }

    @Test
    public void helperProcessesDoNot() {
        assertFalse(LauncherCtlStartPolicy.startsAtApplicationStart(
            "com.termux:tai_runtime", "com.termux"));
        assertFalse(LauncherCtlStartPolicy.startsAtApplicationStart(
            "com.termux:phoenix", "com.termux"));
    }

    @Test
    public void unknownNamesDoNot() {
        assertFalse(LauncherCtlStartPolicy.startsAtApplicationStart(null, "com.termux"));
        assertFalse(LauncherCtlStartPolicy.startsAtApplicationStart("com.termux", null));
    }
}
