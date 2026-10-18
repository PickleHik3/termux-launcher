package com.termux.privileged;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The privileged-availability signal: only real flips reach the listeners, always via main. */
public class PrivilegedAvailabilitySignalTest {

    /** Stands in for the main looper: posted work runs only when the test drains it. */
    private final ArrayDeque<Runnable> mainQueue = new ArrayDeque<>();
    private final PrivilegedAvailabilitySignal signal = new PrivilegedAvailabilitySignal(mainQueue::add);
    private final List<Boolean> heard = new ArrayList<>();
    private final PrivilegedAvailabilitySignal.Listener listener = heard::add;

    private void drainMain() {
        while (!mainQueue.isEmpty()) mainQueue.poll().run();
    }

    @Test public void startsUnavailableAndSaysNothingUntilSomethingFlips() {
        signal.add(listener);
        assertFalse(signal.delivered());
        signal.report(false);
        signal.report(false);
        assertTrue(mainQueue.isEmpty());
        drainMain();
        assertTrue(heard.isEmpty());
    }

    @Test public void aFlipIsDeliveredOnMainOnlyOnce() {
        signal.add(listener);
        signal.report(true);
        signal.report(true);
        assertTrue("delivered through main, never inline", heard.isEmpty());
        drainMain();
        assertEquals(List.of(true), heard);
        assertTrue(signal.delivered());

        signal.report(true);
        drainMain();
        assertEquals(List.of(true), heard);

        signal.report(false);
        drainMain();
        assertEquals(List.of(true, false), heard);
    }

    @Test public void aFlipAndItsUndoBeforeMainRunsAreNoBlink() {
        signal.add(listener);
        signal.report(true);
        drainMain();
        heard.clear();
        // A re-initialization's INITIALIZING step, then READY again, both before main runs.
        signal.report(false);
        signal.report(true);
        drainMain();
        assertTrue(heard.isEmpty());
        assertTrue(signal.delivered());
    }

    @Test public void deliveryCarriesTheLatestReportNotThePostedOne() {
        signal.add(listener);
        signal.report(true);
        signal.report(false);
        signal.report(true);
        drainMain();
        assertEquals(List.of(true), heard);
    }

    @Test public void aRemovedListenerHearsNothing() {
        signal.add(listener);
        signal.remove(listener);
        signal.report(true);
        drainMain();
        assertTrue(heard.isEmpty());
        assertTrue("the delivered value still moves for later readers", signal.delivered());
    }

    @Test public void aListenerMayReRegisterItselfWhileBeingTold() {
        PrivilegedAvailabilitySignal.Listener[] self = new PrivilegedAvailabilitySignal.Listener[1];
        self[0] = available -> {
            heard.add(available);
            // A view rebuilding stops (unregisters) and starts (registers) again.
            signal.remove(self[0]);
            signal.add(self[0]);
        };
        signal.add(self[0]);
        signal.report(true);
        drainMain();
        assertEquals(List.of(true), heard);
        signal.report(false);
        drainMain();
        assertEquals(List.of(true, false), heard);
    }
}
