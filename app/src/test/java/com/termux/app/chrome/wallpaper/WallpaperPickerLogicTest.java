package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.launcher.model.IconPackInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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

    private static final WallpaperSlots.Choice AURORA = WallpaperSlots.Choice.animated("aurora");
    private static final WallpaperSlots.Choice TIDE = WallpaperSlots.Choice.animated("tide");
    private static final WallpaperSlots.Choice SAME = WallpaperSlots.Choice.sameAsHome();
    private static final WallpaperSlots.Choice PHOTO = WallpaperSlots.Choice.photo();

    @Test
    public void applyTakesTheCentredCardsBackground() {
        WallpaperSlots.Slot home = WallpaperSlots.Slot.HOME;
        WallpaperSlots.Slot lock = WallpaperSlots.Slot.LOCK;
        assertEquals("tide", WallpaperPickerLogic.primaryChoice(home, TIDE, AURORA).animatedId);
        assertEquals("aurora", WallpaperPickerLogic.primaryChoice(lock, TIDE, AURORA).animatedId);
        assertEquals("Same as Home resolves to Home's pick", "tide",
            WallpaperPickerLogic.primaryChoice(lock, TIDE, SAME).animatedId);
    }

    @Test
    public void applyBothIsEnabledWhenEitherSlotChanges() {
        assertTrue("Home changes", WallpaperPickerLogic.applyBothEnabled(TIDE, AURORA, SAME, false));
        assertTrue("Lock starts following", WallpaperPickerLogic.applyBothEnabled(AURORA, AURORA, TIDE, false));
        assertFalse("nothing changes", WallpaperPickerLogic.applyBothEnabled(AURORA, AURORA, SAME, false));
        assertFalse("busy", WallpaperPickerLogic.applyBothEnabled(TIDE, AURORA, SAME, true));
        assertFalse("a Lock photo cannot reach Home", WallpaperPickerLogic.applyBothEnabled(PHOTO, AURORA, PHOTO, false));
        assertTrue("Home's photo can be followed", WallpaperPickerLogic.applyBothEnabled(PHOTO, PHOTO, TIDE, false));
        assertTrue(WallpaperPickerLogic.homeChanges(TIDE, AURORA));
        assertFalse(WallpaperPickerLogic.homeChanges(AURORA, AURORA));
    }

    @Test
    public void slotOnlyItemsApplyTheirSlot() {
        WallpaperSlots.Slot home = WallpaperSlots.Slot.HOME;
        WallpaperSlots.Slot lock = WallpaperSlots.Slot.LOCK;
        assertEquals("tide", WallpaperPickerLogic.slotOnlyChoice(home, lock, TIDE, SAME).animatedId);
        assertTrue("Lock only keeps Same as Home", WallpaperPickerLogic.slotOnlyChoice(lock, lock, TIDE, SAME).sameAsHome);
        assertEquals("from Home, Lock takes Home's pick", "tide",
            WallpaperPickerLogic.slotOnlyChoice(lock, home, TIDE, AURORA).animatedId);

        assertTrue(WallpaperPickerLogic.applyOneEnabled(home, TIDE, AURORA, false));
        assertFalse(WallpaperPickerLogic.applyOneEnabled(home, AURORA, AURORA, false));
        assertFalse("Home cannot follow itself", WallpaperPickerLogic.applyOneEnabled(home, SAME, AURORA, false));
        assertTrue(WallpaperPickerLogic.applyOneEnabled(lock, SAME, TIDE, false));
        assertFalse("photos go through Photo…", WallpaperPickerLogic.applyOneEnabled(lock, PHOTO, TIDE, false));
        assertFalse("busy", WallpaperPickerLogic.applyOneEnabled(lock, TIDE, SAME, true));
    }

    @Test
    public void iconPackListingPutsSystemIconsFirstAndChecksTheCurrentPack() {
        List<IconPackInfo> packs = Arrays.asList(
            new IconPackInfo("com.example.arcticons", "Arcticons", 1, true),
            new IconPackInfo("com.example.lines", "Lines", 1, false));
        List<IconPackChoices.Entry> entries = IconPackChoices.entries("System icons", packs, false);
        assertEquals(3, entries.size());
        assertEquals("System icons", entries.get(0).label.toString());
        assertEquals("", entries.get(0).value);
        assertEquals("com.example.arcticons", entries.get(1).value);
        assertEquals("com.example.lines", entries.get(2).value);

        assertEquals(2, IconPackChoices.indexOf(entries, "com.example.lines"));
        assertEquals(0, IconPackChoices.indexOf(entries, ""));
        assertEquals(0, IconPackChoices.indexOf(entries, null));
        assertEquals("an uninstalled pack reads as the default row", 0,
            IconPackChoices.indexOf(entries, "com.example.gone"));
        assertEquals(2, new IconPackChoices.Listing(entries, IconPackChoices.indexOf(entries,
            "com.example.lines")).checked);

        List<IconPackChoices.Entry> themed = IconPackChoices.entries("System icons", packs, true);
        assertEquals(2, themed.size());
        assertEquals("com.example.arcticons", themed.get(1).value);
    }
}
