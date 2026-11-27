package com.termux.app.look;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class WallpaperLooksTest {

    private static final List<String> IDS =
        Arrays.asList(WallpaperLooks.CALM, WallpaperLooks.BALANCED, WallpaperLooks.BOLD);

    private static int[] flat(int argb, int count) {
        int[] pixels = new int[count];
        Arrays.fill(pixels, argb);
        return pixels;
    }

    @Test
    public void threeLooksWithStableIdsInOrder() {
        List<TerlLookSource.Look> looks = WallpaperLooks.from(WallpaperSummary.NEUTRAL);
        assertEquals(3, looks.size());
        for (int i = 0; i < 3; i++) assertEquals(IDS.get(i), looks.get(i).id);
    }

    @Test
    public void eachStepAddsOpacityAndBlur() {
        List<TerlLookSource.Look> looks = WallpaperLooks.from(new WallpaperSummary(50, 50));
        assertTrue(looks.get(0).opacity < looks.get(1).opacity);
        assertTrue(looks.get(1).opacity < looks.get(2).opacity);
        assertTrue(looks.get(0).blur < looks.get(1).blur);
        assertTrue(looks.get(1).blur < looks.get(2).blur);
    }

    @Test
    public void brighterAndBusierWallpapersGetMore() {
        TerlLookSource.Look dark = WallpaperLooks.from(new WallpaperSummary(10, 10)).get(1);
        TerlLookSource.Look bright = WallpaperLooks.from(new WallpaperSummary(90, 10)).get(1);
        TerlLookSource.Look busy = WallpaperLooks.from(new WallpaperSummary(10, 90)).get(1);
        assertTrue(bright.opacity > dark.opacity);
        assertTrue(busy.blur > dark.blur);
    }

    @Test
    public void numbersStayInRangeAtTheExtremes() {
        for (WallpaperSummary s : new WallpaperSummary[] {
            new WallpaperSummary(0, 0), new WallpaperSummary(100, 100)}) {
            for (TerlLookSource.Look look : WallpaperLooks.from(s)) {
                assertTrue(look.opacity >= 0 && look.opacity <= 100);
                assertTrue(look.blur >= 0 && look.blur <= 48);
            }
        }
    }

    @Test
    public void summaryClampsItsInputs() {
        WallpaperSummary s = new WallpaperSummary(-5, 400);
        assertEquals(0, s.luminance);
        assertEquals(100, s.busyness);
    }

    @Test
    public void aFlatBlackPictureIsDarkAndCalm() {
        WallpaperSummary s = WallpaperSummary.ofPixels(flat(0xFF000000, 16), 4, 4);
        assertEquals(0, s.luminance);
        assertEquals(0, s.busyness);
    }

    @Test
    public void aFlatWhitePictureIsBrightAndCalm() {
        WallpaperSummary s = WallpaperSummary.ofPixels(flat(0xFFFFFFFF, 16), 4, 4);
        assertEquals(100, s.luminance);
        assertEquals(0, s.busyness);
    }

    @Test
    public void aCheckerboardIsBusy() {
        int[] pixels = new int[16];
        for (int i = 0; i < 16; i++) pixels[i] = ((i % 4) + (i / 4)) % 2 == 0 ? 0xFF000000 : 0xFFFFFFFF;
        WallpaperSummary s = WallpaperSummary.ofPixels(pixels, 4, 4);
        assertEquals(100, s.busyness);
        assertTrue(s.luminance > 40 && s.luminance < 60);
    }

    @Test
    public void anEmptyPictureIsNeutral() {
        WallpaperSummary s = WallpaperSummary.ofPixels(new int[0], 0, 0);
        assertEquals(WallpaperSummary.NEUTRAL.luminance, s.luminance);
        assertEquals(WallpaperSummary.NEUTRAL.busyness, s.busyness);
    }

    @Test
    public void colourSpreadMakesBusier() {
        WallpaperSummary one = WallpaperSummary.ofColors(0xFF204060, null, null);
        WallpaperSummary many = WallpaperSummary.ofColors(0xFF204060, 0xFFE0A020, 0xFF30D080);
        assertTrue(many.busyness > one.busyness);
        assertEquals(one.luminance, many.luminance);
    }

    @Test
    public void sourceShowsNeutralLooksUntilStoredThenFollowsTheStore() {
        MemoryLookStore backing = new MemoryLookStore();
        TerlLookSource source = new WallpaperLooks.Source(backing);
        assertEquals(3, source.looks().size());
        TerlLookSource.Look before = source.find(WallpaperLooks.BOLD);
        assertNotNull(before);
        assertEquals(WallpaperLooks.from(WallpaperSummary.NEUTRAL).get(2).opacity, before.opacity);

        WallpaperLooks.store(backing, new WallpaperSummary(95, 95));
        TerlLookSource.Look after = source.find(WallpaperLooks.BOLD);
        assertTrue(after.opacity > before.opacity);
        assertTrue(backing.has("terl.look.wallpaper.bold.opacity"));
        assertNull(source.find("nope"));
    }

    @Test
    public void aWornWallpaperLookFollowsAWallpaperChangeAndKeepsItsId() {
        MemoryLookStore backing = new MemoryLookStore();
        TerlLookStore store = new StoredTerlLookStore(backing,
            Arrays.asList(TerlLooks.shipped(), new WallpaperLooks.Source(backing)));
        assertTrue(store.lookIds().containsAll(IDS));
        assertEquals(TerlLookStore.CUSTOM, store.lookIds().get(store.lookIds().size() - 1));
        assertEquals(WallpaperLooks.CALM, store.lookIds().get(store.lookIds().size() - 4));
        assertTrue(store.pick(WallpaperLooks.BALANCED, false).applied);
        int opacity = store.global(TerlProperty.OPACITY);
        WallpaperLooks.store(backing, new WallpaperSummary(100, 100));
        assertEquals(WallpaperLooks.BALANCED, store.look());
        assertTrue(store.global(TerlProperty.OPACITY) > opacity);
    }

    @Test
    public void pickingAsksBeforeClearingOwnValues() {
        MemoryLookStore backing = new MemoryLookStore();
        TerlLookStore store = new StoredTerlLookStore(backing,
            Arrays.asList(TerlLooks.shipped(), new WallpaperLooks.Source(backing)));
        store.setOwn(TerlSurface.KEYBOARD, TerlProperty.OPACITY, 20);
        TerlLookStore.Pick ask = store.pick(WallpaperLooks.BOLD, false);
        assertTrue(!ask.applied && !ask.clears.isEmpty());
        assertTrue(store.pick(WallpaperLooks.BOLD, true).applied);
    }
}
