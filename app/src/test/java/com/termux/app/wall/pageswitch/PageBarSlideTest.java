package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.pageswitch.PageBarModel.Entry;
import com.termux.app.wall.pageswitch.PageBarModel.Input;
import com.termux.app.wall.pageswitch.PageBarSlide.Result;
import com.termux.app.wall.pageswitch.PageBarSlide.State;

import org.junit.Test;

import java.util.List;

public class PageBarSlideTest {

    private static final float SLOP = 8f;
    /** Inside the touch row (top 0, bottom 48). */
    private static final float Y = 24f;

    private PageBarLayout mLayout;

    /** Slot width 40 on a 420 px pane (70 left): slot i spans 70 + 40 i. */
    private float xOf(int slot) {
        return mLayout.restCenterX(slot);
    }

    private PageBarSlide slide(Input in, int maxSlots, boolean keyEntry) {
        List<Entry> entries = PageBarModel.build(in).entries();
        PageBarFold.Result fold = PageBarFold.fold(entries, maxSlots);
        mLayout = new PageBarLayout(1f, 420f, fold.slots.size());
        return new PageBarSlide(mLayout, fold, entries, SLOP, keyEntry);
    }

    private PageBarSlide plain() {
        return slide(PageBarModelTest.wall("HTHD", 1), 8, false);
    }

    @Test public void tapChoosesTheSlotsEntry() {
        PageBarSlide s = plain();
        s.down(xOf(2), Y);
        assertEquals(State.PRESSED, s.state());
        assertEquals(Result.TAP, s.up(xOf(2), Y));
        assertEquals(2, s.entryIndex());
        assertEquals(State.IDLE, s.state());
    }

