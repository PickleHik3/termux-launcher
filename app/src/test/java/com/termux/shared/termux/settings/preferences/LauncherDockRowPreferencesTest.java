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

    /**
     * The A-Z index scrubs the apps row, so it follows it: with the apps row off the index is off
     * too, but the user's own A-Z choice is kept and comes back with the apps row. The extra-keys
     * row is a terminal surface and stays independent of both.
     */
    @Test
    public void rowsPersistIndependently() {
        preferences.setAppLauncherAppsRowEnabled(false);
        assertFalse(preferences.isAppLauncherAppsRowEnabled());
        assertFalse("A-Z is meaningless without the apps row",
            preferences.isAppLauncherAzRowEnabled());
        assertTrue("the A-Z choice survives the apps row going off",
            preferences.isAppLauncherAzRowChosen());
        assertTrue(preferences.isAppLauncherExtraKeysRowEnabled());

        preferences.setAppLauncherAppsRowEnabled(true);
        assertTrue("A-Z comes back with the apps row", preferences.isAppLauncherAzRowEnabled());
        preferences.setAppLauncherAppsRowEnabled(false);

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
