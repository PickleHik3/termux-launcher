package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
 * The editor's Undo and Discard: everything the Appearance editor writes comes back exactly,
 * link shape and absent keys included, and the dirty signature sees every one of them (SPEC §4:
 * tint, rim, motion and depth too).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceSnapshotTest {

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("appearance-snapshot-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void undoPutsBackEveryValueTheEditorWrites() {
        SurfacePresets.apply(preferences, SurfacePresets.presets().get(1));
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);
        String signature = entry.signature();

        // Everything row 1 and row 2 can move.
        SurfacePresets.apply(preferences, SurfacePresets.presets().get(3));
        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED);
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 81);
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_OBSIDIAN);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setInAppKeyboardKeyOpacity(33);
        preferences.setInAppKeyboardKeyCornerRadiusDp(13f);
        preferences.setWallpaperBackdropDim(60);
        SoftWallpaper.set(preferences, true);
        preferences.setTerminalContrastLevel("harder");
        // Layout mode's Corners and Margin.
        int corners = preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS);
        int sideGap = preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP);
        int terminalCorners = preferences.getTerminalCornerRadius();
        int paneGap = preferences.getTerminalPaneGap();
        preferences.setSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS, corners + 7);
        preferences.setTerminalCornerRadius(terminalCorners + 7);
        preferences.setSurfaceBaseValue(SurfaceProperty.SIDE_GAP, sideGap + 5);
        preferences.setTerminalPaneGap(paneGap + 5);
        assertNotEquals(signature, AppearanceSnapshot.signatureOf(preferences));

        entry.restore(preferences);

        assertEquals(signature, AppearanceSnapshot.signatureOf(preferences));
        assertEquals(corners, preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(sideGap, preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP));
        assertEquals(terminalCorners, preferences.getTerminalCornerRadius());
        assertEquals(paneGap, preferences.getTerminalPaneGap());
        assertTrue(SurfacePresets.matches(preferences, SurfacePresets.presets().get(1)));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertEquals(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DEFAULT,
            preferences.getAppLauncherDockStyle());
        assertEquals(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY,
            preferences.getInAppKeyboardKeyOpacity());
        assertEquals(0, preferences.getWallpaperBackdropDim());
        assertFalse(SoftWallpaper.isOn(preferences));
        assertEquals("default", preferences.getTerminalContrastLevel().value);
    }

    /** A key radius the theme owned (no key at all) comes back as no key, not today's default. */
    @Test
    public void anAbsentKeyRadiusComesBackAbsent() {
        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP));
        AppearanceSnapshot entry = AppearanceSnapshot.capture(preferences);

        preferences.setInAppKeyboardKeyCornerRadiusDp(7f);
        entry.restore(preferences);

        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP));
    }

    @Test
    public void theSignatureSeesTintRimMotionAndDepth() {
        String before = AppearanceSnapshot.signatureOf(preferences);
        preferences.setSurfaceGlassRim("gradient");
        assertNotEquals(before, AppearanceSnapshot.signatureOf(preferences));

        before = AppearanceSnapshot.signatureOf(preferences);
        preferences.setSurfaceGlassMotion(TERMUX_APP.GLASS_MOTION_MIST);
        assertNotEquals(before, AppearanceSnapshot.signatureOf(preferences));

        before = AppearanceSnapshot.signatureOf(preferences);
        preferences.setFancierGlassBendDp(preferences.getFancierGlassBendDp() + 3);
        assertNotEquals(before, AppearanceSnapshot.signatureOf(preferences));
    }
}
