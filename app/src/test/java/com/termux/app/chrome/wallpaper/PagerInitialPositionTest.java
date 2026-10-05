package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The Overview's pager is laid out at the Home card, so the Lock card is never first centred. */
public class PagerInitialPositionTest {

    @Test
    public void homeOpensAtTheHomeCardNotPositionZero() {
        assertEquals(WallpaperPickerPage.POS_HOME,
            WallpaperPickerPage.initialPosition(WallpaperSlots.Slot.HOME));
    }

    @Test
    public void aRestoredLockOpensAtTheLockCard() {
        assertEquals(WallpaperPickerPage.POS_LOCK,
            WallpaperPickerPage.initialPosition(WallpaperSlots.Slot.LOCK));
    }
}
