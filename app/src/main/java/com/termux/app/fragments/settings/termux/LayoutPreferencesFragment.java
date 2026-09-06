package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.LayoutOverviewPreference;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.place.KeyboardOnEnter;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The Layout page: what is on screen and where, per place (Home / Terminal / Display) and, for
 * arrangement, per orientation. The overview at the top picks which place and orientation the
 * rows below describe; every row reads and writes {@link PlaceLayoutStore} for that selection.
 *
 * <p>The status bar edge row is wired all the way through the store, the same as every other row,
 * but kept invisible: the launcher does not render a status bar anywhere but the top yet
 * ({@link #SHOW_STATUS_BAR_EDGE_ROW}, phase 4).
 */
@Keep
public final class LayoutPreferencesFragment extends MaterialPreferenceFragment {

    /** Phase 4 (feat/status-bar-positions) flips this on. */
    static final boolean SHOW_STATUS_BAR_EDGE_ROW = false;
    /** The Display place is the only one a keyboard can float over, so the only one that offers it. */
    static final boolean SHOW_KEYBOARD_MODE_ROW = true;

    private static final String KEY_OVERVIEW = "layout_overview";
    private static final String KEY_STATUS_BAR = "layout_status_bar";
    private static final String KEY_APPS_ROW = "layout_apps_row";
    private static final String KEY_ALPHABETS_ROW = "layout_alphabets_row";
    private static final String KEY_EXTRA_KEYS = "layout_extra_keys";
    private static final String KEY_KEYBOARD_ON_ENTER = "layout_keyboard_on_enter";
    private static final String KEY_KEYBOARD_MODE = "layout_keyboard_mode";
    private static final String KEY_GRID_COLUMNS = "layout_grid_columns";
    private static final String KEY_GRID_ROWS = "layout_grid_rows";
    private static final String KEY_LOOK = "layout_look";

    private static final String STATE_PLACE = "layout_selected_place";
    private static final String STATE_ORIENTATION = "layout_selected_orientation";

    private static final String[] EDGE_VALUES = {"top", "bottom", "left", "right"};
    private static final int[] EDGE_LABELS = {
        R.string.settings_layout_edge_top, R.string.settings_x11_extra_keys_side_bottom,
        R.string.settings_dock_rail_side_left, R.string.settings_dock_rail_side_right};

    private static final String[] ROW_VALUES = {"bottom", "left", "right", "hidden"};
    private static final int[] ROW_LABELS = {
        R.string.settings_x11_extra_keys_side_bottom, R.string.settings_dock_rail_side_left,
        R.string.settings_dock_rail_side_right, R.string.settings_layout_row_hidden};

    private static final String[] KEYBOARD_ON_ENTER_VALUES = {"as_left", "open", "closed"};
    private static final int[] KEYBOARD_ON_ENTER_LABELS = {
        R.string.settings_layout_keyboard_on_enter_as_left,
        R.string.settings_layout_keyboard_on_enter_open,
        R.string.settings_layout_keyboard_on_enter_closed};

    private static final String[] KEYBOARD_MODE_VALUES = {"resize", "overlay"};
    private static final int[] KEYBOARD_MODE_LABELS = {
        R.string.settings_layout_keyboard_mode_resize, R.string.settings_layout_keyboard_mode_overlay};

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Nullable private LayoutPreferencesDataStore mStore;
    @NonNull private PaneWallPage mSelectedPlace = PaneWallPage.TERMINAL;
    @NonNull private PlaceOrientation mSelectedOrientation = PlaceOrientation.PORTRAIT;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        LayoutPreferencesDataStore store = new LayoutPreferencesDataStore(context, preferences);
        mStore = store;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(store);
        setPreferencesFromResource(R.xml.layout_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        mSelectedOrientation = PlaceOrientation.of(getResources().getConfiguration());
        if (savedInstanceState != null) {
            restoreSelection(savedInstanceState);
        }
        boolean displayAvailable = isDisplayAvailable(preferences);
        if (!displayAvailable && mSelectedPlace == PaneWallPage.DISPLAY) {
            mSelectedPlace = PaneWallPage.TERMINAL;
        }
        store.setSelection(mSelectedPlace, mSelectedOrientation);

        configureOverview(displayAvailable);
        configureRows();
        refreshRows();
    }

    private void restoreSelection(@NonNull Bundle savedInstanceState) {
        String placeName = savedInstanceState.getString(STATE_PLACE);
        String orientationName = savedInstanceState.getString(STATE_ORIENTATION);
        if (placeName != null) {
            try {
                mSelectedPlace = PaneWallPage.valueOf(placeName);
            } catch (IllegalArgumentException ignored) {
                // Stays at the default.
            }
        }
        if (orientationName != null) {
            try {
                mSelectedOrientation = PlaceOrientation.valueOf(orientationName);
            } catch (IllegalArgumentException ignored) {
                // Stays at the current device orientation.
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PLACE, mSelectedPlace.name());
        outState.putString(STATE_ORIENTATION, mSelectedOrientation.name());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_layout);
        Context context = getContext();
        if (context == null || mStore == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        boolean displayAvailable = isDisplayAvailable(preferences);
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview != null) overview.setDisplayTabVisible(displayAvailable);
        if (!displayAvailable && mSelectedPlace == PaneWallPage.DISPLAY) {
            mSelectedPlace = PaneWallPage.TERMINAL;
            if (overview != null) overview.setSelection(mSelectedPlace, mSelectedOrientation);
            mStore.setSelection(mSelectedPlace, mSelectedOrientation);
            refreshRows();
        }
    }

    private static boolean isDisplayAvailable(@NonNull TermuxAppSharedPreferences preferences) {
        return com.termux.BuildConfig.X11_SERVER && preferences.isX11DisplayEnabled();
    }

    private void configureOverview(boolean displayAvailable) {
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview == null) return;
        overview.setDisplayTabVisible(displayAvailable);
        overview.setSelection(mSelectedPlace, mSelectedOrientation);
        overview.setOnSelectionListener(new LayoutOverviewPreference.Listener() {
            @Override
            public void onSelectionChanged(@NonNull PaneWallPage place,
                                           @NonNull PlaceOrientation orientation) {
                mSelectedPlace = place;
                mSelectedOrientation = orientation;
                if (mStore != null) mStore.setSelection(place, orientation);
                refreshRows();
            }

            @Override
            public void onBlockTapped(@NonNull PlaceMiniatureView.Block block) {
                scrollToRow(rowKeyForBlock(block));
            }
        });
    }

    @NonNull
    private static String rowKeyForBlock(@NonNull PlaceMiniatureView.Block block) {
        switch (block) {
            case STATUS_BAR: return KEY_STATUS_BAR;
            case APPS_ROW: return KEY_APPS_ROW;
            case ALPHABETS_ROW: return KEY_ALPHABETS_ROW;
            case EXTRA_KEYS: return KEY_EXTRA_KEYS;
            case CANVAS:
            default: return KEY_LOOK;
        }
    }

    private void scrollToRow(@NonNull String key) {
        Preference preference = findPreference(key);
        if (preference == null || !preference.isVisible()) return;
        scrollToPreference(preference);
    }

    /** Wires every row once: segment sets, change listeners, and the Look row's deep link. */
    private void configureRows() {
        SegmentedPillPreference statusBar = findPreference(KEY_STATUS_BAR);
        if (statusBar != null) {
            statusBar.setSegments(EDGE_VALUES, EDGE_LABELS);
            statusBar.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SegmentedPillPreference appsRow = findPreference(KEY_APPS_ROW);
        if (appsRow != null) {
            appsRow.setSegments(ROW_VALUES, ROW_LABELS);
            appsRow.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SwitchPreferenceCompat alphabetsRow = findPreference(KEY_ALPHABETS_ROW);
        if (alphabetsRow != null) {
            alphabetsRow.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SegmentedPillPreference extraKeys = findPreference(KEY_EXTRA_KEYS);
        if (extraKeys != null) {
            extraKeys.setSegments(ROW_VALUES, ROW_LABELS);
            extraKeys.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SegmentedPillPreference keyboardOnEnter = findPreference(KEY_KEYBOARD_ON_ENTER);
        if (keyboardOnEnter != null) {
            keyboardOnEnter.setSegments(KEYBOARD_ON_ENTER_VALUES, KEYBOARD_ON_ENTER_LABELS);
            keyboardOnEnter.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SegmentedPillPreference keyboardMode = findPreference(KEY_KEYBOARD_MODE);
        if (keyboardMode != null) {
            keyboardMode.setSegments(KEYBOARD_MODE_VALUES, KEYBOARD_MODE_LABELS);
            keyboardMode.setOnPreferenceChangeListener(this::onRowChanged);
        }
        SeekBarPreference gridColumns = findPreference(KEY_GRID_COLUMNS);
        if (gridColumns != null) gridColumns.setOnPreferenceChangeListener(this::onRowChanged);
        SeekBarPreference gridRows = findPreference(KEY_GRID_ROWS);
        if (gridRows != null) gridRows.setOnPreferenceChangeListener(this::onRowChanged);

        Preference look = findPreference(KEY_LOOK);
        if (look != null) look.setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            if (context == null) return true;
            Intent intent = new Intent(context, TermuxActivity.class);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR, true);
            intent.putExtra(TermuxActivity.EXTRA_SURFACE_EDITOR_PLACE, mSelectedPlace.toolName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        });
    }

    /**
     * The value has not reached the data store yet — a preference change listener runs before the
     * framework persists it — so the miniature and the row enablement are refreshed a moment later,
     * off the same handler {@code LauncherPreferencesFragment} posts its own post-switch sync on.
     */
    private boolean onRowChanged(@NonNull Preference preference, Object newValue) {
        MAIN_HANDLER.post(() -> {
            updateRowVisibilityAndEnablement();
            refreshMiniature();
        });
        return true;
    }

    /** Re-reads every row for the currently selected place and orientation. */
    private void refreshRows() {
        if (mStore == null) return;
        SegmentedPillPreference statusBar = findPreference(KEY_STATUS_BAR);
        if (statusBar != null) statusBar.setSegments(EDGE_VALUES, EDGE_LABELS);
        SegmentedPillPreference appsRow = findPreference(KEY_APPS_ROW);
        if (appsRow != null) appsRow.setSegments(ROW_VALUES, ROW_LABELS);
        SegmentedPillPreference extraKeys = findPreference(KEY_EXTRA_KEYS);
        if (extraKeys != null) extraKeys.setSegments(ROW_VALUES, ROW_LABELS);
        SegmentedPillPreference keyboardOnEnter = findPreference(KEY_KEYBOARD_ON_ENTER);
        if (keyboardOnEnter != null) {
            keyboardOnEnter.setSegments(KEYBOARD_ON_ENTER_VALUES, KEYBOARD_ON_ENTER_LABELS);
        }
        SegmentedPillPreference keyboardMode = findPreference(KEY_KEYBOARD_MODE);
        if (keyboardMode != null) keyboardMode.setSegments(KEYBOARD_MODE_VALUES, KEYBOARD_MODE_LABELS);
        SwitchPreferenceCompat alphabetsRow = findPreference(KEY_ALPHABETS_ROW);
        if (alphabetsRow != null) alphabetsRow.setChecked(mStore.getBoolean(KEY_ALPHABETS_ROW, true));
        SeekBarPreference gridColumns = findPreference(KEY_GRID_COLUMNS);
        if (gridColumns != null) gridColumns.setValue(mStore.getInt(KEY_GRID_COLUMNS, 4));
        SeekBarPreference gridRows = findPreference(KEY_GRID_ROWS);
        if (gridRows != null) gridRows.setValue(mStore.getInt(KEY_GRID_ROWS, 5));

        updateRowVisibilityAndEnablement();
        refreshMiniature();
    }

    private void updateRowVisibilityAndEnablement() {
        if (mStore == null) return;
        Preference statusBar = findPreference(KEY_STATUS_BAR);
        if (statusBar != null) statusBar.setVisible(SHOW_STATUS_BAR_EDGE_ROW);
        Preference keyboardMode = findPreference(KEY_KEYBOARD_MODE);
        if (keyboardMode != null) {
            keyboardMode.setVisible(SHOW_KEYBOARD_MODE_ROW && mSelectedPlace == PaneWallPage.DISPLAY);
        }
        boolean isHome = mSelectedPlace == PaneWallPage.WIDGETS;
        Preference gridColumns = findPreference(KEY_GRID_COLUMNS);
        if (gridColumns != null) gridColumns.setVisible(isHome);
        Preference gridRows = findPreference(KEY_GRID_ROWS);
        if (gridRows != null) gridRows.setVisible(isHome);
        Preference alphabetsRow = findPreference(KEY_ALPHABETS_ROW);
        if (alphabetsRow != null) {
            boolean appsRowIsBottom = mStore.places()
                .appsRow(mSelectedPlace, mSelectedOrientation) == RowPlacement.BOTTOM;
            alphabetsRow.setEnabled(appsRowIsBottom);
        }
    }

    private void refreshMiniature() {
        if (mStore == null) return;
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview == null) return;
        overview.setLayout(mStore.places().resolve(mSelectedPlace, mSelectedOrientation));
    }

    /**
     * Routes every Layout page key to {@link PlaceLayoutStore} for whichever place and orientation
     * the overview currently has selected. A write that does not actually change the resolved value
     * — refreshing the rows after a tab switch reads a value back and can round-trip it through a
     * preference's own persistence — is not forwarded to the store, so switching tabs never queues
     * a spurious restyle.
     */
    static final class LayoutPreferencesDataStore extends PreferenceDataStore {

        @NonNull private final Context mContext;
        @NonNull private final PlaceLayoutStore mPlaces;
        @NonNull private PaneWallPage mPlace = PaneWallPage.TERMINAL;
        @NonNull private PlaceOrientation mOrientation = PlaceOrientation.PORTRAIT;

        LayoutPreferencesDataStore(@NonNull Context context,
                                   @NonNull TermuxAppSharedPreferences preferences) {
            mContext = context.getApplicationContext();
            mPlaces = new PlaceLayoutStore(preferences);
        }

        void setSelection(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
            mPlace = place;
            mOrientation = orientation;
        }

        @NonNull
        PlaceLayoutStore places() {
            return mPlaces;
        }

        private void relayoutLauncher() {
            TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, false);
        }

        @Override
        public void putString(String key, @Nullable String value) {
            if (key == null) return;
            switch (key) {
                case KEY_STATUS_BAR: {
                    Edge edge = Edge.parse(value, Edge.TOP);
                    if (edge == mPlaces.statusBarEdge(mPlace, mOrientation)) return;
                    mPlaces.setStatusBarEdge(mPlace, mOrientation, edge);
                    relayoutLauncher();
                    break;
                }
                case KEY_APPS_ROW: {
                    RowPlacement placement = RowPlacement.parse(value, RowPlacement.BOTTOM);
                    if (placement == mPlaces.appsRow(mPlace, mOrientation)) return;
                    mPlaces.setAppsRow(mPlace, mOrientation, placement);
                    relayoutLauncher();
                    break;
                }
                case KEY_EXTRA_KEYS: {
                    RowPlacement placement = RowPlacement.parse(value, RowPlacement.BOTTOM);
                    if (placement == mPlaces.extraKeys(mPlace, mOrientation)) return;
                    mPlaces.setExtraKeys(mPlace, mOrientation, placement);
                    relayoutLauncher();
                    break;
                }
                case KEY_KEYBOARD_ON_ENTER: {
                    KeyboardOnEnter onEnter = KeyboardOnEnter.parse(value, KeyboardOnEnter.AS_LEFT);
                    if (onEnter == mPlaces.keyboardOnEnter(mPlace)) return;
                    mPlaces.setKeyboardOnEnter(mPlace, onEnter);
                    relayoutLauncher();
                    break;
                }
                case KEY_KEYBOARD_MODE: {
                    KeyboardMode mode = KeyboardMode.parse(value, KeyboardMode.RESIZE);
                    if (mode == mPlaces.keyboardMode(mPlace, mOrientation)) return;
                    mPlaces.setKeyboardMode(mPlace, mOrientation, mode);
                    relayoutLauncher();
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        @Nullable
        public String getString(String key, @Nullable String defValue) {
            if (key == null) return defValue;
            switch (key) {
                case KEY_STATUS_BAR:
                    return mPlaces.statusBarEdge(mPlace, mOrientation).storageValue();
                case KEY_APPS_ROW:
                    return mPlaces.appsRow(mPlace, mOrientation).storageValue();
                case KEY_EXTRA_KEYS:
                    return mPlaces.extraKeys(mPlace, mOrientation).storageValue();
                case KEY_KEYBOARD_ON_ENTER:
                    return mPlaces.keyboardOnEnter(mPlace).storageValue();
                case KEY_KEYBOARD_MODE:
                    return mPlaces.keyboardMode(mPlace, mOrientation).storageValue();
                default:
                    return defValue;
            }
        }

        @Override
        public void putBoolean(String key, boolean value) {
            if (!KEY_ALPHABETS_ROW.equals(key)) return;
            if (value == mPlaces.azRowShown(mPlace, mOrientation)) return;
            mPlaces.setAzRowShown(mPlace, mOrientation, value);
            relayoutLauncher();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return KEY_ALPHABETS_ROW.equals(key)
                ? mPlaces.azRowShown(mPlace, mOrientation) : defValue;
        }

        @Override
        public void putInt(String key, int value) {
            if (KEY_GRID_COLUMNS.equals(key)) {
                if (value == mPlaces.widgetColumns(mPlace, mOrientation)) return;
                mPlaces.setWidgetColumns(mPlace, mOrientation, value);
                relayoutLauncher();
            } else if (KEY_GRID_ROWS.equals(key)) {
                if (value == mPlaces.widgetRows(mPlace, mOrientation)) return;
                mPlaces.setWidgetRows(mPlace, mOrientation, value);
                relayoutLauncher();
            }
        }

        @Override
        public int getInt(String key, int defValue) {
            if (KEY_GRID_COLUMNS.equals(key)) return mPlaces.widgetColumns(mPlace, mOrientation);
            if (KEY_GRID_ROWS.equals(key)) return mPlaces.widgetRows(mPlace, mOrientation);
            return defValue;
        }
    }
}
