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

    /**
     * Pong's reply panel: the keyboard (972 px fit) comes up over Home and goes again. The content
     * column hangs above the stack, which stands on the kept band until the stack's pass, so the
     * inset dispatch must move the column by exactly the root's padding change, both ways, or the
     * grid is laid out a band short (up) or a band tall (down) for one layout.
     */
    @Test
    public void homeReachAnswersTheRootPaddingAndTheBandTogether() {
        int ime = 972;
        int restTake = KeyboardOverlayPolicy.systemImeTakePx(NAV, NAV, 0);
        int imeTake = KeyboardOverlayPolicy.systemImeTakePx(ime, NAV, 0);
        int restReach = BottomInsets.homeContentReachPx(restTake,
            BottomInsets.keptPx(false, NAV, NAV));
        int imeReach = BottomInsets.homeContentReachPx(imeTake,
            BottomInsets.keptPx(true, ime, NAV));
        int paddingUp = BottomInsets.rootPaddingPx(true, ime, NAV)
            - BottomInsets.rootPaddingPx(false, NAV, NAV);
        assertEquals(ime, paddingUp);
        // Keyboard up: the column reaches down by all the root's padding grew by.
        assertEquals(paddingUp, imeReach - restReach);
        // Keyboard down: and back by the same, not by the take alone (a band short of it).
        assertEquals(-paddingUp, restReach - imeReach);
        assertEquals(ime - NAV, imeTake - restTake);
    }

    @Test
    public void homeReachIgnoresNegativeInputs() {
        assertEquals(0, BottomInsets.homeContentReachPx(-5, -5));
        assertEquals(-NAV, BottomInsets.homeContentReachPx(0, NAV));
    }

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
        // The band is laid out as the foot lays it (the container reaches the edge), so the foot
        // changing hands at a landing moves no layout number; only the surface is the foot's.
        assertEquals(21, insets.stackMarginPx);
        assertEquals(NAV, insets.stackExtraHeightPx);
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

    /**
     * The tail of a Workspace -> Home crossing on pong: Home's rows stand, so the stack takes the
     * foot (margin 0, the column one inset up) while the Workspace's 654 px keyboard is still laid
     * out in the column and the travel holds the stack 654 px down. The stack's clip cuts at its
     * own bottom, the screen's edge, which left the keyboard's top 63 px (its number row) showing
     * under the gesture pill; the keyboard's own cut hides all of it.
     */
    @Test
    public void keyboardSlidOutUnderTheFootIsCutWhole() {
        int keyboard = 654;
        BottomInsets foot = BottomInsets.forKept(0, NAV, true, 0, 0);
        int travel = keyboard;
        // The stack's clip alone hides what slid past the stack's bottom, which stands the
        // column's margin (the foot's band) below the keyboard: the band's worth stays in sight.
        int hiddenByStackClip = travel - foot.keyboardColumnMarginPx - foot.stackPaddingPx;
        assertEquals(keyboard - NAV, hiddenByStackClip);
        assertEquals(keyboard,
            BottomInsets.keyboardTravelHiddenPx(travel, 0f, 0, foot.stackPaddingPx));
    }

    /** Without a foot the column stands the kept band up, as with one; the cut is the same. */
    @Test
    public void keyboardCutWithoutAFootIsTheStacksOwn() {
        int flush = 24;
        BottomInsets noFoot = BottomInsets.forKept(0, NAV, false, 0, flush);
        assertEquals(NAV, noFoot.keyboardColumnMarginPx);
        // The stack clips at its bottom minus the travel; the keyboard ends the padding above it.
        assertEquals(300 - flush,
            BottomInsets.keyboardTravelHiddenPx(300f, 0f, 0, noFoot.stackPaddingPx));
        // Bands under the keyboard take the slide first.
        assertEquals(300 - flush - 120,
            BottomInsets.keyboardTravelHiddenPx(300f, 0f, 120, noFoot.stackPaddingPx));
        // At rest, or slid up, nothing is cut.
        assertEquals(0, BottomInsets.keyboardTravelHiddenPx(0f, 0f, 0, 0));
        assertEquals(0, BottomInsets.keyboardTravelHiddenPx(-80f, 0f, 0, 0));
        // The keyboard's own share behind held bands wins when it is the larger cut.
        assertEquals(200, BottomInsets.keyboardTravelHiddenPx(300f, 200f, 120, 0));
    }

    // ---- The keyboard's foot (ADR 0029 section 6) ------------------------------------------

    /** Docked, the keyboard is a slice of the frame: its host reaches the edge by the kept band. */
    @Test
    public void keyboardFootMovesTheBandFromTheColumnMarginIntoTheKeyboard() {
        BottomInsets noDock = BottomInsets.forKept(0, NAV, false, 0, 0, true);
        assertEquals(NAV, noDock.keyboardFootPx);
        assertEquals(0, noDock.stackExtraHeightPx);
        assertEquals(0, noDock.keyboardColumnMarginPx);
        assertEquals(0, noDock.dockFootPx);
        assertEquals(NAV, noDock.containerExtraPx());
        assertEquals(NAV, noDock.rowsBottomFromScreenBottomPx());

        BottomInsets dock = BottomInsets.forKept(0, NAV, true, 0, 6, true);
        assertEquals(NAV, dock.keyboardFootPx);
        assertEquals(0, dock.stackExtraHeightPx);
        assertEquals(0, dock.stackPaddingPx);
        assertEquals(6, dock.keyboardColumnMarginPx);
        assertEquals(6 + NAV, dock.dockFootPx);
        assertEquals(6 + NAV, dock.rowsBottomFromScreenBottomPx());
    }

    @Test
    public void keyboardFootChangesNeitherTheRowsNorTheContainer() {
        for (int kept : new int[] {0, NAV})
            for (boolean dockFoot : new boolean[] {false, true})
                for (int margin : new int[] {0, 21})
                    for (int flush : new int[] {0, 9}) {
                        BottomInsets without = BottomInsets.forKept(0, kept, dockFoot, margin, flush);
                        BottomInsets with = BottomInsets.forKept(0, kept, dockFoot, margin, flush,
                            true);
                        String at = "kept=" + kept + " dockFoot=" + dockFoot + " margin=" + margin
                            + " flush=" + flush;
                        assertEquals(at, without.rowsBottomFromScreenBottomPx(),
                            with.rowsBottomFromScreenBottomPx());
                        assertEquals(at, without.containerExtraPx(), with.containerExtraPx());
                        assertEquals(at, without.dockFootPx, with.dockFootPx);
                        assertEquals(at, without.stackMarginPx, with.stackMarginPx);
                        assertEquals(at, 0, without.keyboardFootPx);
                        assertEquals(at, kept, with.keyboardFootPx);
                    }
    }

    /** The dress phase and the stack pass read the foot from the same static. */
    @Test
    public void dressPhaseFootEqualsTheStackPassFoot() {
        for (int kept : new int[] {0, NAV})
            for (boolean flag : new boolean[] {false, true})
                for (boolean dockFoot : new boolean[] {false, true}) {
                    BottomInsets pass = BottomInsets.forKept(0, kept, dockFoot, 0, 0, flag);
                    assertEquals(pass.keyboardFootPx, BottomInsets.keyboardFootPx(kept, flag));
                }
        assertEquals(0, BottomInsets.keyboardFootPx(0, true));
        assertEquals(0, BottomInsets.keyboardFootPx(NAV, false));
    }

    @Test
    public void keysAreTheLaidOutHeightLessTheFoot() {
        assertEquals(654, BottomInsets.keyboardKeysPx(654 + NAV, NAV));
        assertEquals(654, BottomInsets.keyboardKeysPx(654, 0));
        assertEquals(0, BottomInsets.keyboardKeysPx(10, NAV));
    }

    /** The cut also eats the foot in proportion to the keyboard's share. */
    @Test
    public void keyboardTravelCutEatsTheFootWithTheShare() {
        int keys = 654;
        // Share 0: nothing is hidden.
        assertEquals(0, BottomInsets.keyboardTravelHiddenPx(0f, 0f, 0, 0, NAV, keys));
        // Half the keys slid: half the foot goes with it.
        assertEquals(327 + Math.round(NAV / 2f),
            BottomInsets.keyboardTravelHiddenPx(327f, 327f, 0, 0, NAV, keys));
        // Down at rest (share = keys): the whole keyboard, foot included.
        assertEquals(keys + NAV,
            BottomInsets.keyboardTravelHiddenPx(keys, keys, 0, 0, NAV, keys));
        // A crossing's travel past the keyboard (dock too) hides at least that much.
        assertEquals(keys + 300,
            BottomInsets.keyboardTravelHiddenPx(keys + 300f, keys, 0, 0, NAV, keys));
        // No foot: today's answer.
        assertEquals(BottomInsets.keyboardTravelHiddenPx(300f, 200f, 120, 0),
            BottomInsets.keyboardTravelHiddenPx(300f, 200f, 120, 0, 0, keys));
    }
}
