package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

/**
 * Search from any screen (F1): the toolbar's search action opens this, with the field focused and
 * its placeholder naming the section it was opened from ("Search Home"). It searches the whole
 * tree, that section's hits first; a result replaces this screen with its page, landed on the row.
 */
@Keep
public class SettingsSearchFragment extends PreferenceFragmentCompat {

    /** The section the search was opened from, as a {@link SettingsTree.Section} name. */
    public static final String ARG_SCOPE = "settings_search_scope";

    @Nullable private SettingsSearchResults mResults;

    @NonNull
    public static Bundle arguments(@Nullable SettingsTree.Section scope) {
        Bundle arguments = new Bundle();
        if (scope != null) arguments.putString(ARG_SCOPE, scope.name());
        return arguments;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        SettingsSearchPreference search = new SettingsSearchPreference(context, null);
        search.setKey("settings_search");
        search.setTitle(com.termux.R.string.settings_search_hint);
        search.requestFocusOnBind();
        screen.addPreference(search);
        setPreferenceScreen(screen);
        mResults = SettingsSearchResults.attach(this, search, scope());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(com.termux.R.string.settings_search_hint);
    }

    @Nullable
    private SettingsTree.Section scope() {
        Bundle arguments = getArguments();
        String name = arguments == null ? null : arguments.getString(ARG_SCOPE);
        if (name == null) return null;
        try {
            return SettingsTree.Section.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    public SettingsSearchResults results() {
        return mResults;
    }
}
