package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** A second activity instance coming and going must hand the sessions back to the first. */
public class TerminalSessionClientRosterTest {

    private final TerminalSessionClientRoster<String> roster = new TerminalSessionClientRoster<>();

    @Test
    public void theNewestAttachedClientIsCurrent() {
        roster.attach("first");
        assertSame("second", roster.attach("second"));
        assertEquals("second", roster.current());
    }

    @Test
    public void detachingTheCurrentClientHandsBackToThePreviousOne() {
        roster.attach("first");
        roster.attach("second");

        assertEquals("first", roster.detach("second"));
        assertEquals("first", roster.current());
    }

    @Test
    public void detachingANonCurrentClientLeavesTheCurrentOneAlone() {
        roster.attach("first");
        roster.attach("second");

        assertEquals("second", roster.detach("first"));
        assertEquals("second", roster.current());
    }

    @Test
    public void theLastDepartureLeavesNobody() {
        roster.attach("only");

        assertNull(roster.detach("only"));
        assertTrue(roster.isEmpty());
        assertNull(roster.current());
    }

    @Test
    public void detachingAStrangerChangesNothing() {
        roster.attach("first");

        assertEquals("first", roster.detach("stranger"));
        assertEquals("first", roster.current());
    }

    @Test
    public void reattachingMovesAClientToTheFront() {
        roster.attach("first");
        roster.attach("second");

        roster.attach("first");

        assertEquals("first", roster.current());
        assertEquals("second", roster.detach("first"));
    }

    @Test
    public void detachAllEmptiesTheRosterAndReportsWhoLeft() {
        roster.attach("first");
        roster.attach("second");

        assertEquals(Arrays.asList("first", "second"), roster.detachAll());
        assertTrue(roster.isEmpty());
        assertFalse(roster.contains("first"));
    }
}
