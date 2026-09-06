package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** The widget grid's columns and rows are the user's to set; the store clamps and the activity applies. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherWidgetGridPreferenceTest {

    private static final String KEY_COLUMNS = "app_launcher_widget_grid_columns";
    private static final String KEY_ROWS = "app_launcher_widget_grid_rows";

    @Test
    public void xmlExposesBothSlidersGreyedOutWithThePane() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(app));
        PreferenceScreen screen = manager.inflateFromResource(app, R.xml.launcher_preferences, null);

        Preference columns = screen.findPreference(KEY_COLUMNS);
        assertTrue(KEY_COLUMNS, columns instanceof SeekBarPreference);
        assertEquals("Grid columns", columns.getTitle().toString());
        assertEquals("app_launcher_widget_pane_enabled", columns.getDependency());
        Preference rows = screen.findPreference(KEY_ROWS);
        assertTrue(KEY_ROWS, rows instanceof SeekBarPreference);
        assertEquals("Grid rows", rows.getTitle().toString());
        assertEquals("app_launcher_widget_pane_enabled", rows.getDependency());
    }

    @Test
    public void freshInstallIsFourByFiveAndValuesAreClamped() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences store = app.getSharedPreferences(
            "launcher-widget-grid-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(app, store, null);
        assertEquals(4, preferences.getAppLauncherWidgetGridColumns());
        assertEquals(5, preferences.getAppLauncherWidgetGridRows());

        preferences.setAppLauncherWidgetGridColumns(6);
        preferences.setAppLauncherWidgetGridRows(8);
        assertEquals(6, preferences.getAppLauncherWidgetGridColumns());
        assertEquals(8, preferences.getAppLauncherWidgetGridRows());

        // A hand-edited or future value never reaches the grid outside what it can lay out.
        preferences.setAppLauncherWidgetGridColumns(100);
        preferences.setAppLauncherWidgetGridRows(0);
        assertEquals(8, preferences.getAppLauncherWidgetGridColumns());
        assertEquals(2, preferences.getAppLauncherWidgetGridRows());
    }

    @Test
    public void writingASliderRestylesTheActivityInPlace() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putInt(KEY_ROWS, 6);
        assertEquals(6, store.getInt(KEY_ROWS, 5));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(141, TimeUnit.MILLISECONDS);

        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertTrue(KEY_ROWS, broadcasts.size() > before);
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        // The repository re-lays the grid itself; nothing here needs the activity rebuilt.
        assertFalse(styling.getBooleanExtra(
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
    }
}
