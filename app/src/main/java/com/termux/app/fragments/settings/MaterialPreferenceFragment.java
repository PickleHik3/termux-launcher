package com.termux.app.fragments.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import java.util.List;

/**
 * Base preference fragment that renders ListPreference / EditTextPreference dialogs as
 * rounded Material dialogs (via {@link SettingsMaterialDialogs}) instead of the platform
 * AppCompat alert dialog. Settings fragments extend this to pick up the redesigned look.
 *
 * <p>Also carries the pinned section-chip row ({@link SettingsSectionChips}) every sub-page with
 * three or more visible sections gets: a row of chips above the list ("All" plus one per visible
 * {@link PreferenceCategory}) that jumps straight to a section instead of scrolling for it. The
 * row is built here, once, so no individual page fragment has to opt in.
 */
@Keep
public abstract class MaterialPreferenceFragment extends PreferenceFragmentCompat {

    private static final String STATE_SELECTED_SECTION_TITLE = "settings_section_chips_selected_title";

    @Nullable private View mSectionChipsRow;
    @Nullable private SettingsSectionChips.FilteringAdapter mFilteringAdapter;
    @Nullable private PreferenceCategory mSelectedSection;
    @Nullable private String mPendingRestoreSectionTitle;

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (getContext() != null && SettingsMaterialDialogs.show(getContext(), preference)) {
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @NonNull
    @Override
    public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater,
                                             @NonNull ViewGroup parent, Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        // No change animations: the download catalogs update rows in place on every progress
        // tick, and the default cross-fade binds a second holder per change — visible as a
        // flicker, and on Nothing OS (Android 16) as a ghost insertion cursor over the last
        // glyph of every freshly bound button label.
        recyclerView.setItemAnimator(null);
        // The last row scrolls fully clear of the bottom edge, with room to spare, instead of
        // ending flush against the navigation bar.
        int bottom = Math.round(24f * recyclerView.getResources().getDisplayMetrics().density);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
            recyclerView.getPaddingRight(), recyclerView.getPaddingBottom() + bottom);
        return recyclerView;
    }

    /**
     * Wraps the normal preference list in a small vertical container with the (initially hidden)
     * chip row above it, pinned outside the RecyclerView so it never scrolls away. Whether the row
     * ends up shown at all is decided later, once the preference tree — and therefore the section
     * count — is known, in {@link #onCreateAdapter}.
     */
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        View list = super.onCreateView(inflater, container, savedInstanceState);
        if (getContext() == null) return list;

        if (savedInstanceState != null) {
            mPendingRestoreSectionTitle = savedInstanceState.getString(STATE_SELECTED_SECTION_TITLE);
        }

        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSectionChipsRow = inflater.inflate(R.layout.settings_section_chips_row, wrapper, false);
        wrapper.addView(mSectionChipsRow);
        wrapper.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return wrapper;
    }

    /**
     * Wraps the real {@link PreferenceGroupAdapter} in a {@link SettingsSectionChips.FilteringAdapter}
     * so a section filter narrows the *positions* the list shows instead of touching any
     * preference's own {@code setVisible} — a row the page itself hid stays hidden under every
     * filter, "All" included. Rebuilds the chip row here and every time the wrapped adapter reports
     * a change (the page's own visibility logic ran again), and falls back to "All" if the
     * currently selected section is no longer one of the visible ones.
     */
    @NonNull
    @Override
    protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        RecyclerView.Adapter<?> adapter = super.onCreateAdapter(preferenceScreen);
        if (mSectionChipsRow == null || !(adapter instanceof PreferenceGroupAdapter)) {
            return adapter;
        }

        if (mPendingRestoreSectionTitle != null) {
            mSelectedSection = findSectionByTitle(preferenceScreen, mPendingRestoreSectionTitle);
            mPendingRestoreSectionTitle = null;
        }

        SettingsSectionChips.FilteringAdapter filteringAdapter =
            new SettingsSectionChips.FilteringAdapter((PreferenceGroupAdapter) adapter, preferenceScreen);
        filteringAdapter.setFilter(mSelectedSection);
        filteringAdapter.setOnSectionsChangedListener(this::refreshSectionChips);
        mFilteringAdapter = filteringAdapter;
        refreshSectionChips();
        return filteringAdapter;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // A deep link (SettingsActivity's EXTRA_SCROLL_TO_KEY, including search results that open
        // a page scrolled to a key) always means the whole page: a stale section filter left over
        // from before would hide the very row being scrolled to.
        Bundle arguments = getArguments();
        String scrollToKey = arguments == null ? null
            : arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY);
        if (scrollToKey != null) {
            clearSectionFilter();
            scrollToPreference(scrollToKey);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedSection != null && mSelectedSection.getTitle() != null) {
            outState.putString(STATE_SELECTED_SECTION_TITLE, mSelectedSection.getTitle().toString());
        }
    }

    private void clearSectionFilter() {
        if (mSelectedSection == null) return;
        mSelectedSection = null;
        if (mFilteringAdapter != null) mFilteringAdapter.setFilter(null);
        refreshSectionChips();
    }

    private void onSectionChipSelected(@Nullable PreferenceCategory section) {
        // Tapping "All" passes null; tapping the already-selected section chip re-passes the same
        // reference, which this demotes back to null ("All") — a second tap is the way out.
        mSelectedSection = mSelectedSection == section ? null : section;
        if (mFilteringAdapter != null) mFilteringAdapter.setFilter(mSelectedSection);
        refreshSectionChips();
        RecyclerView list = getListView();
        if (list != null) list.scrollToPosition(0);
    }

    private void refreshSectionChips() {
        if (mSectionChipsRow == null) return;
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        List<PreferenceCategory> sections = SettingsSectionChips.visibleSections(screen);
        if (mSelectedSection != null && !sections.contains(mSelectedSection)) {
            // The section the viewer had picked stopped qualifying (the page hid it, or emptied
            // it) — fall back to "All" rather than silently keep filtering to a section that no
            // longer has a chip.
            mSelectedSection = null;
            if (mFilteringAdapter != null) mFilteringAdapter.setFilter(null);
        }
        SettingsSectionChips.bind(mSectionChipsRow, sections, mSelectedSection, this::onSectionChipSelected);
    }

    /** The pinned chip row's own view, or {@code null} before {@link #onCreateView} has run — a
     *  test's way to inspect and drive the chips without reaching into fragment internals. */
    @VisibleForTesting
    @Nullable
    public View getSectionChipsRow() {
        return mSectionChipsRow;
    }

    @Nullable
    private static PreferenceCategory findSectionByTitle(@NonNull PreferenceScreen screen, @NonNull String title) {
        for (PreferenceCategory section : SettingsSectionChips.visibleSections(screen)) {
            if (section.getTitle() != null && title.contentEquals(section.getTitle())) return section;
        }
        return null;
    }
}
