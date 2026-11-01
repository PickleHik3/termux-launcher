package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.wall.PaneWallPage;

/**
 * Home: the section for the Home half — the Appearance editor first, then every gesture, widgets
 * and the wallpaper's scroll. Use as Terminal only hides it (Start in lives in General). Reuses {@link LauncherPreferencesFragment}'s
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
        bindIconSize();
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

    /** Whether the Appearance editor opened this page (its Gestures or Edit pages chip). */
    private boolean openedFromEditor() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(
            com.termux.app.activities.SettingsActivity.EXTRA_FROM_EDITOR, false);
    }

    /**
     * Icon size is the one icon size (ADR 0020 §3), the editor's S/M/L/XL: the row reads and
     * writes the layouts' sizes document, never the old Home-only key. The steps the pinned apps
     * do not fit at (the cap the launcher measured, {@link com.termux.app.dock.IconSizeCap}) are
     * left out of the list, and the summary names the limit while it holds the choice down.
     */
    private void bindIconSize() {
        ListPreference size = findPreference(HomeSettings.KEY_ICON_SCALE);
        Context context = getContext();
        if (size == null || context == null) return;
        com.termux.app.place.layout.IconSize cap = com.termux.app.dock.IconSizeCap.published();
        java.util.List<CharSequence> names = new java.util.ArrayList<>();
        java.util.List<CharSequence> keys = new java.util.ArrayList<>();
        for (com.termux.app.place.layout.IconSize step
                : com.termux.app.place.layout.IconSize.values()) {
            if (step.dp > cap.dp) continue;
            names.add(context.getString(R.string.settings_icon_size_step, step.name(), step.dp));
            keys.add(step.key);
        }
        size.setEntries(names.toArray(new CharSequence[0]));
        size.setEntryValues(keys.toArray(new CharSequence[0]));
        com.termux.app.place.layout.IconSize chosen = com.termux.app.place.layout.IconSize.of(
            com.termux.app.place.layout.LayoutStore.shared(context).current().sizes());
        showIconSize(size, chosen);
        size.setOnPreferenceChangeListener((row, value) -> {
            com.termux.app.place.layout.IconSize picked =
                com.termux.app.place.layout.IconSize.forKey(String.valueOf(value));
            if (picked == null) return false;
            com.termux.app.place.layout.LayoutStore.shared(context).updateSizes(picked::into);
            showIconSize(size, picked);
            return false;
        });
    }

    /** The row's value and summary for {@code chosen}: the step drawn, and why while capped. */
    private void showIconSize(@NonNull ListPreference size,
                              @NonNull com.termux.app.place.layout.IconSize chosen) {
        Context context = getContext();
        if (context == null) return;
        com.termux.app.place.layout.IconSize drawn = com.termux.app.dock.IconSizeCap.effective(
            chosen, com.termux.app.dock.IconSizeCap.published());
        size.setValue(drawn.key);
        CharSequence name = context.getString(R.string.settings_icon_size_step, drawn.name(),
            drawn.dp);
        CharSequence hint = drawn == chosen ? null
            : com.termux.app.surfaces.SurfaceEditorController.iconSizeCapHint(context);
        size.setSummary(hint == null ? name
            : context.getString(R.string.settings_icon_size_capped, name, hint));
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
