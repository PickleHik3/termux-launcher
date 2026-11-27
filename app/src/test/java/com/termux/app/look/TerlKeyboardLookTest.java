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
}
