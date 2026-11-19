package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.page.PageKind;
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
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, 33);
        Map<String, Object> look = SurfacePresets.captureLook(preferences);
        assertFalse(look.containsKey(SurfacePresets.KEY_HOME_LOOK));
        for (String key : look.keySet())
            assertFalse(key, key.endsWith("_home"));
    }

    @Test
    public void oneHalfsLook_appliesToBoth() {
        // Global is wall-wide: the Workspace's 2 is replaced by Home's 40 on the one wall value.
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.terminalPageId(), 2);
        preferences.setSurfaceGlobalValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL, 40);
        SurfacePresets.Preset home = saved(SurfacePresets.captureLook(preferences, PageKind.HOME));
        assertFalse(SurfacePresets.hasHomeLook(home));

        SurfacePresets.apply(preferences, home);

        assertEquals(40, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN, TermuxAppSharedPreferences.PAGE_WALL));
        assertEquals(40, preferences.getSurfaceGlobalValue(SurfaceProperty.GRAIN,
            TermuxAppSharedPreferences.terminalPageId()));
    }

    @Test
    public void lookOnHome_leavesTheTerminalAlone() {
        preferences.setTerminalBackgroundOpacity(88);

        SurfacePresets.applyLook(preferences, preset("stock"), PageKind.HOME);
        SurfacePresets.applyLook(preferences, preset("stock"), PageKind.TERMINAL);

        // The terminal's Background takes no preset (ADR 0031 §4, #287).
        assertEquals(88, preferences.getTerminalBackgroundOpacity());
    }

}
