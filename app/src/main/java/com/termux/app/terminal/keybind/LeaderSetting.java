package com.termux.app.terminal.keybind;

import com.termux.app.settings.LauncherSettings;
import com.termux.app.leader.LeaderChordSettingAdapter;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.app.leader.Leader;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Keeps the binding table typed under the leader in force (ADR 0042): read once when the launcher
 * starts, and again whenever {@code keys.leader}, {@code keys.leader_chord} or the Terl desktop
 * switch is saved, from whichever door saved it (the Settings row, terl.conf, launcherctl). With
 * the switch off the leader is v1's Ctrl+Alt whatever is stored; the stored choice waits for it.
 * On the first start a v1 user whose bindings file is customised gets Ctrl+Alt stored as their
 * leader ({@link Leader#upgradeChoice}).
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
        String upgrade = Leader.upgradeChoice(prefs.getString(LEADER_KEY, null),
            TerminalKeyBindingResolver.getInstance().hasCustomisedBindings());
        if (upgrade != null) prefs.edit().putString(LEADER_KEY, upgrade).apply();
        apply(prefs);
        // Held here: SharedPreferences keeps its listeners weakly.
        sListener = (changed, key) -> {
            if (LEADER_KEY.equals(key) || LeaderChordSettingAdapter.KEY.equals(key)
                || TERMUX_APP.KEY_TERL_DESKTOP.equals(key)) apply(changed);
        };
        prefs.registerOnSharedPreferenceChangeListener(sListener);
    }

    /** The activity's tiling desktop is running (built at launch) or gone; Terl's table follows it. */
    private static volatile boolean sDesktopRunning;
    private static volatile SharedPreferences sPrefs;

    /**
     * Terl's default bindings go with the running desktop, not the switch alone: the desktop is
     * built at launch, so a flip of the switch takes effect with the next one (#439).
     */
    public static synchronized void desktopRunning(boolean running) {
        sDesktopRunning = running;
        if (sPrefs != null) apply(sPrefs);
    }

    private static void apply(@NonNull SharedPreferences prefs) {
        sPrefs = prefs;
        boolean terl;
        try {
            terl = prefs.getBoolean(TERMUX_APP.KEY_TERL_DESKTOP, TERMUX_APP.DEFAULT_VALUE_TERL_DESKTOP);
        } catch (ClassCastException e) {
            terl = TERMUX_APP.DEFAULT_VALUE_TERL_DESKTOP;
        }
        TerminalKeyBindingResolver.setLeader(
            LeaderChordSettingAdapter.leaderFrom(LauncherSettings.preferencesStore(prefs)), terl,
            terl && sDesktopRunning);
    }
}
