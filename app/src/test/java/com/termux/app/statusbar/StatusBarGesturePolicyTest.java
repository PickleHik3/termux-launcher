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

    @Test public void everyFrozenChildOrSurfaceVetoIsImmediate() {
        assertEquals(StatusBarGesturePolicy.Claim.CHILD_OWNED,
            policy(true, false, false, false, TopStatusBarState.EXPANDED).claim());
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
}
