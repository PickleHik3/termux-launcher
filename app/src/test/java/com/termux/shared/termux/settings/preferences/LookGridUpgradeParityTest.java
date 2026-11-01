package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/**
 * An install stored the old way (Base, own keys, inherit flags, {@code @<page>} values) answers
 * the same through the named getters after the grid upgrade, on the wall and on every page. The
 * expected numbers are worked out by hand from the old rule (a page's own value, else Base while
 * the flag holds, else the surface's own); blur alone answers its nearest Frost stop's blur.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LookGridUpgradeParityTest {

    private static final int WALL = TermuxAppSharedPreferences.PAGE_WALL;
    private static final int[] WHERE = {WALL, 0, 5};

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("look-grid-parity-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        // An install every earlier upgrade has already brought along.
        store.edit()
            .putBoolean(TERMUX_APP.KEY_SHIPPED_SURFACE_DEFAULTS_ADOPTED, true)
            .putBoolean(TERMUX_APP.KEY_LAYOUT_STYLE_MIGRATED, true)
            .putBoolean(TERMUX_APP.KEY_SURFACE_INHERITANCE_MIGRATED, true)
            .putBoolean(TERMUX_APP.KEY_KEYBOARD_GLASS_SLOT_FOLDED, true)
            .putBoolean(TERMUX_APP.KEY_KEYBOARD_OPACITY_SENTINEL_HEALED, true)
            .commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private void upgrade() {
        preferences.migrateLookGrid(Arrays.asList(0, 5));
        // Then step 17: page 0 is the Start-in Home page, page 5 the Terminal page, and each
        // page reads its kind's cells from here on.
        foldPageCellsThroughStep17(store, 0, 5);
        LookGrid.install(page -> page == 0 ? LookGrid.Kind.HOME
            : page == 5 ? LookGrid.Kind.TERMINAL : null, null);
    }

    /**
     * The layouts upgrade's look fold (step 17, ADR 0020 §2) over the test store: the Start-in
     * Home page's and the Terminal page's @<page> cells become their kinds' cells.
     */
    private static void foldPageCellsThroughStep17(android.content.SharedPreferences store,
                                                   int homePageId, int terminalPageId) {
        android.content.SharedPreferences.Editor editor = store.edit();
        for (java.util.Map.Entry<String, Object> cell : LookGrid.foldedPageCells(store.getAll(),
                homePageId, terminalPageId).entrySet())
            editor.putInt(cell.getKey(), ((Number) cell.getValue()).intValue());
        editor.commit();
    }

    /** The grid's page kinds are process-wide: none leaks into the next test class. */
    @org.junit.After
    public void resetLookGridPageKinds() {
        LookGrid.install(null, null);
    }

    @Test
    public void aTunedWallWithADetachedDockAndAPageOverrideAnswersAsBefore() {
        store.edit()
            .putInt("surface_base_blur", 12).putInt("surface_base_grain", 4)
            .putInt("surface_base_opacity", 40).putInt("surface_base_tint", 20)
            .putBoolean("surface_inherit_dock_opacity", false)
            .putInt(TERMUX_APP.KEY_APP_BAR_OPACITY, 70)
            .putInt(TERMUX_APP.KEY_STATUS_BAR_OPACITY + "@5", 12)
            .putBoolean("surface_inherit_canvas_blur", false)
            .putInt(TERMUX_APP.KEY_TERMINAL_GLASS_BLUR_RADIUS, 25)
            .putInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, 120)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 27)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION, 40)
            .putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE, TERMUX_APP.GLASS_TINT_HUE_SECONDARY)
            .putString(TERMUX_APP.KEY_SURFACE_GLASS_RIM, TERMUX_APP.GLASS_RIM_GRADIENT)
            .putInt(TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS, 16)
            .putBoolean(TERMUX_APP.KEY_WIDGETS_ON_GLASS, false)
            .commit();
        upgrade();

        for (int page : WHERE) {
            String at = "page " + page;
            assertEquals(at, 70, preferences.getAppBarOpacity(page));
            assertEquals(at, page == 5 ? 12 : 40, preferences.getStatusBarOpacity(page));
            assertEquals(at, TermuxAppSharedPreferences.clampInAppKeyboardBackgroundOpacity(40),
                preferences.getInAppKeyboardBackgroundOpacity(page));
            // Blur 12 is Tint's stop: exact.
            assertEquals(at, 12, preferences.getExtraKeysBlurRadius(page));
            assertEquals(at, 12, preferences.getStatusBarBlurRadius(page));
            assertEquals(at, 4, preferences.getDockGlassGrain(page));
            assertEquals(at, 4, preferences.getStatusBarGrain(page));
            assertEquals(at, 20, preferences.getDockTintStrength(page));
            assertEquals(at, 20, preferences.getStatusBarTintStrength(page));
        }
        // The terminal's own blur 25 is Mist's stop; its grain still follows Base's 4.
        assertEquals(25, preferences.getTerminalGlassBlurRadius());
        assertEquals(4, preferences.getTerminalGlassGrain());
        assertEquals(20, preferences.getTerminalTintStrength());
        assertEquals(120, preferences.getSurfaceGlassVibrancy());
        assertEquals(27, preferences.getFancierGlassBendDp());
        assertEquals(40, preferences.getFancierGlassDispersionPercent());
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_SECONDARY, preferences.getSurfaceGlassTintHue());
        assertEquals(TERMUX_APP.GLASS_RIM_GRADIENT, preferences.getSurfaceGlassRim());
        assertEquals(16, preferences.getAppLauncherDrawerCornerRadius());
        assertFalse(preferences.isWidgetsOnGlassEnabled());
        assertFalse(store.contains(TERMUX_APP.KEY_APP_BAR_OPACITY));
        assertFalse(store.contains("surface_inherit_dock_opacity"));
    }

    @Test
    public void aBaseOffEveryStopMovesBlurToTheNearestStopAndKeepsTheRest() {
        store.edit().putInt("surface_base_blur", 8).putInt("surface_base_grain", 18).commit();
        upgrade();
        for (int page : WHERE) {
            String at = "page " + page;
            // Blur 8 sits on no stop: Clear's 7 is nearest.
            assertEquals(at, 7, preferences.getExtraKeysBlurRadius(page));
            assertEquals(at, 7, preferences.getStatusBarBlurRadius(page));
            assertEquals(at, 18, preferences.getDockGlassGrain(page));
            assertEquals(at, 18, preferences.getInAppKeyboardGrain(page));
            assertEquals(at, TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY,
                preferences.getAppBarOpacity(page));
        }
        assertEquals(18, preferences.getTerminalGlassGrain());
    }

    @Test
    public void aDetachedKeyboardBlurBetweenStopsTakesTheNearestStop() {
        // Blur 9 is Glass 30, between Clear (7) and Tint (12): 7 is nearer.
        store.edit()
            .putInt("surface_base_blur", 12).putInt("surface_base_grain", 4)
            .putBoolean("surface_inherit_keyboard_blur", false)
            .putInt(TERMUX_APP.KEY_IN_APP_KEYBOARD_BLUR_RADIUS, 9)
            .commit();
        upgrade();
        for (int page : WHERE) {
            assertEquals(7, preferences.getInAppKeyboardBlurRadius(page));
            assertEquals(4, preferences.getInAppKeyboardGrain(page));
            assertEquals(12, preferences.getExtraKeysBlurRadius(page));
        }
    }
}
