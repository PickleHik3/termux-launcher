package com.termux.app.controlbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Round 5: "100%" was cut to "100" with the keyboard up and down the rail. */
public class ReadingPillTest {

    @Test
    public void aRowPillKeepsItsDesignLengthWhenTheTextFits() {
        assertEquals(60f, ReadingPill.rowLengthPx(60f, 16f, 3f, 20f, 8f), 0f);
    }

    @Test
    public void aRowPillGrowsToHoldTheWholeReading() {
        // 8 + 16 + 3 + 40 + 8 = 75: "100%" at a large font.
        assertEquals(75f, ReadingPill.rowLengthPx(60f, 16f, 3f, 40f, 8f), 0f);
    }

    @Test
    public void anIconAloneNeedsNoGap() {
        assertEquals(32f, ReadingPill.rowLengthPx(0f, 16f, 3f, 0f, 8f), 0f);
    }

    @Test
    public void railTextShrinksToTheRailsWidth() {
        assertEquals(1f, ReadingPill.fitScale(30f, 36f, 0.6f), 0f);
        float scale = ReadingPill.fitScale(45f, 36f, 0.6f);
        assertEquals(0.8f, scale, 1e-6f);
        assertTrue(45f * scale <= 36f);
        assertEquals("never unreadably small", 0.6f, ReadingPill.fitScale(100f, 36f, 0.6f), 0f);
    }

    /** Review P3-6: past the least scale the rail shows the number alone rather than clip it. */
    @Test
    public void aRailTooNarrowEvenAtTheLeastScaleFallsBackToTheNumber() {
        assertTrue(ReadingPill.wholeFits(46f, 36f, 0.75f));
        assertTrue(!ReadingPill.wholeFits(80f, 36f, 0.6f));
    }
}
