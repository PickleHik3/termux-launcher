package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageRef;

import org.junit.Test;

/** The layout fixtures are the five layouts they say they are, so a parity over them means it. */
public class LayoutFixturesTest {

    @Test
    public void pongStandsDockRowsUnderItsKeyboardAndTl16None() {
        assertTrue(LayoutFixtures.PONG.settle(PageRef.TERMINAL, true, false).bandsUnderKeyboard);
        assertFalse(LayoutFixtures.TL16.settle(PageRef.TERMINAL, true, false).bandsUnderKeyboard);
        assertEquals(98, LayoutFixtures.EXTRA_KEYS_ROW_PX);
    }

    @Test
    public void theFloatingKeyboardIsLaidOutInItsOwnFrame() {
        assertEquals(0, LayoutFixtures.FLOATING_KEYBOARD.keyboardLaidOutPx());
        assertEquals(LayoutFixtures.KEYBOARD_PX, LayoutFixtures.PONG.keyboardLaidOutPx());
    }

    @Test
    public void onlyTheBottomStatusFixtureStandsABandInTheStack() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL) {
            int band = f.settle(PageRef.TERMINAL, true, false).statusBandPx;
            if (f == LayoutFixtures.STATUS_BOTTOM) {
                assertEquals(f.toString(), LayoutFixtures.BOTTOM_STATUS_BAND_PX, band);
            } else {
                assertEquals(f.toString(), 0, band);
            }
        }
        assertNotEquals(LayoutFixtures.PONG.settle(PageRef.TERMINAL, true, false).stackHeightPx,
            LayoutFixtures.STATUS_BOTTOM.settle(PageRef.TERMINAL, true, false).stackHeightPx);
    }

    @Test
    public void theSplitPaneStandsTheFlushDown() {
        assertTrue(LayoutFixtures.PONG.pass(PageRef.TERMINAL, false, false).flushAllowed);
        assertFalse(LayoutFixtures.SPLIT_PANE.pass(PageRef.TERMINAL, false, false).flushAllowed);
    }
}
