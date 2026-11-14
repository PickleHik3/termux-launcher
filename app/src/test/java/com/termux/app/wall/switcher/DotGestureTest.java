package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.switcher.DotGesture.Action;
import com.termux.app.wall.switcher.DotGesture.Phase;

import org.junit.Test;

public class DotGestureTest {

    private static final float SLOP = 10f;
    private static final Object CELL = "cell";
    private static final Object OTHER = "other";

    private final DotGesture g = new DotGesture(SLOP);

    @Test public void tapOnTheDotOpensAtDownAndStaysOpenOnUp() {
        assertSame(Action.OPEN, g.down(100f, 100f, true, false, null));
        assertSame(Phase.PRESS, g.phase());
        assertSame(Action.NONE, g.move(103f, 102f, null));
        assertSame(Action.STAY_OPEN, g.up(103f, 102f, null));
        assertFalse(g.isLive());
    }

    @Test public void tapOnTheDotWithThePickerOpenCloses() {
        assertSame(Action.NONE, g.down(100f, 100f, true, true, null));
        assertSame(Action.CLOSE, g.up(100f, 100f, null));
        assertSame(Phase.IDLE, g.phase());
    }

    @Test public void slideOntoACellAndReleaseChoosesIt() {
        g.down(100f, 100f, true, false, null);
        assertSame(Action.TRACK, g.move(50f, 100f, CELL));
        assertSame(Phase.SLIDE, g.phase());
        assertSame(Action.TRACK, g.move(40f, 100f, CELL));
        assertSame(Action.CHOOSE, g.up(40f, 100f, CELL));
        assertSame(CELL, g.chosen());
        assertFalse(g.isLive());
    }

    @Test public void slideReleasedOffEveryCellStaysOpen() {
        g.down(100f, 100f, true, false, null);
        g.move(50f, 100f, CELL);
        assertSame(Action.STAY_OPEN, g.up(0f, 0f, null));
    }

    @Test public void holdInsideTheSlopPicksUpAndDragsAndSnaps() {
        g.down(100f, 100f, true, false, null);
        g.move(102f, 101f, null);
        assertSame(Action.PICK_UP, g.holdElapsed(110f, 90f));
        assertSame(Phase.MOVE, g.phase());
        // The dot's centre was 8 right and 11 above the finger at pick-up.
        assertSame(Action.DRAG, g.move(200f, 300f, null));
        assertEquals(208f, g.dragX(), 0.001f);
        assertEquals(289f, g.dragY(), 0.001f);
        assertSame(Action.SNAP, g.up(200f, 300f, null));
        assertFalse(g.isLive());
    }

    @Test public void movingPastTheSlopBeforeTheHoldIsASlideAndALateHoldDoesNothing() {
        g.down(100f, 100f, true, false, null);
        assertSame(Action.TRACK, g.move(100f, 130f, null));
        assertSame(Action.NONE, g.holdElapsed(100f, 100f));
        assertSame(Phase.SLIDE, g.phase());
    }

    @Test public void holdWithNoGestureDoesNothing() {
        assertSame(Action.NONE, g.holdElapsed(0f, 0f));
    }

    @Test public void cancelWhilePickedUpSnaps() {
        g.down(100f, 100f, true, false, null);
        g.holdElapsed(100f, 100f);
        assertSame(Action.SNAP, g.cancel());
        assertFalse(g.isLive());
    }

    @Test public void cancelClosesThePickerThatTheTouchOpened() {
        g.down(100f, 100f, true, false, null);
        assertSame(Action.CLOSE, g.cancel());
        g.down(100f, 100f, true, false, null);
        g.move(50f, 100f, CELL);
        assertSame(Action.CLOSE, g.cancel());
    }

    @Test public void cancelLeavesAPickerThatWasAlreadyOpen() {
        g.down(100f, 100f, true, true, null);
        assertSame(Action.NONE, g.cancel());
        assertFalse(g.isLive());
    }

    @Test public void cancelOutsideDoesNothing() {
        g.down(10f, 10f, false, true, null);
        assertSame(Action.NONE, g.cancel());
        assertFalse(g.isLive());
    }

    @Test public void outsideDownAndUpOnTheSameCellChooses() {
        assertSame(Action.NONE, g.down(10f, 10f, false, true, CELL));
        assertSame(Phase.OUTSIDE, g.phase());
        assertSame(Action.NONE, g.move(12f, 10f, CELL));
        assertSame(Action.CHOOSE, g.up(12f, 10f, CELL));
        assertSame(CELL, g.chosen());
    }

    @Test public void outsideDownAndUpOnNothingCloses() {
        g.down(10f, 10f, false, true, null);
        assertSame(Action.CLOSE, g.up(10f, 10f, null));
    }

    @Test public void outsideDownAndUpOnDifferentThingsDoesNothing() {
        g.down(10f, 10f, false, true, CELL);
        assertSame(Action.NONE, g.up(10f, 10f, OTHER));
        g.down(10f, 10f, false, true, CELL);
        assertSame(Action.NONE, g.up(10f, 10f, null));
        g.down(10f, 10f, false, true, null);
        assertSame(Action.NONE, g.up(10f, 10f, CELL));
        assertFalse(g.isLive());
    }

    @Test public void aTouchNotOursStaysIdle() {
        assertSame(Action.NONE, g.down(10f, 10f, false, false, null));
        assertFalse(g.isLive());
        assertSame(Action.NONE, g.move(20f, 20f, null));
        assertSame(Action.NONE, g.up(20f, 20f, null));
    }

    @Test public void aSecondDownWhileLiveIsIgnored() {
        g.down(100f, 100f, true, false, null);
        assertSame(Action.NONE, g.down(300f, 300f, true, false, null));
        assertTrue(g.isLive());
        assertSame(Phase.PRESS, g.phase());
        assertSame(Action.STAY_OPEN, g.up(100f, 100f, null));
    }
}
