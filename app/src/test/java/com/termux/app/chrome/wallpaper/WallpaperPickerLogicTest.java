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

    @Test
    public void aPhotoWithItsPictureCanBeApplied() {
        WallpaperSlots.Choice picked = WallpaperSlots.Choice.photo(new java.io.File("/p/1.png"));
        WallpaperSlots.Choice kept = WallpaperSlots.Choice.photo(new java.io.File("/p/exact.png"));
        assertTrue(WallpaperPickerLogic.photoWithPicture(picked));
        assertFalse(WallpaperPickerLogic.photoWithPicture(PHOTO));
        assertTrue("a picked photo reaches Home", WallpaperPickerLogic.applyBothEnabled(picked, AURORA, SAME, false));
        assertTrue("a different picture", WallpaperPickerLogic.applyBothEnabled(picked, kept, SAME, false));
        assertFalse("the same picture", WallpaperPickerLogic.applyBothEnabled(kept, kept, SAME, false));
        assertTrue(WallpaperPickerLogic.applyOneEnabled(WallpaperSlots.Slot.LOCK, picked, TIDE, false));
        assertTrue(WallpaperPickerLogic.applyOneEnabled(WallpaperSlots.Slot.HOME, picked, kept, false));
        assertFalse(WallpaperPickerLogic.sameChoice(picked, kept));
        assertTrue(WallpaperPickerLogic.sameChoice(picked,
            WallpaperSlots.Choice.photo(new java.io.File("/p/1.png"))));
        assertFalse(WallpaperPickerLogic.sameChoice(picked, PHOTO));
    }

    @Test
    public void motionNeedsTheAnimatedBackgroundsOffered() {
        assertTrue(WallpaperPickerLogic.motionRowExists(34, true));
        assertFalse(WallpaperPickerLogic.motionRowExists(34, false));
        assertFalse(WallpaperPickerLogic.motionRowExists(33, true));
    }

    // --- the Motion row for living stills (living-stills.md, Part D.6) ---

    private static final java.io.File PHOTO_FILE = new java.io.File("/data/pending/1.png");
    private static final String LIVING = "living:0123456789abcdef";

    private static WallpaperPickerLogic.MotionRow row(WallpaperSlots.Slot centred, int sdk, boolean offered,
                                                      WallpaperSlots.Choice shown, boolean hasLiving, boolean working) {
        return WallpaperPickerLogic.motionRow(centred, sdk, offered, shown, hasLiving, working);
    }

    @Test
    public void aPhotoOffersBringToLifeOnBothCardsWhereLivingStillsAreOffered() {
        for (WallpaperSlots.Slot slot : WallpaperSlots.Slot.values()) {
            assertEquals(WallpaperPickerLogic.MotionRow.OFFER,
                row(slot, 34, true, WallpaperSlots.Choice.photo(PHOTO_FILE), false, false));
            assertEquals(WallpaperPickerLogic.MotionRow.WORKING,
                row(slot, 34, true, WallpaperSlots.Choice.photo(PHOTO_FILE), false, true));
            assertEquals("a photo with its still is that still's switch", WallpaperPickerLogic.MotionRow.SWITCH,
                row(slot, 34, true, WallpaperSlots.Choice.photo(PHOTO_FILE), true, false));
        }
    }

    @Test
    public void aLivingStillAlwaysHasItsSwitch() {
        for (WallpaperSlots.Slot slot : WallpaperSlots.Slot.values()) {
            assertEquals(WallpaperPickerLogic.MotionRow.SWITCH,
                row(slot, 34, true, WallpaperSlots.Choice.animated(LIVING), false, false));
        }
        assertTrue(WallpaperPickerLogic.isLiving(WallpaperSlots.Choice.animated(LIVING)));
        assertFalse(WallpaperPickerLogic.isLiving(WallpaperSlots.Choice.animated("aurora")));
        assertFalse(WallpaperPickerLogic.isLiving(WallpaperSlots.Choice.photo(PHOTO_FILE)));
        assertFalse(WallpaperPickerLogic.isLiving(WallpaperSlots.Choice.sameAsHome()));
        assertFalse(WallpaperPickerLogic.isLiving(null));
    }

    @Test
    public void everythingElseKeepsTheOlderMotionRule() {
        // No living stills offered (below API 34, no job): a photo is just a photo.
        assertEquals(WallpaperPickerLogic.MotionRow.SWITCH,
            row(WallpaperSlots.Slot.LOCK, 34, false, WallpaperSlots.Choice.photo(PHOTO_FILE), false, false));
        assertEquals(WallpaperPickerLogic.MotionRow.SWITCH_HIDDEN,
            row(WallpaperSlots.Slot.HOME, 34, false, WallpaperSlots.Choice.photo(PHOTO_FILE), false, false));
        // A generated background: Lock's switch, nothing on Home.
        assertEquals(WallpaperPickerLogic.MotionRow.SWITCH,
            row(WallpaperSlots.Slot.LOCK, 34, true, WallpaperSlots.Choice.animated("aurora"), false, false));
        assertEquals(WallpaperPickerLogic.MotionRow.SWITCH_HIDDEN,
            row(WallpaperSlots.Slot.HOME, 34, true, WallpaperSlots.Choice.animated("aurora"), false, false));
        // A photo whose picture is not known cannot be analysed.
        assertEquals(WallpaperPickerLogic.MotionRow.SWITCH_HIDDEN,
            row(WallpaperSlots.Slot.HOME, 34, true, WallpaperSlots.Choice.photo(), false, false));
    }

    @Test
    public void theStageLabelAsksGemmaOnlyInTheRecipeStage() {
        assertEquals("depth", WallpaperPickerLogic.stageKey("depth", false));
        assertEquals("depth", WallpaperPickerLogic.stageKey("depth", true));
        assertEquals("recipe", WallpaperPickerLogic.stageKey("recipe", false));
        assertEquals("gemma", WallpaperPickerLogic.stageKey("recipe", true));
    }

    @Test
    public void aLivingChoiceIsComparedById() {
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.animated(LIVING),
            WallpaperSlots.Choice.animated(LIVING)));
        assertFalse(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.animated(LIVING),
            WallpaperSlots.Choice.animated("living:fedcba9876543210")));
        assertTrue("Apply is offered for a living still over a stored photo",
            WallpaperPickerLogic.applyBothEnabled(WallpaperSlots.Choice.animated(LIVING),
                WallpaperSlots.Choice.photo(PHOTO_FILE), WallpaperSlots.Choice.sameAsHome(), false));
    }

    @Test
    public void aCardLosesTheLabelsHeightAndKeepsTheAspect() {
        // A 360 dp wide pager 356 dp tall at 3x: 8 dp of padding each side and a 26 dp label.
        int[] withLabel = WallpaperPickerLogic.cardSize(1080, 1068, 24, 78, 360f, 800f, 0.62f);
        int[] without = WallpaperPickerLogic.cardSize(1080, 1068, 24, 0, 360f, 800f, 0.62f);
        assertEquals(1068 - 48 - 78, withLabel[1]);
        assertTrue("the label shrinks the card", withLabel[1] < without[1]);
        assertEquals("at the overlay's aspect", Math.round(withLabel[1] * 360f / 800f), withLabel[0]);
    }

    @Test
    public void aWideShortPagerNarrowsTheCardToItsShareOfTheWidth() {
        int[] size = WallpaperPickerLogic.cardSize(400, 2000, 0, 0, 360f, 800f, 0.5f);
        assertEquals(200, size[0]);
        assertEquals(Math.round(200 * 800f / 360f), size[1]);
        int[] none = WallpaperPickerLogic.cardSize(0, 0, 10, 10, 360f, 800f, 0.5f);
        assertTrue("never smaller than a pixel", none[0] >= 1 && none[1] >= 1);
    }

    @Test
    public void theStripIsSameAsHomeAndTheRecentsOnly() {
        assertEquals(1, WallpaperPickerLogic.stripTileCount(0, 3));
        assertEquals(3, WallpaperPickerLogic.stripTileCount(2, 3));
        assertEquals("the recents are capped", 4, WallpaperPickerLogic.stripTileCount(9, 3));
        assertEquals("five recents plus Same as Home", 6,
            WallpaperPickerLogic.stripTileCount(9, RecentWallpapers.MAX));
    }

    @Test
    public void stripThumbsShrinkToFitSixTiles() {
        int[] dps = {320, 360, 411};
        for (int dp : dps) {
            // Density 1: a strip of (width - 32 card margins - 24 padding) px.
            int avail = dp - 32 - 24;
            int[] size = WallpaperPickerLogic.stripThumbSize(avail, 6, 1080, 2400, 112, 4);
            assertTrue("fits at " + dp, size[0] + 4 <= avail / 6);
            assertTrue(size[1] <= 112);
            assertEquals("aspect kept at " + dp, 2400 / 1080f, size[1] / (float) size[0], 0.1f);
        }
        int[] roomy = WallpaperPickerLogic.stripThumbSize(2000, 6, 1080, 2400, 112, 4);
        assertEquals("no growth past the natural width", WallpaperPickerLogic.thumbWidth(112, 1080, 2400), roomy[0]);
        assertEquals(112, roomy[1]);
    }

    private static final long MB = 1024L * 1024L;

    @Test
    public void lowMemoryWarningNamesTheShortfallInMbRoundedUp() {
        assertEquals(500L, WallpaperPickerLogic.lowMemoryWarning(3000 * MB, 2500 * MB, false));
        assertEquals("a part of a MB counts as one", 1L,
            WallpaperPickerLogic.lowMemoryWarning(3000 * MB + 1L, 3000 * MB, false));
    }

    @Test
    public void lowMemoryWarningSaysNothingWhenItFitsOrCannotBeKnownOrIsRemote() {
        assertEquals(0L, WallpaperPickerLogic.lowMemoryWarning(2000 * MB, 2000 * MB, false));
        assertEquals(0L, WallpaperPickerLogic.lowMemoryWarning(2000 * MB, 4000 * MB, false));
        assertEquals("a remote reader loads nothing here", 0L,
            WallpaperPickerLogic.lowMemoryWarning(9000 * MB, 100 * MB, true));
        assertEquals("no figure for the need", 0L, WallpaperPickerLogic.lowMemoryWarning(0L, 100 * MB, false));
        assertEquals("no reading of free memory", 0L, WallpaperPickerLogic.lowMemoryWarning(2000 * MB, -1L, false));
    }
}
