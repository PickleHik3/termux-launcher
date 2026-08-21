package com.termux.app.launcher;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The launcher-vs-terminal use case switch.
 *
 * <p>Termux Launcher ships as a home launcher, but a large share of users only ever want the
 * terminal. Rather than making them hunt down five scattered switches, this collapses the choice
 * into one: {@link #MODE_TERMINAL} turns off every home surface (pinned apps row, A-Z index, app
 * drawer, widget pane) and turns on "show in recents when not the default launcher", which is what
 * a terminal-only install needs to stay reachable from the task switcher.
 *
 * <p>The mode is <em>not</em> a lock. Each surface stays individually settable afterwards, so a
 * terminal-only user who later wants just the app drawer can have it. The mode is what the user
 * last chose, stored as its own preference — deriving it from the surfaces was tried and is wrong
 * twice over: the indicator jumped to the other mode as soon as a single surface was re-enabled,
 * and the next switch then snapshotted that half-on state, so switching back restored a layout
 * with the apps row still missing.
 *
 * <p>The surface states from before the switch are captured in a snapshot preference so switching
 * back restores the user's layout rather than the factory defaults. Same shape as
 * {@code TermuxActivity.applyWallpaperModePreferences}, which stashes the wallpaper-era opacities
 * the same way. Re-picking the mode the app is already in does nothing, so the snapshot can never
 * be overwritten with a state the mode itself produced.
 */
public final class LauncherUseCaseMode {

    /** Home launcher plus terminal — the shipped default. */
    public static final String MODE_LAUNCHER = "launcher";

    /** Terminal only: every home surface off, visible in recents. */
    public static final String MODE_TERMINAL = "terminal";

    private static final String SNAPSHOT_SEPARATOR = ",";
    private static final int SNAPSHOT_FIELDS = 5;

    private LauncherUseCaseMode() {}

    /**
     * The mode the user picked. The extra-keys row is deliberately outside it either way: that row
     * drives the terminal, not the home screen, and a terminal-only user wants it more, not less.
     */
    public static boolean isTerminalOnly(@NonNull TermuxAppSharedPreferences preferences) {
        return preferences.isTerminalOnlyUseCase();
    }

    /** The mode value the settings segmented control should show. */
    @NonNull
    public static String currentMode(@NonNull TermuxAppSharedPreferences preferences) {
        return isTerminalOnly(preferences) ? MODE_TERMINAL : MODE_LAUNCHER;
    }

    /** Applies {@link #MODE_LAUNCHER} or {@link #MODE_TERMINAL}; any other value is ignored. */
    public static void applyMode(@NonNull TermuxAppSharedPreferences preferences, String mode) {
        if (MODE_TERMINAL.equals(mode)) {
            applyTerminalOnly(preferences, true);
        } else if (MODE_LAUNCHER.equals(mode)) {
            applyTerminalOnly(preferences, false);
        }
    }

    /**
     * Switches the use case. Turning terminal-only on captures the current surface states first;
     * turning it off restores that capture, falling back to the shipped defaults when there is
     * nothing to restore (a fresh install that started out terminal-only). Picking the mode the
     * app is already in is a no-op, so a repeated tap cannot overwrite the snapshot.
     */
    public static void applyTerminalOnly(@NonNull TermuxAppSharedPreferences preferences,
                                         boolean terminalOnly) {
        if (terminalOnly == isTerminalOnly(preferences)) return;
        preferences.setAppLauncherUseCaseMode(terminalOnly ? MODE_TERMINAL : MODE_LAUNCHER);
        if (terminalOnly) {
            preferences.setAppLauncherUseCaseSnapshot(captureSnapshot(preferences));
            preferences.setAppLauncherAppsRowEnabled(false);
            preferences.setAppLauncherAzRowEnabled(false);
            preferences.setAppLauncherDrawerEnabled(false);
            preferences.setAppLauncherWidgetPaneEnabled(false);
            preferences.setShowInRecentsWhenNotDefaultEnabled(true);
            return;
        }

        boolean[] snapshot = parseSnapshot(preferences.getAppLauncherUseCaseSnapshot());
        if (snapshot == null) {
            preferences.setAppLauncherAppsRowEnabled(true);
            preferences.setAppLauncherAzRowEnabled(true);
            preferences.setAppLauncherDrawerEnabled(true);
            preferences.setAppLauncherWidgetPaneEnabled(true);
            return;
        }
        preferences.setAppLauncherAppsRowEnabled(snapshot[0]);
        preferences.setAppLauncherAzRowEnabled(snapshot[1]);
        preferences.setAppLauncherDrawerEnabled(snapshot[2]);
        preferences.setAppLauncherWidgetPaneEnabled(snapshot[3]);
        preferences.setShowInRecentsWhenNotDefaultEnabled(snapshot[4]);
        preferences.setAppLauncherUseCaseSnapshot(null);
    }

    @NonNull
    static String captureSnapshot(@NonNull TermuxAppSharedPreferences preferences) {
        // The A-Z choice is captured raw: it is hidden while the apps row is off, and a mode
        // round trip must not quietly rewrite what the user picked for it.
        return encode(preferences.isAppLauncherAppsRowEnabled())
            + SNAPSHOT_SEPARATOR + encode(preferences.isAppLauncherAzRowChosen())
            + SNAPSHOT_SEPARATOR + encode(preferences.isAppLauncherDrawerEnabled())
            + SNAPSHOT_SEPARATOR + encode(preferences.isAppLauncherWidgetPaneEnabled())
            + SNAPSHOT_SEPARATOR + encode(preferences.isShowInRecentsWhenNotDefaultEnabled());
    }

    /** Returns null for an absent or malformed snapshot, so callers fall back to the defaults. */
    static boolean[] parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return null;
        String[] fields = snapshot.split(SNAPSHOT_SEPARATOR, -1);
        if (fields.length != SNAPSHOT_FIELDS) return null;
        boolean[] values = new boolean[SNAPSHOT_FIELDS];
        for (int i = 0; i < SNAPSHOT_FIELDS; i++) {
            String field = fields[i].trim();
            if (!"0".equals(field) && !"1".equals(field)) return null;
            values[i] = "1".equals(field);
        }
        return values;
    }

    private static String encode(boolean value) {
        return value ? "1" : "0";
    }
}
