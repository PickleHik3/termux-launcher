package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which session a new row window joins: the one the bar's session box shows. */
public class NewWindowSessionPolicyTest {

    @Test public void aFreshStartBeforeAnyWorkspaceVisitAddsToSessionOne() {
        // Session 1 up with its one window, current, the Workspace never shown.
        assertEquals(0, NewWindowSessionPolicy.sessionFor(0, 1));
    }

    @Test public void afterAVisitTheCurrentSessionTakesIt() {
        assertEquals(0, NewWindowSessionPolicy.sessionFor(0, 2));
        assertEquals(1, NewWindowSessionPolicy.sessionFor(1, 2));
    }

    @Test public void sessionsStandingWithNoneCurrentNeverStartASession() {
        // No current session (the row emptied under Home): the box reads "1", so session 1.
        assertEquals(0, NewWindowSessionPolicy.sessionFor(-1, 1));
        assertEquals(0, NewWindowSessionPolicy.sessionFor(-1, 3));
    }

    @Test public void aCurrentPastTheEndIsTheLastSessionAsTheBoxReadsIt() {
        assertEquals(1, NewWindowSessionPolicy.sessionFor(5, 2));
    }

    @Test public void onlyNoSessionAtAllStartsOne() {
        assertEquals(NewWindowSessionPolicy.NEW_SESSION, NewWindowSessionPolicy.sessionFor(-1, 0));
        assertEquals(NewWindowSessionPolicy.NEW_SESSION, NewWindowSessionPolicy.sessionFor(0, 0));
    }
}
