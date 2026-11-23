package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Fancier Glass switch and its three knobs: off by default, the knobs at the numbers the
 * dock's refraction always used, and every write held inside its track.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FancierGlassPreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "fancier-glass-preferences-test", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    @Test
    public void theSwitchIsOnWhereverTheDeviceSupportsIt() {
        // Spec appearance-layout-editor §4: Fancier Glass is on by default; the device gate (API
        // 33, an in-app wallpaper) and Lazy mode decide whether it draws, not a switch.
        assertTrue(preferences.isFancierGlassEnabled());
        preferences.setFancierGlassEnabled(false);
        assertFalse(preferences.isFancierGlassEnabled());
        preferences.setFancierGlassEnabled(true);
        assertTrue(preferences.isFancierGlassEnabled());
    }

    @Test
    public void depthAndShineDefaultToTheRealisticGlass() {
        assertEquals(9, preferences.getFancierGlassBendDp());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());
        assertEquals(50, preferences.getGlassShine());
        assertEquals(GlassRefraction.Look.DEFAULT, GlassRefraction.Look.of(preferences));
    }

    @Test
    public void depthAndShineRoundTripAndStayInsideTheirTracks() {
        preferences.setFancierGlassBendDp(14);
        assertEquals(14, preferences.getFancierGlassBendDp());
        preferences.setFancierGlassBendDp(999);
        assertEquals(TERMUX_APP.MAX_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());
        preferences.setFancierGlassBendDp(-5);
        assertEquals(TERMUX_APP.MIN_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());

        preferences.setGlassShine(70);
        assertEquals(70, preferences.getGlassShine());
        preferences.setGlassShine(500);
        assertEquals(100, preferences.getGlassShine());
        preferences.setGlassShine(-3);
        assertEquals(0, preferences.getGlassShine());
        assertEquals(0, GlassRefraction.Look.of(preferences).shine);
    }

    @Test
    public void theLookCarriesTheThicknessAndTheShinesColourSplit() {
        preferences.setFancierGlassBendDp(27);
        preferences.setGlassShine(100);
        GlassRefraction.Look look = GlassRefraction.Look.of(preferences);
        assertEquals(27, look.bendDp);
        assertEquals(100, look.shine);
        assertEquals(2.5f, look.shineScale(), 1e-6f);
        assertEquals(60, look.dispersionPercent);
        preferences.setGlassShine(50);
        assertEquals(0, GlassRefraction.Look.of(preferences).dispersionPercent);
    }

    @Test
    public void everyBlurSettingTakesUpToFortyEightDp() {
        preferences.setStatusBarBlurRadius(99);
        assertEquals(36, preferences.getStatusBarBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        // The panes' own Blur is plain dp, no Frost stop: its ceiling is 48 itself.
        preferences.setTerminalGlassBlurRadius(99);
        assertEquals(48, preferences.getTerminalGlassBlurRadius());
        preferences.setExtraKeysBlurRadius(99);
        assertEquals(36, preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        preferences.setInAppKeyboardBlurRadius(99);
        assertEquals(36, preferences.getInAppKeyboardBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
        preferences.setExtraKeysBlurRadius(44);
        // 44 dp is past the last Frost stop: its 36.
        assertEquals(36, preferences.getExtraKeysBlurRadius(com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.terminalPageId()));
    }
}
