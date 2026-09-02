package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The drawer page's keyboard switch: off out of the box, and persisted like every other one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerSearchOnOpenPreferenceTest {

    private static final String KEY = "app_launcher_drawer_search_on_open";

    @Test public void theDrawerPageOffersTheSwitchAndItStartsOff() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(app));
        PreferenceScreen screen = manager.inflateFromResource(
            app, R.xml.app_drawer_preferences, null);

        SwitchPreferenceCompat found = screen.findPreference(KEY);
        assertEquals("Open the keyboard with the drawer", found.getTitle().toString());
        assertFalse(found.isChecked());
    }

    @Test public void theDataStoreRoundTripsIt() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);

        store.putBoolean(KEY, true);
        assertTrue(store.getBoolean(KEY, false));
        store.putBoolean(KEY, false);
        assertFalse(store.getBoolean(KEY, true));
    }
}
