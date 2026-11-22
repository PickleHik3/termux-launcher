package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** #316: the frost under a switch's gap is never left on once its switch is over. */
public class SwitchFrostHoldTest {

    @Test
    public void theSwitchsEndTakesItsFrostAway() {
        SwitchFrostHold hold = new SwitchFrostHold();
        int ticket = hold.begin(1000L, 350L, 100L);
        assertTrue(hold.shown());
        assertTrue(hold.end(ticket));
        assertFalse(hold.shown());
        // An end that runs twice (cancel, then end) takes nothing more away.
        assertFalse(hold.end(ticket));
    }

    @Test
    public void anEarlierSwitchLandingLeavesTheNewerFrostUp() {
        SwitchFrostHold hold = new SwitchFrostHold();
        int first = hold.begin(1000L, 350L, 100L);
        int second = hold.begin(1100L, 350L, 100L);
        assertFalse(hold.end(first));
        assertTrue(hold.shown());
        assertTrue(hold.end(second));
        assertFalse(hold.shown());
    }

    @Test
    public void theBackstopTakesItAwayPastTheTravelTheGateAndTheMargin() {
        SwitchFrostHold hold = new SwitchFrostHold();
        long delay = SwitchFrostHold.backstopDelayMs(350L, 100L);
        assertEquals(350L + 100L + SwitchFrostHold.BACKSTOP_MARGIN_MS, delay);
        int ticket = hold.begin(1000L, 350L, 100L);
        assertFalse(hold.expire(1000L + delay - 1L));
        assertTrue(hold.shown());
        assertTrue(hold.expire(1000L + delay));
        assertFalse(hold.shown());
        // The animator's end after the backstop finds nothing left to take away.
        assertFalse(hold.end(ticket));
    }

    @Test
    public void aSwitchWithoutFrostDropsOneLeftUp() {
        SwitchFrostHold hold = new SwitchFrostHold();
        assertFalse(hold.drop());
        int ticket = hold.begin(1000L, 350L, 100L);
        assertTrue(hold.drop());
        assertFalse(hold.shown());
        assertFalse(hold.end(ticket));
    }
}
