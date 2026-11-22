package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Where a one-line slider prints its value: at the track's end, unless the handle would stand on
 * it; then inside the fill, before the handle (the Opacity 81% the handle cut through on pong).
 */
public class LegendSliderInsetValueTest {

    private static final float WIDTH = 360f;
    private static final float TEXT = 60f;
    private static final float PAD = 16f;
    /** The handle's 4px bar and 6px gaps either side: 16px from fill end to inactive start. */
    private static final float HANDLE = 16f;

    private static float endFor(float fillEnd) {
        return LegendSlider.insetValueEnd(WIDTH, TEXT, PAD, fillEnd, fillEnd + HANDLE);
    }

    @Test public void atTheStartTheValueSitsAtTheTracksEnd() {
        assertEquals(WIDTH - PAD, endFor(0f), 0f);
    }

    @Test public void inTheMiddleTheValueSitsAtTheTracksEnd() {
        assertEquals(WIDTH - PAD, endFor(WIDTH / 2f), 0f);
        // The last handle position whose gap still clears the text by the pad.
        float last = WIDTH - PAD - TEXT - PAD - HANDLE;
        assertEquals(WIDTH - PAD, endFor(last), 0f);
    }

    @Test public void aHandleReachingTheValueMovesItInsideTheFill() {
        float first = WIDTH - PAD - TEXT - PAD - HANDLE + 1f;
        assertEquals(first - PAD, endFor(first), 0f);
        float ending = WIDTH - HANDLE;
        float end = endFor(ending);
        assertEquals(ending - PAD, end, 0f);
        // Wholly inside the fill, clear of the handle.
        assertEquals(true, end - TEXT >= 0f && end <= ending);
    }

    @Test public void aFillTooShortForTheTextKeepsItAtTheEnd() {
        float narrow = 100f;
        assertEquals(narrow - PAD,
            LegendSlider.insetValueEnd(narrow, TEXT, PAD, 40f, 40f + HANDLE), 0f);
    }
}
