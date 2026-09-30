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
    public void theKnobsDefaultToTheDocksOwnRefraction() {
        // GlassRefraction.Look.DEFAULT is built from these same three numbers; see its test.
        assertEquals(9, preferences.getFancierGlassBendDp());
        assertEquals(20, preferences.getFancierGlassEdgeWidthDp());
        assertEquals(18, preferences.getFancierGlassEdgeLightPercent());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
            preferences.getFancierGlassEdgeWidthDp());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_LIGHT,
            preferences.getFancierGlassEdgeLightPercent());
    }

    @Test
    public void everyKnobRoundTripsAndStaysInsideItsTrack() {
        preferences.setFancierGlassBendDp(14);
        assertEquals(14, preferences.getFancierGlassBendDp());
        preferences.setFancierGlassBendDp(999);
        assertEquals(TERMUX_APP.MAX_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());
        preferences.setFancierGlassBendDp(-5);
        assertEquals(TERMUX_APP.MIN_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());

        preferences.setFancierGlassEdgeWidthDp(33);
        assertEquals(33, preferences.getFancierGlassEdgeWidthDp());
        preferences.setFancierGlassEdgeWidthDp(0);
        assertEquals("a band of nothing would divide by zero in the shader",
            TERMUX_APP.MIN_FANCIER_GLASS_EDGE_WIDTH, preferences.getFancierGlassEdgeWidthDp());
        preferences.setFancierGlassEdgeWidthDp(400);
        assertEquals(TERMUX_APP.MAX_FANCIER_GLASS_EDGE_WIDTH, preferences.getFancierGlassEdgeWidthDp());

        preferences.setFancierGlassEdgeLightPercent(70);
        assertEquals(70, preferences.getFancierGlassEdgeLightPercent());
        preferences.setFancierGlassEdgeLightPercent(150);
        assertEquals(100, preferences.getFancierGlassEdgeLightPercent());
        preferences.setFancierGlassEdgeLightPercent(-1);
        assertEquals(0, preferences.getFancierGlassEdgeLightPercent());
    }
}
