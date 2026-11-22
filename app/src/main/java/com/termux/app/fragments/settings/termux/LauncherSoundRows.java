package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.app.sound.LauncherSound;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * The Sound and Media controls rows (General's Audio group) and the status line under them: the
 * store behind the two switches, the status line's text and the change listener that hides it.
 * See project-docs/active/launcher-sound-2026-10-25.md.
 */
final class LauncherSoundRows {

    private static final String KEY_SOUND = TermuxPreferenceConstants.TERMUX_APP.KEY_LAUNCHER_SOUND;
    private static final String KEY_MEDIA_CONTROLS =
        TermuxPreferenceConstants.TERMUX_APP.KEY_LAUNCHER_MEDIA_CONTROLS;
    private static final String KEY_STATUS = "launcher_sound_status";

    private LauncherSoundRows() {}

    static boolean isSoundKey(@Nullable String key) {
        return KEY_SOUND.equals(key) || KEY_MEDIA_CONTROLS.equals(key);
    }

    /** Writes one of the two switches and tells the sound server. */
    static void putBoolean(@NonNull Context context, @NonNull String key, boolean value) {
        TermuxAppSharedPreferences launcher = TermuxAppSharedPreferences.build(context);
        if (launcher == null) return;
        if (KEY_SOUND.equals(key)) launcher.setLauncherSoundEnabled(value);
        else if (KEY_MEDIA_CONTROLS.equals(key)) launcher.setLauncherMediaControlsEnabled(value);
        else return;
        LauncherSound.get(context).onSettingsChanged();
    }

    static boolean getBoolean(@NonNull Context context, @NonNull String key, boolean defValue) {
        TermuxAppSharedPreferences launcher = TermuxAppSharedPreferences.build(context);
        if (launcher == null) return defValue;
        if (KEY_SOUND.equals(key)) return launcher.isLauncherSoundEnabled();
        if (KEY_MEDIA_CONTROLS.equals(key)) return launcher.isLauncherMediaControlsEnabled();
        return defValue;
    }

    /** Called once the page's rows exist: switching Sound off hides the status line at once. */
    static void bind(@NonNull PreferenceFragmentCompat page) {
        Preference sound = page.findPreference(KEY_SOUND);
        if (sound == null) return;
        // Off hides the line at once; on, the server decides afresh, so the line waits for it.
        sound.setOnPreferenceChangeListener((preference, value) -> {
            if (Boolean.FALSE.equals(value)) {
                Preference row = page.findPreference(KEY_STATUS);
                if (row != null && row.isVisible()) row.setVisible(false);
            }
            return true;
        });
    }

    /**
     * The line under the sound switches: the user's own setup in use (decision 7), or the server
     * left down after it kept stopping (decision 3). Hidden when there is nothing to say.
     */
    static void updateStatus(@NonNull PreferenceFragmentCompat page) {
        Preference row = page.findPreference(KEY_STATUS);
        Context context = page.getContext();
        if (row == null || context == null) return;
        LauncherSound.Status status = LauncherSound.get(context).status();
        int text = status == LauncherSound.Status.USER_SETUP
            ? R.string.settings_launcher_sound_user_setup
            : status == LauncherSound.Status.GAVE_UP
                ? R.string.settings_launcher_sound_gave_up : 0;
        if (text != 0) row.setSummary(text);
        if (row.isVisible() != (text != 0)) row.setVisible(text != 0);
    }
}
