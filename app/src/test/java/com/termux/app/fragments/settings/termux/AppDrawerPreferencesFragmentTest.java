package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerPreferencesFragmentTest {

    private static final String ICON_SIZE = "app_launcher_drawer_icon_size";
    private static final String COLUMNS = "app_launcher_drawer_columns";
    private static final String ROWS = "app_launcher_drawer_rows";

    private static AppDrawerPreferencesFragment open(FragmentActivity activity) {
        AppDrawerPreferencesFragment fragment = new AppDrawerPreferencesFragment();
        activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
            .commitNow();
        return fragment;
    }

    @Test public void theDrawerPageOffersTheViewTypeAndTwoGridRows() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        AppDrawerPreferencesFragment fragment = open(activity);
        SegmentedPillPreference view = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE);
        assertNotNull(view);
        assertEquals(4, view.segmentCount());
        // Auto and Home's three sizes; Auto and 4 to 6; Auto and 3 to 5.
        assertEquals(4, ((SegmentedPillPreference) fragment.findPreference(ICON_SIZE)).segmentCount());
        assertEquals(4, ((SegmentedPillPreference) fragment.findPreference(COLUMNS)).segmentCount());
        assertEquals(4, ((SegmentedPillPreference) fragment.findPreference(ROWS)).segmentCount());
        // The keys the settings once had stay gone.
        for (String removed : new String[] {
            "app_launcher_drawer_icon_size_dp",
            "app_launcher_drawer_grid_columns_vertical",
            "app_launcher_drawer_grid_columns_horizontal",
            "app_launcher_drawer_grid_rows_horizontal",
            "app_launcher_drawer_grid_columns_categories"}) {
            assertNull(removed, fragment.findPreference(removed));
        }
        assertSame(activity, fragment.getActivity());
    }

    @Test public void columnsHideForCategories() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        AppDrawerPreferencesFragment fragment = open(activity);
        SegmentedPillPreference view = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE);
        androidx.preference.Preference columns = fragment.findPreference(COLUMNS);
        androidx.preference.Preference iconSize = fragment.findPreference(ICON_SIZE);
        androidx.preference.Preference rows = fragment.findPreference(ROWS);
        String[] types = {"default", "vertical", "horizontal", "categories"};
        boolean[] columnsShown = {true, true, true, false};
        boolean[] rowsShown = {false, false, true, false};
        for (int i = 0; i < types.length; i++) {
            // The live change: the listener is fired before the value is stored.
            assertTrue(view.getOnPreferenceChangeListener()
                .onPreferenceChange(view, types[i]));
            assertEquals(types[i], columnsShown[i], columns.isVisible());
            assertEquals(types[i], rowsShown[i], rows.isVisible());
            assertTrue(types[i], iconSize.isVisible());
        }
    }
}
