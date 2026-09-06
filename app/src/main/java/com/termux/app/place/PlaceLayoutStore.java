package com.termux.app.place;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Locale;

/**
 * Where every place keeps its arrangement. Anything that decides what is on screen and where is a
 * property of a place — the widget grid, the terminal, the Linux display — and, for arrangement, of
 * the orientation as well.
 *
 * <p>Keys are scoped: {@code place.<home|terminal|display>.<portrait|landscape>.<key>} for
 * arrangement, {@code place.<place>.<key>} for the things a place only remembers. A missing scoped
 * key falls back to the shared value the launcher used to keep globally, and then to the shipped
 * default, so nothing has to be written before a place reads the way it always looked.
 *
 * <p>The rows that can still be switched off globally — the pinned apps row, the alphabets row and
 * the extra keys — keep those switches as a master gate: a scoped value only says <em>where</em> a
 * row goes, never that a switched-off row comes back. Phase 2 gives every place its own Hidden and
 * the global switches go away.
 *
 * <p>No Android views here, on purpose: this is a resolver over {@link SharedPreferences} and it is
 * tested as one.
 */
public final class PlaceLayoutStore {

    /** Bumped when a new set of old keys has to be folded into the scoped ones. */
    @VisibleForTesting static final int MIGRATION_VERSION = 1;

    @VisibleForTesting static final String KEY_MIGRATED = "place.migrated";

    private static final String PREFIX = "place.";

    private static final String KEY_STATUS_BAR = "status_bar";
    private static final String KEY_APPS_ROW = "apps_row";
    private static final String KEY_AZ_ROW = "az_row";
    private static final String KEY_EXTRA_KEYS = "extra_keys";
    private static final String KEY_KEYBOARD_MODE = "keyboard_mode";
    private static final String KEY_WIDGET_COLUMNS = "widget_columns";
    private static final String KEY_WIDGET_ROWS = "widget_rows";

    private static final String KEY_STATUS_COMPACT = "status_compact";
    private static final String KEY_KEYBOARD_ON_ENTER = "keyboard_on_enter";
    private static final String KEY_KEYBOARD_OPEN = "keyboard_open";

    /** The launcher's status-bar hide switch for the display, dropped with the hidden state. */
    private static final String LEGACY_KEY_X11_HIDE_STATUS_BAR = "x11_hide_status_bar";

    private static final String[] ARRANGEMENT_KEYS = {
        KEY_STATUS_BAR, KEY_APPS_ROW, KEY_AZ_ROW, KEY_EXTRA_KEYS, KEY_KEYBOARD_MODE,
        KEY_WIDGET_COLUMNS, KEY_WIDGET_ROWS
    };

    @NonNull private final TermuxAppSharedPreferences mPreferences;
    @Nullable private final SharedPreferences mStore;

    private int mRevision;

    /**
     * Any write to the launcher's preferences can change what a place resolves to — the scoped keys
     * and the shared ones a missing key falls back to alike — so every one of them retires a
     * cached layout.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener =
        (preferences, key) -> mRevision++;

    public PlaceLayoutStore(@NonNull TermuxAppSharedPreferences preferences) {
        mPreferences = preferences;
        mStore = preferences.getSharedPreferences();
        migrateIfNeeded();
        if (mStore != null) mStore.registerOnSharedPreferenceChangeListener(mChangeListener);
    }

    /**
     * Counts changes to anything a resolved layout is a function of. A caller that holds a
     * {@link PlaceLayout} can keep it while this has not moved.
     */
    public int revision() {
        return mRevision;
    }

    // ---------------------------------------------------------------- arrangement

    /**
     * The one immutable answer for a place in an orientation. The getters below say where a row is
     * arranged to go; this is where the switches that can still turn a row off entirely are folded
     * in, so a caller reads one effective layout and nothing else.
     */
    @NonNull
    public PlaceLayout resolve(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        RowPlacement appsRow = mPreferences.isAppLauncherAppsRowEnabled()
            ? appsRow(place, orientation) : RowPlacement.HIDDEN;
        RowPlacement extraKeys = mPreferences.isAppLauncherExtraKeysRowEnabled()
            && mPreferences.shouldShowTerminalToolbar()
            ? extraKeys(place, orientation) : RowPlacement.HIDDEN;
        return new PlaceLayout(
            statusBarEdge(place, orientation),
            appsRow,
            azRowShown(place, orientation),
            extraKeys,
            keyboardMode(place, orientation),
            widgetColumns(place, orientation),
            widgetRows(place, orientation));
    }

