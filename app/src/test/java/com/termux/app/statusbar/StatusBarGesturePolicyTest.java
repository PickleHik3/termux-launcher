package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The status bar's one-way arbitration over one immutable DOWN. Two gestures share the bar: a
 * vertical drag that toggles the pane's own form, and a sideways drag that moves the pane wall.
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
        StatusBarGesturePolicy horizontal = policy(false, false, false, false,
            TopStatusBarState.COMPACT);
        assertEquals(StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE, horizontal.move(30, 12));
        assertEquals("a claim never changes once made",
            StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE, horizontal.move(12, 90));

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
            100, state, false, interactive, false, false, eligible, false, 8));
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
        // Or armed for neither, which is what the top slot's own area reports.
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            verticalPolicy(true, false, TopStatusBarState.COMPACT).move(12, 30));
    }

    @Test public void aVerticalOnlyStreamNeverBecomesAHorizontalSwipe() {
        StatusBarGesturePolicy horizontal = verticalPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, horizontal.move(30, 12));
    }

    /** A stream with both gestures armed, as the bar's own chrome arms them. */
    private static StatusBarGesturePolicy wallPolicy(boolean wallEligible, boolean interactive) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, TopStatusBarState.EXPANDED, false, interactive, false, false, true,
            wallEligible, 8));
    }

    @Test public void aSidewaysDragGoesToTheWallWhereverItHasSomewhereToGo() {
        assertEquals(StatusBarGesturePolicy.Claim.WALL_HORIZONTAL,
            wallPolicy(true, false).move(60, 12));
        // Over the clock or a tile too: a horizontal drag on one of those is not a tap.
        StatusBarGesturePolicy overChild = wallPolicy(true, true);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, overChild.claim());
        assertEquals(StatusBarGesturePolicy.Claim.WALL_HORIZONTAL, overChild.move(-60, 12));
        assertEquals(-70f, overChild.horizontalDelta(), 0.01f);
    }

    @Test public void withNoWallTheSidewaysDragKeepsItsOlderMeaning() {
        assertEquals(StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE,
            wallPolicy(false, false).move(60, 12));
        // ...but never over an interactive child, which owns its own taps.
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            wallPolicy(false, true).move(60, 12));
    }

    @Test public void theWallNeverStealsAVerticalDrag() {
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE,
            wallPolicy(true, false).move(12, -60));
    }
}
