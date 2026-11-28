package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerlKeyboardLookTest {

    private final TerlLookStore store = new StoredTerlLookStore(new MemoryLookStore());
    private boolean on;
    private final TerlKeyboardLook look = new TerlKeyboardLook(() -> on, store);

    @Test
    public void switchedOffV1sNumbersPassThrough() {
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY, 33);
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 4);
        assertEquals(71, look.opacityPercent(71));
        assertEquals(18, look.blurDp(18));
        assertTrue(look.opacityFollowsDock(true));
        assertFalse(look.opacityFollowsDock(false));
        assertEquals(64, look.stackAlphaPercent(64));
    }

    @Test
    public void switchedOnTheKeyboardWearsItsOwnValues() {
        on = true;
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY, 33);
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.BLUR, 4);
        assertEquals(33, look.opacityPercent(71));
        assertEquals(4, look.blurDp(18));
        assertFalse(look.opacityFollowsDock(true));
        assertEquals(100, look.stackAlphaPercent(64));
    }

    @Test
    public void switchedOnWithNoOwnValueTheKeyboardWearsGlobal() {
        on = true;
        assertEquals(store.global(TerlProperty.OPACITY), look.opacityPercent(71));
        assertEquals(store.global(TerlProperty.BLUR), look.blurDp(18));
    }

    @Test
    public void theKeyboardWearsGlobalsTintOnlyWhileSwitchedOn() {
        store.setTint(TerlLookStore.TINT_TERTIARY);
        assertEquals("secondary", look.tintHue("secondary"));
        on = true;
        assertEquals(TerlLookStore.TINT_TERTIARY, look.tintHue("secondary"));
    }

    @Test
    public void theKeysCornersFollowGlobalsCornersOnlyWhileSwitchedOn() {
        assertEquals(7.5f, look.keyCornerRadiusDp(7.5f), 0f);
        on = true;
        store.setCornerRadiusDp(StoredTerlLookStore.DEFAULT_CORNER_DP);
        assertEquals("the default corners give v1's default keys", com.termux.shared.termux.settings
            .preferences.TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            look.keyCornerRadiusDp(7.5f), 0.01f);
        store.setCornerRadiusDp(0);
        assertEquals("square looks, square keys", 0f, look.keyCornerRadiusDp(7.5f), 0f);
        store.setCornerRadiusDp(13);
        assertEquals("half the corners, half the keys' radius", com.termux.shared.termux.settings
            .preferences.TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP / 2f,
            look.keyCornerRadiusDp(7.5f), 0.01f);
        store.setCornerRadiusDp(48);
        assertTrue("never past the keys' ceiling", look.keyCornerRadiusDp(7.5f) <= com.termux.shared
            .termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);
    }
}
