package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The Layout page: a door, not an editor. One row per place — Home, Terminal, Display — and each of
 * them closes Settings and opens the launcher on that place with the Layout editor over it, which
 * is where everything about a place's layout is set, beside a picture of the place itself.
 */
@Keep
public final class LayoutPreferencesFragment extends MaterialPreferenceFragment {

    /** The three doors, in the order they stand on the page. */
    @VisibleForTesting
    static final String[] KEY_PLACE_ROWS =
        {"layout_place_home", "layout_place_terminal", "layout_place_display"};
    private static final PaneWallPage[] PLACE_ROW_PLACES =
        {PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        setPreferencesFromResource(R.xml.layout_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configurePlaceRows(isDisplayAvailable(preferences));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_layout);
        Context context = getContext();
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        configurePlaceRows(isDisplayAvailable(preferences));
    }

    private static boolean isDisplayAvailable(@NonNull TermuxAppSharedPreferences preferences) {
        return com.termux.BuildConfig.X11_SERVER && preferences.isX11DisplayEnabled();
    }

    /**
     * The three doors. Each one finishes Settings and opens the launcher on its place with the
     * Layout editor up; the Display row stands only where there is a display to lay out.
     */
    private void configurePlaceRows(boolean displayAvailable) {
        for (int i = 0; i < KEY_PLACE_ROWS.length; i++) {
            Preference row = findPreference(KEY_PLACE_ROWS[i]);
            if (row == null) continue;
            PaneWallPage place = PLACE_ROW_PLACES[i];
            row.setVisible(place != PaneWallPage.DISPLAY || displayAvailable);
            row.setOnPreferenceClickListener(preference -> {
                openLayoutEditor(place);
                return true;
            });
        }
    }

    /**
     * Hands the launcher over: it comes forward on that place with the Layout editor over it, and
     * Settings closes behind, so Back from the editor is the place itself rather than this list.
     */
    private void openLayoutEditor(@NonNull PaneWallPage place) {
        Context context = getContext();
        if (context == null) return;
        startActivity(layoutEditorIntent(context, place));
        Activity activity = getActivity();
        if (activity != null) activity.finish();
    }

    /** The one place the Layout editor's deep link is spelled out. */
    @NonNull
    public static Intent layoutEditorIntent(@NonNull Context context, @NonNull PaneWallPage place) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.putExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR, true);
        intent.putExtra(TermuxActivity.EXTRA_LAYOUT_EDITOR_PLACE, place.toolName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
}
