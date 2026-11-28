package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;

/**
 * Show the control bar in the drawer ({@code terl.bar_in_drawer}): off by default, the drawer's
 * sheet reaches the screen's bottom edge and the bar stands under it while the drawer is on
 * screen; on, the sheet ends at the bar's top and the bar stays.
 */
public class DrawerBarTest {

    @Test public void offByDefaultInTerlSettingsWithTheWordsPeopleUse() {
        assertFalse(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_TERL_BAR_IN_DRAWER);
        assertEquals("terl.bar_in_drawer", SettingDeclarations.TERL_BAR_IN_DRAWER.id);
        assertTrue(SettingDeclarations.ALL.contains(SettingDeclarations.TERL_BAR_IN_DRAWER));
    }

    @Test public void offTheSheetReachesTheBottomEdge() {
        assertEquals("only the band under the pill", 24, DrawerBar.sheetsBandPx(false, 24, 300));
        assertEquals("on: it ends at the bar's top", 300, DrawerBar.sheetsBandPx(true, 24, 300));
        assertEquals(24, DrawerBar.sheetsBandPx(true, 24, 0));
    }

    @Test public void offTheBarStandsUnderTheSheetWhileTheDrawerIsOnScreen() {
        assertFalse("closed", DrawerBar.underSheet(false, 0f));
        assertTrue("from the pull's first frame", DrawerBar.underSheet(false, 0.01f));
        assertTrue("open", DrawerBar.underSheet(false, 1f));
        assertFalse("on: never", DrawerBar.underSheet(true, 1f));
    }
}
