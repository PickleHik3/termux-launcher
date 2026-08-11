package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerPreferencesFragmentTest {
    @Test public void exactRowsDefaultsSummariesAndModeVisibilityUpdateInPlace() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        AppDrawerPreferencesFragment fragment = new AppDrawerPreferencesFragment();
        activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
            .commitNow();
        String viewKey = TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE;
        ListPreference view = fragment.findPreference(viewKey);
        Preference icon = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ICON_SIZE_DP);
        Preference vertical = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_VERTICAL);
        Preference columns = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_HORIZONTAL);
        Preference rows = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_ROWS_HORIZONTAL);
        Preference categories = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_CATEGORIES);
        assertNotNull(view); assertNotNull(icon); assertTrue(vertical.isVisible());
        assertFalse(columns.isVisible()); assertFalse(rows.isVisible()); assertFalse(categories.isVisible());
        view.getOnPreferenceChangeListener().onPreferenceChange(view, "horizontal");
        assertFalse(vertical.isVisible()); assertTrue(columns.isVisible()); assertTrue(rows.isVisible());
        view.getOnPreferenceChangeListener().onPreferenceChange(view, "categories");
        assertFalse(columns.isVisible()); assertFalse(rows.isVisible()); assertTrue(categories.isVisible());
        assertSame(activity, fragment.getActivity());
    }
}
