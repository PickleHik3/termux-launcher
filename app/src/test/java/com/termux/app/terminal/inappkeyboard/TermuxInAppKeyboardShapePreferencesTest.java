package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;

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
 * The two global values a floating or split keyboard is measured from: how wide the float is and
 * how far a split parts. Both are kept per orientation with a default of their own, so a portrait
 * keyboard never inherits a fraction that only makes sense across a landscape screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxInAppKeyboardShapePreferencesTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        store = context.getSharedPreferences(
            "in-app-keyboard-shape-" + System.nanoTime(), Context.MODE_PRIVATE);
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private void landscape() {
        RuntimeEnvironment.setQualifiers("+land");
    }

    @Test
    public void aFreshInstallFloatsNarrowInLandscapeAndWideInPortrait() {
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);
        landscape();
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE_LANDSCAPE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);
    }

    @Test
    public void aFreshInstallPartsWiderInLandscapeThanInPortrait() {
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);
        landscape();
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION_LANDSCAPE,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);
    }

    /** The whole point of two keys: setting one orientation must not move the other. */
    @Test
    public void eachOrientationKeepsItsOwnFloatingWidth() {
        preferences.setInAppKeyboardFloatingWidthScale(0.8f);
        landscape();
        assertEquals("landscape still at its own default",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE_LANDSCAPE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);

        preferences.setInAppKeyboardFloatingWidthScale(0.5f);
        assertEquals(0.5f, preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);
        assertEquals(0.5f, store.getFloat(
            TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE_LANDSCAPE, 0f), 1e-4f);
        assertEquals("portrait kept what it was set to", 0.8f, store.getFloat(
            TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE, 0f), 1e-4f);
    }

    @Test
    public void eachOrientationKeepsItsOwnSplitGap() {
        preferences.setInAppKeyboardSplitGapFraction(0.1f);
        landscape();
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION_LANDSCAPE,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);

        preferences.setInAppKeyboardSplitGapFraction(0.3f);
        assertEquals(0.3f, preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);
        assertEquals(0.1f, store.getFloat(
            TERMUX_APP.KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION, 0f), 1e-4f);
    }

    @Test
    public void bothValuesAreClampedOnTheWayInAndTheWayOut() {
        preferences.setInAppKeyboardFloatingWidthScale(4f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);
        preferences.setInAppKeyboardFloatingWidthScale(0.01f);
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);

        preferences.setInAppKeyboardSplitGapFraction(0.9f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);
        preferences.setInAppKeyboardSplitGapFraction(-1f);
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);
    }

    /** A hand-edited or corrupted value is a value nobody can type; the default stands. */
    @Test
    public void anUnusableStoredValueFallsBackToTheOrientationsDefault() {
        store.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            Float.NaN).commit();
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);

        store.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
            Float.POSITIVE_INFINITY).commit();
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_SPLIT_GAP_FRACTION,
            preferences.getInAppKeyboardSplitGapFraction(), 1e-4f);

        store.edit().putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE, -3f).commit();
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_FLOATING_WIDTH_SCALE,
            preferences.getInAppKeyboardFloatingWidthScale(), 1e-4f);
    }

    /** The Keyboard page's sliders are whole percentages of the fraction the store holds. */
    @Test
    public void thePercentagesTheSlidersShowRoundTripThroughTheStore() {
        for (int percent : new int[] {35, 50, 60, 90, 100}) {
            preferences.setInAppKeyboardFloatingWidthScale(percent / 100f);
            assertEquals(percent,
                Math.round(preferences.getInAppKeyboardFloatingWidthScale() * 100f));
        }
        for (int percent : new int[] {0, 12, 25, 45}) {
            preferences.setInAppKeyboardSplitGapFraction(percent / 100f);
            assertEquals(percent,
                Math.round(preferences.getInAppKeyboardSplitGapFraction() * 100f));
        }
    }
}
