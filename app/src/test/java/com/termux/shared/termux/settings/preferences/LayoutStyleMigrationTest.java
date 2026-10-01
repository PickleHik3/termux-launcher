package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;
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
 * The Style rework's one-time migration: the stored style becomes {@code docked} / {@code floating},
 * the per-surface corner and side-gap overrides and {@code terminal_flush_dock} go, and the typed
 * accessor and every writer speak the new values.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LayoutStyleMigrationTest {

    private static final String LEGACY_FLUSH_DOCK = "terminal_flush_dock";

    private TermuxAppSharedPreferences preferences;
    private SharedPreferences store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("layout-style-migration-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private void putStyle(String value) {
        store.edit().putString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, value).commit();
    }

    // ---------------------------------------------------------------- the style values

    @Test
    public void oldDefaultBecomesDocked() {
        putStyle("default");

        preferences.migrateSurfaceInheritance();

        assertEquals("docked", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
        assertEquals(LayoutStyle.DOCKED, preferences.getLayoutStyle());
    }

    @Test
    public void oldRoundedBecomesFloating() {
        putStyle("rounded");

        preferences.migrateSurfaceInheritance();

        assertEquals("floating", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
        assertEquals(LayoutStyle.FLOATING, preferences.getLayoutStyle());
    }

    @Test
    public void theLongGoneCapsuleValueBecomesFloating() {
        putStyle("valarie_capsule");

        preferences.migrateSurfaceInheritance();

        assertEquals("floating", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
    }

    @Test
    public void anInstallThatNeverChoseAStyleStaysWithoutOneAndReadsDocked() {
        store.edit().putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 10).commit();

        preferences.migrateSurfaceInheritance();

        assertFalse(store.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE));
        assertEquals(LayoutStyle.DOCKED, preferences.getLayoutStyle());
    }

    @Test
    public void aFreshInstallMigratesWithoutIncident() {
        preferences.migrateSurfaceInheritance();

        assertEquals(LayoutStyle.DOCKED, preferences.getLayoutStyle());
        assertEquals("docked", preferences.getAppLauncherDockStyle());
    }

    // ---------------------------------------------------------------- the retired keys

    @Test
    public void perSurfaceCornerAndSideGapOverridesAreDroppedForBase() {
        store.edit()
            .putInt(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS, 30)
            .putInt(TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS, 17)
            .putInt(TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, 6)
            .putInt(TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET, 20)
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET, 14)
            .commit();

        preferences.migrateSurfaceInheritance();

        assertFalse(store.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS));
        assertFalse(store.contains(TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS));
        assertFalse(store.contains(TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET));
        assertFalse(store.contains(TERMUX_APP.KEY_STATUS_BAR_HORIZONTAL_INSET));
        assertFalse(store.contains(TERMUX_APP.KEY_IN_APP_KEYBOARD_HORIZONTAL_INSET));
        for (String key : store.getAll().keySet()) {
            assertFalse(key, key.startsWith(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX)
                && (key.endsWith("_corner_radius") || key.endsWith("_side_gap")));
        }

        // Every surface reads the Base values now.
        int baseCorners = preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS);
        int baseGap = preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP);
        assertEquals(baseCorners, preferences.getAppLauncherDockCornerRadius());
        assertEquals(baseCorners, preferences.getStatusBarCornerRadius());
        assertEquals(baseGap, preferences.getDockHorizontalInset());
        assertEquals(baseGap, preferences.getStatusBarHorizontalInset());
        assertEquals(baseGap, preferences.getInAppKeyboardHorizontalInset());
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            assertTrue(slot.toString(),
                preferences.isSurfaceInheriting(slot, SurfaceProperty.CORNER_RADIUS));
            assertTrue(slot.toString(),
                preferences.isSurfaceInheriting(slot, SurfaceProperty.SIDE_GAP));
        }
    }

    @Test
    public void theOtherSurfaceOverridesSurvive() {
        putStyle("default");
        store.edit()
            .putInt(TERMUX_APP.KEY_EXTRAKEYS_BLUR_RADIUS, 10)
            .putInt(TERMUX_APP.KEY_STATUS_BAR_BLUR_RADIUS, 27)
            .commit();

        preferences.migrateSurfaceInheritance();

        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
        assertEquals(27, preferences.getStatusBarBlurRadius());
    }

    @Test
    public void terminalFlushDockIsRemoved() {
        putStyle("default");
        store.edit().putBoolean(LEGACY_FLUSH_DOCK, true).commit();

        preferences.migrateSurfaceInheritance();

        assertFalse(store.contains(LEGACY_FLUSH_DOCK));
        assertEquals(LEGACY_FLUSH_DOCK, TERMUX_APP.KEY_LEGACY_TERMINAL_FLUSH_DOCK);
    }

    @Test
    public void aDetachOfARetiredPropertyIsIgnored() {
        preferences.migrateSurfaceInheritance();

        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP, 3);
        preferences.setSurfaceValueExact(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS, 3);

        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.SIDE_GAP));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS,
            SurfaceProperty.CORNER_RADIUS));
        assertEquals(preferences.getSurfaceBaseValue(SurfaceProperty.SIDE_GAP),
            preferences.getDockHorizontalInset());
    }

    @Test
    public void aPerSurfaceSetterMovesBaseNowThatNothingCanDetach() {
        preferences.migrateSurfaceInheritance();

        preferences.setStatusBarCornerRadius(22);

        assertEquals(22, preferences.getSurfaceBaseValue(SurfaceProperty.CORNER_RADIUS));
        assertEquals(22, preferences.getAppLauncherDockCornerRadius());
        assertFalse(store.contains(TERMUX_APP.KEY_STATUS_BAR_CORNER_RADIUS));
    }

    // ---------------------------------------------------------------- once, and only once

    @Test
    public void aSecondRunChangesNothingAndLeavesALaterChoiceAlone() {
        putStyle("rounded");
        store.edit().putBoolean(LEGACY_FLUSH_DOCK, true).commit();

        preferences.migrateSurfaceInheritance();
        assertTrue(store.getBoolean(TERMUX_APP.KEY_LAYOUT_STYLE_MIGRATED, false));
        preferences.setLayoutStyle(LayoutStyle.DOCKED);
        preferences.migrateSurfaceInheritance();
        preferences.migrateLayoutStyle();

        assertEquals("docked", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
    }

    @Test
    public void theMigrationRunsAgainstAnInstallThatFoldedUnderAnEarlierBuild() {
        putStyle("rounded");
        store.edit()
            .putBoolean(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true)
            .putBoolean(TERMUX_APP.KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED, true)
            .putBoolean(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + "dock_side_gap", false)
            .putInt(TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET, 4)
            .commit();

        preferences.migrateSurfaceInheritance();

        assertEquals(LayoutStyle.FLOATING, preferences.getLayoutStyle());
        assertFalse(store.contains(TERMUX_APP.KEY_DOCK_HORIZONTAL_INSET));
        assertFalse(store.contains(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + "dock_side_gap"));
    }

    // ---------------------------------------------------------------- the accessor and writers

    @Test
    public void writersStoreTheNewValuesWhateverTheyAreGiven() {
        preferences.setAppLauncherDockStyle("rounded");
        assertEquals("floating", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));

        preferences.setAppLauncherDockStyle("default");
        assertEquals("docked", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));

        preferences.setLayoutStyle(LayoutStyle.FLOATING);
        assertEquals("floating", store.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, null));
        assertEquals(LayoutStyle.FLOATING, preferences.getLayoutStyle());

        preferences.setAppLauncherDockStyle(TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_DOCKED);
        assertEquals(LayoutStyle.DOCKED, preferences.getLayoutStyle());
    }

    @Test
    public void theTypedAccessorReadsOldAndNewValuesAndFallsBackToDocked() {
        assertEquals(LayoutStyle.DOCKED, LayoutStyle.parse(null));
        assertEquals(LayoutStyle.DOCKED, LayoutStyle.parse("nonsense"));
        assertEquals(LayoutStyle.DOCKED, LayoutStyle.parse("docked"));
        assertEquals(LayoutStyle.DOCKED, LayoutStyle.parse("default"));
        assertEquals(LayoutStyle.FLOATING, LayoutStyle.parse("floating"));
        assertEquals(LayoutStyle.FLOATING, LayoutStyle.parse("rounded"));
        assertEquals("docked", LayoutStyle.DOCKED.storageValue());
        assertEquals("floating", LayoutStyle.FLOATING.storageValue());
    }
}
