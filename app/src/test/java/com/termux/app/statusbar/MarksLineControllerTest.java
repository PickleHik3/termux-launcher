package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;

import android.view.View;

import org.junit.Test;

public class MarksLineControllerTest {

    @Test public void underMarksTheFixedLineStandsWhateverThePageOrCrossing() {
        // The answer has no page, rest or crossing in it: the one line is shown from rest on
        // Home, through every frame of a slide, to rest on the Terminal.
        assertEquals(View.VISIBLE,
            MarksLineController.lineVisibility(true, false, false, false));
        assertEquals(View.VISIBLE,
            MarksLineController.lineVisibility(true, true, false, true));
    }

    @Test public void theOtherFormOfTheFixedLineIsGone() {
        assertEquals(View.GONE, MarksLineController.lineVisibility(true, false, false, true));
        assertEquals(View.GONE, MarksLineController.lineVisibility(true, true, false, false));
    }

    @Test public void homesStripKeepsOnlyAnInvisibleSpacerThatTravelsWithItsPage() {
        assertEquals(View.INVISIBLE,
            MarksLineController.lineVisibility(true, false, true, false));
        assertEquals(View.INVISIBLE,
            MarksLineController.lineVisibility(true, true, true, true));
        assertEquals(View.GONE, MarksLineController.lineVisibility(true, false, true, true));
    }

    @Test public void underGlyphsEveryLineViewIsGone() {
        for (int spacer = 0; spacer < 2; spacer++)
            for (int column = 0; column < 2; column++)
                for (int vertical = 0; vertical < 2; vertical++)
                    assertEquals(View.GONE, MarksLineController.lineVisibility(
                        false, vertical == 1, spacer == 1, column == 1));
    }
}
