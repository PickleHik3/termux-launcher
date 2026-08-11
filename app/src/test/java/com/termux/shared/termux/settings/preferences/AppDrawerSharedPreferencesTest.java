package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;

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
public class AppDrawerSharedPreferencesTest {

    private SharedPreferences raw;
    private TermuxAppSharedPreferences preferences;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        raw = context.getSharedPreferences("b4-drawer", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, raw, null);
    }

    @Test public void viewTypeDefaultsAndCorruptValuesPreserveVertical() {
        assertEquals("vertical", preferences.getAppLauncherDrawerViewType());
        raw.edit().putString(TermuxPreferenceConstants.TERMUX_APP
            .KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE, "corrupt").commit();
        assertEquals("vertical", preferences.getAppLauncherDrawerViewType());
        preferences.setAppLauncherDrawerViewType("horizontal");
        assertEquals("horizontal", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void threeGridKeysRoundTripAndClampIndependently() {
        preferences.setAppLauncherDrawerGridColumnsVertical(5);
        preferences.setAppLauncherDrawerGridColumnsHorizontal(99);
        preferences.setAppLauncherDrawerGridRowsHorizontal(1);
        assertEquals(5, preferences.getAppLauncherDrawerGridColumnsVertical());
        assertEquals(6, preferences.getAppLauncherDrawerGridColumnsHorizontal());
        assertEquals(2, preferences.getAppLauncherDrawerGridRowsHorizontal());
    }

    @Test public void horizontalColumnsNeverChangeVerticalColumns() {
        preferences.setAppLauncherDrawerGridColumnsVertical(4);
        preferences.setAppLauncherDrawerGridColumnsHorizontal(6);
        assertEquals(4, preferences.getAppLauncherDrawerGridColumnsVertical());
        assertEquals(6, preferences.getAppLauncherDrawerGridColumnsHorizontal());
        preferences.setAppLauncherDrawerGridColumnsHorizontal(5);
        assertEquals(4, preferences.getAppLauncherDrawerGridColumnsVertical());
    }

    @Test public void zeroRemainsAutoAndNegativeCorruptionSanitizesToAuto() {
        preferences.setAppLauncherDrawerGridColumnsVertical(0);
        preferences.setAppLauncherDrawerGridRowsHorizontal(0);
        assertEquals(0, preferences.getAppLauncherDrawerGridColumnsVertical());
        assertEquals(0, preferences.getAppLauncherDrawerGridRowsHorizontal());
        raw.edit().putInt(TermuxPreferenceConstants.TERMUX_APP
            .KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_HORIZONTAL, -8).commit();
        assertEquals(0, preferences.getAppLauncherDrawerGridColumnsHorizontal());
    }
}
