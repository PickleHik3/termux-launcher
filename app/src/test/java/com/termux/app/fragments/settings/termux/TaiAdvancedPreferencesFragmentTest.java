package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The Advanced page shows the overrides grid with one cell per override, between Parameters and Memory limits. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TaiAdvancedPreferencesFragmentTest {

    private TaiAdvancedPreferencesFragment launch() {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, TaiAdvancedPreferencesFragment.class.getName());
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(String.valueOf(fragment), fragment instanceof TaiAdvancedPreferencesFragment);
        return (TaiAdvancedPreferencesFragment) fragment;
    }

    @Test
    public void theOverridesGridIsOnThePageWithACellPerOverride() {
        PreferenceFragmentCompat page = launch();
        TaiOverridesPreference grid = page.findPreference("tai_runtime_overrides");
        assertNotNull(grid);
        assertTrue(grid.isVisible());
        assertEquals(8, grid.itemCount());
        RecyclerView list = page.getListView();
        assertNotNull(list);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertTrue(String.valueOf(adapter), adapter instanceof androidx.preference.PreferenceGroup.PreferencePositionCallback);
        int position = ((androidx.preference.PreferenceGroup.PreferencePositionCallback) adapter)
            .getPreferenceAdapterPosition("tai_runtime_overrides");
        assertNotEquals("the grid has a row in the list", RecyclerView.NO_POSITION, position);
    }
}
