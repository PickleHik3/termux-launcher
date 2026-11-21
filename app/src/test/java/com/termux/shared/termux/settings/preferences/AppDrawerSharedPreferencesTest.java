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

    @Test public void viewTypeDefaultsToDefaultAndCorruptValuesPreserveVertical() {
        assertEquals("default", preferences.getAppLauncherDrawerViewType());
        raw.edit().putString(TermuxPreferenceConstants.TERMUX_APP
            .KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE, "corrupt").commit();
        assertEquals("vertical", preferences.getAppLauncherDrawerViewType());
        preferences.setAppLauncherDrawerViewType("horizontal");
        assertEquals("horizontal", preferences.getAppLauncherDrawerViewType());
        preferences.setAppLauncherDrawerViewType("categories");
        assertEquals("categories", preferences.getAppLauncherDrawerViewType());
        preferences.setAppLauncherDrawerViewType("default");
        assertEquals("default", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void gridStepsDefaultToAutoAndUnknownValuesReadAsAuto() {
        assertEquals("auto", preferences.getAppLauncherDrawerIconSize());
        assertEquals("auto", preferences.getAppLauncherDrawerColumns());
        assertEquals("auto", preferences.getAppLauncherDrawerRows());
        for (String size : new String[] {"48", "54", "60"}) {
            preferences.setAppLauncherDrawerIconSize(size);
            assertEquals(size, preferences.getAppLauncherDrawerIconSize());
        }
        for (String columns : new String[] {"4", "5", "6"}) {
            preferences.setAppLauncherDrawerColumns(columns);
            assertEquals(columns, preferences.getAppLauncherDrawerColumns());
        }
        for (String rows : new String[] {"3", "4", "5", "6"}) {
            preferences.setAppLauncherDrawerRows(rows);
            assertEquals(rows, preferences.getAppLauncherDrawerRows());
        }
        // Not a step of its own setting: Auto, on the way in and on the way out.
        preferences.setAppLauncherDrawerIconSize("36");
        assertEquals("auto", preferences.getAppLauncherDrawerIconSize());
        preferences.setAppLauncherDrawerColumns("7");
        assertEquals("auto", preferences.getAppLauncherDrawerColumns());
        preferences.setAppLauncherDrawerRows("2");
        assertEquals("auto", preferences.getAppLauncherDrawerRows());
        preferences.setAppLauncherDrawerRows(null);
        assertEquals("auto", preferences.getAppLauncherDrawerRows());
        raw.edit().putString(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_COLUMNS,
            "corrupt").commit();
        assertEquals("auto", preferences.getAppLauncherDrawerColumns());
    }

    @Test public void aFreshInstallKeepsDefault() {
        preferences.migrateLookGrid(java.util.Collections.emptyList(), true);
        assertEquals("default", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void anUsedInstallWithNoStoredTypeIsPinnedToVertical() {
        raw.edit().putInt("some_used_key", 1).commit();
        preferences.migrateLookGrid(java.util.Collections.emptyList(), false);
        assertEquals("vertical", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void anUsedInstallKeepsTheTypeItStored() {
        raw.edit().putString(TermuxPreferenceConstants.TERMUX_APP
            .KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE, "categories").commit();
        preferences.migrateLookGrid(java.util.Collections.emptyList(), false);
        assertEquals("categories", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void thePinRunsOnce() {
        raw.edit().putInt("some_used_key", 1).commit();
        preferences.pinDrawerViewType(false);
        preferences.setAppLauncherDrawerViewType("default");
        preferences.pinDrawerViewType(false);
        preferences.migrateLookGrid(java.util.Collections.emptyList(), false);
        assertEquals("default", preferences.getAppLauncherDrawerViewType());
    }

    @Test public void theSectionDefaultsToAppsAndRoundTrips() {
        assertEquals("apps", preferences.getAppLauncherDrawerSection());
        preferences.setAppLauncherDrawerSection("categories");
        assertEquals("categories", preferences.getAppLauncherDrawerSection());
        preferences.setAppLauncherDrawerSection("junk");
        assertEquals("apps", preferences.getAppLauncherDrawerSection());
    }
}
