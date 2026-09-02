package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Once per count, and never below the threshold. */
public class AppDrawerCategoryNudgePolicyTest {

    @Test
    public void saysNothingBelowTheThreshold() {
        AppDrawerCategoryNudgePolicy policy = new AppDrawerCategoryNudgePolicy();
        assertFalse(policy.onDrawerOpened(0));
        assertFalse(policy.onDrawerOpened(5));
    }

    @Test
    public void speaksOncePerCountAndAgainWhenItMoves() {
        AppDrawerCategoryNudgePolicy policy = new AppDrawerCategoryNudgePolicy();
        assertTrue(policy.onDrawerOpened(6));
        assertFalse("the same six on the next open are old news", policy.onDrawerOpened(6));
        assertTrue("another install is news", policy.onDrawerOpened(7));
        assertFalse(policy.onDrawerOpened(7));
    }

    @Test
    public void aRunThatClearsThemArmsItAgain() {
        AppDrawerCategoryNudgePolicy policy = new AppDrawerCategoryNudgePolicy();
        assertTrue(policy.onDrawerOpened(8));
        assertFalse(policy.onDrawerOpened(0));
        // Eight new installs after a run that sorted the last eight: a new eight, not the old one.
        assertTrue(policy.onDrawerOpened(8));
    }
}
