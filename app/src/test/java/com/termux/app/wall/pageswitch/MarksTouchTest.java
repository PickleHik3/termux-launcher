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
                1000f);
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

    @Test public void movingPastTheSlopSlidesAndTheFocusFollowsTheFingerInTheTouchLayout() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        float down = rest.centreAt(1);
        t.down(down);
        MarksTouchLayout layout = t.layout();
        assertEquals("the finger's own position", down, t.pos(), 1e-3f);
        t.move(down + SLOP + 1f);
        assertEquals(PageBarSlide.State.SLIDING, t.state());
        assertEquals("still the touched mark", 1, t.focusSlot());
        assertFalse(t.focusChanged());
        float half = layout.pitchPx() / 2f;
        t.move(down + half + 1f);
        assertEquals("half a cell on, the neighbour under the finger", 2, t.focusSlot());
        assertTrue("the tick", t.focusChanged());
        t.move(down + half + 2f);
        assertFalse("same slot, no tick", t.focusChanged());
        assertSame("held to the lift", layout, t.layout());
        assertEquals(down + half + 2f, t.pos(), 1e-3f);
    }

    @Test public void releasingOnAnotherSlotCommitsItsEntry() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(0));
        float at = t.layout().centreAt(2);
        t.move(at);
        assertEquals(PageBarSlide.Result.COMMIT, t.up(at));
        assertEquals(2, t.entryIndex());
    }

    @Test public void slidingBackToTheStartingSlotChoosesNothing() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        t.down(rest.centreAt(1));
        MarksTouchLayout layout = t.layout();
        t.move(layout.centreAt(3));
        t.move(layout.centreAt(1) + 3f);
        assertEquals(PageBarSlide.Result.CANCEL, t.up(layout.centreAt(1) + 3f));
        assertEquals(-1, t.entryIndex());
    }

    @Test public void theFocusIsHeldAtTheEndsWhenTheFingerLeavesTheLine() {
        MarksTouch t = touch(rest(false));
        t.down(50f);
        t.move(-300f);
        assertEquals(0, t.focusSlot());
        t.move(9000f);
        assertEquals(3, t.focusSlot());
    }

    @Test public void cancelEndsTheTouchWithNothingChosen() {
        MarksTouch t = touch(rest(false));
        assertEquals(PageBarSlide.Result.NONE, t.cancel());
        t.down(10f);
        assertEquals(PageBarSlide.Result.CANCEL, t.cancel());
        assertEquals(PageBarSlide.State.IDLE, t.state());
        assertEquals(PageBarSlide.Result.NONE, t.up(10f));
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
        float at = t.layout().centreAt(0);
        t.move(at);
        assertEquals(0, t.focusSlot());
        assertEquals(PageBarSlide.Result.COMMIT, t.up(at));
        assertEquals(MarksRowLayout.OVERVIEW_ENTRY, t.entryIndex());

        t.down(rest.centreAt(0));
        at = t.layout().centreAt(2);
        t.move(at);
        assertEquals(PageBarSlide.Result.COMMIT, t.up(at));
        assertEquals(1, t.entryIndex());
    }

    @Test public void aStackBeforeTheTouchedMarkCommitsTheMemberUnderTheFinger() {
        // Home 1..3 | Assistant folded at rest on a short line; touched on the Assistant.
        MarksRowLayout rest = new MarksRowLayout(MarksRowLayoutTest.entries("HHHA", 0, -1), null,
                false, 16f, 40f);
        MarksTouch t = new MarksTouch(rest, DENSITY, SLOP);
        t.down(rest.centreAt(1));
        MarksTouchLayout layout = t.layout();
        float s = layout.cellStart(0);
        float w = layout.cellEnd(0) - s;
        float at = s + 0.9f * w;
        t.move(at);
        assertEquals(0, t.focusSlot());
        assertEquals(2, t.focusMember());
        assertEquals(PageBarSlide.Result.COMMIT, t.up(at));
        assertEquals(2, t.entryIndex());
    }

    @Test public void slidingBackToTheStartingSlotCancelsAfterAMoveAway() {
        MarksRowLayout rest = rest(false);
        MarksTouch t = touch(rest);
        float down = rest.centreAt(2);
        t.down(down);
        t.move(t.layout().centreAt(1));
        assertEquals(1, t.focusSlot());
        assertEquals(PageBarSlide.Result.CANCEL, t.up(down + 2f));
        assertEquals(-1, t.entryIndex());
    }
}
