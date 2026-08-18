package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatusBarGesturePolicyTest {
    private static StatusBarGesturePolicy policy(boolean bar, boolean interactive,
                                                 boolean nested, boolean overlay,
                                                 TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, TopStatusBarState.EXPANDED, bar, interactive, nested, overlay, 8, 7));
    }

    @Test public void windowBarBackgroundIsEligibleButFrozenChildOrSurfaceVetoesAreImmediate() {
        StatusBarGesturePolicy windowBar = policy(true, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, windowBar.claim());
        assertEquals(StatusBarGesturePolicy.Claim.LONG_PRESS, windowBar.timeout(7));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, true, false, false, TopStatusBarState.EXPANDED).claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, false, true, false, TopStatusBarState.EXPANDED).claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, false, false, true, TopStatusBarState.EXPANDED).claim());
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(false, false, false, false, TopStatusBarState.FULL).claim());
    }

    @Test public void horizontalLongVerticalMultiAndNestedAreOneWay() {
        StatusBarGesturePolicy horizontal = policy(false, false, false, false,
            TopStatusBarState.COMPACT);
        assertEquals(StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE, horizontal.move(30, 12));
        assertEquals(StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE, horizontal.timeout(7));

        StatusBarGesturePolicy hold = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.LONG_PRESS, hold.timeout(7));
        assertEquals(StatusBarGesturePolicy.Claim.LONG_PRESS, hold.move(100, 10));

        StatusBarGesturePolicy vertical = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, vertical.move(12, 30));
        StatusBarGesturePolicy multi = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, multi.secondPointer());
        StatusBarGesturePolicy nested = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, nested.nestedScrollStarted());
    }

    @Test public void neutralDiagonalNeverDoubleClaimsAndTokenSurvivesResetAttempt() {
        StatusBarGesturePolicy neutral = policy(false, false, false, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, neutral.move(20, 20));
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, neutral.timeout(99));
        assertEquals(StatusBarGesturePolicy.Claim.LONG_PRESS, neutral.timeout(7));
        assertEquals(StatusBarGesturePolicy.Claim.LONG_PRESS, neutral.cancel());
    }

    private static StatusBarGesturePolicy pullPolicy(boolean interactive, boolean pullEligible,
                                                     TopStatusBarState state) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 10, 10, 10,
            100, state, TopStatusBarState.EXPANDED, false, interactive, false, false,
            pullEligible, 8, 7));
    }

    @Test public void downwardDragClaimsPullDownEvenOverInteractiveChildren() {
        StatusBarGesturePolicy overChip = pullPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.PENDING, overChip.claim());
        assertEquals(StatusBarGesturePolicy.Claim.PULL_DOWN, overChip.move(12, 30));

        // The unified gesture's other direction: expanded bar + upward drag = collapse claim,
        // sharing the pull-down's eligibility (chips included).
        StatusBarGesturePolicy upward = pullPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE, upward.move(12, -30));

        StatusBarGesturePolicy upwardCompact = pullPolicy(true, true, TopStatusBarState.COMPACT);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, upwardCompact.move(12, -30));

        StatusBarGesturePolicy upwardIneligible = pullPolicy(true, false,
            TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, upwardIneligible.move(12, -30));

        StatusBarGesturePolicy ineligible = pullPolicy(false, false, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, ineligible.move(12, 30));
    }

    private static StatusBarGesturePolicy pullUpPolicy(boolean eligible) {
        return new StatusBarGesturePolicy(new StatusBarGesturePolicy.Down(0, 10, 100, 10, 100,
            100, TopStatusBarState.FULL, TopStatusBarState.EXPANDED, false, false, false, false,
            false, eligible, 8, 7));
    }

    @Test public void upwardDragWithFullOpenClaimsPullUpOnlyWhenEligible() {
        assertEquals(StatusBarGesturePolicy.Claim.PULL_UP,
            pullUpPolicy(true).move(12, 70));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            pullUpPolicy(true).move(12, 130));
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            pullUpPolicy(false).move(12, 70));
    }

    @Test public void pullDownOnlyStreamsNeverLongPressOrHorizontalSwipe() {
        StatusBarGesturePolicy hold = pullPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals("chip hold stays the chip's", StatusBarGesturePolicy.Claim.PENDING,
            hold.timeout(7));

        StatusBarGesturePolicy horizontal = pullPolicy(true, true, TopStatusBarState.EXPANDED);
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED, horizontal.move(30, 12));
    }
}
