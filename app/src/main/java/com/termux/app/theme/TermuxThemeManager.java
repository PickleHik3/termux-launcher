package com.termux.app.theme;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

public final class TermuxThemeManager {

    private TermuxThemeManager() {}

    /**
     * Every activity's last theming step, called straight after {@code setTheme}.
     *
     * <p>Dynamic Material remains the default path. When the user has handed the chrome to the
     * terminal colour scheme, the scheme's palette is layered on top here — after {@code setTheme},
     * because that is what discards previously applied overlays.
     */
    public static void applyThemeOverlays(@NonNull Activity activity) {
        LauncherSchemeTheme.apply(activity, TermuxAppSharedPreferences.build(activity));
    }
}
