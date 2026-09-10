package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.LayoutChooserSheet;
import com.termux.app.fragments.settings.LayoutElement;
import com.termux.app.fragments.settings.LayoutElementRowPreference;
import com.termux.app.fragments.settings.LayoutOverviewPreference;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The Layout page: what is on screen and where, per place (Home / Terminal / Display) and, for
 * arrangement, per orientation. The page is the editor — the pill picks the place, the two
 * miniatures show it in portrait and landscape at once, and one compact row per element says what
 * it is set to in each and opens a chooser for it. Every write goes straight to
 * {@link PlaceLayoutStore} and both pictures and every row are re-read afterwards.
 */
@Keep
public final class LayoutPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_OVERVIEW = "layout_overview";
    /** The Widgets page's own cog deep-links to this row, so it names itself. */
    public static final String KEY_WIDGET_GRID = LayoutElement.WIDGET_GRID.key();
    private static final String KEY_LOOK = "layout_look";

    private static final String STATE_PLACE = "layout_selected_place";

    /** How a row's chooser is opened; swapped in a test that only wants to know which one. */
    interface ChooserOpener {
        void open(@NonNull LayoutElement element);
    }

    @Nullable private PlaceLayoutStore mPlaces;
    @NonNull private PaneWallPage mSelectedPlace = PaneWallPage.TERMINAL;
    @NonNull private ChooserOpener mChooserOpener = this::showChooserSheet;
    /** The row a deep link asked for, until the rows exist to scroll to it. */
    @Nullable private String mPendingScrollKey;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        mPlaces = new PlaceLayoutStore(preferences);
        setPreferencesFromResource(R.xml.layout_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        // A deep link opens the page on the place it is about and points at one of its rows; a
        // screen that has already been turned keeps whatever the user last selected instead.
        mSelectedPlace = placeFromArguments(getArguments(), mSelectedPlace);
        mPendingScrollKey = savedInstanceState == null ? scrollKeyFromArguments(getArguments()) : null;
        if (savedInstanceState != null) restoreSelection(savedInstanceState);
        boolean displayAvailable = isDisplayAvailable(preferences);
        if (!displayAvailable && mSelectedPlace == PaneWallPage.DISPLAY) {
            mSelectedPlace = PaneWallPage.TERMINAL;
        }

        configureOverview(displayAvailable);
        configureRows();
        refresh();
        // Only now do the rows know which of them this place has, so this is the first moment a
        // deep link's row can be found and brought into view.
        if (mPendingScrollKey != null) {
            scrollToPreference(mPendingScrollKey);
            mPendingScrollKey = null;
        }
    }

    /**
     * The place a deep link named, by its tool name ("widgets"). An unknown or missing name
     * leaves the page on the place it would have opened on anyway.
     */
    @NonNull
    static PaneWallPage placeFromArguments(@Nullable Bundle arguments,
                                           @NonNull PaneWallPage fallback) {
        String name = arguments == null
            ? null : arguments.getString(SettingsActivity.EXTRA_INITIAL_PLACE);
        if (name == null || name.isEmpty()) return fallback;
        try {
            return PaneWallPage.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** The preference key a deep link asked to be scrolled to, or null. */
    @Nullable
    static String scrollKeyFromArguments(@Nullable Bundle arguments) {
        String key = arguments == null
            ? null : arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY);
        return key == null || key.isEmpty() ? null : key;
    }

    private void restoreSelection(@NonNull Bundle savedInstanceState) {
        String placeName = savedInstanceState.getString(STATE_PLACE);
        if (placeName == null) return;
        try {
            mSelectedPlace = PaneWallPage.valueOf(placeName);
        } catch (IllegalArgumentException ignored) {
            // Stays at the default.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PLACE, mSelectedPlace.name());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_layout);
        Context context = getContext();
        if (context == null || mPlaces == null) return;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        if (preferences == null) return;
        boolean displayAvailable = isDisplayAvailable(preferences);
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview != null) overview.setDisplayTabVisible(displayAvailable);
        if (!displayAvailable && mSelectedPlace == PaneWallPage.DISPLAY) {
            mSelectedPlace = PaneWallPage.TERMINAL;
            if (overview != null) overview.setSelection(mSelectedPlace);
        }
        refresh();
    }

    private static boolean isDisplayAvailable(@NonNull TermuxAppSharedPreferences preferences) {
        return com.termux.BuildConfig.X11_SERVER && preferences.isX11DisplayEnabled();
    }

    private void configureOverview(boolean displayAvailable) {
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview == null) return;
        overview.setDisplayTabVisible(displayAvailable);
        overview.setSelection(mSelectedPlace);
        overview.setOnSelectionListener(new LayoutOverviewPreference.Listener() {
            @Override
            public void onPlaceChanged(@NonNull PaneWallPage place) {
                mSelectedPlace = place;
                refresh();
            }

            @Override
            public void onBlockTapped(@NonNull PlaceMiniatureView.Block block) {
                openChooser(LayoutElement.forBlock(block, mSelectedPlace));
            }
        });
    }

    /** Wires every row's chooser once, and the Look row's deep link into the surface editor. */
    private void configureRows() {
        for (LayoutElement element : LayoutElement.values()) {
            LayoutElementRowPreference row = findPreference(element.key());
            if (row == null) continue;
            row.setOnPreferenceClickListener(preference -> {
                openChooser(element);
                return true;
            });
        }

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

    /** Re-reads both miniatures and every row for the selected place. */
    private void refresh() {
        Context context = getContext();
        if (context == null || mPlaces == null) return;
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview != null) {
            overview.setLayouts(mPlaces.resolve(mSelectedPlace, PlaceOrientation.PORTRAIT),
                mPlaces.resolve(mSelectedPlace, PlaceOrientation.LANDSCAPE));
        }
        for (LayoutElement element : LayoutElement.values()) {
            LayoutElementRowPreference row = findPreference(element.key());
            if (row == null) continue;
            boolean on = element.isOn(mSelectedPlace);
            row.setVisible(on);
            if (!on) continue;
            row.setSwatchColor(element.swatchColor(context));
            row.setSummary(LayoutChooserModel.summary(context, mPlaces, mSelectedPlace, element));
        }
    }

    private void openChooser(@NonNull LayoutElement element) {
        mChooserOpener.open(element);
    }

    private void showChooserSheet(@NonNull LayoutElement element) {
        Context context = getContext();
        if (context == null || mPlaces == null) return;
        PlaceLayoutStore places = mPlaces;
        LayoutChooserSheet.show(context, context.getString(element.titleRes()),
            () -> LayoutChooserModel.groups(context, places, mSelectedPlace, element),
            this::onLayoutWritten);
    }

    /**
     * A chooser wrote: the launcher restyles on its next resume and the page re-reads itself, so
     * both miniatures and every row show the new value without the sheet being closed first.
     */
    void onLayoutWritten() {
        Context context = getContext();
        if (context != null) {
            TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
        }
        refresh();
    }

    @VisibleForTesting
    void setChooserOpener(@NonNull ChooserOpener opener) {
        mChooserOpener = opener;
    }

    /** Drives the same path a tap on one of the miniatures' bands takes. */
    @VisibleForTesting
    void tapMiniatureBlock(@NonNull PlaceMiniatureView.Block block) {
        openChooser(LayoutElement.forBlock(block, mSelectedPlace));
    }

    @VisibleForTesting
    void selectPlace(@NonNull PaneWallPage place) {
        mSelectedPlace = place;
        LayoutOverviewPreference overview = findPreference(KEY_OVERVIEW);
        if (overview != null) overview.setSelection(place);
        refresh();
    }

    @VisibleForTesting
    @Nullable
    PlaceLayoutStore places() {
        return mPlaces;
    }
}
