package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** How the dock's row parts around a carried icon, and what a release lands as. */
public class DockReorderPolicyTest {

    private static final boolean[] NONE = new boolean[8];
    private static final long PART = DockReorderPolicy.PART_HOVER_MS;
    private static final boolean[] ALL = {true, true, true, true, true, true, true, true};

    private static DockReorderPolicy over(int count, int perPage, int source, boolean[] mergeable) {
        DockReorderPolicy p = new DockReorderPolicy();
        p.begin(count, perPage, source, mergeable);
        return p;
    }

    @Test
    public void aLiftedSourceAtRestMovesNothing() {
        DockReorderPolicy p = over(5, 5, 2, NONE);
        for (int i = 0; i < 5; i++) {
            assertEquals(i, p.shownSlot(i));
            assertFalse(p.pushed(i));
        }
        assertEquals(2, p.gap());
        assertEquals(-1, p.nextDueMs());
    }

    @Test
    public void thePartingCommitsAtTwoHundredMsNotBefore() {
        assertEquals(200L, PART);
        DockReorderPolicy p = over(5, 5, 2, NONE);
        p.hover(4, 0.1f, 1000);
        assertEquals(1000 + PART, p.nextDueMs());
        assertFalse(p.tick(1000 + PART - 1));
        assertEquals(2, p.gap());
        assertTrue(p.tick(1000 + PART));
        // Insertion before item 4 (position 3 in L): items 3 and 4 shift left of the gap's side.
        assertEquals(3, p.gap());
        assertEquals(-1, p.nextDueMs());
    }

    @Test
    public void theGapHoldsWhileTheFingerMovesUntilTheNextCommit() {
        DockReorderPolicy p = over(5, 5, 2, NONE);
        p.hover(4, 0.1f, 0);
        p.tick(PART);
        assertEquals(3, p.gap());
        p.hover(0, 0.1f, 700);
        assertEquals("still where it was committed", 3, p.gap());
        assertFalse(p.tick(700 + PART - 1));
        assertEquals(3, p.gap());
        assertTrue(p.tick(700 + PART));
        assertEquals(0, p.gap());
    }

    @Test
    public void aMergeArmsAtThreeHundredMsAndDisarmsWhenTheFingerMovesOff() {
        DockReorderPolicy p = over(4, 5, -1, ALL);
        p.hover(1, 0.5f, 0);
        assertEquals(300, p.nextDueMs());
        assertFalse(p.tick(299));
        assertEquals(-1, p.armed());
        assertTrue(p.tick(300));
        assertEquals(1, p.armed());
        assertTrue("moving off disarms at once", p.hover(2, 0.5f, 400));
        assertEquals(-1, p.armed());
    }

    @Test
    public void aReleaseBeforeThePartingLandsUnderTheFinger() {
        DockReorderPolicy p = over(5, 5, 0, NONE);
        p.hover(3, 0.9f, 0);
        assertEquals(DockReorderPolicy.Kind.INSERT, p.release());
        // Slot 3 is item 3 (position 2 in L); the right side is after it.
        assertEquals(3, p.index());
        assertEquals("nothing had moved", 0, p.gap());
    }

    @Test
    public void aReleaseOnAnArmedIconMerges() {
        DockReorderPolicy p = over(4, 5, -1, ALL);
        p.hover(2, 0.5f, 0);
        p.tick(300);
        assertEquals(DockReorderPolicy.Kind.MERGE, p.release());
        assertEquals(2, p.index());
    }

    @Test
    public void aReleaseOnAnUnarmedIconGoesBesideIt() {
        DockReorderPolicy p = over(4, 5, -1, ALL);
        p.hover(2, 0.5f, 0);
        assertEquals(DockReorderPolicy.Kind.INSERT, p.release());
        assertEquals(3, p.index());
    }

    @Test
    public void aSourceMovedRightShiftsTheOthersLeftOfTheGap() {
        // Items 0..4, source 1; L = [0, 2, 3, 4]. Insert after item 3 (position 3 in L).
        DockReorderPolicy p = over(5, 5, 1, NONE);
        p.hover(3, 0.9f, 0);
        p.tick(PART);
        assertEquals(3, p.gap());
        assertEquals(0, p.shownSlot(0));
        assertEquals(1, p.shownSlot(1));
        assertEquals(1, p.shownSlot(2));
        assertEquals(2, p.shownSlot(3));
        assertEquals(4, p.shownSlot(4));
    }

