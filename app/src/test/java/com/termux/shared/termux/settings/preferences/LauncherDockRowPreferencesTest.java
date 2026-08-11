package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherDockRowPreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences(
            "launcher-dock-row-preferences", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void allThreeRowsDefaultToEnabled() {
        assertTrue(preferences.isAppLauncherAppsRowEnabled());
        assertTrue(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());
    }

    @Test
    public void rowsPersistIndependently() {
        preferences.setAppLauncherAppsRowEnabled(false);
        assertFalse(preferences.isAppLauncherAppsRowEnabled());
        assertTrue("Apps must not switch off A-Z", preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());

        preferences.setAppLauncherAzRowEnabled(false);
        assertFalse(preferences.isAppLauncherAppsRowEnabled());
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());

        preferences.setAppLauncherExtraKeysRowEnabled(false);
        assertFalse(preferences.isAppLauncherAppsRowEnabled());
        assertFalse(preferences.isAppLauncherAzRowEnabled());
        assertFalse(preferences.isAppLauncherExtraKeysRowEnabled());
    }
}
