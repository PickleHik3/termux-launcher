package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the preset round-trip that "Stock" is named after: applied to any state, it is the shipped
 * look — the shipped Base numbers, the dock's one denser detached opacity, and nothing else
 * detached — and the selection ring's match test agrees.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfacePresetsTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("surface-presets-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void applyingStockIsTheShippedLook() {
        // Start somewhere else entirely, detached rows included.
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 25);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 77);

        SurfacePresets.Preset stock = SurfacePresets.presets().get(0);
        assertEquals("stock", stock.id);
        SurfacePresets.apply(preferences, stock);

        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR,
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN,
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS,
            preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP,
            preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));

        // The one shipped asymmetry: the dock's denser opacity, and nothing else detached.
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY,
            preferences.getSurfaceOverrideValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));

        assertTrue(SurfacePresets.matches(preferences, stock));
        assertFalse(SurfacePresets.matches(preferences, SurfacePresets.presets().get(1)));
    }
}
