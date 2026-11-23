package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A shared source's registration opens once for any number of widgets stopping and starting in
 * the same turn, and every open is matched by one close.
 */
public class SourceLeaseTest {

    /** A main loop run by hand. */
    private static final class Turns implements SourceLease.Scheduler {
        final List<Runnable> queue = new ArrayList<>();
        @Override public void post(@NonNull Runnable task) { queue.add(task); }
        @Override public void cancel(@NonNull Runnable task) { queue.remove(task); }
        void run() {
            List<Runnable> now = new ArrayList<>(queue);
            queue.clear();
            for (Runnable task : now) task.run();
        }
    }

    private static final class Counting implements SourceLease.Registration {
        int opens, closes;
        boolean refuse;
        @Override public boolean open() { opens++; return !refuse; }
        @Override public void close() { closes++; }
    }

    @Test public void theFirstHolderOpensAndLaterOnesKeepIt() {
        Turns turns = new Turns();
        Counting registration = new Counting();
        SourceLease lease = new SourceLease(turns, registration);
        assertTrue(lease.ensureOpen());
        assertFalse(lease.ensureOpen());
        assertEquals(1, registration.opens);
        assertTrue(lease.isOpen());
    }

    @Test public void aRebuildInOneTurnRegistersNothingNew() {
        Turns turns = new Turns();
        Counting registration = new Counting();
        SourceLease lease = new SourceLease(turns, registration);
        lease.ensureOpen();
        // A measure-driven rebuild, a hundred times over: stop then start in the same turn.
        for (int i = 0; i < 100; i++) {
            lease.closeSoon();
            assertFalse("kept, not opened again", lease.ensureOpen());
        }
        turns.run();
        assertEquals(1, registration.opens);
        assertEquals(0, registration.closes);
        assertTrue(lease.isOpen());
        assertFalse(lease.isClosePending());
    }

    @Test public void theLastHolderLeavingClosesOnTheNextTurn() {
        Turns turns = new Turns();
        Counting registration = new Counting();
        SourceLease lease = new SourceLease(turns, registration);
        lease.ensureOpen();
        lease.closeSoon();
        lease.closeSoon();
        assertEquals("not yet", 0, registration.closes);
        assertEquals("one close queued", 1, turns.queue.size());
        turns.run();
        assertEquals(1, registration.closes);
        assertFalse(lease.isOpen());
        // A holder after the close opens a new registration, closed again in turn.
        assertTrue(lease.ensureOpen());
        lease.closeSoon();
        turns.run();
        assertEquals(2, registration.opens);
        assertEquals(2, registration.closes);
    }

    @Test public void closeNowClosesAtOnceAndDropsThePendingClose() {
        Turns turns = new Turns();
        Counting registration = new Counting();
        SourceLease lease = new SourceLease(turns, registration);
        lease.ensureOpen();
        lease.closeSoon();
        lease.closeNow();
        assertEquals(1, registration.closes);
        assertTrue(turns.queue.isEmpty());
        turns.run();
        lease.closeNow();
        assertEquals("closed once", 1, registration.closes);
    }

    @Test public void aRefusedOpenIsNeverClosedAndIsTriedAgain() {
        Turns turns = new Turns();
        Counting registration = new Counting();
        registration.refuse = true;
        SourceLease lease = new SourceLease(turns, registration);
        assertFalse(lease.ensureOpen());
        lease.closeSoon();
        lease.closeNow();
        turns.run();
        assertEquals(0, registration.closes);
        assertFalse(lease.ensureOpen());
        assertEquals(2, registration.opens);
    }
}
