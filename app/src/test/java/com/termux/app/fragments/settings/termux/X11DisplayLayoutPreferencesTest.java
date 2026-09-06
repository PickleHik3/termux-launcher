package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.fragments.settings.SegmentedPillPreference;
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

/**
 * The Display page's layout rows: where the extra keys stand and whether the status bar stays
 * while the display is showing. Both are the launcher's own, not the display server's, and both
 * reshape the activity, so writing one asks the launcher to re-lay itself on resume.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11DisplayLayoutPreferencesTest {

    private static final String KEY_SIDE = "x11_extra_keys_side";
    private static final String KEY_HIDE_STATUS_BAR = "x11_hide_status_bar";

    @Test
    public void xmlExposesBothRows() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(
            new X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore(app));
        PreferenceScreen screen = manager.inflateFromResource(app, R.xml.x11_display_preferences, null);

        Preference side = screen.findPreference(KEY_SIDE);
        assertTrue(KEY_SIDE, side instanceof SegmentedPillPreference);
        assertEquals("Extra keys bar", side.getTitle().toString());
        Preference hide = screen.findPreference(KEY_HIDE_STATUS_BAR);
        assertTrue(KEY_HIDE_STATUS_BAR, hide instanceof SwitchPreferenceCompat);
        assertEquals("Hide status bar", hide.getTitle().toString());
    }

    @Test
    public void freshInstallKeepsTheRowAtTheBottomAndTheBarShown() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences store = app.getSharedPreferences(
            "x11-display-layout-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(app, store, null);
        assertEquals("bottom", preferences.getX11ExtraKeysSide());
        assertFalse(preferences.isX11HideStatusBar());

        preferences.setX11ExtraKeysSide("right");
        assertEquals("right", preferences.getX11ExtraKeysSide());
        preferences.setX11ExtraKeysSide("left");
        assertEquals("left", preferences.getX11ExtraKeysSide());
        // Anything else stored reads back as the row, so the keys are never nowhere.
        preferences.setX11ExtraKeysSide("sideways");
        assertEquals("bottom", preferences.getX11ExtraKeysSide());

        preferences.setX11HideStatusBar(true);
        assertTrue(preferences.isX11HideStatusBar());
    }

    @Test
    public void writingEitherRowAsksTheLauncherToRelayoutInPlace() {
        Application app = RuntimeEnvironment.getApplication();
        X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore store =
            new X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore(app);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString(KEY_SIDE, "left");
        assertEquals("left", store.getString(KEY_SIDE, "bottom"));
        store.putBoolean(KEY_HIDE_STATUS_BAR, true);
        assertTrue(store.getBoolean(KEY_HIDE_STATUS_BAR, false));

        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertEquals(before + 2, broadcasts.size());
        for (int i = before; i < broadcasts.size(); i++) {
            Intent styling = broadcasts.get(i);
            assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
                styling.getAction());
            // Both are applied by the activity's own layout passes, so no recreate is needed.
            assertFalse(styling.getBooleanExtra(
                TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
        }
    }
}
