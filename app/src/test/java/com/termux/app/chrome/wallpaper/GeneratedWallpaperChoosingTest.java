package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/** Choosing a living still: the offer rule and the stored choice. */
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
        assertFalse(preferences.isAnimatedWallpaperDisabled());

        preferences.setManagedWallpaperAnimatedId("living:0123456789abcdef");
        assertEquals("living:0123456789abcdef", preferences.getManagedWallpaperAnimatedId());

        preferences.setManagedWallpaperAnimatedId(null);
        assertNull(preferences.getManagedWallpaperAnimatedId());
    }
}
