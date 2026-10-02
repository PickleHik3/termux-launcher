package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperPickerLogicTest {

    @Test
    public void storedMarking() {
        assertTrue(WallpaperPickerLogic.isStored("rain", "rain"));
        assertFalse(WallpaperPickerLogic.isStored("rain", null));
    }

    @Test
    public void thumbWidthFollowsPortraitAspect() {
        assertEquals(90, WallpaperPickerLogic.thumbWidth(200, 1080, 2400));
        assertEquals(90, WallpaperPickerLogic.thumbWidth(200, 2400, 1080));
    }

    @Test
    public void composedTimeIn12Hour() {
        assertEquals("12:00", WallpaperPickerLogic.composedTime(0, 0, false));
        assertEquals("9:05", WallpaperPickerLogic.composedTime(9, 5, false));
        assertEquals("12:45", WallpaperPickerLogic.composedTime(12, 45, false));
        assertEquals("1:30", WallpaperPickerLogic.composedTime(13, 30, false));
        assertEquals("11:59", WallpaperPickerLogic.composedTime(23, 59, false));
    }

    @Test
    public void composedTimeIn24Hour() {
        assertEquals("00:00", WallpaperPickerLogic.composedTime(0, 0, true));
        assertEquals("09:05", WallpaperPickerLogic.composedTime(9, 5, true));
        assertEquals("21:05", WallpaperPickerLogic.composedTime(21, 5, true));
        assertEquals("23:59", WallpaperPickerLogic.composedTime(23, 59, true));
    }

    @Test
    public void composedTimeIsOnlyDigitsAndAColon() {
        for (int h = 0; h < 24; h++) {
            for (boolean is24 : new boolean[] {false, true}) {
                String t = WallpaperPickerLogic.composedTime(h, 7, is24);
                assertTrue(t, t.matches("[0-9]{1,2}:[0-9]{2}"));
            }
        }
    }

    @Test
    public void nextMinute() {
        assertEquals(60_000L, WallpaperPickerLogic.millisToNextMinute(120_000L));
        assertEquals(1_000L, WallpaperPickerLogic.millisToNextMinute(119_000L));
    }

    @Test
    public void choicesCompareByWhatTheyName() {
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.animated("mesh"),
            WallpaperSlots.Choice.animated("mesh")));
        assertFalse(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.animated("mesh"),
            WallpaperSlots.Choice.animated("tide")));
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.sameAsHome(),
            WallpaperSlots.Choice.sameAsHome()));
        assertFalse(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.photo(),
            WallpaperSlots.Choice.sameAsHome()));
    }

    @Test
    public void applyOnlyWhenPendingDiffersAndIdle() {
        WallpaperSlots.Choice mesh = WallpaperSlots.Choice.animated("mesh");
        assertFalse(WallpaperPickerLogic.applyEnabled(mesh, WallpaperSlots.Choice.animated("mesh"), false));
        assertTrue(WallpaperPickerLogic.applyEnabled(mesh, WallpaperSlots.Choice.sameAsHome(), false));
        assertFalse(WallpaperPickerLogic.applyEnabled(mesh, WallpaperSlots.Choice.sameAsHome(), true));
    }

    @Test
    public void sameAsHomeAndMotionAreLockOnly() {
        assertTrue(WallpaperPickerLogic.showsSameAsHome(WallpaperSlots.Slot.LOCK));
        assertFalse(WallpaperPickerLogic.showsSameAsHome(WallpaperSlots.Slot.HOME));
        assertTrue(WallpaperPickerLogic.showsMotion(WallpaperSlots.Slot.LOCK, 34));
        assertFalse(WallpaperPickerLogic.showsMotion(WallpaperSlots.Slot.LOCK, 33));
        assertFalse(WallpaperPickerLogic.showsMotion(WallpaperSlots.Slot.HOME, 34));
    }
}
