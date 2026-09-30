package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LockOnceTest {

    @Test
    public void fireRunsTheLockOnce() {
        LockOnce once = new LockOnce();
        int[] runs = {0};
        assertTrue(once.arm(() -> runs[0]++));
        assertTrue(once.isPending());
        assertTrue(once.fire());
        assertFalse(once.fire());
        assertFalse(once.isPending());
        assertEquals(1, runs[0]);
    }

    @Test
    public void aSecondArmWhilePendingIsRefused() {
        LockOnce once = new LockOnce();
        int[] runs = {0, 0};
        assertTrue(once.arm(() -> runs[0]++));
        assertFalse(once.arm(() -> runs[1]++));
        once.fire();
        assertEquals(1, runs[0]);
        assertEquals(0, runs[1]);
    }

    @Test
    public void canArmAgainAfterFiring() {
        LockOnce once = new LockOnce();
        int[] runs = {0};
        once.arm(() -> runs[0]++);
        once.fire();
        assertTrue(once.arm(() -> runs[0]++));
        once.fire();
        assertEquals(2, runs[0]);
    }

    @Test
    public void cancelDropsTheLockWithoutRunningIt() {
        LockOnce once = new LockOnce();
        int[] runs = {0};
        once.arm(() -> runs[0]++);
        once.cancel();
        assertFalse(once.fire());
        assertEquals(0, runs[0]);
    }

    @Test
    public void fireWithNothingPendingDoesNothing() {
        assertFalse(new LockOnce().fire());
    }
}
