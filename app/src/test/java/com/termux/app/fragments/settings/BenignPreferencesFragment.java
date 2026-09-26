package com.termux.app.fragments.settings;

import androidx.fragment.app.Fragment;

/**
 * An allowlisted settings fragment that does nothing: it lives in the settings fragment package
 * (so it passes {@code SettingsActivity.isAllowedInitialFragment}) and has no view or preference
 * screen of its own, so it can be pushed and restored in a test without inflating real settings
 * resources.
 */
public final class BenignPreferencesFragment extends Fragment {
}
