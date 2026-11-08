package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Who holds the system IME while the wall's travel has the keyboard up, and after it lands. */
public class TravelImeHandOverTest {

    @Test
    public void aTravelShowLeavesTheImeWithThePlaceThatHadIt() {
        // Home holds it with the keyboard down; the slide toward the terminal brings the keys up.
        assertTrue(TermuxInAppKeyboard.placeHoldsSystemIme(true, true, true, false, false, true));
        // Even once the landing says the Workspace owns nothing: it waits for landTravelShow.
        assertTrue(TermuxInAppKeyboard.placeHoldsSystemIme(true, true, false, false, false, true));
    }

    @Test
    public void aTravelShowNeverHandsTheImeToAPlaceThatDidNotHaveIt() {
        assertFalse(TermuxInAppKeyboard.placeHoldsSystemIme(false, true, true, false, false, true));
    }

    @Test
    public void onceLandedTheAnswerIsThePlacesAgain() {
        // The Workspace owns no fields: the keyboard up takes the IME back.
        assertFalse(TermuxInAppKeyboard.placeHoldsSystemIme(true, false, false, false, false, true));
        // Home with the keyboard down holds it; up, it gives it back unless it keeps it.
        assertTrue(TermuxInAppKeyboard.placeHoldsSystemIme(false, false, true, false, false, false));
        assertFalse(TermuxInAppKeyboard.placeHoldsSystemIme(true, false, true, false, false, true));
        assertTrue(TermuxInAppKeyboard.placeHoldsSystemIme(false, false, true, false, true, true));
        // A field outside the keyboard typing with the phone's keyboard: the place does not hold it.
        assertFalse(TermuxInAppKeyboard.placeHoldsSystemIme(true, false, true, true, false, false));
    }
}
