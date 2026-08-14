package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerPreferencesFragmentTest {
    @Test public void viewTypeIsTheOnlyDrawerLayoutPreferenceLeft() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        AppDrawerPreferencesFragment fragment = new AppDrawerPreferencesFragment();
        activity.getSupportFragmentManager().beginTransaction().add(android.R.id.content, fragment)
            .commitNow();
        ListPreference view = fragment.findPreference(
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE);
        assertNotNull(view);
        assertEquals(3, view.getEntryValues().length);
        // Icon size and the per-view column/row counts were removed: every view resolves its own
        // geometry from the plane's width, and the category cards size their previews to fill, so a
        // pinned size could only put the dead space back.
        for (String removed : new String[] {
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_ICON_SIZE_DP,
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_VERTICAL,
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_HORIZONTAL,
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_ROWS_HORIZONTAL,
            TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_CATEGORIES}) {
            assertNull(removed, fragment.findPreference(removed));
        }
        assertSame(activity, fragment.getActivity());
    }
}
