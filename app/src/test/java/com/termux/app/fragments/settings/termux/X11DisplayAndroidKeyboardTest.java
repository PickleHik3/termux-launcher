package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The Display page's "Use Android keyboard" row: off by default, and live in every touch mode. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11DisplayAndroidKeyboardTest {

    private static final String KEY = "x11_android_keyboard";

    private X11DisplayPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                X11DisplayPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof X11DisplayPreferencesFragment);
        return (X11DisplayPreferencesFragment) fragment;
    }

    @Test
    public void theRowIsOffByDefaultAndSaysWhatItGives() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        assertNotNull(preferences);
        assertFalse(preferences.isX11AndroidKeyboardEnabled());

        Preference row = launch().getPreferenceScreen().findPreference(KEY);
        assertTrue(row instanceof SwitchPreferenceCompat);
        assertFalse(((SwitchPreferenceCompat) row).isChecked());
        assertTrue(row.isEnabled());
        assertEquals(app.getString(R.string.settings_x11_android_keyboard_title), row.getTitle());
        assertEquals(app.getString(R.string.settings_x11_android_keyboard_summary),
            row.getSummary());
    }

    @Test
    public void aWriteLandsInTheLauncherPreferencesAndTellsTheDisplay() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, true);
        assertNotNull(preferences);
        X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore store =
            new X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore(app);

        store.putBoolean(KEY, true);
        assertTrue(preferences.isX11AndroidKeyboardEnabled());
        assertTrue(store.getBoolean(KEY, false));

        java.util.List<Intent> broadcasts =
            org.robolectric.Shadows.shadowOf(app).getBroadcastIntents();
        assertFalse("the change was broadcast", broadcasts.isEmpty());
        assertEquals(com.termux.x11.LoriePreferences.ACTION_PREFERENCES_CHANGED,
            broadcasts.get(broadcasts.size() - 1).getAction());

        store.putBoolean(KEY, false);
        assertFalse(preferences.isX11AndroidKeyboardEnabled());
        assertFalse(store.getBoolean(KEY, true));
    }
}
