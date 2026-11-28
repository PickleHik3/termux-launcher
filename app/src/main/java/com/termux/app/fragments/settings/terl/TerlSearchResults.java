package com.termux.app.fragments.settings.terl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsSearchPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * What Terl's search field shows: while it is empty the page's own rows; once it has text, those
 * rows step aside for one list of hits from Terl's tree. A hit opens where it lives; the keyboard's
 * search key opens the first.
 */
final class TerlSearchResults {

    private static final int LIMIT = 40;
    private static final String KEY_RESULTS = "terl_settings_search_results";

    @NonNull private final PreferenceFragmentCompat mFragment;
    @NonNull private final SettingsSearchPreference mSearch;
    @Nullable private TerlSettingsSearch mIndex;
    @NonNull private List<TerlSettingsSearch.Entry> mResults = new ArrayList<>();
    @NonNull private final List<Preference> mOwnRows = new ArrayList<>();
    @NonNull private final List<Boolean> mOwnVisible = new ArrayList<>();
    private boolean mResultOpened;

    private TerlSearchResults(@NonNull PreferenceFragmentCompat fragment,
                              @NonNull SettingsSearchPreference search) {
        mFragment = fragment;
        mSearch = search;
    }

    @NonNull
    static TerlSearchResults attach(@NonNull PreferenceFragmentCompat fragment,
                                    @NonNull SettingsSearchPreference search) {
        TerlSearchResults results = new TerlSearchResults(fragment, search);
        search.setHint(search.getContext().getString(R.string.terl_settings_search_hint));
        search.setOnQueryChangedListener(results::show);
        search.setOnQuerySubmitListener(query -> results.openFirst());
        return results;
    }

    @VisibleForTesting
    @NonNull
    List<TerlSettingsSearch.Entry> results() {
        return mResults;
    }

    boolean openFirst() {
        if (mResults.isEmpty()) return false;
        open(mResults.get(0));
        return true;
    }

    /** After a hit was opened from here, a reader who comes Back finds the page, not the query. */
    void onViewDestroyed() {
        if (!mResultOpened) return;
        mResultOpened = false;
        mSearch.clearQuery();
    }

    private void open(@NonNull TerlSettingsSearch.Entry entry) {
        mResultOpened = true;
        TerlSettingsNavigator.open(mFragment.getActivity(), entry);
    }

    private void show(@NonNull String query) {
        PreferenceScreen screen = mFragment.getPreferenceScreen();
        Context context = mFragment.getContext();
        if (screen == null || context == null) return;
        boolean searching = !query.trim().isEmpty();
        if (searching && mIndex == null) mIndex = TerlSettingsIndex.build(context);
        mResults = searching ? mIndex.search(query, LIMIT) : new ArrayList<>();

        PreferenceCategory list = screen.findPreference(KEY_RESULTS);
        if (!searching) {
            if (list != null) screen.removePreference(list);
            restoreOwnRows();
            return;
        }
        hideOwnRows(screen);
        if (list == null) {
            list = new PreferenceCategory(context);
            list.setKey(KEY_RESULTS);
            list.setPersistent(false);
            list.setOrder(Integer.MAX_VALUE);
            SettingsLayoutUtils.applyCategoryLayout(list, true);
            screen.addPreference(list);
        }
        list.removeAll();
        list.setTitle(mResults.isEmpty() ? context.getString(R.string.settings_search_no_results)
            : context.getString(R.string.settings_search_results_header));
        for (TerlSettingsSearch.Entry entry : mResults) {
            Preference row = new Preference(context);
            row.setPersistent(false);
            // A hold on an edit-mode control's hit offers Copy as config, as a setting row does.
            if (entry.kind == TerlSettingsSearch.Kind.EDIT_MODE && entry.id != null) {
                row.setKey(TerlRowMenu.HIT_KEY_PREFIX + entry.id);
            }
            row.setTitle(entry.title);
            row.setSummary(entry.breadcrumb);
            row.setSingleLineTitle(true);
            SettingsLayoutUtils.applyRowLayout(row);
            row.setOnPreferenceClickListener(clicked -> {
                open(entry);
                return true;
            });
            list.addPreference(row);
        }
    }

    private void hideOwnRows(@NonNull PreferenceScreen screen) {
        if (!mOwnRows.isEmpty()) return;
        // Rows above the field stay, so the focused field keeps its place in the list.
        boolean belowField = false;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference row = screen.getPreference(i);
            if (row instanceof SettingsSearchPreference) belowField = true;
            if (!belowField || row instanceof SettingsSearchPreference || KEY_RESULTS.equals(row.getKey())) continue;
            mOwnRows.add(row);
            mOwnVisible.add(row.isVisible());
            row.setVisible(false);
        }
    }

    private void restoreOwnRows() {
        for (int i = 0; i < mOwnRows.size(); i++) mOwnRows.get(i).setVisible(mOwnVisible.get(i));
        mOwnRows.clear();
        mOwnVisible.clear();
    }
}
