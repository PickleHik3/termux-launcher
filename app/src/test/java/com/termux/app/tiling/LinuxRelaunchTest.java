package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which window of a loaded Workspace a relaunched Linux app's window goes into (#431). */
public class LinuxRelaunchTest {

    @Test
    public void aWindowGoesToTheOldestWaitingWindowOfItsApp() {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        relaunch.await(7, "firefox", null);
        relaunch.await(5, "firefox", null);
        relaunch.await(6, "gimp", null);

        assertEquals(5, relaunch.claim("firefox", null));
        assertEquals(7, relaunch.claim("firefox", null));
        assertEquals(TilingStack.NO_WINDOW, relaunch.claim("firefox", null));
        assertTrue(relaunch.isWaiting(6));
    }

    @Test
    public void theLaunchKeyMatchesBeforeTheAppIdAndWhenTheAppIdIsNotKnownYet() {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        relaunch.await(1, "org.kde.kate", null);
        relaunch.await(2, "org.kde.kate", "kate-session");

        assertEquals("by key", 2, relaunch.claim("", "kate-session"));
        assertEquals(1, relaunch.claim("org.kde.kate", "other"));
    }

    @Test
    public void nothingEmptyMatches() {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        relaunch.await(1, "", null);

        assertEquals(TilingStack.NO_WINDOW, relaunch.claim("", null));
        assertEquals(TilingStack.NO_WINDOW, relaunch.claim(null, ""));
        assertTrue(relaunch.isWaiting(1));
    }

    @Test
    public void itsWaitingWindowsRoundTripThroughJson() throws Exception {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        relaunch.await(4, "gimp", "gimp.desktop");
        relaunch.await(2, "kate", null);

        LinuxRelaunch read = LinuxRelaunch.fromJson(relaunch.toJson(), id -> id != 3);

        assertEquals(java.util.Arrays.asList(2L, 4L), read.windows());
        assertEquals(4, read.claim(null, "gimp.desktop"));
        assertEquals(2, read.claim("kate", null));
        assertTrue(LinuxRelaunch.fromJson(null, id -> true).isEmpty());
        assertTrue("a window the stack does not hold is left out",
            LinuxRelaunch.fromJson(relaunch.toJson(), id -> false).isEmpty());
    }

    @Test
    public void aForgottenWindowWaitsNoMore() {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        relaunch.await(1, "firefox", null);

        relaunch.forget(1);

        assertFalse(relaunch.isWaiting(1));
        assertTrue(relaunch.isEmpty());
        assertEquals(TilingStack.NO_WINDOW, relaunch.claim("firefox", null));
    }
}
