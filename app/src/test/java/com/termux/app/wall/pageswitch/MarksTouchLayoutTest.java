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

    /** Home 1 | Terminal 1, 2 | Assistant, the finger on the rest slot {@code touched}. */
    private static MarksTouchLayout touch(float length, float finger, int touched) {
        return new MarksTouchLayout(rest("HTA", 2, false, length), DENSITY, finger, touched);
    }

    @Test public void theCellsShareTheMaximumPitchOnALongLine() {
        MarksTouchLayout t = touch(1000f, 500f, 1);
        assertEquals(MAX, t.pitchPx(), DELTA);
        assertEquals(4, t.slotCount());
        assertEquals(4 * MAX + 2 * GAP, t.contentLengthPx(), DELTA);
        assertEquals(MAX + GAP, t.cellStart(1) - t.contentStartPx(), DELTA);
        assertEquals("same kind touch", t.cellEnd(1), t.cellStart(2), DELTA);
    }

    @Test public void thePitchIsTheRoomSharedBetweenTheSlotsWhenItIsBetweenTheBounds() {
        MarksTouchLayout t = touch(200f, 100f, 1);
        assertEquals((200f - 2 * GAP) / 4f, t.pitchPx(), DELTA);
        assertEquals(200f, t.contentLengthPx() + 0f, DELTA);
        assertEquals(0f, t.contentStartPx(), DELTA);
    }

    @Test public void thePitchHoldsAtTheMinimumWhenNothingFits() {
        MarksTouchLayout t = touch(100f, 50f, 1);
        assertEquals(MIN, t.pitchPx(), DELTA);
        assertEquals("the windows stay folded", 3, t.slotCount());
        assertEquals("longer than the line starts at 0", 0f, t.contentStartPx(), DELTA);
        assertEquals(3 * MIN + 2 * GAP, t.contentLengthPx(), DELTA);
    }

    @Test public void theTouchedMarkStaysUnderTheFinger() {
        MarksTouchLayout t = touch(1000f, 500f, 1);
        assertEquals(1, t.anchorSlot());
        assertEquals(500f, t.centreAt(1), DELTA);
        MarksTouchLayout last = touch(1000f, 600f, 3);
        assertEquals(3, last.anchorSlot());
        assertEquals(600f, last.centreAt(3), DELTA);
    }

    @Test public void theLineIsShiftedInsideTheLineAtBothEnds() {
        MarksTouchLayout left = touch(300f, 5f, 0);
        assertEquals(0f, left.contentStartPx(), DELTA);
        assertEquals(0f, left.cellStart(0), DELTA);
        MarksTouchLayout right = touch(300f, 295f, 3);
        assertEquals(300f, right.cellEnd(3), DELTA);
        assertEquals(300f - right.contentLengthPx(), right.contentStartPx(), DELTA);
    }

    @Test public void everyFoldedStackOpens() {
        // Folded at rest on a short line, a long line holds every entry in its own slot.
        assertEquals(2, new MarksRowLayout(MarksRowLayoutTest.entries("HHHA", 0, -1), null, false,
                MARK, 40f).slotCount());
        MarksTouchLayout open = new MarksTouchLayout(new MarksRowLayout(
                MarksRowLayoutTest.entries("HHHA", 0, -1), null, false, MARK, 400f),
                DENSITY, 100f, 0);
        assertEquals(4, open.slotCount());
        assertFalse(open.slot(0).folded());
    }

    @Test public void slotsRefoldWhenEvenTheMinimumPitchDoesNotHoldThem() {
        // Home 1..3 | Assistant: 4 * 40 + 7 = 167 does not fit 150; folding the dots does.
        MarksRowLayout r = new MarksRowLayout(MarksRowLayoutTest.entries("HHHA", 0, -1), null,
                false, MARK, 150f);
        MarksTouchLayout t = new MarksTouchLayout(r, DENSITY, 75f, 0);
        assertEquals(2, t.slotCount());
        assertEquals(3, t.slot(0).count);
        assertEquals(MAX, t.pitchPx(), DELTA);
        // The folded cell's members split it equally.
        float s = t.cellStart(0);
        assertEquals(0, t.memberAt(s + 10f, 0));
        assertEquals(1, t.memberAt(s + 32f, 0));
        assertEquals(2, t.memberAt(s + 60f, 0));
        assertEquals(0, t.memberAt(s - 100f, 0));
        assertEquals(2, t.memberAt(s + 500f, 0));
        assertEquals(1, t.entryIndex(0, 1));
        assertEquals(3, t.entryIndex(1, 0));
    }

    @Test public void aFoldedSlotIsAnchoredOnTheEntryATapOnItWouldGoTo() {
        MarksRowLayout r = new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, 1), null, false,
                MARK, 80f);
        MarksTouchLayout t = new MarksTouchLayout(r, DENSITY, 40f, 1);
        assertEquals(1, t.anchorSlot());
        assertEquals(1, t.anchorMember());
    }

    @Test public void overviewIsASlotOfItsOwnAndCanBeTheAnchor() {
        MarksRowLayout r = rest("HTA", 2, true, 1000f);
        MarksTouchLayout t = new MarksTouchLayout(r, DENSITY, 300f, 0);
        assertEquals(5, t.slotCount());
        assertTrue(t.isOverview(0));
        assertNull(t.slot(0));
        assertEquals(0, t.anchorSlot());
        assertEquals(300f, t.centreAt(0), DELTA);
        assertEquals(MarksRowLayout.OVERVIEW_ENTRY, t.entryIndex(0, 0));
        assertEquals(0, t.entryIndex(1, 0));
        assertEquals("a gap sets Overview apart", MAX + GAP, t.cellStart(1) - t.cellStart(0), DELTA);
    }

    @Test public void slotAtInAGapGoesToTheNearerCellAndOutsideIsMinusOne() {
        MarksTouchLayout t = touch(1000f, 500f, 1);
        float mid = (t.cellEnd(0) + t.cellStart(1)) * 0.5f;
        assertEquals(0, t.slotAt(mid - 1f));
        assertEquals(1, t.slotAt(mid + 1f));
        assertEquals(-1, t.slotAt(t.contentStartPx() - 1f));
        assertEquals(-1, t.slotAt(t.contentStartPx() + t.contentLengthPx()));
        assertEquals(0, t.clampedSlotAt(-100f));
        assertEquals(3, t.clampedSlotAt(5000f));
    }

    @Test public void theZoomFallsFromOneAndAHalfToNoneOverTwoPitches() {
        MarksTouchLayout t = touch(1000f, 500f, 1);
        float c = t.centreAt(1);
        assertEquals(1.5f, t.scaleAt(1, c), DELTA);
        assertEquals(1.34f, t.scaleAt(1, c + 0.5f * MAX), DELTA);
        assertEquals(1.18f, t.scaleAt(1, c + MAX), DELTA);
        assertEquals(1.09f, t.scaleAt(1, c - 1.5f * MAX), DELTA);
        assertEquals(1f, t.scaleAt(1, c + 2f * MAX), DELTA);
        assertEquals(1f, t.scaleAt(1, c - 5f * MAX), DELTA);
    }

    @Test public void overTheWholeRowTheCellsShareItsLengthAndStayInsideIt() {
        // The marks take 120px of a row that runs from 30px before them to 370px after.
        MarksRowLayout rest = rest("HTA", 2, false, 120f);
        MarksTouchLayout t = new MarksTouchLayout(rest, DENSITY, 10f, 0, -30f, 370f);
        assertEquals("the row's 400px, not the marks' 120", Math.min(MAX, (400f - 2 * GAP) / 4f),
            t.pitchPx(), DELTA);
        assertTrue(t.contentStartPx() >= -30f);
        assertTrue(t.contentStartPx() + t.contentLengthPx() <= 370f + DELTA);
        // Anchored where the finger is: the touched mark stays under it unless the row's start holds it.
        MarksTouchLayout mid = new MarksTouchLayout(rest, DENSITY, 200f, 1, -30f, 370f);
        assertEquals(200f, mid.centreAt(1), DELTA);
        MarksTouchLayout left = new MarksTouchLayout(rest, DENSITY, -20f, 0, -30f, 370f);
        assertEquals("held at the row's start", -30f, left.contentStartPx(), DELTA);
    }
}
