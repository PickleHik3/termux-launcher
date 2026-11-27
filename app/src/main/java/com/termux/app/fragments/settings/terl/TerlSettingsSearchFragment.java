package com.termux.app.fragments.settings.terl;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsSearchFragment;
import com.termux.app.fragments.settings.SettingsSearchPreference;

/**
 * Terl's search from any page (the toolbar's search action): the field, focused, over Terl's
 * tree. A hit replaces this page with where it lives.
 */
@Keep
public final class TerlSettingsSearchFragment extends PreferenceFragmentCompat {

    @Nullable private TerlSearchResults mResults;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        SettingsSearchPreference search = new SettingsSearchPreference(context, null);
        search.setKey("settings_search");
        search.setPersistent(false);
        search.setTitle(R.string.terl_settings_search_hint);
        search.requestFocusOnBind();
        screen.addPreference(search);
        setPreferenceScreen(screen);
        mResults = TerlSearchResults.attach(this, search);
        Bundle arguments = getArguments();
        String query = arguments == null ? null : arguments.getString(SettingsSearchFragment.ARG_QUERY);
        if (query != null && !query.trim().isEmpty()) search.setQuery(query.trim());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.terl_settings_search_hint);
    }

    @Override
    public void onDestroyView() {
        if (mResults != null) mResults.onViewDestroyed();
        super.onDestroyView();
    }
}
