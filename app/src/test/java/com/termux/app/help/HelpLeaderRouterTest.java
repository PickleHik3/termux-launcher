package com.termux.app.help;

import static com.termux.app.help.HelpLeaderRouter.Box;
import static com.termux.app.help.HelpLeaderRouter.Segment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The pixel vocabulary help lays itself out in: boxes that do or do not overlap, and lines that
 * have to clear them. {@link HelpExplorePlacementTest} covers what is built on top of it.
 */
public class HelpLeaderRouterTest {

    private Box box(float l, float t, float r, float b) { return new Box(l, t, r, b); }
    private Segment line(float x1, float y1, float x2, float y2) { return new Segment(x1, y1, x2, y2); }

    @Test public void aBoxKnowsItsOwnSizeAndCentre() {
        Box b = box(10, 20, 110, 220);
        assertEquals(100, b.width(), 0.01f);
        assertEquals(200, b.height(), 0.01f);
        assertEquals(60, b.cx(), 0.01f);
        assertEquals(120, b.cy(), 0.01f);
    }

    /** Touching is not overlapping: a card may sit against a control's edge, never on it. */
    @Test public void touchingBoxesDoNotOverlap() {
        Box control = box(0, 0, 100, 100);
        assertTrue(control.overlaps(box(99, 99, 200, 200)));
        assertFalse(control.overlaps(box(100, 0, 200, 100)));
        assertFalse(control.overlaps(box(0, 100, 100, 200)));
        assertFalse(control.overlaps(box(200, 200, 300, 300)));
    }

    /** Boundary contact counts for lines, so two leaders cannot share one lane. */
    @Test public void linesThatTouchAreLinesThatClash() {
        Segment down = line(50, 0, 50, 100);
        assertTrue(down.intersects(line(0, 50, 100, 50)));
        assertTrue("a shared endpoint is a clash", down.intersects(line(50, 100, 150, 100)));
        assertFalse(down.intersects(line(80, 0, 80, 100)));
        assertEquals(30, down.separation(line(80, 0, 80, 100)), 0.01f);
        assertTrue(down.tooClose(line(80, 0, 80, 100), 40));
        assertFalse(down.tooClose(line(80, 0, 80, 100), 20));
    }

    /** A leader may end on a box's edge; it may never run through the middle of one. */
    @Test public void aLineEntersABoxOnlyWhenItRunsInsideIt() {
        Box card = box(0, 200, 100, 300);
        assertFalse("ends on the top edge", line(50, 100, 50, 200).enters(card));
        assertTrue("runs into it", line(50, 100, 50, 250).enters(card));
        assertFalse("beside it", line(150, 100, 150, 400).enters(card));
        assertTrue("across it", line(0, 250, 200, 250).enters(card));
        assertFalse("along its edge", line(0, 200, 200, 200).enters(card));
    }
}
