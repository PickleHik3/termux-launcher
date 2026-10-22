package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The Display Input page's "Keyboard over display" row reads and writes both orientations. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11DisplayKeyboardOverDisplayTest {

    private static final String KEY = "x11_keyboard_over_display";

    private PlaceLayoutStore store() {
        return new PlaceLayoutStore(
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true));
    }

    private X11DisplayPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                X11DisplayInputPreferencesFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof X11DisplayPreferencesFragment);
        return (X11DisplayPreferencesFragment) fragment;
    }

    @Test
    public void aFreshInstallShowsOverlay() {
        ListPreference row = launch().findPreference(KEY);
        assertNotNull(row);
        assertFalse(row.isPersistent());
        assertEquals("overlay", row.getValue());
        assertEquals("Overlay", String.valueOf(row.getSummary()));
    }

    @Test
    public void aPickWritesBothOrientations() {
        ListPreference row = launch().findPreference(KEY);
        assertNotNull(row);
        assertTrue(row.getOnPreferenceChangeListener().onPreferenceChange(row, "resize") == false);
        PlaceLayoutStore store = store();
        assertEquals(KeyboardMode.RESIZE, store.keyboardMode(PlaceOrientation.PORTRAIT));
        assertEquals(KeyboardMode.RESIZE, store.keyboardMode(PlaceOrientation.LANDSCAPE));
        assertEquals("resize", row.getValue());
        assertEquals("Resize display", String.valueOf(row.getSummary()));
    }

    @Test
    public void orientationsApartAreNamedAndNothingIsChecked() {
        store().setKeyboardMode(PlaceOrientation.LANDSCAPE, KeyboardMode.RESIZE);
        store().setKeyboardMode(PlaceOrientation.PORTRAIT, KeyboardMode.OVERLAY);
        ListPreference row = launch().findPreference(KEY);
        assertNotNull(row);
        assertEquals("Portrait: Overlay · Landscape: Resize display",
            String.valueOf(row.getSummary()));
    }
}
