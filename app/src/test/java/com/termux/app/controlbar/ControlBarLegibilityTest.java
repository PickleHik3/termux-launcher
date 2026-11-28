package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.GlassInk;
import com.termux.app.glass.LegibilityBand;
import com.termux.app.glass.LegibilityStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The bar's per-page legibility correction (ADR 0029 section 9): kept in the dock band's store per
 * page, measured at rest once, never in a crossing, and the bar's ink chosen on it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ControlBarLegibilityTest {

    private static final int PALE = 0xFFEEEEEE;
    private static final int DARK = 0xFF111111;
    private static final int THEME = 0xFF888888;

    private final LegibilityStore store = new LegibilityStore();
    private int measured;

    private int face(long page, int faceNow) {
        return ControlBarLegibility.face(store, page, 0, 2000, 1080, 2250, 7, () -> {
            measured++;
            return faceNow;
        });
    }

    @Test
    public void eachPageKeepsItsOwnFaceMeasuredOnceAtRest() {
        long home1 = LegibilityStore.pageKey(0, 1);
        long home2 = LegibilityStore.pageKey(0, 2);
        assertEquals(PALE, face(home1, PALE));
        assertEquals(DARK, face(home2, DARK));
        assertEquals(2, measured);
        assertEquals("kept: not measured again", PALE, face(home1, DARK));
        assertEquals(2, measured);
        assertEquals(PALE, store.peek(home1, LegibilityBand.DOCK).face(0));
    }

    @Test
    public void aCrossingMeasuresNothingAndReadsWhatEachPageKept() {
        long home1 = LegibilityStore.pageKey(0, 1);
        face(home1, PALE);
        store.beginCrossing();
        assertEquals(PALE, face(home1, DARK));
        assertEquals("an unvisited page has nothing yet", 0, face(LegibilityStore.pageKey(0, 3), DARK));
        assertEquals(1, measured);
        store.endCrossing();
    }

    @Test
    public void aNewWallpaperMeasuresTheFaceAgain() {
        long home1 = LegibilityStore.pageKey(0, 1);
        face(home1, PALE);
        store.onWallpaperChanged();
        assertEquals(DARK, face(home1, DARK));
        assertEquals(2, measured);
    }

    @Test
    public void unreadWallpaperKeepsNothingAndTheThemesInk() {
        long home1 = LegibilityStore.pageKey(0, 1);
        assertEquals(0, face(home1, Color.TRANSPARENT));
        assertEquals(THEME, ControlBarLegibility.ink(0, THEME, 4.5));
        assertEquals("asked again next time", 0, face(home1, Color.TRANSPARENT));
        assertEquals(2, measured);
    }

    @Test
    public void theInkIsChosenOnThePagesFaceAsTheDockBandsIs() {
        int onPale = ControlBarLegibility.ink(PALE, THEME, 4.5);
        int onDark = ControlBarLegibility.ink(DARK, THEME, 4.5);
        assertEquals(GlassInk.faceInk(PALE, THEME, 4.5), onPale);
        assertEquals(GlassInk.faceInk(DARK, THEME, 4.5), onDark);
        assertNotEquals("each page's face moves the ink", onPale, onDark);
        assertTrue("dark on a pale face", luminance(onPale) < luminance(onDark));
    }

    /**
     * Review P2 (ADR 0029 sections 8 and 9): a page never visited is measured at rest before its
     * first crossing, and the crossing blends the two kept inks on its curve instead of flipping
     * at the landing; it measures nothing.
     */
    @Test
    public void aCrossingBlendsTheTwoKeptInksAndAnUnvisitedPageIsMeasuredFirst() {
        long home1 = LegibilityStore.pageKey(0, 1);
        long home2 = LegibilityStore.pageKey(0, 2);
        face(home1, PALE);
        assertEquals("Home 2 never visited: nothing to blend", null,
            ControlBarLegibility.crossingInks(store, home1, home2, THEME, 4.5));
        assertTrue("measured at rest, before the crossing",
            ControlBarLegibility.prepare(store, home2, 0, 2000, 1080, 2250, 7, () -> DARK));
        assertEquals("kept: not measured again", false,
            ControlBarLegibility.prepare(store, home2, 0, 2000, 1080, 2250, 7, () -> DARK));

        store.beginCrossing();
        int[] inks = ControlBarLegibility.crossingInks(store, home1, home2, THEME, 4.5);
        assertEquals(ControlBarLegibility.ink(PALE, THEME, 4.5), inks[0]);
        assertEquals(ControlBarLegibility.ink(DARK, THEME, 4.5), inks[1]);
        assertEquals(inks[0], ControlBarLegibility.blend(inks[0], inks[1], 0f));
        assertEquals(inks[1], ControlBarLegibility.blend(inks[0], inks[1], 1f));
        int middle = ControlBarLegibility.blend(inks[0], inks[1], 0.5f);
        assertTrue("between the two, not one or the other",
            luminance(middle) > Math.min(luminance(inks[0]), luminance(inks[1]))
                && luminance(middle) < Math.max(luminance(inks[0]), luminance(inks[1])));
        assertEquals("a crossing measures nothing", false,
            ControlBarLegibility.prepare(store, LegibilityStore.pageKey(0, 3), 0, 2000, 1080, 2250, 7,
                () -> DARK));
        store.endCrossing();
    }

    private static double luminance(int color) {
        return 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color);
    }
}
