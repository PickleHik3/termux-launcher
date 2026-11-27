package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.TerminalPaneController;

import org.junit.Test;

/** The seam between the stack's long window ids and the pane controller's int hosted ids (#418). */
public class TilingHostedIdsTest {

    @Test
    public void aWindowIdComesBackFromItsHostedId() {
        for (long id : new long[] {1, 2, 99, Integer.MAX_VALUE}) {
            int hosted = TilingHostedIds.hostedId(id);
            assertTrue(TilingHostedIds.isTilingWindow(hosted));
            assertEquals(id, TilingHostedIds.windowId(hosted));
        }
    }

    @Test
    public void hostedIdsNeverMeetAWidgetsOrTheUnhostedMark() {
        // AppWidget ids are 0 or more; the pane controller's "not hosted" is Integer.MIN_VALUE.
        assertFalse(TilingHostedIds.isTilingWindow(0));
        assertFalse(TilingHostedIds.isTilingWindow(42));
        assertFalse(TilingHostedIds.isTilingWindow(TerminalPaneController.Window.NOT_HOSTED));
        assertTrue(TilingHostedIds.hostedId(1) < 0);
        assertNotEquals(TerminalPaneController.Window.NOT_HOSTED,
            TilingHostedIds.hostedId(Integer.MAX_VALUE));
    }

    @Test
    public void anIdTheHostedRangeCannotHoldIsRefused() {
        assertEquals(TilingHostedIds.NONE, TilingHostedIds.hostedId(0));
        assertEquals(TilingHostedIds.NONE, TilingHostedIds.hostedId(-5));
        assertEquals(TilingHostedIds.NONE, TilingHostedIds.hostedId(Integer.MAX_VALUE + 1L));
        assertEquals(TilingStack.NO_WINDOW, TilingHostedIds.windowId(7));
    }
}
