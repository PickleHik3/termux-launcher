package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The keep-screen-on rule for the dictation pill, without a window. */
public class VoiceScreenHoldTest {

    private final List<Boolean> flag = new ArrayList<>();
    private Runnable pending;
    private long pendingDelay = -1;

    private final VoiceScreenHold hold = new VoiceScreenHold(flag::add, new VoiceScreenHold.Timer() {
        @Override
        public void schedule(@NonNull Runnable task, long delayMs) {
            pending = task;
            pendingDelay = delayMs;
        }

        @Override
        public void cancel(@NonNull Runnable task) {
            if (pending == task) pending = null;
        }
    });

    @Test
    public void listeningHoldsWithoutALimit() {
        hold.hold(false);
        assertTrue(hold.isHeld());
        assertEquals(List.of(true), flag);
        assertNull(pending);
    }

    @Test
    public void textThatWaitsKeepsTheHoldUntilTheIdleReleaseThenLetsGo() {
        hold.hold(false);
        // The cleanup has landed: still held, now with the idle release armed.
        hold.hold(true);
        assertTrue(hold.isHeld());
        assertEquals(List.of(true), flag);
        assertEquals(VoiceScreenHold.IDLE_RELEASE_MS, pendingDelay);
        pending.run();
        assertFalse(hold.isHeld());
        assertEquals(List.of(true, false), flag);
    }

    @Test
    public void aTouchTakesTheHoldAgainAndStartsTheIdleReleaseOver() {
        hold.hold(true);
        Runnable first = pending;
        first.run();
        assertFalse(hold.isHeld());
        hold.hold(true);
        assertTrue(hold.isHeld());
        assertEquals(List.of(true, false, true), flag);
        assertEquals(VoiceScreenHold.IDLE_RELEASE_MS, pendingDelay);
    }

    @Test
    public void resumingTheDictationDisarmsTheIdleRelease() {
        hold.hold(true);
        hold.hold(false);
        assertNull(pending);
        assertTrue(hold.isHeld());
    }

    @Test
    public void closingReleasesAtOnceAndOnlyOnce() {
        hold.hold(true);
        hold.release();
        assertFalse(hold.isHeld());
        assertNull(pending);
        hold.release();
        assertEquals(List.of(true, false), flag);
    }

    @Test
    public void releasingWhatWasNeverHeldTouchesNothing() {
        hold.release();
        assertTrue(flag.isEmpty());
    }
}
