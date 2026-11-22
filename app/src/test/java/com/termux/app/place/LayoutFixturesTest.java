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

    /**
     * D11: on Home with the keyboard up a Floating Peek dock is Home's card standing on the
     * keyboard, flush, so Home's rest loses Floating's edge gap and nothing else; the Terminal's
     * rest (its tray) is untouched.
     */
    @Test
    public void homePeekStandsOnTheKeyboardAndTheTerminalRestHolds() {
        LayoutFixtures.Fixture tray = LayoutFixtures.PONG;
        LayoutFixtures.Fixture peek = LayoutFixtures.PONG.withHomeDesign(
            com.termux.app.dock.DockStyle.PEEK_GLASS);
        StackLayout.Result trayHome = tray.settle(PageRef.HOME, true, true);
        StackLayout.Result peekHome = peek.settle(PageRef.HOME, true, true);
        assertTrue("the tray keeps Floating's gap over the keyboard", trayHome.bottomMarginPx > 0);
        assertEquals("the Peek card stands flush on the keyboard", 0, peekHome.bottomMarginPx);
        assertEquals(trayHome.stackHeightPx, peekHome.stackHeightPx);
        // Keyboard down the card stood already: nothing changes there.
        assertEquals(0, peek.settle(PageRef.HOME, false, true).bottomMarginPx);
        for (boolean keyboard : new boolean[] {false, true}) {
            StackLayout.Result before = tray.settle(PageRef.TERMINAL, keyboard, true);
            StackLayout.Result after = peek.settle(PageRef.TERMINAL, keyboard, true);
            assertEquals(before.stackHeightPx, after.stackHeightPx);
            assertEquals(before.bottomMarginPx, after.bottomMarginPx);
            assertEquals(tray.restReservationPx(PageRef.TERMINAL, keyboard, true),
                peek.restReservationPx(PageRef.TERMINAL, keyboard, true));
        }
    }
}
