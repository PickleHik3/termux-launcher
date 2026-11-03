package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveNeighbourPolicyTest {

    @Test
    public void neighboursAreTheAdjacentWindowsOnly() {
        assertEquals(1, LiveNeighbourPolicy.neighbourIndex(true, 2, 5, -1));
        assertEquals(3, LiveNeighbourPolicy.neighbourIndex(true, 2, 5, 1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 2, 5, 2));
    }

    @Test
    public void rowEndsHaveNoNeighbourBeyondThem() {
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 0, 3, -1));
        assertEquals(1, LiveNeighbourPolicy.neighbourIndex(true, 0, 3, 1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 2, 3, 1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 0, 1, 1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 0, 0, 1));
    }

    @Test
    public void toggleOffHasNoNeighbours() {
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(false, 1, 3, -1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(false, 1, 3, 1));
    }

    @Test
    public void outOfRangeCurrentHasNoNeighbours() {
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, -1, 3, 1));
        assertEquals(LiveNeighbourPolicy.NONE, LiveNeighbourPolicy.neighbourIndex(true, 3, 3, -1));
    }

    @Test
    public void hiddenAtRestShownWhileTheWallMoves() {
        assertFalse(LiveNeighbourPolicy.shown(true, false, false, false, false));
        assertTrue(LiveNeighbourPolicy.shown(true, true, false, false, false));
        assertTrue(LiveNeighbourPolicy.shown(true, false, true, false, false));
        assertTrue(LiveNeighbourPolicy.shown(true, false, false, true, false));
        assertTrue(LiveNeighbourPolicy.shown(true, false, false, false, true));
    }

    @Test
    public void neverShownAwayFromTheWorkspace() {
        assertFalse(LiveNeighbourPolicy.shown(false, true, true, true, true));
    }

    @Test
    public void neighboursStandOneWidthBesideTheWorkspacePage() {
        assertEquals(-1080f, LiveNeighbourPolicy.slidePx(-1, 0f, 1080), 0f);
        assertEquals(1080f, LiveNeighbourPolicy.slidePx(1, 0f, 1080), 0f);
        // A drag 400 px to the left brings the next window 400 px onto the screen.
        assertEquals(680f, LiveNeighbourPolicy.slidePx(1, -400f, 1080), 0f);
        assertTrue(LiveNeighbourPolicy.onScreen(680f, 1080));
        assertFalse(LiveNeighbourPolicy.onScreen(-1480f, 1080));
    }

    @Test
    public void promotedWindowStandsWhereTheDragLeftIt() {
        // Released 400 px into a step right: the wall settles and the arriving window is nudged
        // in from fromPx = offset + width (PaneWallLayout.stepInside). Where it stood as the next
        // neighbour is where it now stands as the Workspace page, and the window that left stands
        // as the previous neighbour exactly where it was.
        int width = 1080;
        float offset = -400f;
        float fromPx = offset + width;
        assertEquals(LiveNeighbourPolicy.slidePx(1, offset, width), fromPx, 0f);
        assertEquals(offset, LiveNeighbourPolicy.slidePx(-1, fromPx, width), 0f);
    }

    @Test
    public void slotsAndSidesRoundTrip() {
        assertEquals(0, LiveNeighbourPolicy.slotOf(-1));
        assertEquals(1, LiveNeighbourPolicy.slotOf(1));
        assertEquals(-1, LiveNeighbourPolicy.sideOf(0));
        assertEquals(1, LiveNeighbourPolicy.sideOf(1));
    }
}
