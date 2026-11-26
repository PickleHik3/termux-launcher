package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlaceSessionsTest {

    /** Windows compared by identity, as the activity's are. */
    private static final class W {
        final String name;
        W(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final W a = new W("a");
    private final W b = new W("b");
    private final W c = new W("c");
    private final W d = new W("d");

    @Test
    public void aPlaceOfOneSessionHasNoRowAndNumberOne() {
        PlaceSessions<W> places = new PlaceSessions<>();
        assertEquals(1, places.count(a));
        assertTrue(places.waiting(a).isEmpty());
        assertEquals(1, places.numberOf(a));
        assertTrue(places.isEmpty());
    }

    @Test
    public void aNewSessionStandsInFrontAndTheOldOneWaits() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addInFront(a, b);
        assertEquals(2, places.count(b));
        assertEquals(Collections.singletonList(a), places.waiting(b));
        assertEquals(2, places.numberOf(b));
        assertEquals(1, places.waitingNumber(b, 0));
        assertSame(b, places.frontOf(a));
        assertTrue(places.isWaiting(a));
        assertFalse(places.isWaiting(b));
        // A third: the newest in front, the one it replaced at the row's start.
        places.addInFront(b, c);
        assertEquals(Arrays.asList(b, a), places.waiting(c));
        assertEquals(3, places.numberOf(c));
    }

    @Test
    public void aSwapPutsTheOldFrontWhereTheChosenCardStood() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addInFront(a, b);
        places.addInFront(b, c); // front c(3), waiting b(2), a(1)
        W old = places.bringToFront(a);
        assertSame(c, old);
        assertEquals(Arrays.asList(b, c), places.waiting(a));
        assertEquals(1, places.numberOf(a));
        assertEquals(3, places.waitingNumber(a, 1));
        // Both stay sessions of the place: nothing was closed.
        assertEquals(3, places.count(a));
        assertNull(places.bringToFront(d));
    }

    @Test
    public void closingTheFrontBringsTheNextIn() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addInFront(a, b);
        places.addInFront(b, c);
        assertSame(b, places.promote(c));
        assertEquals(Collections.singletonList(a), places.waiting(b));
        assertEquals(2, places.numberOf(b));
        // The last one waiting comes in: the place is one session again, number 1.
        assertSame(a, places.promote(b));
        assertTrue(places.isEmpty());
        assertEquals(1, places.numberOf(a));
        assertNull(places.promote(a));
    }

    @Test
    public void closingAWaitingSessionRemovesTheRowWithTheLast() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addWaiting(a, b);
        places.addWaiting(a, c);
        assertEquals(Arrays.asList(b, c), places.waiting(a));
        assertEquals(2, places.waitingNumber(a, 0));
        assertEquals(3, places.waitingNumber(a, 1));
        assertTrue(places.removeWaiting(b));
        assertEquals(Collections.singletonList(c), places.waiting(a));
        assertTrue(places.removeWaiting(c));
        assertTrue(places.isEmpty());
        assertFalse(places.removeWaiting(c));
    }

    @Test
    public void aFreedNumberIsTakenAgain() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addWaiting(a, b);
        places.addWaiting(a, c); // a1 b2 c3
        places.removeWaiting(b);
        places.addWaiting(a, d);
        assertEquals(2, places.waitingNumber(a, 1));
        // A saved number that is taken falls back to a free one.
        PlaceSessions<W> restored = new PlaceSessions<>();
        restored.addWaiting(a, b, 4);
        restored.addWaiting(a, c, 4);
        assertEquals(4, restored.waitingNumber(a, 0));
        assertEquals(2, restored.waitingNumber(a, 1));
        // The front's own number comes back too, unless a waiting session holds it.
        restored.restoreFrontNumber(a, 4);
        assertEquals(1, restored.numberOf(a));
        restored.restoreFrontNumber(a, 3);
        assertEquals(3, restored.numberOf(a));
        restored.restoreFrontNumber(d, 5);
        assertEquals(1, restored.numberOf(d));
    }

    @Test
    public void healKeepsWaitingSessionsOnTheWall() {
        PlaceSessions<W> places = new PlaceSessions<>();
        places.addInFront(a, b); // front b, waiting a
        places.addWaiting(c, d); // front c, waiting d
        List<W> row = new ArrayList<>(Arrays.asList(c)); // b left the row by another path
        assertEquals(1, places.heal(row));
        assertEquals(Arrays.asList(c, a), row);
        assertEquals(Collections.singletonList(d), places.waiting(c));
        assertEquals(0, places.heal(row));
    }

    @Test
    public void everyChangeIsAnnounced() {
        PlaceSessions<W> places = new PlaceSessions<>();
        int[] heard = new int[1];
        places.setOnChanged(() -> heard[0]++);
        places.addInFront(a, b);
        places.bringToFront(a);
        places.removeWaiting(b);
        assertEquals(3, heard[0]);
    }

    @Test
    public void theUpgradePutsEveryWindowOfEverySessionOnTheWall() {
        // Today: session 0 holds [a, b] showing b, session 1 holds [c], session 2 holds [d];
        // session 1 is on screen.
        List<List<W>> sessions = Arrays.asList(Arrays.asList(a, b),
            Collections.singletonList(c), Collections.<W>emptyList(), Collections.singletonList(d));
        PlaceSessions.Folded<W> wall = PlaceSessions.fold(sessions, new int[] {1, 0, 0, 0}, 1);
        assertEquals(Arrays.asList(a, b, c, d), wall.windows);
        assertEquals(2, wall.current);
    }

    @Test
    public void theUpgradeWithNoSessionOnScreenKeepsTheFirstSessionsWindow() {
        List<List<W>> sessions = Arrays.asList(Arrays.asList(a, b), Collections.singletonList(c));
        PlaceSessions.Folded<W> wall = PlaceSessions.fold(sessions, new int[] {9, 0}, -1);
        assertEquals(Arrays.asList(a, b, c), wall.windows);
        assertEquals(1, wall.current);
        PlaceSessions.Folded<W> none = PlaceSessions.fold(Collections.<List<W>>emptyList(),
            new int[0], 0);
        assertTrue(none.windows.isEmpty());
        assertEquals(-1, none.current);
    }
}
