package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which swipe pages the wall and which one folds the bar, per edge. The two gestures are always
 * perpendicular: the wall is dragged along the bar, the bar's own form changes across it.
 */
public class StatusBarGestureAxisTest {

    /** A stream with both gestures armed, as the bar's own chrome arms them. */
    private static StatusBarGesturePolicy policy(Edge edge, TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, false, false, false, false, true, true, 8, edge));
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

    @Test public void aRowPagesSidewaysAndFoldsVertically() {
        assertEquals(StatusBarGesturePolicy.Claim.WALL_PAGING,
            policy(Edge.TOP, TopStatusBarState.EXPANDED).move(90, 22));
        assertEquals(StatusBarGesturePolicy.Claim.WALL_PAGING,
            policy(Edge.BOTTOM, TopStatusBarState.EXPANDED).move(90, 22));
        // Travel along the bar is what the wall is dragged by, sign included.
        StatusBarGesturePolicy paged = policy(Edge.BOTTOM, TopStatusBarState.EXPANDED);
        paged.move(-70, 22);
        assertEquals(-80f, paged.pagingDelta(), 0.01f);
    }

    @Test public void aColumnPagesUpAndDown() {
        StatusBarGesturePolicy left = policy(Edge.LEFT, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.WALL_PAGING, left.move(22, 90));
        // Down the screen is towards the places that follow, exactly as to the right is on a row.
        assertEquals(80f, left.pagingDelta(), 0.01f);
        assertEquals(StatusBarGesturePolicy.Claim.WALL_PAGING,
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

    @Test public void theCurlThatStartsASwipeAlongTheBarNeverFoldsIt() {
        // The same forgiveness the top bar has, on a column: a first slop across the bar is not
        // yet a fold while the wall is in reach.
        StatusBarGesturePolicy curl = policy(Edge.LEFT, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, curl.move(-1, 20));
        assertEquals(StatusBarGesturePolicy.Claim.WALL_PAGING, curl.move(-1, 90));
    }
}
