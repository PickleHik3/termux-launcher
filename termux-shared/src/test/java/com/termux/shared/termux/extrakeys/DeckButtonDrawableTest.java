package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeckButtonDrawableTest {

    @Test
    public void footIsWholeButton() {
        assertArrayEquals(new int[] {0, 0, 100, 40}, DeckButtonDrawable.footRect(0, 0, 100, 40));
    }

    @Test
    public void topLosesFootWhenResting() {
        assertArrayEquals(new int[] {0, 0, 100, 38}, DeckButtonDrawable.topRect(0, 0, 100, 40, 2, false));
    }

    @Test
    public void pressedSinksFootAway() {
        assertArrayEquals(new int[] {0, 0, 100, 40}, DeckButtonDrawable.topRect(0, 0, 100, 40, 2, true));
        assertArrayEquals(new int[] {0, 1, 100, 40}, DeckButtonDrawable.faceRect(0, 0, 100, 40, 2, 1, true));
    }

    @Test
    public void faceStartsOneDpDown() {
        assertArrayEquals(new int[] {0, 1, 100, 38}, DeckButtonDrawable.faceRect(0, 0, 100, 40, 2, 1, false));
    }

    @Test
    public void barIsInsetAndAboveBottom() {
        assertArrayEquals(new int[] {6, 35, 94, 37}, DeckButtonDrawable.barRect(0, 0, 100, 40, 6, 2, 3));
        assertEquals(0xFF404040, DeckButtonDrawable.mix(0xFF000000, 0xFFFFFFFF, 0.25f));
    }
}
