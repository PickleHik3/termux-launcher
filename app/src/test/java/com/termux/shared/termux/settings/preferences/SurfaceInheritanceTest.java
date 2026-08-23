package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

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
 * Surface inheritance: Base → per-surface override → built-in default.
 *
 * <p>The resolution runs inside the preference object, so every render path in the app reads a
 * resolved number without knowing inheritance exists. These cases pin that contract, and in
 * particular pin the migration — an existing install has to come out the other side looking
 * pixel-identical, or the upgrade silently restyles someone's launcher.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceInheritanceTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-inheritance-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private void putRaw(String key, int value) {
        store.edit().putInt(key, value).commit();
    }

    // ---------------------------------------------------------------- the link

    @Test
    public void freshInstall_everySurfaceFollowsBase() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                assertTrue(slot + "/" + property,
                    preferences.isSurfaceInheriting(slot, property));
            }
            assertEquals(slot.toString(), 0, preferences.surfaceOverrideCount(slot));
        }
    }

    @Test
    public void movingBase_movesEveryFollowingSurface() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 21);
        assertEquals(21, preferences.getExtraKeysBlurRadius());
        assertEquals(21, preferences.getStatusBarBlurRadius());
        assertEquals(21, preferences.getTerminalGlassBlurRadius());

        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 64);
        assertEquals(64, preferences.getAppBarOpacity());
        assertEquals(64, preferences.getStatusBarOpacity());
        assertEquals(64, preferences.getTerminalBackgroundOpacity());
        assertEquals(64, preferences.getInAppKeyboardBackgroundOpacity());
    }

    @Test
    public void detachingOneProperty_leavesTheOthersOnBase() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 12);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 28);

        assertEquals("the detached one holds its own", 28, preferences.getStatusBarBlurRadius());
        assertEquals("its neighbours still follow", 12, preferences.getExtraKeysBlurRadius());
        assertEquals(12, preferences.getTerminalGlassBlurRadius());

        // ...and Base still moves everyone who is still following it.
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 18);
        assertEquals(28, preferences.getStatusBarBlurRadius());
        assertEquals(18, preferences.getExtraKeysBlurRadius());
    }

    @Test
    public void detachingBlur_doesNotDetachOpacity() {
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 4);
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.BLUR));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.OPACITY));
        assertEquals(1, preferences.surfaceOverrideCount(SurfaceSlot.DOCK));
    }

    @Test
    public void reattaching_putsTheSurfaceBackOnBaseAndForgetsNothingElse() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 30);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, 90);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.OPACITY, 15);
        assertEquals(2, preferences.surfaceOverrideCount(SurfaceSlot.STATUS));

        preferences.setSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.GRAIN, true);
        assertEquals(30, preferences.getStatusBarGrain());
        assertEquals("the other override survives", 15, preferences.getStatusBarOpacity());
        assertEquals(1, preferences.surfaceOverrideCount(SurfaceSlot.STATUS));

        preferences.reattachSurface(SurfaceSlot.STATUS);
        assertEquals(0, preferences.surfaceOverrideCount(SurfaceSlot.STATUS));
    }

    // ---------------------------------------------------------------- writes respect the link

    @Test
    public void settingASurfaceValueWhileLinked_movesBaseRatherThanDetaching() {
        // What keeps the Settings sliders and the wallpaper-mode policy from quietly detaching a
        // surface just by writing to it.
        preferences.setStatusBarBlurRadius(9);
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertEquals(9, preferences.getExtraKeysBlurRadius());
        assertEquals(9, preferences.getStatusBarBlurRadius());
    }

    @Test
    public void settingASurfaceValueWhileDetached_movesOnlyThatSurface() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 25);
        preferences.setStatusBarBlurRadius(3);
        assertEquals(3, preferences.getStatusBarBlurRadius());
        assertEquals(10, preferences.getExtraKeysBlurRadius());
    }

    // ---------------------------------------------------------------- per-surface property sets

    @Test
    public void keyboardHasNoGlassOfItsOwn() {
        // It renders on the dock's material, so blur/grain/radius rows would control nothing.
        assertFalse(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR));
        assertFalse(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN));
        assertFalse(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.KEYBOARD, SurfaceProperty.CORNER_RADIUS));
        assertTrue(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY));
        assertTrue(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP));

        // A property a slot does not have can never be detached, so it never shows in the badge.
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR, 20);
        assertEquals(0, preferences.surfaceOverrideCount(SurfaceSlot.KEYBOARD));
    }

    @Test
    public void canvasHasNoCapsuleRadiusAndNoScreenEdgeGap() {
        assertFalse(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.CANVAS, SurfaceProperty.CORNER_RADIUS));
        assertFalse(TermuxAppSharedPreferences.hasSurfaceProperty(
            SurfaceSlot.CANVAS, SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void anInheritedValueNarrowsToTheSurfacesOwnCeiling() {
        // The terminal pane tops out at 30dp of blur; a larger Base must clamp there rather than
        // leak a value the pane cannot render.
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 30);
        assertEquals(30, preferences.getTerminalGlassBlurRadius());
        assertEquals(30, preferences.getExtraKeysBlurRadius());
    }

    // ---------------------------------------------------------------- migration

    @Test
    public void migration_keepsAnInstallWhoseSurfacesAlreadyAgreed() {
        putRaw(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 16);
        putRaw(TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, 16);
        putRaw(TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS, 16);

        preferences.migrateSurfaceInheritance();

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.BLUR));
        assertEquals(16, preferences.getExtraKeysBlurRadius());
        assertEquals(16, preferences.getStatusBarBlurRadius());
        assertEquals(16, preferences.getTerminalGlassBlurRadius());
    }

    @Test
    public void migration_startsADifferingSurfaceDetachedSoTheLookSurvives() {
        putRaw(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 10);
        putRaw(TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, 27);

        preferences.migrateSurfaceInheritance();

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.BLUR));
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertEquals("every stored number survives untouched", 10,
            preferences.getExtraKeysBlurRadius());
        assertEquals(27, preferences.getStatusBarBlurRadius());
    }

    @Test
    public void migration_linksEverythingWhenTheOldMatchAllSwitchWasOn() {
        store.edit().putBoolean(TERMUX_APP.KEY_SURFACE_TUNING_NORMALIZED, true).commit();
        putRaw(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 14);
        // Stale differing values are what the old switch was overriding anyway.
        putRaw(TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, 3);

        preferences.migrateSurfaceInheritance();

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertEquals(14, preferences.getStatusBarBlurRadius());
    }

    @Test
    public void migration_keepsAKeyboardGapTheUserActuallyChose() {
        // The keyboard shipped 13dp against everyone else's 10dp. A value the user stored has to
        // survive the upgrade, so migration sees the difference and starts that pair detached.
        putRaw(TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET);

        preferences.migrateSurfaceInheritance();

        assertFalse(preferences.isSurfaceInheriting(
            SurfaceSlot.KEYBOARD, SurfaceProperty.SIDE_GAP));
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET,
            preferences.getInAppKeyboardHorizontalInset());
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET,
            preferences.getDockHorizontalInset());
    }

    @Test
    public void migration_leavesAnUntouchedSurfaceLinked() {
        // Nothing stored means no opinion to preserve. Without this an untouched keyboard would
        // start detached purely because its shipped default differed, and a brand-new install
        // would open with override badges already lit.
        preferences.migrateSurfaceInheritance();

        for (SurfaceSlot slot : SurfaceSlot.values())
            assertEquals(slot.toString(), 0, preferences.surfaceOverrideCount(slot));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET,
            preferences.getInAppKeyboardHorizontalInset());
    }

    @Test
    public void migration_runsOnce() {
        putRaw(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 11);
        preferences.migrateSurfaceInheritance();
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR, 29);

        // A second pass must not re-baseline and swallow the user's later override.
        preferences.migrateSurfaceInheritance();

        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertEquals(29, preferences.getStatusBarBlurRadius());
    }
}
