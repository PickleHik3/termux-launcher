package com.termux.app.fragments.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * What a search box shows (F1), the same on the root and on the search screen: while the query is
 * empty the screen's own rows; once it has text, those rows step aside for one list of results
 * from the whole tree, the searched-from section's first. A result opens its page through the
 * {@link Navigator} and lands on the row; the keyboard's search key opens the first result.
 */
public final class SettingsSearchResults {

    /** Opens a result: its page, in the tree, scrolled to the row and ringing it once. */
    public interface Navigator {
        void openSearchResult(@NonNull String screenClass, @Nullable String key);
    }

    /** How many results the list shows; a query that needs more wants another word. */
    static final int LIMIT = 40;
    static final String KEY_RESULTS = "settings_search_results";

    @NonNull private final PreferenceFragmentCompat mFragment;
    @Nullable private final SettingsTree.Section mScope;
    @Nullable private SettingsSearchIndex mIndex;
    @NonNull private List<SettingsSearchIndex.Entry> mResults = new ArrayList<>();
    /** The screen's own top-level rows and whether each was visible before a search hid it. */
    @NonNull private final List<Preference> mOwnRows = new ArrayList<>();
    @NonNull private final List<Boolean> mOwnVisible = new ArrayList<>();

    private SettingsSearchResults(@NonNull PreferenceFragmentCompat fragment,
                                  @Nullable SettingsTree.Section scope) {
        mFragment = fragment;
        mScope = scope;
    }

    /**
     * Wires {@code search} on {@code fragment}'s screen. {@code scope} is the section searched
     * from, ranked first and named in the placeholder ("Search Home"); null searches everything.
     */
    @NonNull
    public static SettingsSearchResults attach(@NonNull PreferenceFragmentCompat fragment,
                                               @NonNull SettingsSearchPreference search,
                                               @Nullable SettingsTree.Section scope) {
        SettingsSearchResults results = new SettingsSearchResults(fragment, scope);
        Context context = search.getContext();
        if (scope != null) {
            search.setHint(context.getString(R.string.settings_search_in, context.getString(scope.titleRes)));
        }
        search.setOnQueryChangedListener(results::show);
        search.setOnQuerySubmitListener(query -> results.openFirst());
        return results;
    }

    /** The results the list shows now, best first. */
    @NonNull
    @VisibleForTesting
    public List<SettingsSearchIndex.Entry> results() {
        return mResults;
    }

    /** Opens the first result, as the keyboard's search key does; false when there is none. */
    public boolean openFirst() {
        if (mResults.isEmpty()) return false;
        open(mResults.get(0));
        return true;
    }

    private void open(@NonNull SettingsSearchIndex.Entry entry) {
        if (mFragment.getActivity() instanceof Navigator) {
            ((Navigator) mFragment.getActivity()).openSearchResult(entry.screenClass, entry.key);
        }
    }

    private void show(@NonNull String query) {
        PreferenceScreen screen = mFragment.getPreferenceScreen();
        Context context = mFragment.getContext();
        if (screen == null || context == null) return;
        boolean searching = !query.trim().isEmpty();
        if (searching && mIndex == null) mIndex = SettingsSearchIndex.get(context);
        mResults = searching ? mIndex.search(query, mScope, LIMIT) : new ArrayList<>();

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
        for (SettingsSearchIndex.Entry entry : mResults) {
            Preference row = new Preference(context);
            row.setPersistent(false);
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
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference row = screen.getPreference(i);
            if (row instanceof SettingsSearchPreference || KEY_RESULTS.equals(row.getKey())) continue;
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
