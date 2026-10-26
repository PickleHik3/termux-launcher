package com.termux.app.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherRestartPolicyTest {

    @Test
    public void theFirstRestartIsAccepted() {
        assertTrue(LauncherRestartPolicy.accept(10_000L, -1L));
    }

    @Test
    public void aSecondTapInsideTheWindowIsTheSameRestart() {
        assertFalse(LauncherRestartPolicy.accept(11_000L, 10_000L));
        assertFalse(LauncherRestartPolicy.accept(13_999L, 10_000L));
    }

    @Test
    public void aLaterRestartIsAccepted() {
        assertTrue(LauncherRestartPolicy.accept(14_000L, 10_000L));
    }

    @Test
    public void aClockThatWentBackwardsDoesNotLockRestartOut() {
        assertTrue(LauncherRestartPolicy.accept(5_000L, 10_000L));
    }
}
