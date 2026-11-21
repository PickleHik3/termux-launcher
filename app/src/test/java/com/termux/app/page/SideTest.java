package com.termux.app.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The two sides of the seam and the rows each owns (ADR 0030 section 2): Home owns the apps row,
 * the A-Z row, the search field and its own extra-keys bar; the Terminal owns its extra keys, the
 * minimal-layout tab and the window chips. Both own an extra-keys bar of their own.
 */
public class SideTest {

    @Test public void homeOwnsItsRows() {
        assertTrue(Side.HOME.owns(Side.Row.APPS));
        assertTrue(Side.HOME.owns(Side.Row.AZ));
        assertTrue(Side.HOME.owns(Side.Row.SEARCH));
        assertTrue(Side.HOME.owns(Side.Row.EXTRA_KEYS));
        assertFalse(Side.HOME.owns(Side.Row.MINIMAL_TAB));
        assertFalse(Side.HOME.owns(Side.Row.WINDOW_CHIPS));
    }

    @Test public void theTerminalOwnsItsRows() {
        assertTrue(Side.TERMINAL.owns(Side.Row.EXTRA_KEYS));
        assertTrue(Side.TERMINAL.owns(Side.Row.MINIMAL_TAB));
        assertTrue(Side.TERMINAL.owns(Side.Row.WINDOW_CHIPS));
        assertFalse(Side.TERMINAL.owns(Side.Row.APPS));
        assertFalse(Side.TERMINAL.owns(Side.Row.AZ));
        assertFalse(Side.TERMINAL.owns(Side.Row.SEARCH));
    }

    @Test public void everyRowHasASideAndOnlyTheExtraKeysHaveTwo() {
        for (Side.Row row : Side.Row.values()) {
            int owners = (Side.HOME.owns(row) ? 1 : 0) + (Side.TERMINAL.owns(row) ? 1 : 0);
            assertEquals(row.name(), row == Side.Row.EXTRA_KEYS ? 2 : 1, owners);
        }
    }

    @Test public void aWindowIsATerminalPage() {
        assertSame(Side.HOME, Side.of(true));
        assertSame(Side.TERMINAL, Side.of(false));
        assertSame(Side.TERMINAL, Side.HOME.other());
        assertSame(Side.HOME, Side.TERMINAL.other());
        assertEquals(2, Side.COUNT);
    }
}
