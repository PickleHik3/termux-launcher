package com.termux.app.tour;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Which key of a chord card glows, as the user latches their way through the chord.
 *
 * <p>The in-app keyboard latches a modifier on a tap, so the three keys of Ctrl+Alt+Enter are
 * three separate presses and the glow has to keep up with them without ever running ahead.
 */
public class TourChordGlowTest {

    private static final TourStep SPLIT = new TourStep("kb_split", 1, 0,
        new String[] {TourTargets.CTRL_KEY, TourTargets.ALT_KEY, TourTargets.ENTER_KEY},
        new String[] {TourSignals.PANE_SPLIT},
        new TourGesture[] {TourGesture.TAP}, false, true);

    private static final TourStep SESSION = new TourStep("kb_session", 1, 0,
        new String[] {TourTargets.CTRL_KEY, TourTargets.ALT_KEY, TourTargets.SHIFT_KEY,
            TourTargets.C_KEY},
        new String[] {TourSignals.SESSION_OPENED},
        new TourGesture[] {TourGesture.TAP}, false, true);

    private static final TourStep PLAIN = new TourStep("drawer", 1, 0, TourTargets.DOCK,
        new String[] {TourSignals.DRAWER_OPENED},
        new TourGesture[] {TourGesture.DRAG_DOWN});

    @Test
    public void nothingLatchedAsksForTheFirstModifier() {
        assertEquals(0, TourChordGlow.indexFor(SPLIT, false, false, false));
    }

    @Test
    public void theGlowWalksOneKeyForEachModifierTheUserLatches() {
        assertEquals(1, TourChordGlow.indexFor(SPLIT, true, false, false));
        assertEquals(2, TourChordGlow.indexFor(SPLIT, true, true, false));
    }

    @Test
    public void theKeyTheChordEndsOnKeepsTheGlowOnceEveryModifierIsDown() {
        // Shift is nothing to do with this chord, and holding it does not move the glow past the
        // key the card is asking for.
        assertEquals(2, TourChordGlow.indexFor(SPLIT, true, true, true));
    }

    @Test
    public void aModifierLatchedOutOfOrderStillAsksForTheOneTheCardNamedFirst() {
        assertEquals(0, TourChordGlow.indexFor(SPLIT, false, true, false));
        assertEquals(0, TourChordGlow.indexFor(SPLIT, false, false, true));
    }

    @Test
    public void aThreeModifierChordWalksAllThreeBeforeItsLetter() {
        assertEquals(0, TourChordGlow.indexFor(SESSION, false, false, false));
        assertEquals(1, TourChordGlow.indexFor(SESSION, true, false, false));
        assertEquals(2, TourChordGlow.indexFor(SESSION, true, true, false));
        assertEquals(3, TourChordGlow.indexFor(SESSION, true, true, true));
    }

    @Test
    public void aCardThatIsNotAChordNeverMovesItsGlow() {
        assertEquals(0, TourChordGlow.indexFor(PLAIN, true, true, true));
        assertEquals(0, TourChordGlow.indexFor(null, true, true, true));
    }

    @Test
    public void everyChordCardOfTheRunEndsOnAKeyOfItsOwn() {
        for (TourStep step : TourRun.steps()) {
            if (!step.chordGlow) continue;
            int last = TourChordGlow.indexFor(step, true, true, true);
            assertEquals("chord " + step.id + " should land on its last key",
                step.targetCount() - 1, last);
        }
    }
}