    @Test
    public void aSourceMovedLeftOpensTheGapBeforeIt() {
        // Items 0..4, source 3; L = [0, 1, 2, 4]. Insert before item 1 (position 1 in L).
        DockReorderPolicy p = over(5, 5, 3, NONE);
        p.hover(1, 0.1f, 0);
        p.tick(PART);
        assertEquals(1, p.gap());
        assertEquals(0, p.shownSlot(0));
        assertEquals(2, p.shownSlot(1));
        assertEquals(3, p.shownSlot(2));
        assertEquals(3, p.shownSlot(3));
        assertEquals(4, p.shownSlot(4));
    }

    @Test
    public void fromOutsideOnAFullPageTheInsertionClampsAndPushesTheLastIcon() {
        DockReorderPolicy p = over(5, 5, -1, NONE);
        p.hover(4, 0.9f, 0);
        p.tick(PART);
        assertEquals(4, p.gap());
        assertEquals(3, p.shownSlot(3));
        assertFalse(p.pushed(3));
        p.hover(1, 0.1f, 700);
        p.tick(700 + PART);
        assertEquals(1, p.gap());
        assertTrue("the last icon moves off the page", p.pushed(4));
        assertFalse(p.pushed(3));
        assertEquals(0, p.shownSlot(0));
        assertEquals(2, p.shownSlot(1));
    }

    @Test
    public void fromOutsideOnAPageWithRoomTheEndIsAnInsertion() {
        DockReorderPolicy p = over(3, 5, -1, NONE);
        p.hover(4, 0.5f, 0);
        assertEquals(DockReorderPolicy.Kind.INSERT, p.release());
        assertEquals(3, p.index());
        p.tick(PART);
        assertEquals(3, p.gap());
        assertFalse(p.pushed(2));
    }

    @Test
    public void leavingTheRowRestoresRest() {
        DockReorderPolicy p = over(5, 5, 1, ALL);
        p.hover(3, 0.5f, 0);
        p.tick(300);
        p.hover(4, 0.1f, 400);
        p.tick(400 + PART);
        p.leave();
        assertEquals(1, p.gap());
        assertEquals(-1, p.armed());
        assertEquals(-1, p.nextDueMs());
        assertEquals(DockReorderPolicy.Kind.NONE, p.release());
        for (int i = 0; i < 5; i++) assertEquals(i, p.shownSlot(i));
    }

    @Test
    public void droppingTheSourceOnItsOwnSlotChangesNothing() {
        DockReorderPolicy p = over(5, 5, 2, ALL);
        p.hover(2, 0.5f, 0);
        assertEquals(DockReorderPolicy.Kind.NONE, p.release());
    }

    @Test
    public void anIconThatCannotTakeTheCarriedOneGivesAnInsertionNotAMerge() {
        DockReorderPolicy p = over(4, 5, -1, NONE);
        p.hover(1, 0.5f, 0);
        assertEquals("only a parting is pending", PART, p.nextDueMs());
        p.tick(PART);
        assertEquals(-1, p.armed());
        assertEquals(DockReorderPolicy.Kind.INSERT, p.release());
        assertEquals(2, p.index());
    }

    @Test
    public void anIconsMiddleWaitsForAFolderWhileItsSidesPartQuickly() {
        assertTrue(PART < DockReorderPolicy.FOLDER_REST_MS);
        DockReorderPolicy p = over(4, 5, -1, ALL);
        p.hover(1, 0.5f, 0);
        assertEquals("the middle only arms a folder", DockReorderPolicy.FOLDER_REST_MS,
            p.nextDueMs());
        assertFalse(p.tick(PART));
        assertEquals(-1, p.gap());
        p.hover(1, 0.1f, 400);
        assertEquals("the side parts after the short hover", 400 + PART, p.nextDueMs());
        assertTrue(p.tick(400 + PART));
        assertEquals(1, p.gap());
        assertEquals(-1, p.armed());
    }
}
