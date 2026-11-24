package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksSlideTest {

    private static final float DENSITY = 2f;
    private static final float SLOP = 8f;

    private static MarksLine line(String kinds, int windows, int currentPageId, float length) {
        PageBarModel.Input in = PageBarModelTest.wall(kinds, windows);
        in.currentPageId = currentPageId;
        in.currentWindow = -1;
        return new MarksLine(PageBarModel.build(in).entries(), null, DENSITY, length);
    }

    private static MarksSlide slide(MarksLine line) {
        return new MarksSlide(line, SLOP);
    }

    @Test public void aTapWithoutSlidingChoosesTheSlotUnderIt() {
        MarksLine line = line("HTA", 1, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(2));
        assertEquals(PageBarSlide.State.PRESSED, s.state());
        assertEquals(PageBarSlide.Result.TAP, s.up(line.centreAt(2) + 2f));
        assertEquals(2, s.entryIndex());
    }

    @Test public void movingPastTheSlopStartsASlideThatFollowsTheFinger() {
        MarksLine line = line("HTA", 1, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(0));
        assertEquals(PageBarSlide.Result.NONE, s.move(line.centreAt(0) + 4f));
        assertEquals(PageBarSlide.State.PRESSED, s.state());
        s.move(line.centreAt(1));
        assertEquals(PageBarSlide.State.SLIDING, s.state());
        assertEquals(1, s.focusSlot());
        assertTrue("the tick", s.focusChanged());
        s.move(line.centreAt(1) + 1f);
        assertFalse("same slot, no tick", s.focusChanged());
        s.move(line.centreAt(2));
        assertEquals(2, s.focusSlot());
        assertTrue(s.focusChanged());
    }

    @Test public void releasingOnAnotherSlotCommitsIt() {
        MarksLine line = line("HTA", 2, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(0));
        s.move(line.centreAt(2));
        assertEquals(PageBarSlide.Result.COMMIT, s.up(line.centreAt(2)));
        assertEquals(2, s.entryIndex());
        assertEquals(PageBarSlide.State.IDLE, s.state());
    }

    @Test public void slidingBackToTheStartCancels() {
        MarksLine line = line("HTA", 2, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(1));
        s.move(line.centreAt(3));
        s.move(line.centreAt(1) + 3f);
        assertEquals(PageBarSlide.Result.CANCEL, s.up(line.centreAt(1) + 3f));
        assertEquals(-1, s.entryIndex());
    }

    @Test public void thePlatformCancelEndsItWithNothingChosen() {
        MarksLine line = line("HTA", 1, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(0));
        s.move(line.centreAt(2));
        assertEquals(PageBarSlide.Result.CANCEL, s.cancel());
        assertEquals(PageBarSlide.Result.NONE, s.up(line.centreAt(2)));
    }

    @Test public void theFocusClampsAtBothEndsOfTheLine() {
        MarksLine line = line("HTA", 1, 1, 800f);
        MarksSlide s = slide(line);
        s.down(line.centreAt(1));
        s.move(-500f);
        assertEquals(0, s.focusSlot());
        s.move(5000f);
        assertEquals(line.slotCount() - 1, s.focusSlot());
    }

    @Test public void focusAtIsTheFractionalSlotAsThePageBarReadsIt() {
        MarksLine line = line("HTA", 1, 1, 800f);
        assertEquals(0f, line.focusAt(line.centreAt(0)), 1e-4f);
        assertEquals(1.5f, line.focusAt(line.centreAt(1) + line.pitchPx() / 2f), 1e-4f);
        assertEquals(0f, line.focusAt(-10f), 1e-4f);
        assertEquals(line.slotCount() - 1f, line.focusAt(5000f), 1e-4f);
    }

    @Test public void aFoldedSlotSplitsEquallyAmongItsMembers() {
        MarksLine line = line("HHHHHHHHHH", 0, 1, 120f * DENSITY);
        int folded = -1;
        for (int i = 0; i < line.slotCount(); i++) if (line.slots().get(i).folded()) folded = i;
        assertTrue(folded >= 0);
        PageBarFold.Slot slot = line.slots().get(folded);
        float start = folded * line.pitchPx();
        assertEquals(0, line.memberAt(start + 0.01f, folded));
        assertEquals(slot.count - 1, line.memberAt(start + line.pitchPx() - 0.01f, folded));
        MarksSlide s = slide(line);
        s.down(start + 0.01f);
        s.move(start + line.pitchPx() - 0.01f);
        assertEquals(slot.count - 1, s.focusMember());
        assertTrue("another member of the same slot is another page", s.focusChanged());
    }
}
