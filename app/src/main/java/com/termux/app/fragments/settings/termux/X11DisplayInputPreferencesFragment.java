package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.x11.KeyboardOverDisplayPolicy;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Display input. Reuses {@link X11DisplayPreferencesFragment}'s controller; only the XML and title
 * differ, and the one row that is not the display's own: whether the keyboard floats over the
 * display or makes it resize. That mode lives in the layout store per orientation, so the row
 * reads both, writes both, and reads them again on resume for a switch made on the touchpad.
 */
@Keep
public class X11DisplayInputPreferencesFragment extends X11DisplayPreferencesFragment {

    private static final String KEY_KEYBOARD_OVER_DISPLAY = "x11_keyboard_over_display";

    @Nullable private PlaceLayoutStore mPlaces;

    @Override
    protected int preferencesXml() {
        return com.termux.R.xml.x11_display_input_preferences;
    }

    @Override
    protected int titleRes() {
        return com.termux.R.string.settings_x11_sub_input_title;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Context context = getContext();
        ListPreference row = findPreference(KEY_KEYBOARD_OVER_DISPLAY);
        if (context == null || row == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) {
            row.setVisible(false);
            return;
        }
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        mPlaces = places;
        row.setOnPreferenceChangeListener((preference, value) -> {
            KeyboardMode mode = KeyboardMode.parse(String.valueOf(value), KeyboardMode.OVERLAY);
            places.setKeyboardMode(PlaceOrientation.PORTRAIT, mode);
            places.setKeyboardMode(PlaceOrientation.LANDSCAPE, mode);
            showKeyboardMode();
            // The row shows what the store holds, which showKeyboardMode has just read back.
            return false;
        });
        showKeyboardMode();
    }

    @Override
    public void onResume() {
        super.onResume();
        showKeyboardMode();
    }

    /** Fills the row from what the store holds for both orientations right now. */
    private void showKeyboardMode() {
        ListPreference row = findPreference(KEY_KEYBOARD_OVER_DISPLAY);
        PlaceLayoutStore places = mPlaces;
        if (row == null || places == null) return;
        KeyboardMode portrait = places.keyboardMode(PlaceOrientation.PORTRAIT);
        KeyboardMode landscape = places.keyboardMode(PlaceOrientation.LANDSCAPE);
        row.setValue(KeyboardOverDisplayPolicy.value(portrait, landscape));
        row.setSummary(summary(portrait, landscape));
    }

    @NonNull
    private String summary(@NonNull KeyboardMode portrait, @NonNull KeyboardMode landscape) {
        String[] labels = getResources().getStringArray(com.termux.R.array.x11_keyboard_over_display_entries);
        return KeyboardOverDisplayPolicy.summary(portrait, landscape, labels[0], labels[1],
            getString(com.termux.R.string.settings_x11_keyboard_over_display_split));
    }
}
