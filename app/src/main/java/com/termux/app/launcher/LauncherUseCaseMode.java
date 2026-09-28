package com.termux.app.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The usage mode: how much of the launcher this install is.
 *
 * <p>Three nested presets. {@link #MODE_TERMINAL} is the terminal alone: every home surface off,
 * the display off, and the app visible in Recents so it stays reachable from the task switcher.
 * {@link #MODE_HOME} adds the home screen — the pinned apps row, the A-Z index, the app drawer and
 * the widget pane. {@link #MODE_DISPLAY} adds the embedded Linux display to that. A build made
 * without the X server never offers the third; a stored {@code display} reads as {@code home}
 * there.
 *
 * <p>The mode is a <em>preset</em>, not a lock. Picking one writes the real surface switches, and
 * each of them stays individually settable afterwards, so a terminal-only user who later wants
 * just the app drawer can have it. The mode is what the user last chose, stored as its own
 * preference — deriving it from the surfaces was tried and is wrong twice over: the indicator
 * jumped to another mode as soon as a single surface was re-enabled, and the next switch then
 * snapshotted that half-on state, so switching back restored a layout with the apps row still
 * missing. What the surfaces spell right now is {@link #modeMatchingSwitches}; the settings row
 * shows the stored mode while the two agree and "Custom" once they part.
 *
 * <p>The apps row is a per-orientation placement now, not one global switch, so leaving terminal
 * mode does not try to restore whatever it was before — it puts the row back at the shipped
 * default (bottom in portrait, the left rail in landscape). The other home surfaces are captured
 * in a snapshot preference on the way into terminal mode so the way out restores the user's
 * choice for them rather than the factory default. Same shape as
 * {@code TermuxActivity.applyWallpaperModePreferences}, which stashes the wallpaper-era opacities
 * the same way. Re-picking the mode the app is already in does nothing, so the snapshot can never
 * be overwritten with a state the mode itself produced.
 *
 * <p>The older two-way switch stored {@code launcher} for everything that was not the terminal
 * alone. It is mapped once, on read: {@code display} when the display was switched on under it,
 * {@code home} otherwise. Anything unrecognised is {@code home}, the shipped default.
 */
public final class LauncherUseCaseMode {

    /** Terminal only: every home surface off, the display off, visible in recents. */
    public static final String MODE_TERMINAL = "terminal";

    /** Terminal plus the home screen — the shipped default. */
    public static final String MODE_HOME = "home";

    /** Terminal, home screen and the Linux display. */
    public static final String MODE_DISPLAY = "display";

    /** What the two-way switch wrote for "launcher"; read and mapped, never written again. */
    static final String LEGACY_MODE_LAUNCHER = "launcher";

    private static final String SNAPSHOT_SEPARATOR = ",";
    private static final int SNAPSHOT_FIELDS = 4;

    private LauncherUseCaseMode() {}

    /** Whether this build carries the display server, and so may offer {@link #MODE_DISPLAY}. */
    public static boolean isDisplayOffered() {
        return com.termux.BuildConfig.X11_SERVER;
    }

    /** The modes the picker offers, in order; the display only in a build that carries one. */
    @NonNull
    public static List<String> offeredModes(boolean displayOffered) {
        List<String> modes = new ArrayList<>(3);
        modes.add(MODE_TERMINAL);
        modes.add(MODE_HOME);
        if (displayOffered) modes.add(MODE_DISPLAY);
        return Collections.unmodifiableList(modes);
    }

    /**
     * The mode the user picked, mapped from whatever the store holds. The extra-keys row is
     * deliberately outside every mode: that row drives the terminal, not the home screen, and a
     * terminal-only user wants it more, not less.
     */
    @NonNull
    public static String currentMode(@NonNull TermuxAppSharedPreferences preferences) {
        return currentMode(preferences, isDisplayOffered());
    }

    @NonNull
    public static String currentMode(@NonNull TermuxAppSharedPreferences preferences,
                                     boolean displayOffered) {
        return resolve(preferences.getAppLauncherUseCaseMode(), preferences.isX11DisplayEnabled(),
            displayOffered);
    }

    /**
     * What a stored value means: the three modes themselves, the older switch's {@code launcher}
     * by whether the display was on under it, and anything else as the shipped default.
     */
    @NonNull
    static String resolve(@Nullable String stored, boolean displayOn, boolean displayOffered) {
        if (MODE_TERMINAL.equals(stored)) return MODE_TERMINAL;
        if (MODE_HOME.equals(stored)) return MODE_HOME;
        if (MODE_DISPLAY.equals(stored)) return displayOffered ? MODE_DISPLAY : MODE_HOME;
        if (LEGACY_MODE_LAUNCHER.equals(stored)) {
            return displayOffered && displayOn ? MODE_DISPLAY : MODE_HOME;
        }
        return MODE_HOME;
    }

    /**
     * Writes the mapped value over a legacy or unknown one, once, so the store and every reader
     * say the same thing from then on. A recognised value is left exactly as it is.
     */
    public static void migrateIfNeeded(@NonNull TermuxAppSharedPreferences preferences,
                                       boolean displayOffered) {
        String stored = preferences.getAppLauncherUseCaseMode();
        if (MODE_TERMINAL.equals(stored) || MODE_HOME.equals(stored) || MODE_DISPLAY.equals(stored))
            return;
        preferences.setAppLauncherUseCaseMode(
            resolve(stored, preferences.isX11DisplayEnabled(), displayOffered));
    }

    public static boolean isTerminalOnly(@NonNull TermuxAppSharedPreferences preferences) {
        return MODE_TERMINAL.equals(currentMode(preferences));
    }

    /** Applies a mode with this build's display on offer; see {@link #applyMode(TermuxAppSharedPreferences, String, boolean)}. */
    public static boolean applyMode(@NonNull TermuxAppSharedPreferences preferences,
                                    @Nullable String mode) {
        return applyMode(preferences, mode, isDisplayOffered());
    }

    /**
     * Switches the mode, writing every surface switch the preset owns. Going into terminal mode
     * captures the home surfaces first; coming out of it restores that capture, falling back to
     * the shipped defaults when there is nothing to restore (a fresh install that started out
     * terminal-only). Between home and display only the display switch moves. Picking the mode
     * the app is already in is a no-op, so a repeated tap cannot overwrite the snapshot.
     *
     * @param displayOffered whether this build carries the display; without one {@code display}
     *                       is applied as {@code home}
     * @return whether any switch changed
     */
    public static boolean applyMode(@NonNull TermuxAppSharedPreferences preferences,
                                    @Nullable String mode, boolean displayOffered) {
        if (!isMode(mode)) return false;
        String target = resolve(mode, false, displayOffered);
        String current = currentMode(preferences, displayOffered);
        if (target.equals(current)) {
            // Same mode, possibly spelled the old way: record the mapped value and stop.
            if (!target.equals(preferences.getAppLauncherUseCaseMode()))
                preferences.setAppLauncherUseCaseMode(target);
            return false;
        }
        preferences.setAppLauncherUseCaseMode(target);
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        if (MODE_TERMINAL.equals(target)) {
            preferences.setAppLauncherUseCaseSnapshot(captureSnapshot(preferences));
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                places.setAppsRow(orientation, RowPlacement.HIDDEN);
            }
            setAzRow(preferences, places, false);
            preferences.setAppLauncherDrawerEnabled(false);
            preferences.setAppLauncherWidgetPaneEnabled(false);
            preferences.setShowInRecentsWhenNotDefaultEnabled(true);
            preferences.setX11DisplayEnabled(false);
            return true;
        }
        if (MODE_TERMINAL.equals(current)) restoreHomeSurfaces(preferences, places);
        preferences.setX11DisplayEnabled(MODE_DISPLAY.equals(target));
        return true;
    }

    /** The way out of terminal mode: the snapshot, or the shipped defaults without one. */
    private static void restoreHomeSurfaces(@NonNull TermuxAppSharedPreferences preferences,
                                            @NonNull PlaceLayoutStore places) {
        // The apps row is a layout value now; there is nothing to restore it to but the shipped
        // default — bottom in portrait, the left rail in landscape.
        places.setAppsRow(PlaceOrientation.PORTRAIT, RowPlacement.BOTTOM);
        places.setAppsRow(PlaceOrientation.LANDSCAPE, RowPlacement.LEFT);

        boolean[] snapshot = parseSnapshot(preferences.getAppLauncherUseCaseSnapshot());
        if (snapshot == null) {
            setAzRow(preferences, places, true);
            preferences.setAppLauncherDrawerEnabled(true);
            preferences.setAppLauncherWidgetPaneEnabled(true);
            return;
        }
        setAzRow(preferences, places, snapshot[0]);
        preferences.setAppLauncherDrawerEnabled(snapshot[1]);
        preferences.setAppLauncherWidgetPaneEnabled(snapshot[2]);
        preferences.setShowInRecentsWhenNotDefaultEnabled(snapshot[3]);
        preferences.setAppLauncherUseCaseSnapshot(null);
    }

    /**
     * The A-Z row is one global switch and one layout key per orientation, and the layout key wins
     * when it is set — so a mode writes both, or a row the Layout editor once switched on would
     * come back over a terminal-only install.
     */
    private static void setAzRow(@NonNull TermuxAppSharedPreferences preferences,
                                 @NonNull PlaceLayoutStore places, boolean shown) {
        preferences.setAppLauncherAzRowEnabled(shown);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            places.setAzRowShown(orientation, shown);
        }
    }

    /**
     * The Display switch was moved on its own, from Settings → Display, the first-run card or the
     * page's own button. Between the two home presets the mode follows it: a home-screen install
     * that turns the display on is a display install now, and one that turns it off is not. A
     * terminal-only choice is left as it is; the settings row reads "Custom" until the switches
     * agree with a preset again.
     */
    public static void onDisplaySwitched(@NonNull TermuxAppSharedPreferences preferences,
                                         boolean enabled, boolean displayOffered) {
        String current = currentMode(preferences, displayOffered);
        if (enabled && displayOffered && MODE_HOME.equals(current)) {
            preferences.setAppLauncherUseCaseMode(MODE_DISPLAY);
        } else if (!enabled && MODE_DISPLAY.equals(current)) {
            preferences.setAppLauncherUseCaseMode(MODE_HOME);
        }
    }

    /**
     * The preset the surface switches spell right now, or null when they spell none. A home
     * surface on — the apps row on either orientation, the A-Z index, the drawer or the widget
     * pane — is a home screen; the display on top of it is the display mode; nothing on is the
     * terminal alone. The display on with no home screen under it matches no preset.
     */
    @Nullable
    public static String modeMatchingSwitches(@NonNull TermuxAppSharedPreferences preferences,
                                              boolean displayOffered) {
        boolean home = anyHomeSurfaceOn(preferences);
        boolean display = displayOffered && preferences.isX11DisplayEnabled();
        if (!home) return display ? null : MODE_TERMINAL;
        return display ? MODE_DISPLAY : MODE_HOME;
    }

    /**
     * The mode the settings row names: the stored one while the switches still spell it, or null
     * — "Custom" — once a switch below has moved them off it.
     */
    @Nullable
    public static String summaryMode(@NonNull TermuxAppSharedPreferences preferences,
                                     boolean displayOffered) {
        String stored = currentMode(preferences, displayOffered);
        return stored.equals(modeMatchingSwitches(preferences, displayOffered)) ? stored : null;
    }

    static boolean anyHomeSurfaceOn(@NonNull TermuxAppSharedPreferences preferences) {
        if (preferences.isAppLauncherDrawerEnabled()) return true;
        if (preferences.isAppLauncherWidgetPaneEnabled()) return true;
        PlaceLayoutStore places = new PlaceLayoutStore(preferences);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            if (places.appsRow(orientation) != RowPlacement.HIDDEN) return true;
            if (places.azRowShown(orientation)) return true;
        }
        return false;
    }

    /** The row's short name for a mode; null is the "Custom" the row shows for no preset. */
    @StringRes
    public static int titleRes(@Nullable String mode) {
        if (MODE_TERMINAL.equals(mode)) return R.string.settings_use_as_terminal;
        if (MODE_HOME.equals(mode)) return R.string.settings_use_as_home;
        if (MODE_DISPLAY.equals(mode)) return R.string.settings_use_as_display;
        return R.string.settings_use_as_custom;
    }

    /** The one line under each choice in the picker. */
    @StringRes
    public static int descriptionRes(@NonNull String mode) {
        if (MODE_TERMINAL.equals(mode)) return R.string.settings_use_as_terminal_description;
        if (MODE_DISPLAY.equals(mode)) return R.string.settings_use_as_display_description;
        return R.string.settings_use_as_home_description;
    }

    private static boolean isMode(@Nullable String value) {
        return MODE_TERMINAL.equals(value) || MODE_HOME.equals(value) || MODE_DISPLAY.equals(value);
    }

    @NonNull
    static String captureSnapshot(@NonNull TermuxAppSharedPreferences preferences) {
        // The A-Z choice is captured raw: it is hidden while the apps row is off, and a mode
        // round trip must not quietly rewrite what the user picked for it.
        return encode(preferences.isAppLauncherAzRowChosen())
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