    @Test public void smallWobbleStaysATap() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        assertEquals(Result.NONE, s.move(xOf(1) + SLOP, Y));
        assertEquals(State.PRESSED, s.state());
        assertEquals(Result.TAP, s.up(xOf(1) + SLOP, Y));
    }

    @Test public void slideCommitsTheSlotUnderTheFinger() {
        PageBarSlide s = plain();
        s.down(xOf(0), Y);
        s.move(xOf(0) + 20f, Y);
        assertEquals(State.SLIDING, s.state());
        s.move(xOf(3), Y);
        assertEquals(3, s.focusSlot());
        assertEquals(Result.COMMIT, s.up(xOf(3), Y));
        assertEquals(3, s.entryIndex());
    }

    @Test public void slidingBackToTheStartCancels() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        s.move(xOf(3), Y);
        s.move(xOf(1) + 5f, Y);
        assertEquals(Result.CANCEL, s.up(xOf(1) + 5f, Y));
        assertEquals(State.IDLE, s.state());
    }

    @Test public void fingerPastTheEndClampsToTheEndSlot() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        s.move(900f, Y);
        assertEquals(3, s.focusSlot());
        assertEquals(Result.COMMIT, s.up(900f, Y));
        assertEquals(3, s.entryIndex());
    }

    @Test public void leavingAboveCancelsAtOnceAndIgnoresTheRest() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        s.move(xOf(2), Y);
        assertEquals(Result.CANCEL, s.move(xOf(2), 0f - 33f));
        assertEquals(State.IDLE, s.state());
        assertEquals(Result.NONE, s.move(xOf(3), Y));
        assertEquals(Result.NONE, s.up(xOf(3), Y));
    }

    @Test public void leavingBelowCancelsExceptOnTheKeyEntry() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        assertEquals(Result.CANCEL, s.move(xOf(1), 48f + 33f));

        PageBarSlide key = slide(PageBarModelTest.wall("HTHD", 1), 8, true);
        key.down(xOf(1), Y);
        assertEquals(Result.NONE, key.move(xOf(2), 48f + 200f));
        assertEquals(Result.CANCEL, key.move(xOf(2), 0f - 33f));
    }

    @Test public void justInsideTheBandKeepsSliding() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        assertEquals(Result.NONE, s.move(xOf(2), 0f - 31f));
        assertEquals(Result.COMMIT, s.up(xOf(2), 0f - 31f));
    }

    @Test public void cancelEndsATouchOnce() {
        PageBarSlide s = plain();
        s.down(xOf(1), Y);
        s.move(xOf(2), Y);
        assertEquals(Result.CANCEL, s.cancel());
        assertEquals(State.IDLE, s.state());
        assertEquals(Result.NONE, s.cancel());
    }

    @Test public void focusChangedFiresOnSlotChangesOnly() {
        PageBarSlide s = plain();
        s.down(xOf(0), Y);
        s.move(xOf(0) + mLayout.slotWidthPx() * 0.375f, Y);
        assertFalse(s.focusChanged());
        s.move(xOf(1), Y);
        assertTrue(s.focusChanged());
        s.move(xOf(1) + 3f, Y);
        assertFalse(s.focusChanged());
        s.move(xOf(2), Y);
        assertTrue(s.focusChanged());
    }

    // H T(3 windows) H, folded to 3 slots: slot 1 holds the windows.
    private PageBarSlide folded(int currentPage, int currentWindow) {
        Input in = PageBarModelTest.wall("HTH", 3);
        in.currentPageId = currentPage;
        in.currentWindow = currentWindow;
        return slide(in, 3, false);
    }

    @Test public void tapOnAFoldedSlotGoesToItsCurrentMember() {
        PageBarSlide s = folded(2, 2);
        s.down(xOf(1), Y);
        assertEquals(Result.TAP, s.up(xOf(1), Y));
        assertEquals(1 + 2, s.entryIndex());
    }

    @Test public void tapOnAFoldedSlotFromAnotherPageGoesToTheWindowYouReturnTo() {
        PageBarSlide s = folded(1, 1);
        s.down(xOf(1), Y);
        assertEquals(Result.TAP, s.up(xOf(1), Y));
        assertEquals(1 + 1, s.entryIndex());
    }

    @Test public void tapOnAFoldedSlotWithNothingToReturnToGoesToTheFirstMember() {
        PageBarSlide s = folded(1, -1);
        s.down(xOf(1), Y);
        assertEquals(Result.TAP, s.up(xOf(1), Y));
        assertEquals(1, s.entryIndex());
    }

    @Test public void slidingInAFoldedSlotStepsThroughItsMembers() {
        PageBarSlide s = folded(1, 0);
        float left = mLayout.pillLeftPx() + mLayout.slotWidthPx();
        float w = mLayout.slotWidthPx();
        s.down(left + w * 0.1f, Y);
        s.move(left + w * 0.1f + 9f, Y);
        assertEquals(State.SLIDING, s.state());
        // The slop already carries the finger past a narrow member: step on to the last one.
        s.move(left + w * 0.9f, Y);
        assertTrue(s.focusChanged());
        assertEquals(Result.COMMIT, s.up(left + w * 0.9f, Y));
        assertEquals(1 + 2, s.entryIndex());
    }

    @Test public void slidingBackToTheSameMemberCancels() {
        PageBarSlide s = folded(1, 0);
        float left = mLayout.pillLeftPx() + mLayout.slotWidthPx();
        float w = mLayout.slotWidthPx();
        s.down(left + w * 0.5f, Y);
        s.move(left + w * 0.9f, Y);
        assertEquals(Result.CANCEL, s.up(left + w * 0.5f + 1f, Y));
    }

    @Test public void slidingToAnotherMemberOfTheSameSlotCommitsIt() {
        PageBarSlide s = folded(1, 0);
        float left = mLayout.pillLeftPx() + mLayout.slotWidthPx();
        float w = mLayout.slotWidthPx();
        s.down(left + w * 0.1f, Y);
        s.move(left + w * 0.1f + 9f, Y);
        assertEquals(Result.COMMIT, s.up(left + w * 0.5f, Y));
        assertEquals(1 + 1, s.entryIndex());
    }
}
