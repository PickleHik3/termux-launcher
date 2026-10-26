package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.wall.PaneWallPage;

/**
 * Home: the section for the Home half — the Appearance editor first, then every gesture, widgets,
 * the wallpaper's scroll and where a cold start lands. Reuses {@link LauncherPreferencesFragment}'s
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
            if (openedFromEditor()) {
                // The editor that opened this page is still open under it, on the Home card:
                // going back lands there instead of opening a second one over Settings.
                if (getActivity() != null) getActivity().finish();
                return true;
            }
            SettingsEditorDoors.openEditor(this, row, com.termux.app.surfaces.EditorDoor.SETTINGS_HOME);
            return true;
        });
        adoptIconSizePreset();
        HomeGesturesRows.bind(this);
        offerDisplayStartIn();
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

    /** Whether the Appearance editor opened this page (its Gestures or Edit pages chip). */
    private boolean openedFromEditor() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(
            com.termux.app.activities.SettingsActivity.EXTRA_FROM_EDITOR, false);
    }

    /**
     * Icon size is one of three presets now (editor-redesign §6); a percentage stored before
     * reads as the preset nearest it, and is written back as that preset so the list shows it.
     */
    private void adoptIconSizePreset() {
        ListPreference size = findPreference(HomeSettings.KEY_ICON_SCALE);
        if (size == null) return;
        String stored = size.getValue();
        com.termux.app.launcher.widget.HomeIconSize preset =
            com.termux.app.launcher.widget.HomeIconSize.fromStored(stored);
        if (!preset.value.equals(stored)) size.setValue(preset.value);
    }

    /** Start in offers Display only on a build that has a display; it is the list's last entry. */
    private void offerDisplayStartIn() {
        if (com.termux.BuildConfig.X11_SERVER) return;
        ListPreference startIn = findPreference("app_launcher_start_in");
        if (startIn == null || startIn.getEntries() == null || startIn.getEntryValues() == null
            || startIn.getEntries().length < 3) return;
        startIn.setEntries(java.util.Arrays.copyOf(startIn.getEntries(), 2));
        startIn.setEntryValues(java.util.Arrays.copyOf(startIn.getEntryValues(), 2));
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
