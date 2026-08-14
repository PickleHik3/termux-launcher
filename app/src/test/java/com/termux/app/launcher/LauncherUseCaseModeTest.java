package com.termux.app.launcher;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherUseCaseModeTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "launcher-use-case-mode-test", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    @Test
    public void freshInstallIsLauncherMode() {
        assertFalse(LauncherUseCaseMode.isTerminalOnly(preferences));
        assertEquals(LauncherUseCaseMode.MODE_LAUNCHER, LauncherUseCaseMode.currentMode(preferences));
    }

    @Test
    public void terminalOnlyDisablesEveryHomeSurfaceAndShowsInRecents() {
        preferences.setShowInRecentsWhenNotDefaultEnabled(false);

        LauncherUseCaseMode.applyMode(preferences, LauncherUseCaseMode.MODE_TERMINAL);

        assertFalse(preferences.isAppLauncherAppsRowEnabled());
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertFalse(preferences.isAppLauncherDrawerEnabled());
        assertFalse(preferences.isAppLauncherWidgetPaneEnabled());
        assertTrue(preferences.isShowInRecentsWhenNotDefaultEnabled());
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL, LauncherUseCaseMode.currentMode(preferences));
    }

    @Test
    public void terminalOnlyLeavesTheExtraKeysRowAlone() {
        preferences.setAppLauncherExtraKeysRowEnabled(true);

        LauncherUseCaseMode.applyTerminalOnly(preferences, true);

        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());
    }

    @Test
    public void switchingBackRestoresTheLayoutFromBeforeTheSwitch() {
        preferences.setAppLauncherAppsRowEnabled(true);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherDrawerEnabled(true);
        preferences.setAppLauncherWidgetPaneEnabled(false);
        preferences.setShowInRecentsWhenNotDefaultEnabled(false);

        LauncherUseCaseMode.applyTerminalOnly(preferences, true);
        LauncherUseCaseMode.applyTerminalOnly(preferences, false);

        assertTrue(preferences.isAppLauncherAppsRowEnabled());
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertFalse(preferences.isAppLauncherWidgetPaneEnabled());
        assertFalse(preferences.isShowInRecentsWhenNotDefaultEnabled());
    }

    @Test
    public void reapplyingTerminalOnlyKeepsTheOriginalSnapshot() {
        preferences.setAppLauncherAzRowEnabled(false);

        LauncherUseCaseMode.applyTerminalOnly(preferences, true);
        LauncherUseCaseMode.applyTerminalOnly(preferences, true);
        LauncherUseCaseMode.applyTerminalOnly(preferences, false);

        assertTrue(preferences.isAppLauncherAppsRowEnabled());
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void switchingBackWithNoSnapshotEnablesEverySurface() {
        preferences.setAppLauncherUseCaseMode(LauncherUseCaseMode.MODE_TERMINAL);
        preferences.setAppLauncherAppsRowEnabled(false);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherDrawerEnabled(false);
        preferences.setAppLauncherWidgetPaneEnabled(false);

        LauncherUseCaseMode.applyTerminalOnly(preferences, false);

        assertTrue(preferences.isAppLauncherAppsRowEnabled());
        assertTrue(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void reenablingOneSurfaceKeepsTheChosenMode() {
        LauncherUseCaseMode.applyTerminalOnly(preferences, true);

        preferences.setAppLauncherDrawerEnabled(true);

        assertTrue(LauncherUseCaseMode.isTerminalOnly(preferences));
        assertEquals(LauncherUseCaseMode.MODE_TERMINAL, LauncherUseCaseMode.currentMode(preferences));
    }

    /** The bug this mode was rewritten for: a mid-mode surface flip must not eat the snapshot. */
    @Test
    public void surfaceFlipInTerminalModeDoesNotCostTheAppsRowOnTheWayBack() {
        LauncherUseCaseMode.applyTerminalOnly(preferences, true);
        preferences.setAppLauncherAzRowEnabled(true);
        LauncherUseCaseMode.applyTerminalOnly(preferences, true); // no-op, mode unchanged

        LauncherUseCaseMode.applyTerminalOnly(preferences, false);

        assertTrue(preferences.isAppLauncherAppsRowEnabled());
        assertTrue(preferences.isAppLauncherDrawerEnabled());
        assertTrue(preferences.isAppLauncherWidgetPaneEnabled());
    }

    @Test
    public void azRowFollowsTheAppsRowButKeepsItsStoredChoice() {
        preferences.setAppLauncherAzRowEnabled(true);
        preferences.setAppLauncherAppsRowEnabled(false);

        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherAzRowChosen());

        preferences.setAppLauncherAppsRowEnabled(true);

        assertTrue(preferences.isAppLauncherAzRowEnabled());
    }

    @Test
    public void malformedSnapshotFallsBackToDefaults() {
        assertEquals(null, LauncherUseCaseMode.parseSnapshot(""));
        assertEquals(null, LauncherUseCaseMode.parseSnapshot("1,0,1"));
        assertEquals(null, LauncherUseCaseMode.parseSnapshot("1,0,1,0,yes"));
    }
}
