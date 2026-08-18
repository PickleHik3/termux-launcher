package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerFourWayGestureArbiterTest {
    @Test public void frozenEligibilityAndClaimsAreOneWayForEveryViewType() {
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            AppDrawerDragPolicy.FrozenDown down = new AppDrawerDragPolicy.FrozenDown(
                type, true, true, true, "id");
            AppDrawerDragPolicy policy = new AppDrawerDragPolicy(down);
            boolean drag = policy.claim(AppDrawerDragPolicy.Claim.DRAG);
            assertEquals(type != AppDrawerViewType.CATEGORIES, drag);
            if (drag) {
                assertFalse(policy.claim(AppDrawerDragPolicy.Claim.CLOSE));
                assertFalse(policy.claim(AppDrawerDragPolicy.Claim.PAGE_OR_TILE));
                assertFalse(policy.claim(AppDrawerDragPolicy.Claim.CHILD_SCROLL));
            } else {
                assertTrue(policy.claim(AppDrawerDragPolicy.Claim.PAGE_OR_TILE));
                assertFalse(policy.claim(AppDrawerDragPolicy.Claim.CLOSE));
            }
            assertSame(down, policy.frozenDown());
        }
    }

    @Test public void searchAndInitiallyInactiveStreamsNeverBecomeEligible() {
        AppDrawerDragPolicy search = new AppDrawerDragPolicy(new AppDrawerDragPolicy.FrozenDown(
            AppDrawerViewType.VERTICAL, true, false, true, "id"));
        AppDrawerDragPolicy inactive = new AppDrawerDragPolicy(new AppDrawerDragPolicy.FrozenDown(
            AppDrawerViewType.HORIZONTAL, false, true, true, "id"));
        assertFalse(search.claim(AppDrawerDragPolicy.Claim.DRAG));
        assertFalse(inactive.claim(AppDrawerDragPolicy.Claim.DRAG));
    }
}
