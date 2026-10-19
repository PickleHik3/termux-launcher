package com.termux.app.fragments.settings.termux;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.termux.R;
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
            SettingsEditorDoors.openLayoutEditor(this, PaneWallPage.WIDGETS);
            return true;
        });
    }
}
