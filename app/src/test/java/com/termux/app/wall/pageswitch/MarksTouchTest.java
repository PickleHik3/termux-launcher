package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksTouchTest {

    private static final float DENSITY = 2f;
    private static final float SLOP = 8f;

    /** Home 1 | Terminal 1, 2 | Assistant (entries 0..3) on a long line. */
    private static MarksRowLayout rest(boolean overview) {
        return new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, -1), null, overview, 16f,
                0f, 1000f);
    }

    private static MarksTouch touch(MarksRowLayout rest) {
        return new MarksTouch(rest, DENSITY, SLOP);
    }

    @Test public void downBuildsTheLayoutAndFocusesTheAnchorAtOnce() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        assertNull(t.layout());
        t.down(rest.centreAt(2));
        assertEquals(PageBarSlide.State.PRESSED, t.state());
        assertEquals(2, t.layout().anchorSlot());
        assertEquals(2, t.focusSlot());
        assertFalse(t.focusChanged());
    }

    @Test public void aTapChoosesTheTouchedEntry() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(2));
        assertEquals(PageBarSlide.Result.NONE, t.move(rest.centreAt(2) + 4f));
        assertEquals(PageBarSlide.State.PRESSED, t.state());
        assertEquals(PageBarSlide.Result.TAP, t.up(rest.centreAt(2) + 4f));
        assertEquals(2, t.entryIndex());
        assertEquals(PageBarSlide.State.IDLE, t.state());
    }

    @Test public void movingPastTheSlopSlidesAndTheFocusFollowsInTheTouchLayout() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(1));
        MarksTouchLayout layout = t.layout();
        int anchor = t.focusSlot();
        float down = rest.centreAt(1);
        t.move(down + layout.pitchPx());
        assertEquals(PageBarSlide.State.SLIDING, t.state());
        assertEquals(anchor + 1, t.focusSlot());
        assertTrue("the tick", t.focusChanged());
        t.move(down + layout.pitchPx() + 1f);
        assertFalse("same slot, no tick", t.focusChanged());
        assertSame("held to the lift", layout, t.layout());
        assertEquals(layout.centreAt(anchor) + layout.pitchPx() + 1f, t.pos(), 1e-3f);
    }

    @Test public void releasingOnAnotherSlotCommitsItsEntry() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(0));
        float raw = t.layout().centreAt(2) - t.offsetPx();
        t.move(raw);
        assertEquals(PageBarSlide.Result.COMMIT, t.up(raw));
        assertEquals(2, t.entryIndex());
    }

    @Test public void slidingBackToTheStartingSlotChoosesNothing() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(1));
        MarksTouchLayout layout = t.layout();
        t.move(layout.centreAt(3) - t.offsetPx());
        t.move(layout.centreAt(1) + 3f - t.offsetPx());
        assertEquals(PageBarSlide.Result.CANCEL, t.up(layout.centreAt(1) + 3f - t.offsetPx()));
        assertEquals(-1, t.entryIndex());
    }

    @Test public void theFocusIsHeldAtTheEndsWhenTheFingerLeavesTheLine() {
        MarksTouch t = touch(rest(false));
        t.down(500f);
        t.move(-300f);
        assertEquals(0, t.focusSlot());
        t.move(9000f);
        assertEquals(3, t.focusSlot());
    }

    @Test public void cancelEndsTheTouchWithNothingChosen() {
        MarksTouch t = touch(rest(false));
        assertEquals(PageBarSlide.Result.NONE, t.cancel());
        t.down(100f);
        assertEquals(PageBarSlide.Result.CANCEL, t.cancel());
        assertEquals(PageBarSlide.State.IDLE, t.state());
        assertEquals(PageBarSlide.Result.NONE, t.up(100f));
    }

    @Test public void tappingOverviewChoosesItsConstant() {
        MarksRowLayout rest = rest(true);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(0));
        assertEquals(0, t.focusSlot());
        assertEquals(PageBarSlide.Result.TAP, t.up(rest.centreAt(0)));
        assertEquals(MarksTouch.OVERVIEW_ENTRY, t.entryIndex());
    }

    @Test public void slidingOntoOverviewCommitsItAndOffItCommitsAnEntry() {
        MarksRowLayout rest = rest(true);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(1));
        float raw = t.layout().centreAt(0) - t.offsetPx();
        t.move(raw);
        assertEquals(0, t.focusSlot());
        assertEquals(PageBarSlide.Result.COMMIT, t.up(raw));
        assertEquals(MarksRowLayout.OVERVIEW_ENTRY, t.entryIndex());

        t.down(rest.centreAt(0));
        raw = t.layout().centreAt(2) - t.offsetPx();
        t.move(raw);
        assertEquals(PageBarSlide.Result.COMMIT, t.up(raw));
        assertEquals(1, t.entryIndex());
    }

    @Test public void aFoldedSlotCommitsTheMemberUnderTheFinger() {
        // Home 1..3 | Assistant: open at rest, folded at touch-down (the minimum pitch does not fit).
        MarksRowLayout rest = new MarksRowLayout(MarksRowLayoutTest.entries("HHHA", 0, -1), null,
                false, 16f, 0f, 150f);
        MarksTouch t = new MarksTouch(rest, DENSITY, SLOP);
        t.down(rest.centreAt(1));
        MarksTouchLayout layout = t.layout();
        float s = layout.cellStart(0);
        assertEquals("anchored on Home 2", 1, t.focusMember());
        float raw = s + 60f - t.offsetPx();
        t.move(raw);
        assertEquals(0, t.focusSlot());
        assertEquals(2, t.focusMember());
        assertEquals(PageBarSlide.Result.COMMIT, t.up(raw));
        assertEquals(2, t.entryIndex());
    }

    @Test public void aClampedDownKeepsTheAnchorFocusedUntilTheFingerTravelsAPitch() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        float down = rest.centreAt(2);
        t.down(down);
        MarksTouchLayout layout = t.layout();
        assertTrue("the layout was shifted onto the line", t.offsetPx() > 0f);
        assertEquals(layout.centreAt(2) - down, t.offsetPx(), 1e-3f);
        assertEquals(layout.centreAt(2), t.pos(), 1e-3f);
        assertEquals(2, t.focusSlot());
        t.move(down + SLOP + 1f);
        assertEquals(PageBarSlide.State.SLIDING, t.state());
        assertEquals("still the anchor", 2, t.focusSlot());
        assertFalse(t.focusChanged());
        t.move(down + layout.pitchPx());
        assertEquals(3, t.focusSlot());
        assertTrue(t.focusChanged());
        assertEquals(layout.centreAt(2) + layout.pitchPx(), t.pos(), 1e-3f);
    }

    @Test public void anUnclampedDownHasNoOffsetAndFollowsTheFingerAsBefore() {
        MarksTouch t = touch(rest(false));
        // Past the packed row the touch lands on the last mark, which the layout centres on 500.
        t.down(500f);
        assertEquals(0f, t.offsetPx(), 1e-3f);
        assertEquals(500f, t.pos(), 1e-3f);
        assertEquals(3, t.focusSlot());
        t.move(500f - t.layout().pitchPx());
        assertEquals(2, t.focusSlot());
        assertEquals(500f - t.layout().pitchPx(), t.pos(), 1e-3f);
    }

    @Test public void slidingBackToTheStartingSlotCancelsWhenClamped() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        float down = rest.centreAt(2);
        t.down(down);
        t.move(down - t.layout().pitchPx());
        assertEquals(1, t.focusSlot());
        assertEquals(PageBarSlide.Result.CANCEL, t.up(down + 2f));
        assertEquals(-1, t.entryIndex());
    }
}
