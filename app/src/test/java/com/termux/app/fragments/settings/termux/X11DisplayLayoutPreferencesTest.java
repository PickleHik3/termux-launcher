package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
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
 * The Display page's layout row: where the extra keys stand while the display is showing. It is the
 * launcher's own arrangement, not the display server's, and it reshapes the activity, so writing it
 * asks the launcher to re-lay itself on resume. The status bar has no hidden state to switch.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class X11DisplayLayoutPreferencesTest {

    private static final String KEY_SIDE = "x11_extra_keys_side";
    private static final String KEY_HIDE_STATUS_BAR = "x11_hide_status_bar";

    @Test
    public void xmlExposesTheKeysRowAndNoStatusBarSwitch() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(
            new X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore(app));
        PreferenceScreen screen = manager.inflateFromResource(app, R.xml.x11_display_preferences, null);

        Preference side = screen.findPreference(KEY_SIDE);
        assertTrue(KEY_SIDE, side instanceof SegmentedPillPreference);
        assertEquals("Extra keys bar", side.getTitle().toString());
        // The bar moves rather than hides, so the wall's paging gesture always has one.
        assertNull(KEY_HIDE_STATUS_BAR, screen.findPreference(KEY_HIDE_STATUS_BAR));
    }

    @Test
    public void freshInstallKeepsTheKeysAlongTheBottomInBothOrientations() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = app.getSharedPreferences(
            "x11-display-layout-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        PlaceLayoutStore places =
            new PlaceLayoutStore(new TermuxAppSharedPreferences(app, prefs, null));

        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(orientation.storageValue(), PlaceLayout.RowPlacement.BOTTOM,
                places.extraKeys(PaneWallPage.DISPLAY, orientation));
        }
        places.setExtraKeys(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE,
            PlaceLayout.RowPlacement.RIGHT);
        assertEquals(PlaceLayout.RowPlacement.RIGHT,
            places.extraKeys(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE));
        // Anything else stored — a hand-edited prefs file, an older build — reads back as the row,
        // so the keys are never nowhere.
        prefs.edit().putString("place.display.landscape.extra_keys", "sideways").commit();
        assertEquals(PlaceLayout.RowPlacement.BOTTOM,
            places.extraKeys(PaneWallPage.DISPLAY, PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void writingTheRowStandsTheKeysThereAndRelayoutsInPlace() {
        Application app = RuntimeEnvironment.getApplication();
        X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore store =
            new X11DisplayPreferencesFragment.X11DisplayPreferencesDataStore(app);

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString(KEY_SIDE, "left");
        assertEquals("left", store.getString(KEY_SIDE, "bottom"));

        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertEquals(before + 1, broadcasts.size());
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        // Applied by the activity's own layout passes, so no recreate is needed.
        assertFalse(styling.getBooleanExtra(
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
    }
}
