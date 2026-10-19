package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.wall.PaneWallPage;

/**
 * Home: the section for the Home half — the Layout editor first, then gestures, widgets, the
 * wallpaper's scroll and where a cold start lands. Reuses {@link LauncherPreferencesFragment}'s
 * store and listeners (every row writes the key it always did); only the XML and title differ.
 */
@Keep
public class HomePreferencesFragment extends LauncherPreferencesFragment {

    @Override
    protected int preferencesXml() {
        return R.xml.home_preferences;
    }

    @Override
    protected int titleRes() {
        return R.string.settings_section_home;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Preference layout = findPreference("home_layout_editor");
        if (layout != null) layout.setOnPreferenceClickListener(row -> {
            SettingsEditorDoors.openLayoutEditor(this, row, PaneWallPage.WIDGETS);
            return true;
        });
        HomeGesturesRows.bind(this);
        Preference lock = findPreference(HomeSettings.KEY_LAYOUT_LOCKED);
        if (lock != null) lock.setOnPreferenceChangeListener((row, value) -> {
            showEditPagesRow(Boolean.TRUE.equals(value));
            return true;
        });
        Preference pages = findPreference("home_edit_pages");
        if (pages != null) pages.setOnPreferenceClickListener(row -> {
            Context context = getContext();
            if (context != null)
                SettingsEditorDoors.open(this, row, SettingsEditorDoors.homePagesEditorIntent(context));
            return true;
        });
    }

    /** Edit pages changes the layout: while it is locked, its row waits for the lock to come off. */
    private void showEditPagesRow(boolean locked) {
        Preference pages = findPreference("home_edit_pages");
        if (pages == null) return;
        // Off and not an action: dimmed, no chevron (the row layouts' disabled colours), and out
        // of the focus order, since it opens nothing.
        pages.setEnabled(!locked);
        pages.setSelectable(!locked);
        pages.setSummary(locked ? R.string.settings_home_edit_pages_locked
            : R.string.settings_home_edit_pages_summary);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;
        // The accessibility service or Shizuku may have come or gone while the page was away.
        HomeGesturesRows.bind(this);
        showEditPagesRow(HomeSettings.isLayoutLocked(context));
    }
}
