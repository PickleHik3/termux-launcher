package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherDockRowPreferenceTest {

    @Test
    public void xmlExposesThreeIndependentRowSwitches() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(app));
        PreferenceScreen screen = manager.inflateFromResource(app, R.xml.launcher_preferences, null);

        assertIndependentSwitch(screen, "app_launcher_apps_row_enabled", "Apps row");
        assertIndependentSwitch(screen, "app_launcher_az_row_enabled", "Alphabets row");
        assertIndependentSwitch(screen, "app_launcher_extra_keys_row_enabled", "Extra keys row");
    }

    @Test
    public void appsAndExtraKeysWritesLiveApplyWithoutRecreation() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);
        store.putBoolean("app_launcher_az_row_enabled", true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(141, TimeUnit.MILLISECONDS);

        assertLiveBooleanWrite(app, store, "app_launcher_apps_row_enabled", false);
        assertLiveBooleanWrite(app, store, "app_launcher_extra_keys_row_enabled", false);
        assertTrue("A-Z remains independent when Apps is disabled",
            store.getBoolean("app_launcher_az_row_enabled", true));
    }

    private static void assertIndependentSwitch(PreferenceScreen screen, String key, String title) {
        Preference preference = screen.findPreference(key);
        assertTrue(key, preference instanceof SwitchPreferenceCompat);
        assertEquals(title, preference.getTitle().toString());
        assertNull(key + " must not depend on another row", preference.getDependency());
    }

    private static void assertLiveBooleanWrite(Application app,
                                               TermuxStylePreferencesDataStore store,
                                               String key, boolean value) {
        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putBoolean(key, value);
        assertEquals(value, store.getBoolean(key, !value));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(141, TimeUnit.MILLISECONDS);
        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertTrue(key, broadcasts.size() > before);
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        assertFalse(styling.getBooleanExtra(
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
    }
}
