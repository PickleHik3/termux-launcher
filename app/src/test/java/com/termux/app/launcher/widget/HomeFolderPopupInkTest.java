package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.chrome.ChromeInk.Polarity;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.FloatTitlebarPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Home folder popup's ink, for its title, rename draft and labels: the titlebar's rule on the
 * chrome's polarity, as the capsule status bar takes it, while the wallpaper shows through.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFolderPopupInkTest {

    private static final int LIGHT_ON_SURFACE = 0xFF171C24;
    private static final int LIGHT_SURFACE_BASE = 0xFFF4F6FB;
    private static final int PLATE = 0xFF202020;
    /** A dark wallpaper seen through the default 43% plate. */
    private static final int DARK_GROUND = 0xFF1B1E24;

    @Test public void onGlassOverADarkWallpaper_theLightThemeDrawsPaleInk() {
        int ink = HomeFolderPopup.ink(true, Polarity.PALE_INK, LIGHT_ON_SURFACE,
            LIGHT_SURFACE_BASE, PLATE);
        assertEquals(LIGHT_SURFACE_BASE, ink);
        assertTrue(OnGlass.ratio(ink, DARK_GROUND) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("the old ink was dark on dark",
            OnGlass.ratio(LIGHT_ON_SURFACE, DARK_GROUND) < OnGlass.TARGET_LARGE_TEXT);
        assertEquals("over a light wallpaper", LIGHT_ON_SURFACE, HomeFolderPopup.ink(true,
            Polarity.DARK_INK, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE, PLATE));
    }

    @Test public void itIsTheTitlebarsRule() {
        Polarity[] polarities = {Polarity.PALE_INK, Polarity.DARK_INK, null};
        for (boolean glass : new boolean[]{true, false}) {
            for (Polarity polarity : polarities) {
                assertEquals(FloatTitlebarPolicy.glyphInk(glass, polarity, LIGHT_ON_SURFACE,
                        LIGHT_SURFACE_BASE, PLATE, true),
                    HomeFolderPopup.ink(glass, polarity, LIGHT_ON_SURFACE, LIGHT_SURFACE_BASE,
                        PLATE));
            }
        }
    }

    @Test public void onAPanelThatHidesTheWallpaperOrUnmeasured_theInkIsLegibleOnThePanel() {
        int legible = GlassInk.legible(PLATE, LIGHT_ON_SURFACE, OnGlass.TARGET_LARGE_TEXT, 255);
        assertEquals(legible, HomeFolderPopup.ink(false, Polarity.DARK_INK, LIGHT_ON_SURFACE,
            LIGHT_SURFACE_BASE, PLATE));
        assertEquals(legible, HomeFolderPopup.ink(true, null, LIGHT_ON_SURFACE,
            LIGHT_SURFACE_BASE, PLATE));
        assertTrue(OnGlass.ratio(legible, PLATE) >= OnGlass.TARGET_LARGE_TEXT);
    }

    @Test public void theWallpaperShowsThroughBelowHalfOpacity() {
        assertTrue("the dock's default", HomeFolderPopup.wallpaperShowsThrough(43));
        assertTrue(HomeFolderPopup.wallpaperShowsThrough(0));
        assertFalse(HomeFolderPopup.wallpaperShowsThrough(50));
        assertFalse(HomeFolderPopup.wallpaperShowsThrough(80));
        assertFalse(HomeFolderPopup.wallpaperShowsThrough(100));
    }
}
