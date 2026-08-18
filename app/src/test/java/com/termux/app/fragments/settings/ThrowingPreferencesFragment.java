package com.termux.app.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

/**
 * An allowlisted settings fragment whose constructor fails.
 *
 * <p>It lives in the settings fragment package on purpose: SettingsActivity only instantiates
 * fragments from there, so a test for "a real settings screen that blows up is not silently
 * swallowed" has to use one that passes the allowlist.
 */
public final class ThrowingPreferencesFragment extends PreferenceFragmentCompat {
    public ThrowingPreferencesFragment() {
        throw new ConstructorFailure();
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    }

    public static final class ConstructorFailure extends RuntimeException {
    }
}
