package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/** Dedicated drawer controls. Visibility changes immediately; persistence drives live apply. */
@Keep
public final class AppDrawerPreferencesFragment extends MaterialPreferenceFragment {
    private ListPreference viewType;
    private Preference verticalColumns;
    private Preference horizontalColumns;
    private Preference horizontalRows;
    private Preference categoryColumns;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.app_drawer_preferences, rootKey);
        viewType = findPreference(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_VIEW_TYPE);
        verticalColumns = findPreference(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_VERTICAL);
        horizontalColumns = findPreference(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_HORIZONTAL);
        horizontalRows = findPreference(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_ROWS_HORIZONTAL);
        categoryColumns = findPreference(TermuxPreferenceConstants.TERMUX_APP.KEY_APP_LAUNCHER_DRAWER_GRID_COLUMNS_CATEGORIES);
        if (viewType != null) viewType.setOnPreferenceChangeListener((preference, value) -> {
            updateVisibility(String.valueOf(value));
            return true;
        });
        updateVisibility(viewType == null ? null : viewType.getValue());
    }

    private void updateVisibility(String value) {
        boolean horizontal = TermuxPreferenceConstants.TERMUX_APP
            .APP_LAUNCHER_DRAWER_VIEW_TYPE_HORIZONTAL.equals(value);
        boolean categories = TermuxPreferenceConstants.TERMUX_APP
            .APP_LAUNCHER_DRAWER_VIEW_TYPE_CATEGORIES.equals(value);
        if (verticalColumns != null) verticalColumns.setVisible(!horizontal && !categories);
        if (horizontalColumns != null) horizontalColumns.setVisible(horizontal);
        if (horizontalRows != null) horizontalRows.setVisible(horizontal);
        if (categoryColumns != null) categoryColumns.setVisible(categories);
    }
}
