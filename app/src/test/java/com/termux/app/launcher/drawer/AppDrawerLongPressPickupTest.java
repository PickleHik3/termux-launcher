package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerLongPressPickupTest {
    @Test public void contextCanYieldToDragOnceButVerticalOrOtherClaimCannot() {
        AppDrawerDragPolicy policy = eligible();
        assertTrue(policy.claim(AppDrawerDragPolicy.Claim.CONTEXT));
        assertTrue(policy.claim(AppDrawerDragPolicy.Claim.DRAG));
        assertFalse(policy.claim(AppDrawerDragPolicy.Claim.DRAG));
        assertFalse(policy.claim(AppDrawerDragPolicy.Claim.CHILD_SCROLL));
        AppDrawerDragPolicy vertical = eligible();
        assertTrue(vertical.claim(AppDrawerDragPolicy.Claim.CHILD_SCROLL));
        assertFalse(vertical.claim(AppDrawerDragPolicy.Claim.DRAG));
    }
    private static AppDrawerDragPolicy eligible() {
        return new AppDrawerDragPolicy(new AppDrawerDragPolicy.FrozenDown(
            AppDrawerViewType.VERTICAL, true, true, true, "app"));
    }
}
