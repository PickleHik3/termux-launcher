package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksTouchLayoutTest {

    private static final float MARK = 16f;
    private static final float DENSITY = 2f;
    /** The pitch bounds at this density, in px. */
    private static final float MIN = 40f;
    private static final float MAX = 64f;
    private static final float GAP = 7f;
    private static final float DELTA = 1e-3f;

    private static MarksRowLayout rest(String kinds, int windows, boolean overview, float length) {
        return new MarksRowLayout(MarksRowLayoutTest.entries(kinds, windows, -1), null, overview,
                MARK, length);
    }

    /** Home 1 | Terminal 1, 2 | Assistant at rest, the finger on the rest slot {@code touched}. */
    private static MarksTouchLayout touch(float lineEnd, int touched) {
        return new MarksTouchLayout(rest("HTA", 2, false, 1000f), DENSITY, touched, lineEnd);
    }

    @Test public void theTouchedCellStaysCentredWhereItIsAtRest() {
        MarksRowLayout rest = rest("HTA", 2, false, 1000f);
        for (int s = 0; s < rest.slotCount(); s++) {
            MarksTouchLayout t = new MarksTouchLayout(rest, DENSITY, s, 1000f);
            assertEquals(s, t.anchorSlot());
            assertEquals("slot " + s, rest.centreAt(s), t.centreAt(s), DELTA);
        }
    }

    @Test public void theMarksAfterTheTouchedOneSpreadAtTheTouchPitchWithTheGapsKept() {
        MarksTouchLayout t = touch(1000f, 1);
        assertEquals(MAX, t.pitchPx(), DELTA);
        assertEquals(4, t.slotCount());
        assertEquals("same kind touch", t.cellEnd(1), t.cellStart(2), DELTA);
        assertEquals(MAX, t.cellEnd(2) - t.cellStart(2), DELTA);
        assertEquals("the kind gap", GAP, t.cellStart(3) - t.cellEnd(2), DELTA);
        assertEquals(MAX, t.cellEnd(3) - t.cellStart(3), DELTA);
    }

    @Test public void thePitchIsTheRoomToTheRowsEndSharedWhenItIsBetweenTheBounds() {
        // From the Home dot's centre (6) to 160: 154 less the two gaps over three and a half cells.
        MarksTouchLayout t = touch(160f, 0);
        assertEquals((154f - 2 * GAP) / 3.5f, t.pitchPx(), DELTA);
        assertEquals(4, t.slotCount());
    }

    @Test public void theLongestRunAfterTheTouchedMarkRefoldsWhenTheMinimumDoesNotFit() {
        MarksTouchLayout t = touch(100f, 0);
        assertEquals(MIN, t.pitchPx(), DELTA);
        assertEquals("the windows fold again", 3, t.slotCount());
        assertEquals(2, t.slot(1).count);
    }

    @Test public void theMarksBeforeTheTouchedOneShareTheirOldRoomEqually() {
        // Home, Terminal 1, 2 in the 70px before the Assistant, less their two kind gaps.
        MarksRowLayout rest = rest("HTA", 2, false, 1000f);
        MarksTouchLayout t = new MarksTouchLayout(rest, DENSITY, 3, 1000f);
        float cell = (rest.cellStart(3) - 2 * GAP) / 3f;
        for (int s = 0; s < 3; s++) {
            assertEquals("a dot's cell is as wide as a square's", cell,
                    t.cellEnd(s) - t.cellStart(s), DELTA);
        }
        assertEquals(0f, t.cellStart(0), DELTA);
        assertEquals(rest.cellStart(3) - GAP, t.cellEnd(2), DELTA);
        assertEquals(GAP, t.cellStart(1) - t.cellEnd(0), DELTA);
        assertEquals("same kind touch", t.cellEnd(1), t.cellStart(2), DELTA);
    }

    @Test public void aStackBeforeTheTouchedMarkOpensOnlyWhileItsMembersFit() {
        MarksRowLayout open = rest("HHHA", 0, false, 400f);
        MarksTouchLayout opened = new MarksTouchLayout(open, DENSITY, 3, 400f);
        assertEquals(4, opened.slotCount());
        assertFalse(opened.slot(0).folded());
        MarksRowLayout tight = rest("HHHA", 0, false, 40f);
        assertEquals(2, tight.slotCount());
        MarksTouchLayout stayed = new MarksTouchLayout(tight, DENSITY, 1, 40f);
        assertEquals(2, stayed.slotCount());
        assertEquals(3, stayed.slot(0).count);
        assertEquals(tight.centreAt(1), stayed.centreAt(1), DELTA);
        // The members of the stack split its cell.
        float s = stayed.cellStart(0);
        float w = stayed.cellEnd(0) - s;
        assertEquals(0, stayed.memberAt(s + 0.1f * w, 0));
        assertEquals(1, stayed.memberAt(s + 0.5f * w, 0));
        assertEquals(2, stayed.memberAt(s + 0.9f * w, 0));
        assertEquals(1, stayed.entryIndex(0, 1));
        assertEquals(3, stayed.entryIndex(1, 0));
    }

    @Test public void overviewStandsBeforeAndCanBeTheTouchedMark() {
        MarksRowLayout r = rest("HTA", 2, true, 1000f);
        MarksTouchLayout t = new MarksTouchLayout(r, DENSITY, 0, 1000f);
        assertEquals(5, t.slotCount());
        assertTrue(t.isOverview(0));
        assertNull(t.slot(0));
        assertEquals(0, t.anchorSlot());
        assertEquals(r.centreAt(0), t.centreAt(0), DELTA);
        assertEquals(MarksRowLayout.OVERVIEW_ENTRY, t.entryIndex(0, 0));
        assertEquals(0, t.entryIndex(1, 0));
        assertEquals("a gap sets Overview apart", GAP, t.cellStart(1) - t.cellEnd(0), DELTA);
        MarksTouchLayout home = new MarksTouchLayout(r, DENSITY, 1, 1000f);
        assertTrue(home.isOverview(0));
        assertEquals(1, home.anchorSlot());
        assertEquals(r.centreAt(1), home.centreAt(1), DELTA);
    }

    @Test public void theNearestCentreIsTheSlotUnderTheFingerAndTheEndsAreHeld() {
        MarksTouchLayout t = touch(1000f, 1);
        float mid = (t.centreAt(1) + t.centreAt(2)) * 0.5f;
        assertEquals(1, t.clampedSlotAt(mid - 1f));
        assertEquals(2, t.clampedSlotAt(mid + 1f));
        assertEquals(0, t.clampedSlotAt(-100f));
        assertEquals(3, t.clampedSlotAt(5000f));
    }

    @Test public void theZoomFallsFromOneAndAHalfToNoneOverTwoPitches() {
        MarksTouchLayout t = touch(1000f, 1);
        float c = t.centreAt(1);
        assertEquals(1.5f, t.scaleAt(1, c), DELTA);
        assertEquals(1.34f, t.scaleAt(1, c + 0.5f * MAX), DELTA);
        assertEquals(1.18f, t.scaleAt(1, c + MAX), DELTA);
        assertEquals(1.09f, t.scaleAt(1, c - 1.5f * MAX), DELTA);
        assertEquals(1f, t.scaleAt(1, c + 2f * MAX), DELTA);
        assertEquals(1f, t.scaleAt(1, c - 5f * MAX), DELTA);
    }

    @Test public void aFoldedSlotAtRestIsTouchedOnTheEntryATapWouldGoTo() {
        MarksRowLayout r = new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, 1), null, false,
                MARK, 80f);
        assertEquals(3, r.slotCount());
        int entry = r.tapEntryIndex(1);
        MarksTouchLayout t = new MarksTouchLayout(r, DENSITY, 1, 80f);
        assertEquals("every entry before it has a cell of its own", entry, t.anchorSlot());
        assertEquals(entry, t.entryIndex(t.anchorSlot(), 0));
        assertEquals("the stack's centre", r.centreAt(1), t.centreAt(t.anchorSlot()), DELTA);
    }
}
