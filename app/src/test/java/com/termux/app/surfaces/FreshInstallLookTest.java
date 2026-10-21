package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
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
 * The Look a first run opens on (glass plan 2026-10-16, Round 0, option a): Clear, written whole,
 * so the stored edge sits on Depth's curve and the slider's first step is one step. A first run is
 * a store holding nothing but the log level (TermuxAppSharedPreferences#isFreshInstall); anything
 * else is an install that has been used, and its look is left exactly as it was. Each case runs
 * the start-up sequence TermuxApplication runs: the inheritance migration, then the re-adoption of
 * moved Looks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FreshInstallLookTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("fresh-install-look-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private void launch() {
        preferences.migrateSurfaceInheritance();
        SurfacePresets.readoptMovedLooks(preferences);
    }

    private static SurfacePresets.Preset clear() {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if ("minimal".equals(preset.id)) return preset;
        throw new AssertionError("Clear is a shipped Look");
    }

    private static int stored(String key) {
        return (Integer) clear().values.get(key);
    }

    @Test
    public void aFirstRunStoresClearsValues() {
        launch();

        assertEquals(stored(TERMUX_APP.KEY_SURFACE_BASE_BLUR),
            preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(stored(TERMUX_APP.KEY_SURFACE_BASE_OPACITY),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY));
        assertEquals(stored(TERMUX_APP.KEY_SURFACE_BASE_GRAIN),
            preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        assertEquals(stored(TERMUX_APP.KEY_FANCIER_GLASS_BEND), preferences.getFancierGlassBendDp());
        assertEquals(stored(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH),
            preferences.getFancierGlassEdgeWidthDp());
        assertEquals(stored(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION),
            preferences.getFancierGlassDispersionPercent());
        assertEquals(stored(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY),
            preferences.getFancierGlassClarityPercent());
        // Written, not read through from a default: a later change to the DEFAULT_* constants
        // cannot restyle this install.
        assertTrue(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_BEND));
        assertTrue(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH));
        assertTrue(SurfacePresets.matches(preferences, clear()));
    }

    @Test
    public void aFirstRunsDepthReadsClearsPositionAndItsFirstStepIsOneStep() {
        launch();

        int depth = GlassEdgeControls.depthFor(preferences.getFancierGlassBendDp(),
            preferences.getFancierGlassEdgeWidthDp());
        assertEquals("Clear's own position on the curve", 90, depth);
        assertEquals(GlassEdgeControls.depthFor(stored(TERMUX_APP.KEY_FANCIER_GLASS_BEND),
            stored(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH)), depth);
        // On the curve, so the position writes back exactly what is stored.
        assertEquals(preferences.getFancierGlassBendDp(), GlassEdgeControls.bendForDepth(depth));
        assertEquals(preferences.getFancierGlassEdgeWidthDp(), GlassEdgeControls.widthForDepth(depth));
        // One step down is a neighbour on the curve, not the jump from the off-curve 9/20.
        assertEquals(26, GlassEdgeControls.bendForDepth(depth - 1));
        assertEquals(17, GlassEdgeControls.widthForDepth(depth - 1));
    }

    @Test
    public void anInstallThatHasRunBeforeKeepsTheEdgeItHad() {
        // An install from before the shipped look: something other than the log level is stored,
        // and no edge key ever was. It reads the defaults it has always read.
        store.edit().putString("current_session", "a-session-from-a-previous-run").commit();

        launch();

        assertFalse(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_BEND));
        assertFalse(store.contains(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH));
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND, preferences.getFancierGlassBendDp());
        assertEquals(TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
            preferences.getFancierGlassEdgeWidthDp());
        assertFalse(SurfacePresets.matches(preferences, clear()));
    }

    @Test
    public void anInstallWithItsOwnAppearanceKeepsEveryValue() {
        store.edit()
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_BEND, 12)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH, 30)
            .putInt(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY, 5)
            .commit();

        launch();

        assertEquals(12, preferences.getFancierGlassBendDp());
        assertEquals(30, preferences.getFancierGlassEdgeWidthDp());
        assertEquals(5, preferences.getFancierGlassClarityPercent());
        assertFalse(SurfacePresets.matches(preferences, clear()));
    }

    @Test
    public void theFirstRunHappensOnce() {
        // Clear goes in on the first launch only; what the user does after that survives every
        // later launch.
        launch();
        preferences.setFancierGlassBendDp(15);
        preferences.setFancierGlassEdgeWidthDp(10);

        launch();

        assertEquals(15, preferences.getFancierGlassBendDp());
        assertEquals(10, preferences.getFancierGlassEdgeWidthDp());
    }
}
