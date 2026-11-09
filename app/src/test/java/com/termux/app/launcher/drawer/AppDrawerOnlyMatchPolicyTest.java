package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerOnlyMatchPolicyTest {

    @Test
    public void offNeverOpensByItself() {
        assertFalse(AppDrawerOnlyMatchPolicy.shouldAutoOpen(false, "cam", 1));
    }

    @Test
    public void aLoneMatchOpensWhenOn() {
        assertTrue(AppDrawerOnlyMatchPolicy.shouldAutoOpen(true, "cam", 1));
    }

    @Test
    public void severalOrNoMatchesWait() {
        assertFalse(AppDrawerOnlyMatchPolicy.shouldAutoOpen(true, "ca", 2));
        assertFalse(AppDrawerOnlyMatchPolicy.shouldAutoOpen(true, "zzz", 0));
    }

    @Test
    public void anEmptyQueryNeverOpensTheOnlyAppOnAFreshPhone() {
        assertFalse(AppDrawerOnlyMatchPolicy.shouldAutoOpen(true, "", 1));
    }
}
