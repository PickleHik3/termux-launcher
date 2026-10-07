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
    public void choicesCompareByWhatTheyName() {
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.photo(FILE_A),
            WallpaperSlots.Choice.photo(new java.io.File("/p/a.png"))));
        assertFalse(WallpaperPickerLogic.sameChoice(A, B));
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.sameAsHome(),
            WallpaperSlots.Choice.sameAsHome()));
        assertFalse(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.photo(),
            WallpaperSlots.Choice.sameAsHome()));
    }

    @Test
    public void sameAsHomeIsLockOnly() {
        assertTrue(WallpaperPickerLogic.showsSameAsHome(WallpaperSlots.Slot.LOCK));
        assertFalse(WallpaperPickerLogic.showsSameAsHome(WallpaperSlots.Slot.HOME));
    }

    private static final java.io.File FILE_A = new java.io.File("/p/a.png");
    private static final java.io.File FILE_B = new java.io.File("/p/b.png");
    private static final WallpaperSlots.Choice A = WallpaperSlots.Choice.photo(FILE_A);
    private static final WallpaperSlots.Choice B = WallpaperSlots.Choice.photo(FILE_B);
    private static final WallpaperSlots.Choice SAME = WallpaperSlots.Choice.sameAsHome();
    private static final WallpaperSlots.Choice PHOTO = WallpaperSlots.Choice.photo();

    @Test
    public void doneAppliesAPendingChoiceThatDiffersFromItsSlot() {
        WallpaperSlots.Slot home = WallpaperSlots.Slot.HOME;
        WallpaperSlots.Slot lock = WallpaperSlots.Slot.LOCK;
        assertTrue(WallpaperPickerLogic.pendingApplies(home, B, A));
        assertFalse("nothing changed", WallpaperPickerLogic.pendingApplies(home, A, A));
        assertFalse("Home cannot follow itself", WallpaperPickerLogic.pendingApplies(home, SAME, A));
        assertTrue(WallpaperPickerLogic.pendingApplies(lock, SAME, B));
        assertFalse("a photo with no picture is not applied", WallpaperPickerLogic.pendingApplies(lock, PHOTO, B));
        assertFalse("null", WallpaperPickerLogic.pendingApplies(lock, null, B));
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
        assertTrue("a picked photo reaches Home", WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.HOME, picked, A));
        assertTrue("a different picture", WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.HOME, picked, kept));
        assertFalse("the same picture", WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.HOME, kept, kept));
        assertTrue(WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.LOCK, picked, B));
        assertFalse(WallpaperPickerLogic.sameChoice(picked, kept));
        assertTrue(WallpaperPickerLogic.sameChoice(picked,
            WallpaperSlots.Choice.photo(new java.io.File("/p/1.png"))));
        assertFalse(WallpaperPickerLogic.sameChoice(picked, PHOTO));
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
}
