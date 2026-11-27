package com.termux.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Keeps the binding table typed under the leader the settings name (ADR 0042): read once when the
 * launcher starts, and again whenever {@code keys.leader} or {@code keys.leader_chord} is saved,
 * from whichever door saved it (the Settings row, terl.conf, launcherctl).
 */
public final class LeaderSetting {

    /** The preference {@code keys.leader} is kept under. */
    static final String LEADER_KEY = "keys_leader";

    @Nullable private static SharedPreferences.OnSharedPreferenceChangeListener sListener;

    private LeaderSetting() {}

    /** Applies the stored leader and follows later saves; a second call changes nothing. */
    public static synchronized void follow(@NonNull Context context) {
        if (sListener != null) return;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext()
            : context;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        SharedPreferences prefs = preferences == null ? null : preferences.getSharedPreferences();
        if (prefs == null) return;
        apply(prefs);
        // Held here: SharedPreferences keeps its listeners weakly.
        sListener = (changed, key) -> {
            if (LEADER_KEY.equals(key) || LeaderChordSettingAdapter.KEY.equals(key)) apply(changed);
        };
        prefs.registerOnSharedPreferenceChangeListener(sListener);
    }

    private static void apply(@NonNull SharedPreferences prefs) {
        TerminalKeyBindingResolver.setLeader(
            LeaderChordSettingAdapter.leaderFrom(LauncherSettings.preferencesStore(prefs)));
    }
}