    /** Always an edge: the bar moves, it never goes away, so the wall's pager always has a grip. */
    @NonNull
    public Edge statusBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return Edge.parse(readString(place, orientation, KEY_STATUS_BAR), Edge.TOP);
    }

    public void setStatusBarEdge(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                 @NonNull Edge edge) {
        writeString(place, orientation, KEY_STATUS_BAR, edge.storageValue());
    }

    /**
     * Where the pinned apps stand. Landscape defaults to a column on the left — the rail every
     * landscape session has had — and portrait to the row along the bottom.
     */
    @NonNull
    public RowPlacement appsRow(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return RowPlacement.parse(readString(place, orientation, KEY_APPS_ROW),
            orientation == PlaceOrientation.LANDSCAPE ? RowPlacement.LEFT : RowPlacement.BOTTOM);
    }

    public void setAppsRow(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                           @NonNull RowPlacement placement) {
        writeString(place, orientation, KEY_APPS_ROW, placement.storageValue());
    }

    /** The alphabets row is a switch, not a place: it rides on the apps row wherever that goes. */
    public boolean azRowShown(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        String key = arrangementKey(place, orientation, KEY_AZ_ROW);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, true);
        return mPreferences.isAppLauncherAzRowEnabled();
    }

    public void setAzRowShown(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              boolean shown) {
        writeBoolean(arrangementKey(place, orientation, KEY_AZ_ROW), shown);
    }

    /** Where the extra keys stand when they are shown at all. */
    @NonNull
    public RowPlacement extraKeys(@NonNull PaneWallPage place,
                                  @NonNull PlaceOrientation orientation) {
        return RowPlacement.parse(readString(place, orientation, KEY_EXTRA_KEYS),
            RowPlacement.BOTTOM);
    }

    public void setExtraKeys(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull RowPlacement placement) {
        writeString(place, orientation, KEY_EXTRA_KEYS, placement.storageValue());
    }

    /**
     * The display in landscape is the one place a keyboard should float over rather than squeeze:
     * it has a fixed screen of its own and nothing to reflow.
     */
    @NonNull
    public KeyboardMode keyboardMode(@NonNull PaneWallPage place,
                                     @NonNull PlaceOrientation orientation) {
        KeyboardMode fallback = place == PaneWallPage.DISPLAY
            && orientation == PlaceOrientation.LANDSCAPE ? KeyboardMode.OVERLAY : KeyboardMode.RESIZE;
        return KeyboardMode.parse(readString(place, orientation, KEY_KEYBOARD_MODE), fallback);
    }

    public void setKeyboardMode(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                                @NonNull KeyboardMode mode) {
        writeString(place, orientation, KEY_KEYBOARD_MODE, mode.storageValue());
    }

    public int widgetColumns(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return clamp(readInt(place, orientation, KEY_WIDGET_COLUMNS,
                mPreferences.getAppLauncherWidgetGridColumns()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS);
    }

    public void setWidgetColumns(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, int columns) {
        writeInt(arrangementKey(place, orientation, KEY_WIDGET_COLUMNS),
            clamp(columns, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS));
    }

    public int widgetRows(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        return clamp(readInt(place, orientation, KEY_WIDGET_ROWS,
                mPreferences.getAppLauncherWidgetGridRows()),
            TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);
    }

    public void setWidgetRows(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              int rows) {
        writeInt(arrangementKey(place, orientation, KEY_WIDGET_ROWS),
            clamp(rows, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS));
    }

    /** Puts one place's orientation back to whatever the shared values and defaults say. */
    public void clear(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        if (mStore == null) return;
        SharedPreferences.Editor editor = mStore.edit();
        for (String key : ARRANGEMENT_KEYS) editor.remove(arrangementKey(place, orientation, key));
        editor.apply();
        mRevision++;
    }

    // ---------------------------------------------------------------- memory

    /** Whether the place was left with the status bar compact. */
    public boolean isStatusCompact(@NonNull PaneWallPage place) {
        String key = memoryKey(place, KEY_STATUS_COMPACT);
        if (mStore != null && mStore.contains(key)) return mStore.getBoolean(key, false);
        return mPreferences.isTopPaneClockCollapsed();
    }

    public void setStatusCompact(@NonNull PaneWallPage place, boolean compact) {
        writeBoolean(memoryKey(place, KEY_STATUS_COMPACT), compact);
    }

    /**
     * How the place wants its keyboard on the way in. The widget grid has nothing to type into, so
     * it comes back closed; everywhere else comes back the way it was left.
     */
    @NonNull
    public KeyboardOnEnter keyboardOnEnter(@NonNull PaneWallPage place) {
        KeyboardOnEnter fallback = place == PaneWallPage.WIDGETS
            ? KeyboardOnEnter.CLOSED : KeyboardOnEnter.AS_LEFT;
        return KeyboardOnEnter.parse(readString(memoryKey(place, KEY_KEYBOARD_ON_ENTER)), fallback);
    }

    public void setKeyboardOnEnter(@NonNull PaneWallPage place, @NonNull KeyboardOnEnter onEnter) {
        writeString(memoryKey(place, KEY_KEYBOARD_ON_ENTER), onEnter.storageValue());
    }

    /** Whether the place was left with the keyboard up. */
    public boolean wasKeyboardOpen(@NonNull PaneWallPage place) {
        String key = memoryKey(place, KEY_KEYBOARD_OPEN);
        return mStore != null && mStore.getBoolean(key, false);
    }

    public void setKeyboardOpen(@NonNull PaneWallPage place, boolean open) {
        writeBoolean(memoryKey(place, KEY_KEYBOARD_OPEN), open);
    }

    // ---------------------------------------------------------------- keys

    @VisibleForTesting
    @NonNull
    static String placeKey(@NonNull PaneWallPage place) {
        // The widget grid is the home screen everywhere a user can see it; only the enum says
        // WIDGETS.
        return place == PaneWallPage.WIDGETS ? "home" : place.name().toLowerCase(Locale.ROOT);
    }

    @VisibleForTesting
    @NonNull
    static String arrangementKey(@NonNull PaneWallPage place,
                                 @NonNull PlaceOrientation orientation, @NonNull String key) {
        return PREFIX + placeKey(place) + "." + orientation.storageValue() + "." + key;
    }

    @VisibleForTesting
    @NonNull
    static String memoryKey(@NonNull PaneWallPage place, @NonNull String key) {
        return PREFIX + placeKey(place) + "." + key;
    }

    // ---------------------------------------------------------------- migration

    /**
     * Folds the launcher's old single-place keys into the scoped ones, once. Everything here was a
     * global that only ever described one place, or one place at a time.
     */
    private void migrateIfNeeded() {
        if (mStore == null) return;
        if (mStore.getInt(KEY_MIGRATED, 0) >= MIGRATION_VERSION) return;
        SharedPreferences.Editor editor = mStore.edit();

        // The landscape rail's edge was the only place-and-orientation setting the launcher had.
        // It described every place, because there was only one rail.
        if (mStore.contains(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE)) {
            RowPlacement side = RowPlacement.parse(
                mStore.getString(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_RAIL_SIDE, null),
                RowPlacement.LEFT);
            if (!side.isOnSide()) side = RowPlacement.LEFT;
            for (PaneWallPage place : PaneWallPage.values()) {
                editor.putString(arrangementKey(place, PlaceOrientation.LANDSCAPE, KEY_APPS_ROW),
                    side.storageValue());
            }
        }

        // The display's extra keys column, which is now every place's.
        if (mStore.contains(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE)) {
            RowPlacement side = RowPlacement.parse(
                mStore.getString(TERMUX_APP.KEY_X11_EXTRA_KEYS_SIDE, null), RowPlacement.BOTTOM);
            if (side == RowPlacement.HIDDEN) side = RowPlacement.BOTTOM;
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                editor.putString(arrangementKey(PaneWallPage.DISPLAY, orientation, KEY_EXTRA_KEYS),
                    side.storageValue());
            }
        }

        // There is no hidden status bar any more: the bar moves instead, so the wall's paging
        // gesture survives every arrangement.
        editor.remove(LEGACY_KEY_X11_HIDE_STATUS_BAR);

        if (mStore.contains(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN)) {
            editor.putBoolean(memoryKey(PaneWallPage.DISPLAY, KEY_KEYBOARD_OPEN),
                mStore.getBoolean(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN, false));
            editor.remove(TERMUX_APP.KEY_X11_KEYBOARD_SHOWN);
        }

        if (mStore.contains(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED)) {
            boolean compact = mStore.getBoolean(TERMUX_APP.KEY_TOP_PANE_CLOCK_COLLAPSED, false);
            for (PaneWallPage place : PaneWallPage.values()) {
                editor.putBoolean(memoryKey(place, KEY_STATUS_COMPACT), compact);
            }
        }

        editor.putInt(KEY_MIGRATED, MIGRATION_VERSION);
        editor.apply();
        mRevision++;
    }

    // ---------------------------------------------------------------- plumbing

    @Nullable
    private String readString(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                              @NonNull String key) {
        return readString(arrangementKey(place, orientation, key));
    }

    @Nullable
    private String readString(@NonNull String key) {
        return mStore == null ? null : mStore.getString(key, null);
    }

    private int readInt(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                        @NonNull String key, int fallback) {
        String scoped = arrangementKey(place, orientation, key);
        return mStore != null && mStore.contains(scoped) ? mStore.getInt(scoped, fallback) : fallback;
    }

    private void writeString(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation,
                             @NonNull String key, @NonNull String value) {
        writeString(arrangementKey(place, orientation, key), value);
    }

    private void writeString(@NonNull String key, @NonNull String value) {
        if (mStore == null) return;
        mStore.edit().putString(key, value).apply();
        mRevision++;
    }

    private void writeBoolean(@NonNull String key, boolean value) {
        if (mStore == null) return;
        mStore.edit().putBoolean(key, value).apply();
        mRevision++;
    }

    private void writeInt(@NonNull String key, int value) {
        if (mStore == null) return;
        mStore.edit().putInt(key, value).apply();
        mRevision++;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
