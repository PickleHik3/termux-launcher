package com.termux.app.chrome;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperModePreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "wallpaper-mode-preferences-test",
            Context.MODE_PRIVATE
        );
        sharedPreferences.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    @Test
    public void disablingWallpaperForcesOpaqueUnblurredSurfacesAndEnablingRestoresValues() {
        preferences.setUseSystemWallpaperEnabled(true);
        // A user whose terminal and dock differ has, by definition, taken the terminal off the
        // shared layer - while linked the two name the same value and cannot disagree.
        preferences.setAppBarOpacity(63);
        preferences.detachSurfaceValue(TermuxAppSharedPreferences.SurfaceSlot.CANVAS,
            TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 37);
        preferences.setExtraKeysBlurRadius(19);

        ChromePolicy.applyWallpaperModePreferences(preferences, false);

        assertFalse(preferences.isUseSystemWallpaperEnabled());
        assertEquals(100, preferences.getTerminalBackgroundOpacity());
        assertEquals(100, preferences.getAppBarOpacity());
        assertEquals(0, preferences.getExtraKeysBlurRadius());
        assertEquals(37, preferences.getWallpaperEnabledTerminalBackgroundOpacity());
        assertEquals(63, preferences.getWallpaperEnabledAppBarOpacity());
        assertEquals(19, preferences.getWallpaperEnabledExtraKeysBlurRadius());

        ChromePolicy.applyWallpaperModePreferences(preferences, true);

        assertTrue(preferences.isUseSystemWallpaperEnabled());
        assertEquals(37, preferences.getTerminalBackgroundOpacity());
        assertEquals(63, preferences.getAppBarOpacity());
        assertEquals(19, preferences.getExtraKeysBlurRadius());
    }

    @Test
    public void enablingWhileAlreadyEnabledKeepsTheLookAsItStands() {
        // Applying a new wallpaper while wallpaper mode is on re-states "enabled". The Look picked
        // since the mode last came on (Mist's blur 25 and opacity 60 here) must survive it; the
        // remembered values are older and would otherwise land over it.
        preferences.setUseSystemWallpaperEnabled(true);
        preferences.setWallpaperEnabledTerminalBackgroundOpacity(10);
        preferences.setWallpaperEnabledAppBarOpacity(10);
        preferences.setWallpaperEnabledExtraKeysBlurRadius(4);
        preferences.setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR, 25);
        preferences.setSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 60);

        ChromePolicy.applyWallpaperModePreferences(preferences, true);

        assertTrue(preferences.isUseSystemWallpaperEnabled());
        assertEquals(25, preferences.getSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR));
        assertEquals(60, preferences.getSurfaceBaseValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY));
        assertEquals(25, preferences.getExtraKeysBlurRadius());
        assertEquals(60, preferences.getAppBarOpacity());
        assertEquals(60, preferences.getTerminalBackgroundOpacity());
    }
}
