package com.termux.app;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lands an install from before the wall rework (ADR 0008) in the new model, once, at the first
 * start of a build that has it. Run from {@link TermuxApplication#onCreate()} before any activity
 * reads a preference.
 *
 * <p>Version 1:
 * <ul>
 *   <li>The usage mode becomes Start in: the terminal alone starts in the Workspace, the other two
 *   (and the older two-way switch's {@code launcher}) start on Home. A Start in that is already
 *   stored is the user's and stands. The mode and the snapshot it kept of the home surfaces go.</li>
 *   <li>The Display switch goes: the display comes up on demand now.</li>
 *   <li>The place the wall last rested on goes: a cold start reads Start in.</li>
 *   <li>Place-scoped keys left from before ADR 0003 go, except the keyboard memory the terminal
 *   and the display still keep. Only once {@code PlaceLayoutStore} has folded them (its version 6
 *   or later); before that its own migration folds and drops them, and removing them here first
 *   would lose the values it folds.</li>
 * </ul>
 *
 * <p>Not here, on purpose: the normal and minimal layouts are the Workspace layouts as they are,
 * the Home layout seeds itself from the normal one on its first read (after {@code
 * PlaceLayoutStore} has migrated, which its constructor does first), and the Terminal entry's dock
 * pin is {@code TerminalEntryPin}'s, guarded by its own once-flag so it can neither pin twice nor
 * put back an entry the user took off.
 */
public final class WallReworkMigration {

    /** Bumped when a new set of old keys has to be folded into the current ones. */
    @VisibleForTesting static final int VERSION = 1;

    /** {@code PlaceLayoutStore.KEY_MIGRATED}, and the version that folded the place-scoped keys. */
    @VisibleForTesting static final String KEY_PLACE_MIGRATED = "place.migrated";
    private static final int PLACE_SHARED_LAYOUT_VERSION = 6;

    /** {@code PlaceLayoutStore.placeKey} of each wall page, under its {@code place.} prefix. */
    private static final List<String> PLACE_PREFIXES = Collections.unmodifiableList(
        Arrays.asList("place.home.", "place.terminal.", "place.display."));

    /** The place-scoped keys still read: {@code PlaceLayoutStore.wasKeyboardOpen}. */
    private static final Set<String> PLACE_KEYS_KEPT = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList("place.terminal.keyboard_open", "place.display.keyboard_open")));

    private WallReworkMigration() {}

    /**
     * Runs the steps this store has not had yet and records the version; a store already at
     * {@link #VERSION} is left alone.
     *
     * @return whether anything ran
     */
    public static boolean migrateIfNeeded(@Nullable SharedPreferences store) {
        if (store == null) return false;
        int fromVersion = store.getInt(TERMUX_APP.KEY_WALL_MIGRATION_VERSION, 0);
        if (fromVersion >= VERSION) return false;
        SharedPreferences.Editor editor = store.edit();
        if (fromVersion < 1) migrateToStartIn(store, editor);
        editor.putInt(TERMUX_APP.KEY_WALL_MIGRATION_VERSION, VERSION);
        editor.apply();
        return true;
    }

    /** Version 1: the usage mode, the Display switch and the place memory leave (ADR 0008). */
    private static void migrateToStartIn(@NonNull SharedPreferences store,
                                         @NonNull SharedPreferences.Editor editor) {
        if (store.contains(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE)
            && !store.contains(TERMUX_APP.KEY_LAUNCHER_START_IN)) {
            editor.putString(TERMUX_APP.KEY_LAUNCHER_START_IN,
                startInFor(store.getString(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE, null)));
        }
        // Auto-start used to need the Display switch as well. With the switch gone the opt-in
        // stands alone, so one left behind under a switch that was off must not wake up and
        // start a server on every launch.
        if (!store.getBoolean(TERMUX_APP.KEY_X11_DISPLAY_ENABLED, false)) {
            editor.remove(TERMUX_APP.KEY_X11_DISPLAY_AUTOSTART);
        }
        editor.remove(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_MODE);
        editor.remove(TERMUX_APP.KEY_APP_LAUNCHER_USE_CASE_SNAPSHOT);
        editor.remove(TERMUX_APP.KEY_X11_DISPLAY_ENABLED);
        editor.remove(TERMUX_APP.KEY_WALL_LAST_PAGE);
        if (store.getInt(KEY_PLACE_MIGRATED, 0) >= PLACE_SHARED_LAYOUT_VERSION) {
            for (String key : store.getAll().keySet()) {
                if (isRetiredPlaceKey(key)) editor.remove(key);
            }
        }
    }

    /** Terminal alone starts in the Workspace; everything else, known or not, on Home. */
    @VisibleForTesting
    @NonNull
    static String startInFor(@Nullable String usageMode) {
        return usageMode != null
            && TERMUX_APP.APP_LAUNCHER_USE_CASE_MODE_TERMINAL.equals(usageMode.trim())
            ? TERMUX_APP.LAUNCHER_START_IN_WORKSPACE : TERMUX_APP.LAUNCHER_START_IN_HOME;
    }

    @VisibleForTesting
    static boolean isRetiredPlaceKey(@NonNull String key) {
        if (PLACE_KEYS_KEPT.contains(key)) return false;
        for (String prefix : PLACE_PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }
}
