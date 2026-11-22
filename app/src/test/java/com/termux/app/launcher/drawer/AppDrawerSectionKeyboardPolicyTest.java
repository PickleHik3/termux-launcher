package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerSectionKeyboardPolicyTest {

    @Test public void settlingOnCategoriesWithTheKeyboardUpHidesIt() {
        assertTrue(AppDrawerSectionKeyboardPolicy.hidesKeyboard(AppDrawerSection.CATEGORIES, true));
    }

    @Test public void settlingOnCategoriesWithTheKeyboardDownDoesNothing() {
        assertFalse(AppDrawerSectionKeyboardPolicy.hidesKeyboard(AppDrawerSection.CATEGORIES, false));
    }

    @Test public void comingBackToAppsNeverHidesOrRaises() {
        assertFalse(AppDrawerSectionKeyboardPolicy.hidesKeyboard(AppDrawerSection.APPS, true));
        assertFalse(AppDrawerSectionKeyboardPolicy.hidesKeyboard(AppDrawerSection.APPS, false));
    }
}
