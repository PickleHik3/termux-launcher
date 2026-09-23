package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The hold/release bookkeeping in isolation: no session, no dispatcher, no host — just whether a
 * handle is remembered, released and cleared on the exit path the way {@code keyboard.hide --hold}
 * needs.
 */
public class KeyboardHoldTrackerTest {

    private final KeyboardHoldTracker tracker = KeyboardHoldTracker.getInstance();

    @After
    public void clear() {
        tracker.clear();
    }

    @Test
    public void notHeldUntilSomethingHolds() {
        assertFalse(tracker.isHeld());
        assertFalse(tracker.isHeldBy("a"));
    }

    @Test
    public void holdingRecordsTheHandle() {
        tracker.hold("session-a");

        assertTrue(tracker.isHeld());
        assertTrue(tracker.isHeldBy("session-a"));
        assertFalse(tracker.isHeldBy("session-b"));
    }

    @Test
    public void aSecondHoldReplacesTheFirst() {
        tracker.hold("session-a");
        tracker.hold("session-b");

        assertFalse(tracker.isHeldBy("session-a"));
        assertTrue(tracker.isHeldBy("session-b"));
    }

    @Test
    public void releaseClearsWhoeverIsHeldWhoeverAsks() {
        tracker.hold("session-a");

        tracker.release();

        assertFalse(tracker.isHeld());
    }

    @Test
    public void releaseOnSessionFinishedClearsAMatchingHoldAndSaysSo() {
        tracker.hold("session-a");

        assertTrue(tracker.releaseOnSessionFinished("session-a"));
        assertFalse(tracker.isHeld());
    }

    @Test
    public void releaseOnSessionFinishedIgnoresAnUnrelatedSession() {
        tracker.hold("session-a");

        assertFalse(tracker.releaseOnSessionFinished("session-b"));
        assertTrue("the real hold survives an unrelated session ending", tracker.isHeldBy("session-a"));
    }

    @Test
    public void releaseOnSessionFinishedIsFalseWhenNothingIsHeld() {
        assertFalse(tracker.releaseOnSessionFinished("session-a"));
    }
}
