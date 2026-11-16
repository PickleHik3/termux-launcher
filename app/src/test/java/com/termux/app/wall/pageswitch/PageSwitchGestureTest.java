package com.termux.app.wall.pageswitch;

import static com.termux.app.wall.pageswitch.PageSwitchGesture.Result;
import static com.termux.app.wall.pageswitch.PageSwitchGesture.State;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class PageSwitchGestureTest {

    private PageSwitchLayout layout;
    private PageSwitchGesture g;
    private static final float TOP = 100f;

    @Before public void setUp() {
        layout = new PageSwitchLayout(1f, 1000, 4, 20f);
        g = new PageSwitchGesture(layout, 10f);
        g.setPanelBounds(TOP, 300f);
    }

    private float cx(int i) {
        return layout.cellCentreX(i);
    }

    @Test public void downOpensWithCurrentHighlighted() {
        g.down(cx(1), 400f, 1);
        assertEquals(State.PRESSED, g.state());
        assertEquals(1, g.highlightIndex());
        assertTrue(g.isTouching());
        assertFalse(g.highlightChanged());
    }

    @Test public void moveWithinSlopStaysPressed() {
        g.down(cx(1), 400f, 1);
        g.move(cx(1) + 10f, 400f);
        assertEquals(State.PRESSED, g.state());
        assertEquals(1, g.highlightIndex());
    }

    @Test public void dragPastSlopFollowsFingerAndCommitsOnRelease() {
        g.down(cx(0), 400f, 0);
        g.move(cx(0) + 11f, 200f);
        assertEquals(State.DRAGGING, g.state());
        g.move(cx(2), 200f);
        assertEquals(2, g.highlightIndex());
        assertTrue(g.highlightChanged());
        assertFalse(g.highlightChanged());
        assertEquals(Result.COMMIT, g.up(cx(2), 200f));
        assertEquals(2, g.committedIndex());
        assertEquals(State.IDLE, g.state());
        assertFalse(g.isTouching());
    }

    @Test public void releaseOnCurrentCellStillReportsCommit() {
        g.down(cx(1), 400f, 1);
        g.move(cx(3), 200f);
        g.move(cx(1), 200f);
        assertEquals(Result.COMMIT, g.up(cx(1), 200f));
        assertEquals(1, g.committedIndex());
    }

    @Test public void aboveThePanelCancelsAndReturnRestores() {
        g.down(cx(0), 400f, 0);
        g.move(cx(2), 200f);
        g.move(cx(2), TOP - 1f);
        assertEquals(State.CANCELLED, g.state());
        assertEquals(-1, g.highlightIndex());
        assertTrue(g.highlightChanged());
        g.move(cx(3), TOP);
        assertEquals(State.DRAGGING, g.state());
        assertEquals(3, g.highlightIndex());
        assertEquals(Result.COMMIT, g.up(cx(3), TOP + 5f));
        assertEquals(3, g.committedIndex());
    }

    @Test public void releaseWhileCancelledCloses() {
        g.down(cx(0), 400f, 0);
        g.move(cx(2), 200f);
        g.move(cx(2), 10f);
        assertEquals(Result.CLOSE, g.up(cx(2), 10f));
        assertEquals(-1, g.committedIndex());
        assertEquals(State.IDLE, g.state());
    }

    @Test public void releaseAbovePanelWithoutMoveCloses() {
        g.down(cx(0), 400f, 0);
        g.move(cx(1), 200f);
        assertEquals(Result.CLOSE, g.up(cx(1), 10f));
    }

    @Test public void tapOpensThenTapOnCellCommits() {
        g.down(cx(2), 400f, 2);
        assertEquals(Result.STAY_OPEN, g.up(cx(2), 400f));
        assertEquals(State.OPEN, g.state());
        assertEquals(2, g.highlightIndex());
        assertFalse(g.isTouching());
        assertEquals(Result.COMMIT, g.tapInPanel(cx(0), 200f));
        assertEquals(0, g.committedIndex());
        assertEquals(State.IDLE, g.state());
    }

    @Test public void tapOutsideClosesOpenPanel() {
        g.down(cx(2), 400f, 2);
        g.up(cx(2), 400f);
        assertEquals(Result.CLOSE, g.tapOutside());
        assertEquals(State.IDLE, g.state());
        assertEquals(-1, g.highlightIndex());
    }

    @Test public void cancelClosesFromAnyState() {
        g.down(cx(2), 400f, 2);
        g.cancel();
        assertEquals(State.IDLE, g.state());
        g.down(cx(2), 400f, 2);
        g.up(cx(2), 400f);
        g.cancel();
        assertEquals(State.IDLE, g.state());
    }

    @Test public void hitTestClampsOutsideTheRow() {
        g.down(cx(1), 400f, 1);
        g.move(-500f, 200f);
        assertEquals(0, g.highlightIndex());
        g.move(5000f, 200f);
        assertEquals(3, g.highlightIndex());
    }

    @Test public void eventsInIdleDoNothing() {
        assertEquals(Result.NONE, g.up(10f, 10f));
        assertEquals(Result.NONE, g.tapInPanel(10f, 10f));
        assertEquals(Result.NONE, g.tapOutside());
        g.move(10f, 10f);
        assertEquals(State.IDLE, g.state());
    }
}
