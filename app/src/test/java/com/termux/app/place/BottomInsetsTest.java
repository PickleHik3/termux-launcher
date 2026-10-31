package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The navigation inset's one owner: the rows and the keyboard stand exactly where the root's old
 * fit put them, with the dock's foot or without, and the foot's surface ends on the screen's edge.
 */
public class BottomInsetsTest {

    /** Pong: a 63 px gesture inset (2412 - 2349). */
    private static final int NAV = 63;

    @Test
    public void pongDockedFootReachesTheEdgeAndTheRowsStay() {
        BottomInsets insets = BottomInsets.of(false, NAV, NAV, true, 0, 0);
        assertEquals(0, insets.rootPaddingPx);
        assertEquals(NAV, insets.keptPx);
        assertEquals(0, insets.stackMarginPx);
        assertEquals(NAV, insets.stackExtraHeightPx);
        assertEquals(NAV, insets.keyboardColumnMarginPx);
        assertEquals(NAV, insets.dockFootPx);
        assertEquals(NAV, insets.rowsBottomFromScreenBottomPx());
    }

    @Test
    public void systemKeyboardFitsTheRootAsBefore() {
        BottomInsets insets = BottomInsets.of(true, 900, NAV, false, 0, 0);
        assertEquals(900, insets.rootPaddingPx);
        assertEquals(0, insets.keptPx);
        assertEquals(0, insets.dockFootPx);
        assertEquals(900, insets.rowsBottomFromScreenBottomPx());
        // Nothing reaches under the system keyboard even if a foot is asked for (D8).
        assertEquals(0, BottomInsets.of(true, 900, NAV, true, 0, 0).dockFootPx);
    }

    @Test
    public void floatingCardKeepsItsGapAboveTheInset() {
        BottomInsets insets = BottomInsets.of(false, NAV, NAV, false, 21, 0);
        assertEquals(NAV + 21, insets.stackMarginPx);
        assertEquals(0, insets.dockFootPx);
        assertEquals(NAV + 21, insets.rowsBottomFromScreenBottomPx());
    }

    /** Parity: the rows' bottom is the old fit + margin + flush in every state. */
    @Test
    public void rowsStandWhereTheOldFitPutThem() {
        for (boolean ime : new boolean[] {false, true})
            for (int fit : new int[] {0, NAV, 900})
                for (int rest : new int[] {0, NAV})
                    for (boolean foot : new boolean[] {false, true})
                        for (int margin : new int[] {0, 21})
                            for (int flush : new int[] {0, 9}) {
                                if (!ime && fit > rest) continue;
                                BottomInsets insets = BottomInsets.of(ime, fit, rest, foot, margin,
                                    flush);
                                String at = "ime=" + ime + " fit=" + fit + " rest=" + rest
                                    + " foot=" + foot + " margin=" + margin + " flush=" + flush;
                                assertEquals(at, fit + margin + flush,
                                    insets.rowsBottomFromScreenBottomPx());
                                assertEquals(at, fit, insets.rootPaddingPx + insets.keptPx);
                                if (insets.dockFootPx > 0) {
                                    // The surface's bottom: the container's, on the screen's edge
                                    // but for the stack pass's own margin.
                                    assertEquals(at, margin,
                                        insets.rootPaddingPx + insets.stackMarginPx);
                                    assertEquals(at, flush + insets.keptPx, insets.dockFootPx);
                                }
                            }
    }
}
