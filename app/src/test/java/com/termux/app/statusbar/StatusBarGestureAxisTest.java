package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which swipe folds the bar, per edge. The bar's own form changes across it, whichever edge it
 * stands on; a drag along it is never the bar's.
 */
public class StatusBarGestureAxisTest {

    /** A stream with the fold armed, as the bar's own chrome arms it. */
    private static StatusBarGesturePolicy policy(Edge edge, TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, false, false, false, false, true, 8, edge));
    }

    @Test public void onlyTheSideEdgesStandTheBarInAColumn() {
        assertFalse(StatusBarGesturePolicy.isVertical(Edge.TOP));
        assertFalse(StatusBarGesturePolicy.isVertical(Edge.BOTTOM));
        assertTrue(StatusBarGesturePolicy.isVertical(Edge.LEFT));
        assertTrue(StatusBarGesturePolicy.isVertical(Edge.RIGHT));
    }

    @Test public void theBarAlwaysOpensAwayFromItsOwnEdge() {
        assertEquals(1f, StatusBarGesturePolicy.expandSign(Edge.TOP), 0f);
        assertEquals(-1f, StatusBarGesturePolicy.expandSign(Edge.BOTTOM), 0f);
        assertEquals(1f, StatusBarGesturePolicy.expandSign(Edge.LEFT), 0f);
        assertEquals(-1f, StatusBarGesturePolicy.expandSign(Edge.RIGHT), 0f);
    }

    @Test public void aDragAlongTheBarIsNobodysOnEveryEdge() {
        // Sideways along a row, up and down along a column: the wall is paged from the pane's
        // border, never from the bar, so the drag along it is left to a child or to nobody.
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(Edge.TOP, TopStatusBarState.EXPANDED).move(90, 22));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(Edge.BOTTOM, TopStatusBarState.EXPANDED).move(-70, 22));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(Edge.LEFT, TopStatusBarState.EXPANDED).move(22, 90));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(Edge.RIGHT, TopStatusBarState.EXPANDED).move(22, -70));
    }

    @Test public void aColumnFoldsSidewaysTowardsItsOwnEdge() {
        // Left bar: away from the left edge opens it, back towards the edge folds it.
        assertEquals(StatusBarGesturePolicy.Claim.EXPAND_SWIPE,
            policy(Edge.LEFT, TopStatusBarState.COMPACT).move(70, 18));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE,
            policy(Edge.LEFT, TopStatusBarState.EXPANDED).move(-50, 18));
        // Right bar: mirrored.
        assertEquals(StatusBarGesturePolicy.Claim.EXPAND_SWIPE,
            policy(Edge.RIGHT, TopStatusBarState.COMPACT).move(-50, 18));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE,
            policy(Edge.RIGHT, TopStatusBarState.EXPANDED).move(70, 18));
    }

    @Test public void aRowAtTheBottomFoldsDownwardAndOpensUpward() {
        assertEquals(StatusBarGesturePolicy.Claim.EXPAND_SWIPE,
            policy(Edge.BOTTOM, TopStatusBarState.COMPACT).move(18, -50));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE,
            policy(Edge.BOTTOM, TopStatusBarState.EXPANDED).move(18, 70));
        // The top bar keeps today's directions.
        assertEquals(StatusBarGesturePolicy.Claim.EXPAND_SWIPE,
            policy(Edge.TOP, TopStatusBarState.COMPACT).move(18, 70));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE,
            policy(Edge.TOP, TopStatusBarState.EXPANDED).move(18, -50));
    }

    @Test public void aFormDragWithNowhereToGoStaysTheChildsOnEveryEdge() {
        for (Edge edge : Edge.values()) {
            // Already open and dragged further open: nowhere to go, so the child keeps the stream.
            float open = StatusBarGesturePolicy.expandSign(edge) * 60f;
            boolean vertical = StatusBarGesturePolicy.isVertical(edge);
            float x = 10f + (vertical ? open : 0f);
            float y = 10f + (vertical ? 0f : open);
            assertEquals("already open at " + edge, StatusBarGesturePolicy.Claim.CHILD_OWNED,
                policy(edge, TopStatusBarState.EXPANDED).move(x, y));
        }
    }

    @Test public void aFirstSlopAcrossTheBarFoldsItAtOnce() {
        // One slop of travel across the bar decides: the doubled slop the wall's drag once asked
        // for, so a curl at the start of a sideways swipe did not fold the bar, went with it.
        StatusBarGesturePolicy column = policy(Edge.LEFT, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, column.move(4, 12));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, column.move(1, 12));
    }
}
