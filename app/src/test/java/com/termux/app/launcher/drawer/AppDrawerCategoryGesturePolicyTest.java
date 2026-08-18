package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerCategoryGesturePolicy.Claim;
import com.termux.app.launcher.drawer.AppDrawerCategoryGesturePolicy.Down;
import com.termux.app.launcher.drawer.AppDrawerCategoryTouchRegions.Part;

import org.junit.Test;

public class AppDrawerCategoryGesturePolicyTest {
    private static final float ARM = 28f;

    @Test public void actionTapStaysActionUntilAClaimSuppressesIt() {
        AppDrawerCategoryGesturePolicy policy = new AppDrawerCategoryGesturePolicy();
        policy.begin(new Down(Part.EXPAND_ACTION, true, false, 0), 1000);
        assertEquals(Claim.ACTION, policy.claim());
        assertFalse(policy.suppressClick());
        assertEquals(Claim.CLOSE_DRAG, policy.claimOnPreScroll(-1));
        assertTrue(policy.suppressClick());
    }

    @Test public void overviewFirstPullArmsAndFreshSecondPullCloses() {
        AppDrawerCategoryGesturePolicy policy = new AppDrawerCategoryGesturePolicy();
        policy.begin(new Down(Part.OVERVIEW_LIST, true, true, 0), 1000);
        assertEquals(Claim.OVERPULL, policy.claimOnPreScroll(-20));
        assertTrue(policy.finishOverview(ARM, ARM, 0, true, 1000));
        policy.begin(new Down(Part.OVERVIEW_LIST, true, true, 1000), 1100);
        assertEquals(Claim.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        assertTrue(policy.suppressClick());
    }

    @Test public void midListAndUpwardClaimsAreFrozenForTheStream() {
        AppDrawerCategoryGesturePolicy mid = new AppDrawerCategoryGesturePolicy();
        mid.begin(new Down(Part.DETAIL_LIST, false, true, 0), 1000);
        assertEquals(Claim.SCROLL, mid.claimOnPreScroll(-20));
        assertEquals(Claim.SCROLL, mid.claimOnPreScroll(-200));

        AppDrawerCategoryGesturePolicy up = new AppDrawerCategoryGesturePolicy();
        up.begin(new Down(Part.DETAIL_LIST, true, true, 0), 1000);
        assertEquals(Claim.SCROLL, up.claimOnPreScroll(20));
        assertEquals(Claim.SCROLL, up.claimOnPreScroll(-100));
    }

    @Test public void expandedTopDownCollapsesButNeverBecomesClose() {
        AppDrawerCategoryGesturePolicy policy = new AppDrawerCategoryGesturePolicy();
        policy.begin(new Down(Part.DETAIL_LIST, true, true, 0), 1000);
        assertEquals(Claim.COLLAPSE_DRAG, policy.claimOnPreScroll(-20));
        assertEquals(Claim.COLLAPSE_DRAG, policy.claimOnPreScroll(-200));
        assertEquals(Claim.COLLAPSE_DRAG, policy.claimOnPreScroll(50));
        assertTrue(policy.suppressClick());
    }

    @Test public void unscrollableOverviewClosesOnceAndDuplicateStopIsIdempotent() {
        AppDrawerCategoryGesturePolicy policy = new AppDrawerCategoryGesturePolicy();
        policy.begin(new Down(Part.OVERVIEW_LIST, true, false, 0), 1000);
        assertEquals(Claim.CLOSE_DRAG, policy.claimOnPreScroll(-20));
        assertTrue(policy.finishOnce());
        assertFalse(policy.finishOnce());
    }
}
