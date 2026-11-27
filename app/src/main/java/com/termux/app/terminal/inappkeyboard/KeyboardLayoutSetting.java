package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Applies {@code keyboard.layout} at once (ADR 0042): when it is saved, from whichever door saved
 * it (the Settings row, terl.conf, launcherctl), the launcher keyboard reloads its layout.
 */
public final class KeyboardLayoutSetting {

    @Nullable private static SharedPreferences.OnSharedPreferenceChangeListener sListener;

    private KeyboardLayoutSetting() {}

    /** Whether a save of {@code key} moves the launcher keyboard's layout. */
    static boolean moves(@Nullable String key) {
        return TerlKeyboardLayout.KEY.equals(key) || TerlKeyboardLayout.BASE_KEY.equals(key);
    }

    /** Runs {@code reload} on the main thread after each save of the choice; once per process. */
    public static synchronized void follow(@NonNull Context context, @NonNull Runnable reload) {
        if (sListener != null) return;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        SharedPreferences prefs = preferences == null ? null : preferences.getSharedPreferences();
        if (prefs == null) return;
        Handler main = new Handler(Looper.getMainLooper());
        // Held here: SharedPreferences keeps its listeners weakly.
        sListener = (changed, key) -> {
            if (moves(key)) main.post(reload);
        };
        prefs.registerOnSharedPreferenceChangeListener(sListener);
    }
}
