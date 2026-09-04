package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopStatusBarStateTest {
    @Test public void theTwoFormsMapBothWaysToTheStoredBoolean() {
        assertEquals(TopStatusBarState.COMPACT, TopStatusBarState.fromCollapsedPreference(true));
        assertEquals(TopStatusBarState.EXPANDED, TopStatusBarState.fromCollapsedPreference(false));
        assertTrue(TopStatusBarState.COMPACT.toCollapsedPreference());
        assertFalse(TopStatusBarState.EXPANDED.toCollapsedPreference());
    }
}
