package com.termux.app.fragments.settings.terl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.SettingsSearchPreference;

import java.util.List;

/**
 * The root of Terl's Settings, built into Settings' own root page while the Terl desktop switch is
 * on: the search field first, focused, then the seven groups, then the Changed filter. v1's root
 * stays as it was with the switch off.
 */
public final class TerlSettingsRoot {

    /** The Changed row's key on the root. */
    static final String KEY_CHANGED = "terl_changed";

    @NonNull private final PreferenceFragmentCompat mFragment;
    @NonNull private final TerlSearchResults mSearch;

    private TerlSettingsRoot(@NonNull PreferenceFragmentCompat fragment, @NonNull TerlSearchResults search) {
        mFragment = fragment;
        mSearch = search;
    }

    /** Builds Terl's root into {@code fragment}'s screen. */
    @NonNull
    public static TerlSettingsRoot build(@NonNull PreferenceFragmentCompat fragment) {
        Context context = fragment.getPreferenceManager().getContext();
        PreferenceScreen screen = fragment.getPreferenceManager().createPreferenceScreen(context);

        SettingsSearchPreference search = new SettingsSearchPreference(context, null);
        search.setKey("settings_search");
        search.setPersistent(false);
        search.setTitle(R.string.terl_settings_search_hint);
        search.requestFocusOnBind();
        screen.addPreference(search);

        PreferenceCategory groups = new PreferenceCategory(context);
        groups.setKey("terl_groups");
        groups.setPersistent(false);
        screen.addPreference(groups);
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            if (!TerlSettings.shows(context, group)) continue;
            Preference row = new Preference(context);
            row.setKey(group.key);
            row.setPersistent(false);
            row.setIcon(group.iconRes);
            row.setTitle(group.titleRes);
            row.setSummary(group.summaryRes);
            row.setFragment(TerlSettingsTree.pageOf(group));
            groups.addPreference(row);
        }

        PreferenceCategory filters = new PreferenceCategory(context);
        filters.setKey("terl_filters");
        filters.setPersistent(false);
        screen.addPreference(filters);
        Preference changed = new Preference(context);
        changed.setKey(KEY_CHANGED);
        changed.setPersistent(false);
        changed.setIcon(R.drawable.ic_symbol_restart);
        changed.setTitle(R.string.terl_settings_changed_title);
        changed.setFragment(TerlChangedFragment.class.getName());
        filters.addPreference(changed);

        fragment.setPreferenceScreen(screen);
        SettingsLayoutUtils.applyRootLayout(fragment);
        TerlSettingsRoot root = new TerlSettingsRoot(fragment, TerlSearchResults.attach(fragment, search));
        root.refresh();
        return root;
    }

    /** The counts and notes that move while Settings is away: Changed, Home is off. */
    public void refresh() {
        Context context = mFragment.getContext();
        if (context == null) return;
        Preference changed = mFragment.findPreference(KEY_CHANGED);
        if (changed != null) {
            List<TerlChanged.Setting> settings = TerlChanged.changed(TerlSettings.registry(context),
                id -> TerlSettings.shows(context, id));
            changed.setSummary(settings.isEmpty() ? context.getString(R.string.terl_settings_changed_none)
                : context.getResources().getQuantityString(R.plurals.terl_settings_changed_count,
                    settings.size(), settings.size()));
        }
        Preference home = mFragment.findPreference(TerlSettingsTree.Group.HOME.key);
        if (home != null) {
            home.setSummary(TerlSettings.homeIsOff(context) ? R.string.terl_settings_home_off
                : TerlSettingsTree.Group.HOME.summaryRes);
        }
    }

    /** The root's view went away: a query a hit was opened from is spent. */
    public void onViewDestroyed() {
        mSearch.onViewDestroyed();
    }

    /** The search under the field, for tests. */
    @Nullable
    TerlSearchResults search() {
        return mSearch;
    }
}
