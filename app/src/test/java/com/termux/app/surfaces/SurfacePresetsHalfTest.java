package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Looks across the two halves of the wall (ADR 0013): a saved look carries both halves where they
 * differ, one half's look applies to both, and a Look chosen on one half leaves the other's alone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfacePresetsHalfTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store =
            context.getSharedPreferences("surface-presets-half-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        com.termux.app.look.TerminalPageIdFixture.give(preferences);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    private static SurfacePresets.Preset saved(Map<String, Object> look) {
        return new SurfacePresets.Preset(SurfacePresets.CUSTOM_ID, 0,
            SurfacePresets.deserialize(SurfacePresets.serialize(look)));
    }

    @Test
    public void equalHalves_saveOneLook() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 33);
        Map<String, Object> look = SurfacePresets.captureLook(preferences);
        assertFalse(look.containsKey(SurfacePresets.KEY_HOME_LOOK));
        for (String key : look.keySet())
            assertFalse(key, key.endsWith("_home"));
    }

    @Test
    public void oldCustomWithHalfKeys_loadsHomesWallValuesAndTheWorkspacesOverrides() {
        Map<String, Object> look =
            new java.util.LinkedHashMap<>(SurfacePresets.captureLook(preferences));
        look.put(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_SURFACE_BASE_OPACITY, 70);
        look.put(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
            .TERMUX_APP.KEY_SURFACE_BASE_OPACITY + "_home", 15);
        look.put(SurfacePresets.KEY_HOME_LOOK, Boolean.TRUE);
        SurfacePresets.apply(preferences, saved(look));

        // Base is wall-wide: Home's value is the wall's, for both halves.
        assertEquals(15, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
        assertEquals(15, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY,
            WallHalf.WORKSPACE));
        // The dock follows Base: Home shows the wall's 15, the Terminal page keeps its 70.
        assertEquals(15, preferences.getAppBarOpacity(WallHalf.HOME));
        assertEquals(70, preferences.getAppBarOpacity(WallHalf.WORKSPACE));
        assertTrue(preferences.getSharedPreferences().contains("app_bar_opacity@"
            + com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID));
    }

    @Test
    public void differingHalves_saveAndLoadBoth() {
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE, 70);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME, 15);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.TINT, WallHalf.HOME, 60);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            WallHalf.WORKSPACE, 30);
        SurfacePresets.Preset look = saved(SurfacePresets.captureLook(preferences));
        assertTrue(SurfacePresets.hasHomeLook(look));

        // Somewhere else entirely, then the saved look back.
        SurfacePresets.apply(preferences, preset("solid"));
        SurfacePresets.apply(preferences, look);

        assertEquals(70, preferences.getAppBarOpacity(WallHalf.WORKSPACE));
        assertEquals(15, preferences.getAppBarOpacity(WallHalf.HOME));
        assertEquals(60, preferences.getDockTintStrength(WallHalf.HOME));
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.TINT,
            WallHalf.WORKSPACE));
        assertEquals(30, preferences.getStatusBarBlurRadius(WallHalf.WORKSPACE));
        // Home's cells are all named, so the status bar Home never detached follows Home's Base.
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            WallHalf.HOME));
        assertTrue(SurfacePresets.matches(preferences, look, WallHalf.WORKSPACE));
        assertTrue(SurfacePresets.matches(preferences, look, WallHalf.HOME));
    }

    @Test
    public void oneHalfsLook_appliesToBoth() {
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME, 40);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.WORKSPACE, 2);
        SurfacePresets.Preset home = saved(SurfacePresets.captureLook(preferences, WallHalf.HOME));
        assertFalse(SurfacePresets.hasHomeLook(home));

        SurfacePresets.apply(preferences, home);

        assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, WallHalf.HOME));
        assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN,
            WallHalf.WORKSPACE));
    }

    @Test
    public void lookOnOneHalf_leavesTheOtherAlone() {
        SurfacePresets.Preset clear = preset("minimal");
        SurfacePresets.Preset solid = preset("solid");
        SurfacePresets.apply(preferences, clear);

        SurfacePresets.applyLook(preferences, solid, WallHalf.WORKSPACE);

        assertTrue(SurfacePresets.matches(preferences, solid, WallHalf.WORKSPACE));
        assertFalse(SurfacePresets.matches(preferences, solid, WallHalf.HOME));
        assertEquals(solid.values.get(SurfaceProperty.OPACITY.baseKey),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.WORKSPACE));
        assertEquals(clear.values.get(SurfaceProperty.OPACITY.baseKey),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, WallHalf.HOME));
        // Today's callers read the Workspace's stop.
        assertTrue(SurfacePresets.matches(preferences, solid));
    }

    @Test
    public void lookOnHome_leavesTheTerminalAlone() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 88);

        SurfacePresets.applyLook(preferences, preset("stock"), WallHalf.HOME);

        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertEquals(88, preferences.getTerminalBackgroundOpacity());
    }
}
