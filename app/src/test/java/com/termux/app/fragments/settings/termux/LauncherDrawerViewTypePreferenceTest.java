package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

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
public class LauncherDrawerViewTypePreferenceTest {

    @Test public void xmlExposesExactlyAllThreeModesWithSimpleSummary() {
        PreferenceManager manager = new PreferenceManager(RuntimeEnvironment.getApplication());
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(
            RuntimeEnvironment.getApplication()));
        PreferenceScreen screen = manager.inflateFromResource(
            RuntimeEnvironment.getApplication(), R.xml.launcher_preferences, null);
        Preference found = screen.findPreference("app_launcher_drawer_view_type");
        assertTrue(found instanceof ListPreference);
        ListPreference list = (ListPreference) found;
        assertArrayEquals(new CharSequence[] {"Vertical", "Horizontal pages", "Categories"},
            list.getEntries());
        assertArrayEquals(new CharSequence[] {"vertical", "horizontal", "categories"},
            list.getEntryValues());
        list.setValue("categories");
        assertEquals("Categories", list.getSummary().toString());
    }

    @Test public void dataStorePersistsAndSchedulesANonRecreatingReload() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);
        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString("app_launcher_drawer_view_type", "horizontal");
        assertEquals("horizontal", store.getString("app_launcher_drawer_view_type", "vertical"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(141, TimeUnit.MILLISECONDS);
        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertTrue(broadcasts.size() > before);
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        assertFalse(styling.getBooleanExtra(
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
    }

    @Test public void categoriesPersistsThroughTheExistingDataStore() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);
        store.putString("app_launcher_drawer_view_type", "categories");
        assertEquals("categories", store.getString("app_launcher_drawer_view_type", "vertical"));
        store.putString("app_launcher_drawer_view_type", "corrupt-future-value");
        assertEquals("vertical", store.getString("app_launcher_drawer_view_type", "horizontal"));
    }
}
