package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.x11.DisplayKeyboardModeChoice;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Display input. Reuses {@link X11DisplayPreferencesFragment}'s controller; only the XML and
 * title differ, plus the "Keyboard over display" row, which the layout store holds per
 * orientation rather than as a preference of its own.
 */
@Keep
public class X11DisplayInputPreferencesFragment extends X11DisplayPreferencesFragment {

    private static final String KEY_KEYBOARD_OVER_DISPLAY = "x11_keyboard_over_display";

    private static final String KEY_AUTO_KEYBOARD = "x11_keyboard_follows_text";
    private static final String KEY_TOUCH_MODE = "touchMode";

    @Nullable private PlaceLayoutStore mPlaces;

    /** One plain line for the touch mode in force, shown under the segments. */
    private String touchModeSummary(@Nullable String value) {
        int res = "1".equals(value) ? com.termux.R.string.settings_x11_touch_mode_trackpad
            : "3".equals(value) ? com.termux.R.string.settings_x11_touch_mode_direct
            : com.termux.R.string.settings_x11_touch_mode_touch;
        return getString(res);
    }

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
        SegmentedPillPreference touch = findPreference(KEY_TOUCH_MODE);
        if (touch != null) {
            touch.setSummary(touchModeSummary(touch.getValue()));
            touch.setOnPreferenceChangeListener((preference, value) -> {
                preference.setSummary(touchModeSummary(String.valueOf(value)));
                return true;
            });
        }
        ListPreference row = findPreference(KEY_KEYBOARD_OVER_DISPLAY);
        if (row == null) return;
        row.setOnPreferenceChangeListener((preference, value) -> {
            PlaceLayoutStore places = places();
            if (places == null) return false;
            KeyboardMode mode = DisplayKeyboardModeChoice.modeOf(String.valueOf(value));
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                places.setKeyboardMode(orientation, mode);
            }
            refreshKeyboardOverDisplay();
            return false;
        });
        refreshKeyboardOverDisplay();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The touchpad's mode button writes the same values while the launcher is up.
        refreshKeyboardOverDisplay();
        refreshAutoKeyboard();
    }

    /** Auto Keyboard needs fcitx5, which the Linux apps guide installs: greyed until it has run. */
    private void refreshAutoKeyboard() {
        androidx.preference.Preference row = findPreference(KEY_AUTO_KEYBOARD);
        Context context = getContext();
        if (row == null || context == null) return;
        boolean ready = com.termux.app.x11.GuiAppsSetupGuide.isSetUp(context);
        if (row.isEnabled() != ready) row.setEnabled(ready);
        row.setSummary(ready ? com.termux.R.string.settings_x11_keyboard_follows_text_summary
            : com.termux.R.string.settings_x11_keyboard_follows_text_needs_setup);
    }

    @Nullable
    private PlaceLayoutStore places() {
        if (mPlaces == null) {
            Context context = getContext();
            TermuxAppSharedPreferences preferences =
                context == null ? null : TermuxAppSharedPreferences.build(context);
            if (preferences != null) mPlaces = new PlaceLayoutStore(preferences);
        }
        return mPlaces;
    }

    private void refreshKeyboardOverDisplay() {
        ListPreference row = findPreference(KEY_KEYBOARD_OVER_DISPLAY);
        PlaceLayoutStore places = places();
        if (row == null || places == null) return;
        KeyboardMode portrait = places.keyboardMode(PlaceOrientation.PORTRAIT);
        KeyboardMode landscape = places.keyboardMode(PlaceOrientation.LANDSCAPE);
        row.setValue(DisplayKeyboardModeChoice.commonValue(portrait, landscape));
        row.setSummary(DisplayKeyboardModeChoice.summary(portrait, landscape,
            getString(com.termux.R.string.settings_x11_keyboard_over_display_overlay),
            getString(com.termux.R.string.settings_x11_keyboard_over_display_resize),
            getString(com.termux.R.string.settings_x11_keyboard_over_display_differs)));
    }
}
