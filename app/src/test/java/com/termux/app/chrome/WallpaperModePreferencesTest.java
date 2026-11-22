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
        // The terminal's Opacity is its own Background's (ADR 0031 §4), never the shared layer's.
        preferences.setAppBarOpacity(63);
        preferences.setTerminalBackgroundOpacity(37);
        preferences.setExtraKeysBlurRadius(19);

        ChromePolicy.applyWallpaperModePreferences(preferences, false);

        assertFalse(preferences.isUseSystemWallpaperEnabled());
        assertEquals(100, preferences.getTerminalBackgroundOpacity());
        assertEquals(100, preferences.getAppBarOpacity(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(0, preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(37, preferences.getWallpaperEnabledTerminalBackgroundOpacity());
        assertEquals(63, preferences.getWallpaperEnabledAppBarOpacity());
        // The remembered blur is the blur worn, which sits on a Frost stop: 19 wears 17.
        assertEquals(17, preferences.getWallpaperEnabledExtraKeysBlurRadius());

        ChromePolicy.applyWallpaperModePreferences(preferences, true);

        assertTrue(preferences.isUseSystemWallpaperEnabled());
        assertEquals(37, preferences.getTerminalBackgroundOpacity());
        assertEquals(63, preferences.getAppBarOpacity(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(17, preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
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
        preferences.setSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR, 25);
        preferences.setSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY, 60);

        // Not a switch of the mode, so no recreate: the picker's Apply and `launcherctl wallpaper
        // set` land here, and a recreate closed the Look editor and dropped an X11 display pane.
        assertFalse(ChromePolicy.applyWallpaperModePreferences(preferences, true));

        assertTrue(preferences.isUseSystemWallpaperEnabled());
        assertEquals(25, preferences.getSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.BLUR));
        assertEquals(60, preferences.getSurfaceGlobalValue(TermuxAppSharedPreferences.SurfaceProperty.OPACITY));
        assertEquals(25, preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(60, preferences.getAppBarOpacity(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        // The terminal's own Opacity is not the Look's: it stays at its shipped value.
        assertEquals(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.DEFAULT_TERMINAL_BG_OPACITY, preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void onlyASwitchOfTheModeAsksForARecreate() {
        // The window theme follows the mode, so a switch either way still recreates.
        preferences.setUseSystemWallpaperEnabled(false);
        assertTrue(ChromePolicy.applyWallpaperModePreferences(preferences, true));
        assertTrue(ChromePolicy.applyWallpaperModePreferences(preferences, false));
        // Off and asked off again: nothing in the theme moves.
        assertFalse(ChromePolicy.applyWallpaperModePreferences(preferences, false));
    }

    @Test
    public void aRecreateAlreadyOwedIsNotCancelledByAnInPlaceRequest() {
        // A theme change from Settings is owed on resume; a wallpaper set before that resume
        // asks for an in-place restyle and must not take the theme's recreate away.
        assertTrue(com.termux.app.TermuxActivity.pendingRecreateAfterRequest(true, true, false));
        // Nothing owed (or the owed reload is in place): the request decides.
        assertFalse(com.termux.app.TermuxActivity.pendingRecreateAfterRequest(false, true, false));
        assertFalse(com.termux.app.TermuxActivity.pendingRecreateAfterRequest(true, false, false));
        assertTrue(com.termux.app.TermuxActivity.pendingRecreateAfterRequest(false, false, true));
    }
}
