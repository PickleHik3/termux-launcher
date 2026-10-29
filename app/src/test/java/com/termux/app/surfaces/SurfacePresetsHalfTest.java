package com.termux.app.surfaces;

import com.termux.app.wall.PaneWallPage;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

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
        assertEquals(15, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(15, preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId()));
        // The dock follows Base: Home shows the wall's 15, the Terminal page keeps its 70.
        assertEquals(15, preferences.getAppBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(70, preferences.getAppBarOpacity(TermuxAppSharedPreferences.terminalPageId()));
        assertTrue(preferences.getSharedPreferences().contains("app_bar_opacity@"
            + com.termux.app.look.TerminalPageIdFixture.TERMINAL_PAGE_ID));
    }

    @Test
    public void differingHalves_saveAndLoadBoth() {
        // Base is wall-wide, so the halves differ by the Terminal page's own values.
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL, 15);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.terminalPageId(), 70);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.TINT, TermuxAppSharedPreferences.PAGE_WALL, 60);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            TermuxAppSharedPreferences.terminalPageId(), 30);
        SurfacePresets.Preset look = saved(SurfacePresets.captureLook(preferences));
        assertTrue(SurfacePresets.hasHomeLook(look));

        // Somewhere else entirely, then the saved look back.
        SurfacePresets.apply(preferences, preset("solid"));
        SurfacePresets.apply(preferences, look);

        assertEquals(70, preferences.getAppBarOpacity(TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(15, preferences.getAppBarOpacity(TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(60, preferences.getDockTintStrength(TermuxAppSharedPreferences.PAGE_WALL));
        assertFalse("the detach is wall-wide, so the Terminal page holds it too", preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.TINT,
            TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(30, preferences.getStatusBarBlurRadius(TermuxAppSharedPreferences.terminalPageId()));
        // Home's cells are all named, so the status bar Home never detached follows Home's Base.
        assertTrue(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR,
            TermuxAppSharedPreferences.PAGE_WALL));
        assertTrue(SurfacePresets.matches(preferences, look, PaneWallPage.TERMINAL));
        assertTrue(SurfacePresets.matches(preferences, look, PaneWallPage.WIDGETS));
    }

    @Test
    public void oneHalfsLook_appliesToBoth() {
        // Base is wall-wide: the Workspace's 2 is replaced by Home's 40 on the one wall value.
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId(), 2);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL, 40);
        SurfacePresets.Preset home = saved(SurfacePresets.captureLook(preferences, PaneWallPage.WIDGETS));
        assertFalse(SurfacePresets.hasHomeLook(home));

        SurfacePresets.apply(preferences, home);

        assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(40, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN,
            TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void lookOnOneHalf_leavesTheOtherAlone() {
        SurfacePresets.Preset clear = preset("minimal");
        SurfacePresets.Preset solid = preset("solid");
        SurfacePresets.apply(preferences, clear);

        SurfacePresets.applyLook(preferences, solid, PaneWallPage.TERMINAL);

        assertTrue(SurfacePresets.matches(preferences, solid, PaneWallPage.TERMINAL));
        assertFalse(SurfacePresets.matches(preferences, solid, PaneWallPage.WIDGETS));
        // Base is the wall's, so the Look lands on the Workspace as the Terminal page's own
        // surface values: its dock shows Solid's number, Home's dock keeps Clear's.
        assertEquals(solid.values.get(SurfaceProperty.OPACITY.baseKey),
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                TermuxAppSharedPreferences.terminalPageId()));
        assertEquals(clear.values.get(SurfaceProperty.OPACITY.baseKey),
            preferences.getSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.OPACITY,
                TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(clear.values.get(SurfaceProperty.OPACITY.baseKey),
            preferences.getSurfaceBaseValue(SurfaceProperty.OPACITY, TermuxAppSharedPreferences.PAGE_WALL));
        // Today's callers read the Workspace's stop.
        assertTrue(SurfacePresets.matches(preferences, solid));
    }

    @Test
    public void lookOnHome_leavesTheTerminalAlone() {
        preferences.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, 88);

        SurfacePresets.applyLook(preferences, preset("stock"), PaneWallPage.WIDGETS);

        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY));
        assertEquals(88, preferences.getTerminalBackgroundOpacity());
    }
}
