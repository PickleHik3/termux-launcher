package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.ChromePolicy;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.TerminalSurfacePolicy;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Terminal's Background (ADR 0031 §4, #287): the panes' Opacity and Blur and the Wallpaper
 * dim live in keys of their own. Global never reaches them, an unset value reads the Terminal's
 * shipped value, and the dim lies on every page of the Workspace and none of Home's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalBackgroundTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences store = context.getSharedPreferences("terminal-background-test",
            Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void unsetValuesReadTheTerminalsShippedValuesNotGlobals() {
        preferences.setSurfaceGlobalValue(SurfaceProperty.OPACITY, 12);
        preferences.setSurfaceGlobalValue(SurfaceProperty.BLUR, 30);

        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_BG_OPACITY,
            preferences.getTerminalBackgroundOpacity());
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_BG_BLUR,
            preferences.getTerminalGlassBlurRadius());
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_WALLPAPER_DIM,
            preferences.getTerminalWallpaperDim());
    }

    @Test
    public void theTerminalShipsDarkerThanGlobalsGlass() {
        // A fresh install opens on Clear: Global's Opacity is Clear's.
        preferences.migrateLookGrid(java.util.Collections.emptyList(), true);
        assertTrue(TERMUX_APP.DEFAULT_TERMINAL_BG_OPACITY
            > preferences.getSurfaceGlobalValue(SurfaceProperty.OPACITY));
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_BG_OPACITY,
            preferences.getWallpaperEnabledTerminalBackgroundOpacity());
    }

    @Test
    public void globalWritesNeverReachTheTerminal() {
        preferences.setTerminalBackgroundOpacity(77);
        preferences.setTerminalGlassBlurRadius(20);
        preferences.setTerminalWallpaperDim(35);

        GlobalGlass.write(preferences, SurfaceProperty.OPACITY, 5);
        GlobalGlass.write(preferences, SurfaceProperty.BLUR, 2);
        GlobalGlass.write(preferences, SurfaceProperty.GRAIN, 50);
        GlobalGlass.write(preferences, SurfaceProperty.OPACITY,
            TermuxAppSharedPreferences.PAGE_WALL, 9);
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            SurfacePresets.apply(preferences, preset);

        assertEquals(77, preferences.getTerminalBackgroundOpacity());
        assertEquals(20, preferences.getTerminalGlassBlurRadius());
        assertEquals(35, preferences.getTerminalWallpaperDim());
    }

    @Test
    public void wallpaperOffIsSolidAndBackOnRestoresTheTerminalsOpacity() {
        preferences.setUseSystemWallpaperEnabled(true);
        preferences.setTerminalBackgroundOpacity(62);

        ChromePolicy.applyWallpaperModePreferences(preferences, false);
        assertEquals(100, preferences.getTerminalBackgroundOpacity());

        ChromePolicy.applyWallpaperModePreferences(preferences, true);
        assertEquals(62, preferences.getTerminalBackgroundOpacity());
    }

    @Test
    public void theDimLiesOnTheWorkspacesPagesAndNeverOnHome() {
        assertEquals(40, TerminalSurfacePolicy.pageDimPercent(PageKind.TERMINAL, 40));
        assertEquals(40, TerminalSurfacePolicy.pageDimPercent(PageKind.DISPLAY, 40));
        assertEquals(40, TerminalSurfacePolicy.pageDimPercent(PageKind.ASSISTANT, 40));
        assertEquals(0, TerminalSurfacePolicy.pageDimPercent(PageKind.HOME, 40));
        assertEquals(0, TerminalSurfacePolicy.pageDimPercent(null, 40));
        assertEquals(0, TerminalSurfacePolicy.dimColor(0));
        assertEquals(0xFF000000, TerminalSurfacePolicy.dimColor(100));
    }

    @Test
    public void theResetCoversTheBackground() {
        for (String key : AppearanceSnapshot.TERMINAL_BACKGROUND_KEYS)
            assertTrue(key, AppearanceReset.inScope(key));
    }

    @Test
    public void theSnapshotPutsTheBackgroundBackAsItWas() {
        preferences.setTerminalBackgroundOpacity(70);
        AppearanceSnapshot snapshot = AppearanceSnapshot.capture(preferences);

        preferences.setTerminalBackgroundOpacity(20);
        preferences.setTerminalGlassBlurRadius(30);
        preferences.setTerminalWallpaperDim(50);
        snapshot.restore(preferences);

        assertEquals(70, preferences.getTerminalBackgroundOpacity());
        // Unset at capture: unset again, so they read the shipped values.
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_BG_BLUR,
            preferences.getTerminalGlassBlurRadius());
        assertEquals(TERMUX_APP.DEFAULT_TERMINAL_WALLPAPER_DIM,
            preferences.getTerminalWallpaperDim());
    }
}
