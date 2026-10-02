package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Choosing a generated background: palette modes, the offer rule, stored choice and the routes' early answers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GeneratedWallpaperChoosingTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sp = context.getSharedPreferences("generated-wallpaper-choosing-test", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sp, null);
    }

    @Test
    public void ownModeGivesTheBackgroundsShippedPaletteWithoutAContext() {
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            int[] palette = WallpaperPaletteCapture.own(w);
            assertArrayEquals(w.id(), w.ownPalette(), palette);
            assertNotSame("a copy, so a caller cannot edit the shipped palette", w.ownPalette(), palette);
        }
    }

    @Test
    public void offeredNeedsApi34AndFancierGlass() {
        assertTrue(GeneratedWallpaperApplier.offered(34, true));
        assertTrue(GeneratedWallpaperApplier.offered(35, true));
        assertFalse(GeneratedWallpaperApplier.offered(33, true));
        assertFalse(GeneratedWallpaperApplier.offered(34, false));
        assertEquals("api", GeneratedWallpaperApplier.notOfferedReason(33, true));
        assertEquals("fancier_glass_off", GeneratedWallpaperApplier.notOfferedReason(34, false));
        assertNull(GeneratedWallpaperApplier.notOfferedReason(34, true));
    }

    @Test
    public void storedChoiceRoundTrips() {
        assertNull(preferences.getManagedWallpaperAnimatedId());
        assertNull(preferences.getManagedWallpaperAnimatedColors());
        assertFalse(preferences.isAnimatedWallpaperDisabled());

        int[] colors = {0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC};
        preferences.setManagedWallpaperAnimatedId("aurora");
        preferences.setManagedWallpaperAnimatedColors(colors);
        assertEquals("aurora", preferences.getManagedWallpaperAnimatedId());
        assertArrayEquals(colors, preferences.getManagedWallpaperAnimatedColors());

        preferences.setManagedWallpaperAnimatedId(null);
        preferences.setManagedWallpaperAnimatedColors(null);
        assertNull(preferences.getManagedWallpaperAnimatedId());
        assertNull(preferences.getManagedWallpaperAnimatedColors());
    }
}
