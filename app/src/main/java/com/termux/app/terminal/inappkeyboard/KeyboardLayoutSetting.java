package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Applies {@code keyboard.layout} at once (ADR 0042), from whichever door saved it (the Settings
 * row, terl.conf, launcherctl): the launcher keyboard reloads its layout, and a change to Custom
 * remembers the layout it came from ({@link TerlKeyboardLayout#baseAfter}). The listener is one
 * per process and holds no activity; the activity holds its own reload, which is held weakly
 * here, so a recreated activity's {@link #follow} takes over and the old one is released.
 */
public final class KeyboardLayoutSetting {

    @Nullable private static SharedPreferences.OnSharedPreferenceChangeListener sListener;
    @Nullable private static volatile WeakReference<Runnable> sReload;
    @Nullable private static String sLast;

    private KeyboardLayoutSetting() {}

    /** Whether a save of {@code key} moves the launcher keyboard's layout. */
    static boolean moves(@Nullable String key) {
        return TerlKeyboardLayout.KEY.equals(key) || TerlKeyboardLayout.BASE_KEY.equals(key);
    }

    /**
     * Runs {@code reload} on the main thread after each save of the choice, in place of the one
     * given before. The caller keeps {@code reload} reachable for as long as it wants it.
     */
    public static synchronized void follow(@NonNull Context context, @NonNull Runnable reload) {
        sReload = new WeakReference<>(reload);
        if (sListener != null) return;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        SharedPreferences prefs = preferences == null ? null : preferences.getSharedPreferences();
        if (prefs == null) return;
        Handler main = new Handler(Looper.getMainLooper());
        sLast = prefs.getString(TerlKeyboardLayout.KEY, null);
        // Held here: SharedPreferences keeps its listeners weakly.
        sListener = (changed, key) -> {
            if (!moves(key)) return;
            if (TerlKeyboardLayout.KEY.equals(key)) {
                String now = changed.getString(TerlKeyboardLayout.KEY, null);
                String base = TerlKeyboardLayout.baseAfter(sLast, now, null);
                sLast = now;
                if (base != null) {
                    changed.edit().putString(TerlKeyboardLayout.BASE_KEY, base).apply();
                    return; // the base's own save reloads
                }
            }
            WeakReference<Runnable> ref = sReload;
            Runnable run = ref == null ? null : ref.get();
            if (run != null) main.post(run);
        };
        prefs.registerOnSharedPreferenceChangeListener(sListener);
    }
}
