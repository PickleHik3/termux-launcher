package com.termux.app.tiling.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Where a hardware key goes while navigation mode is on (#433): no window takes it. */
public class NavigationKeysTest {

    @Test
    public void outsideTheModeNothingIsSwallowed() {
        assertFalse(NavigationKeys.swallows(false, false, false));
    }

    @Test
    public void inTheModeATypedKeyGoesNowhere() {
        assertTrue(NavigationKeys.swallows(true, false, false));
    }

    @Test
    public void aSystemKeyGoesOnSoBackLeavesTheMode() {
        assertFalse(NavigationKeys.swallows(true, true, false));
    }

    @Test
    public void aLayerOpenOverThePageKeepsItsKeys() {
        // The bar's search or the palette, opened from the mode, is typed into as ever.
        assertFalse(NavigationKeys.swallows(true, false, true));
    }
}
