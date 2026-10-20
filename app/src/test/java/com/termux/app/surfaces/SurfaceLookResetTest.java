package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

/**
 * Choosing a Look on the editor's slider ({@link SurfacePresets#applyLook}) puts every surface
 * back on it: the keyboard card's Glass, Opacity, Tint and Keys included, and every Glass slider
 * position. Custom's contract is unchanged: this visit's Custom values come back on the move to
 * Custom ({@link SurfaceEditorController.SessionCustom}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceLookResetTest {

    private static final int KEYBOARD_GLASS = 90;
    private static final int KEYBOARD_OPACITY = 81;
    private static final int KEYBOARD_TINT = 66;
    private static final int KEYS = 37;
    private static final int DOCK_GLASS = 5;

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;
    private SurfacePresets.Preset clear;
    private SurfacePresets.Preset mist;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-look-reset-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        clear = AppearanceLooks.presetForStop(SurfacePresets.presets(), 0);
        mist = AppearanceLooks.presetForStop(SurfacePresets.presets(), 1);
        assertNotNull(clear);
        assertNotNull(mist);
    }

    @Test
    public void clearResetsEveryKeyboardOverrideToClears() {
        SurfacePresets.applyLook(preferences, mist);
        overrideTheKeyboardAndTheDock();
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.leave(preferences);
        SurfacePresets.applyLook(preferences, clear);

        assertOnClear();
    }

    @Test
    public void aDragsBatchResetsThemToo() {
        SurfacePresets.applyLook(preferences, mist);
        overrideTheKeyboardAndTheDock();
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);

        SurfacePresets.applyLook(SurfacePresets.writingInto(preferences, batch), clear);
        // Nothing stored until the release.
        assertEquals(KEYS, preferences.getInAppKeyboardKeyOpacity());
        batch.applyToStore();

        assertOnClear();
    }

    @Test
    public void customBringsThisVisitsOverridesBack() {
        SurfacePresets.applyLook(preferences, clear);
        overrideTheKeyboardAndTheDock();
        String custom = AppearanceSnapshot.signatureOf(preferences);
        SurfaceEditorController.SessionCustom session = new SurfaceEditorController.SessionCustom();

        session.leave(preferences);
        SurfacePresets.applyLook(preferences, clear);
        assertOnClear();
        session.enter(preferences);

        assertEquals(custom, AppearanceSnapshot.signatureOf(preferences));
        assertEquals(KEYS, preferences.getInAppKeyboardKeyOpacity());
        assertEquals(KEYBOARD_GLASS, preferences.getSurfaceGlassPosition(SurfaceSlot.KEYBOARD));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertEquals(KEYBOARD_OPACITY, preferences.getInAppKeyboardBackgroundOpacity());
    }

    @Test
    public void theMigrationsApplyStillLeavesKeysAlone() {
        SurfacePresets.applyLook(preferences, clear);
        preferences.setInAppKeyboardKeyOpacity(KEYS);

        SurfacePresets.apply(preferences, clear);

        assertEquals(KEYS, preferences.getInAppKeyboardKeyOpacity());
    }

    /** What Look › Custom's keyboard and dock cards write, through the same setters. */
    private void overrideTheKeyboardAndTheDock() {
        preferences.setSurfaceGlassPosition(SurfaceSlot.KEYBOARD, KEYBOARD_GLASS);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR,
            GlassControls.blurForGlass(KEYBOARD_GLASS));
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN,
            GlassControls.grainForGlass(KEYBOARD_GLASS));
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY,
            KEYBOARD_OPACITY);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, KEYBOARD_TINT);
        preferences.setInAppKeyboardKeyOpacity(KEYS);
        preferences.setSurfaceGlassPosition(SurfaceSlot.DOCK, DOCK_GLASS);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR,
            GlassControls.blurForGlass(DOCK_GLASS));
        preferences.setSurfaceGlassPosition(null, 55);
        assertFalse(SurfacePresets.matches(preferences, clear));
    }

    private void assertOnClear() {
        assertTrue(SurfacePresets.matches(preferences, clear));
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values())
                assertTrue(slot + " " + property, preferences.isSurfaceInheriting(slot, property));
            assertEquals(slot.toString(), -1, preferences.getSurfaceGlassPosition(slot));
        }
        assertEquals(-1, preferences.getSurfaceGlassPosition(null));
        int clearBlur = (Integer) clear.values.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR);
        int clearGrain = (Integer) clear.values.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN);
        int clearOpacity = (Integer) clear.values.get(TERMUX_APP.KEY_SURFACE_BASE_OPACITY);
        int clearTint = (Integer) clear.values.get(TERMUX_APP.KEY_SURFACE_BASE_TINT);
        assertEquals(clearBlur, preferences.getInAppKeyboardBlurRadius());
        assertEquals(clearGrain, preferences.getInAppKeyboardGrain());
        assertEquals(clearOpacity, preferences.getInAppKeyboardBackgroundOpacity());
        assertEquals(clearTint, preferences.getInAppKeyboardTintStrength());
        assertEquals(clearBlur, preferences.getExtraKeysBlurRadius());
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY,
            preferences.getInAppKeyboardKeyOpacity());
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS,
            preferences.getInAppKeyboardBlurRadiusRaw());
        // The Glass sliders read Clear's position back from the glass they now draw.
        assertEquals(GlassControls.CLEAR, GlassControls.readBack(
            preferences.getSurfaceGlassPosition(SurfaceSlot.KEYBOARD),
            preferences.getInAppKeyboardBlurRadius(), preferences.getInAppKeyboardGrain()));
        assertEquals(clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY),
            preferences.getSurfaceGlassVibrancy());
        assertEquals(clear.values.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION),
            preferences.getFancierGlassDispersionPercent());
        assertEquals(clear.values.get(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE),
            preferences.getSurfaceGlassTintHue());
    }
}
