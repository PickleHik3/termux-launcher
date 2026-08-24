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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the preset round-trip the first card ("Classic", id {@code stock}) is named after: applied
 * to any state, it is the shipped look — the shipped Base numbers, the dock's one denser detached
 * opacity, and nothing else detached — and the selection ring's match test agrees. Plus the fifth
 * card, which is the user's own saved look rather than one this build ships.
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

    /**
     * The saved look is a pin, not a snapshot of "now": it has to survive every later edit, which
     * is the whole difference between the Custom card and simply leaving the editor alone.
     */
    @Test
    public void savedCustomLookOutlivesLaterEdits() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 21);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 63);
        SurfacePresets.saveCustom(preferences);

        SurfacePresets.Preset custom = SurfacePresets.custom(preferences);
        assertNotNull(custom);
        assertEquals(SurfacePresets.CUSTOM_ID, custom.id);
        assertTrue(SurfacePresets.matches(preferences, custom));

        // Wander off, then come back through the card.
        SurfacePresets.apply(preferences, SurfacePresets.presets().get(0));
        assertFalse(SurfacePresets.matches(preferences, custom));
        assertEquals(21, SurfacePresets.custom(preferences).values
            .get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));

        SurfacePresets.apply(preferences, SurfacePresets.custom(preferences));
        assertEquals(21, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));
        assertEquals(63,
            preferences.getSurfaceOverrideValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN));
    }

    /** Nothing saved is not an empty look: the card has to be able to tell those apart. */
    @Test
    public void thereIsNoCustomPresetUntilOneIsSaved() {
        assertNull(SurfacePresets.custom(preferences));
        assertNull(SurfacePresets.deserialize(""));
        assertNull(SurfacePresets.deserialize("not json"));
    }

    /** JSON widens ints on the way out; a look that read back as Long would not apply. */
    @Test
    public void aStoredLookReadsBackAsTheTypesTheFormatUses() {
        SurfacePresets.saveCustom(preferences);
        java.util.Map<String, Object> look =
            SurfacePresets.deserialize(preferences.getSurfaceCustomPreset());
        assertNotNull(look);
        assertTrue(look.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY) instanceof Integer);
        assertTrue(look.get(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED) instanceof Boolean);
        assertTrue(look.get(TERMUX_APP.KEY_SURFACE_MATERIAL) instanceof String);
    }

    @Test
    public void everyPresetCarriesTheKeysItsCardRenders() {
        // The device-mock cards read these five directly; a preset omitting one would silently
        // fall back to a hardcoded number and the card would lie about the look it applies.
        String[] rendered = {
            TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE,
            TERMUX_APP.KEY_SURFACE_BASE_OPACITY,
            TERMUX_APP.KEY_SURFACE_BASE_GRAIN,
            TERMUX_APP.KEY_SURFACE_BASE_CORNER_RADIUS,
            TERMUX_APP.KEY_SURFACE_BASE_SIDE_GAP,
        };
        for (SurfacePresets.Preset preset : SurfacePresets.presets()) {
            for (String key : rendered)
                assertTrue(preset.id + " misses " + key, preset.values.containsKey(key));
            assertTrue(preset.id + " misses the border switch",
                preset.values.containsKey(TERMUX_APP.KEY_TERMINAL_BORDER_ENABLED));
        }
    }
}
