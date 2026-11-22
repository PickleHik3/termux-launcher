package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The rows stand on a system keyboard's top whether or not the window resized for it: the root's
 * padding and the stack's lift are both worked out from the same insets, so together they reach
 * exactly the keyboard's top and never count it twice (Motorola g15, Android 15, fullscreen: a
 * keyboard-high empty band under the extra-keys bar when the lift read the root's stale padding).
 */
public class SystemImeLiftTest {

    /** The g15's keyboard, its own nav band included. */
    private static final int IME = 640;
    private static final int NAV = 63;

    /** Where the rows stand above the screen's bottom: the root's padding plus the lift. */
    private static int rowsBottom(int ime, int nav, int fit, int rest) {
        return BottomInsets.rootPaddingPx(true, fit, rest)
            + BottomInsets.systemImeLiftPx(ime, nav, fit, rest);
    }

    @Test
    public void aWindowResizedForTheKeyboardIsNotLiftedAgain() {
        assertEquals(0, BottomInsets.systemImeLiftPx(IME, 0, IME, 0));
        assertEquals(IME, rowsBottom(IME, 0, IME, 0));
    }

    @Test
    public void aWindowNotResizedLiftsByTheWholeKeyboard() {
        assertEquals(IME, BottomInsets.systemImeLiftPx(IME, 0, 0, 0));
        assertEquals(IME, rowsBottom(IME, 0, 0, 0));
    }

    @Test
    public void aVisibleNavBarResizedLiftsNothing() {
        assertEquals(0, BottomInsets.systemImeLiftPx(IME, NAV, IME, NAV));
        assertEquals(IME, rowsBottom(IME, NAV, IME, NAV));
    }

    @Test
    public void aVisibleNavBarNotResizedLiftsTheKeyboardAboveTheBar() {
        assertEquals(IME - NAV, BottomInsets.systemImeLiftPx(IME, NAV, NAV, NAV));
        assertEquals(IME, rowsBottom(IME, NAV, NAV, NAV));
    }

    @Test
    public void aHiddenNavBarWhoseBandTheRootFitsIsClearedOnce() {
        assertEquals(IME - NAV, BottomInsets.systemImeLiftPx(IME, 0, NAV, NAV));
        assertEquals(IME, rowsBottom(IME, 0, NAV, NAV));
    }

    @Test
    public void noKeyboardLiftsNothing() {
        assertEquals(0, BottomInsets.systemImeLiftPx(0, 0, 0, 0));
        assertEquals(0, BottomInsets.systemImeLiftPx(0, NAV, NAV, NAV));
    }

    /**
     * Parity: for every fit between none and the whole keyboard the rows land on the keyboard's
     * top, never a second keyboard above it.
     */
    @Test
    public void rootPaddingAndLiftTogetherReachTheKeyboardForEveryFit() {
        for (int nav : new int[] {0, NAV}) {
            for (int fit = nav; fit <= IME; fit += 7) {
                assertEquals("nav " + nav + " fit " + fit, IME, rowsBottom(IME, nav, fit, nav));
            }
        }
    }
}
