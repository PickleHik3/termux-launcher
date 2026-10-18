package com.termux.app.statusbar;

import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The status bar's one-way arbitration over one immutable DOWN. The bar has one gesture of its
 * own, the drag across it that toggles its form; a drag along it is a child's or nobody's.
 */
public class StatusBarGesturePolicyTest {

    /** A stream with neither gesture armed: only a child can own it. */
    private static StatusBarGesturePolicy policy(boolean bar, boolean interactive,
                                                 boolean nested, boolean overlay,
                                                 TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, bar, interactive, nested, overlay, 8));
    }

    @Test public void windowBarBackgroundIsEligibleButFrozenChildOrSurfaceVetoesAreImmediate() {
        StatusBarGesturePolicy windowBar = policy(true, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, windowBar.claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, true, false, false, TopStatusBarState.EXPANDED).claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, false, true, false, TopStatusBarState.EXPANDED).claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, false, false, true, TopStatusBarState.EXPANDED).claim());
    }

    @Test public void horizontalMultiAndNestedAreOneWay() {
        // A sideways drag means nothing here: the bar's form is the vertical drag's alone.
        StatusBarGesturePolicy horizontal = policy(false, false, false, false,
            TopStatusBarState.COMPACT);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, horizontal.move(30, 12));
        assertEquals("a claim never changes once made",
            StatusBarGesturePolicy.Claim.CHILD_OWNED, horizontal.move(12, 90));

        StatusBarGesturePolicy multi = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, multi.secondPointer());
        StatusBarGesturePolicy nested = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, nested.nestedScrollStarted());
    }

    @Test public void aNeutralDiagonalNeverDoubleClaims() {
        StatusBarGesturePolicy neutral = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, neutral.move(20, 20));
        assertEquals(StatusBarGesturePolicy.Claim.CANCELLED, neutral.cancel());
    }

    /** A stream with the vertical drag armed, as the layout arms it along the bar's length. */
    private static StatusBarGesturePolicy verticalPolicy(boolean interactive, boolean eligible,
                                                         TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, false, interactive, false, false, eligible, 8));
    }

    @Test public void theVerticalDragTogglesTheFormEvenOverInteractiveChildren() {
        // Compact bar, downward drag: expand — and a chip under the finger does not veto it.
        StatusBarGesturePolicy overChip = verticalPolicy(true, true, TopStatusBarState.COMPACT);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, overChip.claim());
        assertEquals(StatusBarGesturePolicy.Claim.EXPAND_SWIPE, overChip.move(12, 30));

        // Expanded bar, upward drag: collapse.
        StatusBarGesturePolicy upward = verticalPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, upward.move(12, -30));
    }

    @Test public void aVerticalDragWithNowhereToGoStaysTheChilds() {
        // Already expanded and dragged further down, or already compact and dragged up.
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            verticalPolicy(true, true, TopStatusBarState.EXPANDED).move(12, 30));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            verticalPolicy(true, true, TopStatusBarState.COMPACT).move(12, -30));
        // Or armed for neither, which is what a child owning the fold's own axis reports.
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            verticalPolicy(true, false, TopStatusBarState.COMPACT).move(12, 30));
    }

    @Test public void aVerticalOnlyStreamNeverBecomesAHorizontalSwipe() {
        StatusBarGesturePolicy horizontal = verticalPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, horizontal.move(30, 12));
    }

    /** A stream with the fold armed over an open bar, as the bar's own chrome arms it. */
    private static StatusBarGesturePolicy formPolicy(boolean interactive) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, TopStatusBarState.EXPANDED, false, interactive, false, false, true, 8));
    }

    /** The bar never pages the wall: a drag along it is a child's or nobody's, chip or no chip. */
    @Test public void aSidewaysDragIsNeverTheBars() {
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, formPolicy(false).move(60, 12));
        StatusBarGesturePolicy overChild = formPolicy(true);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, overChild.claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, overChild.move(-60, 12));
        assertEquals("a claim never changes once made",
            StatusBarGesturePolicy.Claim.CHILD_OWNED, overChild.move(-60, 90));
    }

    /** The fold asks for one slop of travel across the bar, beating the travel along it. */
    @Test public void theFoldAsksForOneSlop() {
        // From the down at (10, 10): nine across on a slop of eight.
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, formPolicy(false).move(14, 1));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, formPolicy(false).move(18, 0));
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, formPolicy(false).move(12, -60));
        // Inside the slop the stream is still open.
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, formPolicy(false).move(10, 3));
    }

    /** The stream the layout arms on an edge: the fold is armed wherever the edge allows one. */
    private static StatusBarGesturePolicy edgePolicy(Edge edge, TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, false, true, false, false,
            StatusBarGesturePolicy.expansionAllowed(edge), 8, edge));
    }

    /** Moves the finger {@code delta} across the bar on {@code edge} and reports the claim. */
    private static StatusBarGesturePolicy.Claim across(StatusBarGesturePolicy policy, Edge edge,
                                                       float delta) {
        boolean vertical = StatusBarGesturePolicy.isVertical(edge);
        return policy.move(vertical ? 10 + delta : 10, vertical ? 10 : 10 + delta);
    }

    @Test public void theFoldRunsAgainstTheGrowthOnEveryEdgeThatGrows() {
        // One rule, four edges: a drag away from the bar's own edge opens it, one back towards
        // that edge folds it. The bottom bar is the case this was written for — its panel grows
        // upward, so the fold is the downward drag, and nothing about it is a special case.
        for (Edge edge : Edge.values()) {
            boolean grows = StatusBarGesturePolicy.expansionAllowed(edge);
            float open = 40f * StatusBarGesturePolicy.expandSign(edge);
            assertEquals(edge + " opens away from its edge",
                grows ? StatusBarGesturePolicy.Claim.EXPAND_SWIPE
                    : StatusBarGesturePolicy.Claim.CHILD_OWNED,
                across(edgePolicy(edge, TopStatusBarState.COMPACT), edge, open));
            assertEquals(edge + " folds back towards it",
                grows ? StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE
                    : StatusBarGesturePolicy.Claim.CHILD_OWNED,
                across(edgePolicy(edge, TopStatusBarState.EXPANDED), edge, -open));
            // And the drag with nowhere to go is nobody's, on every edge.
            assertEquals(edge + " cannot open twice",
                StatusBarGesturePolicy.Claim.CHILD_OWNED,
                across(edgePolicy(edge, TopStatusBarState.EXPANDED), edge, open));
            assertEquals(edge + " cannot fold twice",
                StatusBarGesturePolicy.Claim.CHILD_OWNED,
                across(edgePolicy(edge, TopStatusBarState.COMPACT), edge, -open));
        }
    }

    @Test public void expansionIsAllowedOnlyAlongTopOrBottom() {
        assertTrue(StatusBarGesturePolicy.expansionAllowed(Edge.TOP));
        assertTrue(StatusBarGesturePolicy.expansionAllowed(Edge.BOTTOM));
        assertFalse(StatusBarGesturePolicy.expansionAllowed(Edge.LEFT));
        assertFalse(StatusBarGesturePolicy.expansionAllowed(Edge.RIGHT));
    }
}
