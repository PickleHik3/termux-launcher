package com.termux.app.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.HashMap;
import java.util.Map;

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
public class LowMemoryPresetTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("low-memory-preset-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void appliesEveryValue() {
        preferences.setTerminalCursorTrailEnabled(true);
        preferences.setTerminalRetroEffect("crt");
        preferences.setWallpaperParallaxEnabled(true);
        preferences.setSurfaceGlassMotion(TERMUX_APP.GLASS_MOTION_MIST);

        LowMemoryPreset.turnOn(preferences);

        assertEquals(TERMUX_APP.SURFACE_MATERIAL_SOLID, preferences.getSurfaceMaterial());
        assertEquals(100, preferences.getSurfaceOverrideValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertEquals(100, preferences.getTerminalBackgroundOpacity());
        assertFalse(preferences.isTerminalCursorTrailEnabled());
        assertEquals("none", preferences.getTerminalRetroEffect());
        assertEquals(TERMUX_APP.GLASS_MOTION_CLASSIC, preferences.getSurfaceGlassMotion());
        assertFalse(preferences.isWallpaperParallaxEnabled());
        assertTrue(preferences.isLazyModeEnabled());
    }

    @Test
    public void otherSurfacesKeepSolidsOpacity() {
        LowMemoryPreset.turnOn(preferences);
        assertEquals(92, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
    }

    @Test
    public void onThenOffRestoresEveryKey() {
        preferences.setTerminalCursorTrailEnabled(true);
        preferences.setTerminalRetroEffect("crt");
        preferences.setWallpaperParallaxEnabled(true);
        preferences.setTerminalBackgroundOpacity(60);
        preferences.setSurfaceMaterial(TERMUX_APP.SURFACE_MATERIAL_GLASS);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 12);
        Map<String, ?> before = new HashMap<>(preferences.getSharedPreferences().getAll());

        LowMemoryPreset.turnOn(preferences);
        assertTrue(preferences.isLowMemoryPresetEnabled());
        assertTrue(preferences.isLazyModeEnabled());
        LowMemoryPreset.turnOff(preferences);

        assertFalse(preferences.isLowMemoryPresetEnabled());
        assertFalse(preferences.isLazyModeEnabled());
        Map<String, Object> after = new HashMap<>(preferences.getSharedPreferences().getAll());
        after.remove(TERMUX_APP.KEY_LOW_MEMORY_PRESET);
        after.remove(TERMUX_APP.KEY_LOW_MEMORY_PRESET_SNAPSHOT);
        Map<String, Object> expected = new HashMap<>(before);
        assertEquals(expected, after);
        assertEquals("", preferences.getLowMemoryPresetSnapshot());
    }

    @Test
    public void offWithoutSnapshotChangesNothing() {
        preferences.setTerminalRetroEffect("crt");
        LowMemoryPreset.turnOff(preferences);
        assertEquals("crt", preferences.getTerminalRetroEffect());
        assertFalse(preferences.isLowMemoryPresetEnabled());
    }

    @Test
    public void secondOnKeepsTheFirstSnapshot() {
        preferences.setTerminalRetroEffect("crt");
        LowMemoryPreset.turnOn(preferences);
        LowMemoryPreset.turnOn(preferences);
        LowMemoryPreset.turnOff(preferences);
        assertEquals("crt", preferences.getTerminalRetroEffect());
    }
}
