package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MarksRowLayoutTest {

    /** The canvas's figures: a 16dp mark gives a 12 cell for a dot, 22 for the rest, 7 between kinds. */
    private static final float MARK = 16f;
    private static final float DELTA = 1e-3f;

    /** Home 1 is the current page; the windows are numbered 1.. in the Terminal run. */
    static List<PageBarModel.Entry> entries(String kinds, int windows, int returnWindow) {
        PageBarModel.Input in = PageBarModelTest.wall(kinds, windows);
        in.currentPageId = 1;
        in.currentWindow = returnWindow;
        return PageBarModel.build(in).entries();
    }

    private static MarksRowLayout row(String kinds, int windows, boolean overview,
                                      float length) {
        return new MarksRowLayout(entries(kinds, windows, -1), null, overview, MARK, length);
    }

    @Test public void mixedListPacksDotsCloseAndSetsKindsApart() {
        // Home 1 | Terminal 1, Terminal 2 | Assistant
        MarksRowLayout r = row("HTA", 2, false, 400f);
        assertEquals(4, r.slotCount());
        assertEquals(0f, r.cellStart(0), DELTA);
        assertEquals(12f, r.cellEnd(0), DELTA);
        assertEquals(19f, r.cellStart(1), DELTA);
        assertEquals(41f, r.cellEnd(1), DELTA);
        assertEquals("same kind touch", r.cellEnd(1), r.cellStart(2), DELTA);
        assertEquals(63f, r.cellEnd(2), DELTA);
        assertEquals(70f, r.cellStart(3), DELTA);
        assertEquals(92f, r.cellEnd(3), DELTA);
        assertEquals(92f, r.contentLengthPx(), DELTA);
        assertEquals(6f, r.centreAt(0), DELTA);
        assertFalse(r.overflow());
    }

    @Test public void dotsOfTheSameKindTouch() {
        MarksRowLayout r = row("HH", 0, false, 400f);
        assertEquals(2, r.slotCount());
        assertEquals(12f, r.cellEnd(0), DELTA);
        assertEquals(12f, r.cellStart(1), DELTA);
        assertEquals(24f, r.contentLengthPx(), DELTA);
    }

    @Test public void aShortListIsNotStretchedToTheLine() {
        MarksRowLayout r = row("HTA", 2, false, 2000f);
        assertEquals(92f, r.contentLengthPx(), DELTA);
        assertEquals(-1, r.slotAt(500f));
    }

    @Test public void theCurrentCellIsAsWideAsAnyOtherOfItsKind() {
        MarksRowLayout r = row("HH", 0, false, 400f);
        assertEquals(r.cellEnd(1) - r.cellStart(1), r.cellEnd(0) - r.cellStart(0), DELTA);
        MarksRowLayout t = row("HTA", 2, false, 400f);
        assertEquals("a window's cell is the same whether or not it is current",
            t.cellEnd(2) - t.cellStart(2), t.cellEnd(1) - t.cellStart(1), DELTA);
    }

    @Test public void overviewStandsFirstAsAKindOfItsOwn() {
        MarksRowLayout r = row("HTA", 2, true, 400f);
        assertEquals(5, r.slotCount());
        assertTrue(r.isOverview(0));
        assertFalse(r.isOverview(1));
        assertNull(r.slot(0));
        assertEquals(22f, r.cellEnd(0), DELTA);
        assertEquals(29f, r.cellStart(1), DELTA);
        assertEquals(MarksRowLayout.OVERVIEW_ENTRY, r.tapEntryIndex(0));
        assertEquals(0, r.tapEntryIndex(1));
        assertFalse(r.attention(0));
    }

    @Test public void runsFoldOnlyWhenTheRowRunsOutOfRoom() {
        assertEquals(4, row("HTA", 2, false, 92f).slotCount());
        MarksRowLayout r = row("HTA", 2, false, 91f);
        assertEquals("the windows' run folds", 3, r.slotCount());
        assertEquals(2, r.slot(1).count);
        assertEquals(70f, r.contentLengthPx(), DELTA);
        assertFalse(r.overflow());
    }

    @Test public void theLongestOtherRunFoldsOnceWindowsAreFolded() {
        // Home 1..3 | Terminal 1, 2 | Assistant: 3*12 + 2*22 + 22 + 2*7 = 116.
        MarksRowLayout open = row("HHHTA", 2, false, 116f);
        assertEquals(6, open.slotCount());
        MarksRowLayout r = row("HHHTA", 2, false, 115f);
        assertEquals(5, r.slotCount());
        assertEquals(2, r.slot(3).count);
        assertFalse(r.slot(0).folded());
        // Folded windows: 3*12 + 22 + 22 + 14 = 94; below that the dots fold.
        MarksRowLayout dots = row("HHHTA", 2, false, 93f);
        assertEquals(3, dots.slotCount());
        assertEquals(3, dots.slot(0).count);
        assertEquals(22f, dots.cellEnd(0), DELTA);
    }

    @Test public void whenNothingFitsTheSmallestIsKeptAndFlagged() {
        MarksRowLayout r = row("HTA", 2, false, 40f);
        assertEquals(3, r.slotCount());
        assertTrue(r.overflow());
        assertEquals(70f, r.contentLengthPx(), DELTA);
    }

    @Test public void aPositionInAGapBelongsToTheNearerCell() {
        MarksRowLayout r = row("HTA", 2, false, 400f);
        // Home 1 ends at 12, Terminal 1 starts at 19: the middle is 15.5.
        assertEquals(0, r.slotAt(14f));
        assertEquals(1, r.slotAt(17f));
        assertEquals(0, r.slotAt(0f));
        assertEquals(3, r.slotAt(91f));
        assertEquals(-1, r.slotAt(-1f));
        assertEquals(-1, r.slotAt(92f));
        assertEquals(0, r.clampedSlotAt(-50f));
        assertEquals(3, r.clampedSlotAt(500f));
    }

    @Test public void aFoldedSlotTapsItsCurrentMemberElseTheReturnWindowElseItsFirst() {
        // Home 1 | Terminal 1, 2 | Assistant, folded; Terminal 2 is the window to return to.
        MarksRowLayout back = new MarksRowLayout(entries("HTA", 2, 1), null, false, MARK, 80f);
        assertEquals(3, back.slotCount());
        assertEquals(2, back.tapEntryIndex(1));
        MarksRowLayout first = new MarksRowLayout(entries("HTA", 2, -1), null, false, MARK, 80f);
        assertEquals(1, first.tapEntryIndex(1));
        assertEquals(0, first.tapEntryIndex(0));
    }

    @Test public void attentionIsReadPerSlotAndCopied() {
        boolean[] attention = {false, false, true, false};
        MarksRowLayout r = new MarksRowLayout(entries("HTA", 2, -1), attention, false, MARK, 80f);
        attention[2] = false;
        assertFalse(r.attention(0));
        assertTrue("the folded slot holds the flagged member", r.attention(1));
        assertFalse(r.attention(2));
    }
}
