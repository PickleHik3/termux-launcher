package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@link SliderRowFit}: one line where the name, the value and its line fit; two otherwise. */
public class SliderRowFitTest {

    // At 1x density: a 411dp screen's row, less 16dp gutters and the 40dp glyph column.
    private static final int COLUMN = 411 - 32 - 40;
    private static final int MIN_TRACK = SliderRowFit.MIN_TRACK_DP;
    private static final int GAP = SliderRowFit.NAME_GAP_DP;

    @Test
    public void theTrackTakesHalfTheColumnAndNeverLessThanItsLeast() {
        assertEquals(170, SliderRowFit.trackPx(COLUMN, MIN_TRACK));
        assertEquals(MIN_TRACK, SliderRowFit.trackPx(200, MIN_TRACK));
        assertEquals("never wider than the column", 100, SliderRowFit.trackPx(100, MIN_TRACK));
        assertEquals(COLUMN - 170 - GAP, SliderRowFit.nameRoomPx(COLUMN, MIN_TRACK, GAP));
    }

    @Test
    public void aShortNameAndAValueThatFitStandOnOneLine() {
        assertTrue(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, 40, 12, 20, 40));
    }

    @Test
    public void aNameWiderThanItsRoomKeepsTwoLines() {
        int room = SliderRowFit.nameRoomPx(COLUMN, MIN_TRACK, GAP);
        assertTrue(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, room, 40, 12, 20, 40));
        assertFalse(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, room + 1, 40, 12, 20, 40));
    }

    @Test
    public void aValueThatWouldBeClippedKeepsTwoLines() {
        int track = SliderRowFit.trackPx(COLUMN, MIN_TRACK);
        assertTrue(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, track - 24, 12, 20, 40));
        assertFalse(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, track - 23, 12, 20, 40));
    }

    @Test
    public void aValueLineTallerThanTheTrackKeepsTwoLines() {
        assertFalse(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, 40, 12, 41, 40));
    }

    @Test
    public void noWidthIsNoLine() {
        assertFalse(SliderRowFit.oneLine(0, MIN_TRACK, GAP, 0, 0, 0, 0, 40));
    }

    @Test
    public void aNoteHasNoDigitANumberHasOne() {
        assertTrue(SliderRowFit.isNote("Off while Reduce idle activity is on."));
        assertFalse(SliderRowFit.isNote("20%"));
        assertFalse(SliderRowFit.isNote("2.5 dp"));
        assertFalse(SliderRowFit.isNote(""));
    }

    @Test
    public void aNoteWiderThanTheTrackKeepsTwoLinesWhereTheNumberFitsOne() {
        int note = 230;
        int number = SliderRowFit.fitValuePx(false, 40, note);
        int noteFit = SliderRowFit.fitValuePx(true, 40, note);
        assertTrue(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, number, 12, 20, 40));
        assertFalse(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70, noteFit, 12, 20, 40));
    }

    @Test
    public void aShortNoteStaysOnOneLine() {
        assertTrue(SliderRowFit.oneLine(COLUMN, MIN_TRACK, GAP, 70,
            SliderRowFit.fitValuePx(true, 40, 60), 12, 20, 40));
    }

    @Test
    public void aNoteInTheTwoLineRowEllipsizesWithinTheTracksShareOfTheColumn() {
        int share = Math.round(COLUMN * SliderRowFit.TRACK_SHARE);
        assertEquals(share, SliderRowFit.twoLineValuePx(true, 40, 500, COLUMN));
        assertEquals(120, SliderRowFit.twoLineValuePx(true, 40, 120, COLUMN));
        assertEquals("a number keeps the widest number's width", 40,
            SliderRowFit.twoLineValuePx(false, 40, 500, COLUMN));
    }
}
