package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stopping the display: close every app, wait for the list to drain, then kill the server. */
public class DisplayStopSequenceTest {

    /** Records what the sequence asked for, in order: {@code close} and {@code kill}. */
    private static final class RecordingActions implements DisplayStopSequence.Actions {
        final List<String> calls = new ArrayList<>();
        @Override public void closeAllWindows() { calls.add("close"); }
        @Override public void killDisplayServer() { calls.add("kill"); }
    }

    /** The drain's ceiling, stepped by hand. */
    private static final class FakeScheduler implements DisplayStopSequence.Scheduler {
        @Nullable Runnable pending;
        long delayMs;
        int scheduled;

        @Override public void schedule(long delayMs, @NonNull Runnable action) {
            this.delayMs = delayMs;
            pending = action;
            scheduled++;
        }

        @Override public void cancel() {
            pending = null;
        }

        /** Let the ceiling run out. */
        void expire() {
            Runnable run = pending;
            pending = null;
            if (run != null) run.run();
        }
    }

    private RecordingActions actions;
    private FakeScheduler scheduler;
    private DisplayStopSequence sequence;

    @Before
    public void setUp() {
        actions = new RecordingActions();
        scheduler = new FakeScheduler();
        sequence = new DisplayStopSequence(actions, scheduler);
    }

    @Test
    public void closesEveryWindowBeforeKillingTheServer() {
        sequence.start(2);

        assertEquals(Arrays.asList("close"), actions.calls);
        assertTrue(sequence.isWaitingForDrain());
        assertEquals(DisplayStopSequence.DRAIN_TIMEOUT_MS, scheduler.delayMs);
    }

    @Test
    public void killsAsSoonAsTheListDrains() {
        sequence.start(2);
        sequence.onWindowsChanged(1);

        assertEquals(Arrays.asList("close"), actions.calls);
        assertTrue(sequence.isWaitingForDrain());

        sequence.onWindowsChanged(0);

        assertEquals(Arrays.asList("close", "kill"), actions.calls);
        assertFalse(sequence.isWaitingForDrain());
        assertEquals(null, scheduler.pending);
    }

    @Test
    public void killsAnywayWhenTheDrainRunsOut() {
        sequence.start(3);
        scheduler.expire();

        assertEquals(Arrays.asList("close", "kill"), actions.calls);
        assertFalse(sequence.isWaitingForDrain());
    }

    @Test
    public void anEmptyDisplayIsKilledStraightAway() {
        sequence.start(0);

        assertEquals(Arrays.asList("kill"), actions.calls);
        assertFalse(sequence.isWaitingForDrain());
        assertEquals(0, scheduler.scheduled);
    }

    @Test
    public void killsOnlyOnceWhenTheDrainAndTheCeilingRace() {
        sequence.start(1);
        sequence.onWindowsChanged(0);
        scheduler.expire();

        assertEquals(Arrays.asList("close", "kill"), actions.calls);
    }

    @Test
    public void aSecondStopWhileDrainingChangesNothing() {
        sequence.start(2);
        sequence.start(2);

        assertEquals(Arrays.asList("close"), actions.calls);
        assertEquals(1, scheduler.scheduled);
    }

    @Test
    public void windowsChangingOutsideAStopIsIgnored() {
        sequence.onWindowsChanged(0);
        sequence.onWindowsChanged(2);

        assertTrue(actions.calls.isEmpty());
    }

    @Test
    public void aStopAfterOneThatFinishedStartsOver() {
        sequence.start(1);
        sequence.onWindowsChanged(0);
        sequence.start(2);

        assertEquals(Arrays.asList("close", "kill", "close"), actions.calls);
        assertTrue(sequence.isWaitingForDrain());
    }

    @Test
    public void abandoningAWaitKillsNothing() {
        sequence.start(2);
        sequence.abandon();
        sequence.onWindowsChanged(0);

        assertEquals(Arrays.asList("close"), actions.calls);
        assertFalse(sequence.isWaitingForDrain());
        assertEquals(null, scheduler.pending);
    }
}
