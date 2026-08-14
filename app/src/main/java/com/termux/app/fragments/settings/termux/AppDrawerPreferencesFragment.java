package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;

/**
 * Dedicated drawer controls. Visibility changes immediately; persistence drives live apply.
 *
 * <p>Only the view type is a preference. Icon size and the per-view column/row counts were removed:
 * each view resolves its geometry from the plane's width, and the category cards size their preview
 * icons to fill the card, so a user-chosen size could only reintroduce dead space.
 */
@Keep
public final class AppDrawerPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager manager = getPreferenceManager();
        manager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.app_drawer_preferences, rootKey);
    }
}
