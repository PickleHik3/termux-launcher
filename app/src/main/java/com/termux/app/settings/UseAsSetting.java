package com.termux.app.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.wall.UseAs;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Use as as it is stored, for the screens that only hold a {@link Context} (Settings, the welcome
 * sheet). What each answer means is {@link UseAs}'s; this only reads and writes the preference.
 */
public final class UseAsSetting {

    private UseAsSetting() {}

    /** The stored Use as; Home screen when the store cannot be read. */
    @NonNull
    public static UseAs read(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        return UseAs.read(prefs == null ? null : prefs.getUseAs());
    }

    /** Stores {@code useAs}; the launcher's preference listener applies it to the wall. */
    public static void write(@NonNull Context context, @NonNull UseAs useAs) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs != null) prefs.setUseAs(useAs.value);
    }
}
