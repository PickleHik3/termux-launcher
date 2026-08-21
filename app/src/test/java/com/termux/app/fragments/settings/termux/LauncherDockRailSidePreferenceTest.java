package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
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
import java.util.concurrent.TimeUnit;

/**
 * The landscape rail's edge preference. It is not decoration: the app drawer's swipe runs away
 * from whichever edge this names, and in landscape that swipe is the only way to reach the drawer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherDockRailSidePreferenceTest {

    private static final String KEY = "app_launcher_dock_rail_side";

    @Test
    public void xmlExposesTheSideChooserGreyedOutWithThePinnedRow() {
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(app));
        PreferenceScreen screen = manager.inflateFromResource(app, R.xml.launcher_preferences, null);

        Preference side = screen.findPreference(KEY);
        assertTrue(KEY, side instanceof SegmentedPillPreference);
        assertEquals("Landscape rail side", side.getTitle().toString());
        // No rail without the pinned row it is built from.
        assertEquals("app_launcher_apps_row_enabled", side.getDependency());
    }

    @Test
    public void freshInstallDocksTheRailLeftAndReadsBackWhatWasWritten() {
        Application app = RuntimeEnvironment.getApplication();
        SharedPreferences store = app.getSharedPreferences(
            "launcher-dock-rail-side-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        TermuxAppSharedPreferences preferences =
            new TermuxAppSharedPreferences(app, store, null);
        assertEquals("left", preferences.getAppLauncherDockRailSide());

        preferences.setAppLauncherDockRailSide("right");
        assertEquals("right", preferences.getAppLauncherDockRailSide());
        assertTrue(preferences.isAppLauncherDockRailOnRight());

        // Anything else stored — a hand-edited prefs file, an older build — reads back as left
        // rather than leaving the rail with no edge and the drawer with no gesture.
        preferences.setAppLauncherDockRailSide("sideways");
        assertEquals("left", preferences.getAppLauncherDockRailSide());
    }

    @Test
    public void writingTheSideRecreatesTheActivity() {
        Application app = RuntimeEnvironment.getApplication();
        TermuxStylePreferencesDataStore store = TermuxStylePreferencesDataStore.getInstance(app);
        assertEquals("left", store.getString(KEY, "left"));

        int before = Shadows.shadowOf(app).getBroadcastIntents().size();
        store.putString(KEY, "right");
        assertEquals("right", store.getString(KEY, "left"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(141, TimeUnit.MILLISECONDS);

        List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
        assertTrue(KEY, broadcasts.size() > before);
        Intent styling = broadcasts.get(broadcasts.size() - 1);
        assertEquals(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE,
            styling.getAction());
        // The side moves the content root's cutout padding, which is only applied on an insets
        // pass: a restyle in place would leave the terminal inset from the edge the rail just left.
        assertTrue(styling.getBooleanExtra(
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, false));
    }
}
