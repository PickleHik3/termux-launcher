package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxInAppKeyboardHeightPreferenceTest {

    private SharedPreferences mStore;
    private TermuxAppSharedPreferences mPreferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mStore = context.getSharedPreferences(
            "in-app-keyboard-height-" + System.nanoTime(), Context.MODE_PRIVATE);
        mPreferences = new TermuxAppSharedPreferences(context, mStore, null);
    }

    @Test
    public void setterPersistsClampedHeightScale() {
        mPreferences.setInAppKeyboardHeightScale(2.5f);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
            mStore.getFloat(
                TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 0f),
            0.0001f);

        mPreferences.setInAppKeyboardHeightScale(0.1f);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            mPreferences.getInAppKeyboardHeightScale(), 0.0001f);

        mPreferences.setInAppKeyboardHeightScale(Float.NaN);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            mPreferences.getInAppKeyboardHeightScale(), 0.0001f);
    }

    @Test
    public void getterClampsInvalidStoredValues() {
        mStore.edit().putFloat(
            TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            -12f).commit();
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            mPreferences.getInAppKeyboardHeightScale(), 0.0001f);

        mStore.edit().putFloat(
            TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE,
            Float.POSITIVE_INFINITY).commit();
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE,
            mPreferences.getInAppKeyboardHeightScale(), 0.0001f);
    }

    @Test
    public void keyMarginScaleIsClampedOnSetAndGet() {
        mPreferences.setInAppKeyboardKeyMarginScale(12f);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            mStore.getFloat(
                TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE, -1f),
            0.0001f);

        mStore.edit().putFloat(
            TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            -3f).commit();
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            mPreferences.getInAppKeyboardKeyMarginScale(), 0.0001f);

        mPreferences.setInAppKeyboardKeyMarginScale(Float.NaN);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            mPreferences.getInAppKeyboardKeyMarginScale(), 0.0001f);
    }

    @Test
    public void keyCornerRadiusUsesPaletteSentinelAndClampsOverrides() {
        assertEquals(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);

        mPreferences.setInAppKeyboardKeyCornerRadiusDp(42f);
        assertEquals(
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);

        mPreferences.setInAppKeyboardKeyCornerRadiusDp(-4f);
        assertEquals(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            mStore.getFloat(
                TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
                0f),
            0.0001f);

        mStore.edit().putFloat(
            TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            Float.POSITIVE_INFINITY).commit();
        assertEquals(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP,
            mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);
    }
}
