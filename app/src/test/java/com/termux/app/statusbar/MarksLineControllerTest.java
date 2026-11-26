package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import android.view.View;

import org.junit.Test;

public class MarksLineControllerTest {

    @Test public void underMarksTheLineOfTheBarsFormStands() {
        // The answer has no page, rest or crossing in it: where the Terminal's strip stands (on
        // Home it rests out of sight) is the top stack's, which carries the line with the chips.
        assertEquals(View.VISIBLE, MarksLineController.lineVisibility(true, false, false));
        assertEquals(View.VISIBLE, MarksLineController.lineVisibility(true, true, true));
    }

    @Test public void theOtherFormOfTheLineIsGone() {
        assertEquals(View.GONE, MarksLineController.lineVisibility(true, false, true));
        assertEquals(View.GONE, MarksLineController.lineVisibility(true, true, false));
    }

    @Test public void underGlyphsEveryLineViewIsGone() {
        for (int column = 0; column < 2; column++)
            for (int vertical = 0; vertical < 2; vertical++)
                assertEquals(View.GONE, MarksLineController.lineVisibility(
                    false, vertical == 1, column == 1));
    }
}
