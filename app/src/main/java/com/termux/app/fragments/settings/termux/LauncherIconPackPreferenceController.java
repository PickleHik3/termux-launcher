package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.List;

/** The Style page's two icon pack rows; the listing and the write live in {@link IconPackChoices}. */
final class LauncherIconPackPreferenceController {
    private LauncherIconPackPreferenceController() {
    }

    static void configure(@NonNull PreferenceFragmentCompat fragment, @NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        populateIconPackList(context, preferences, fragment.findPreference(IconPackChoices.KEY_GLOBAL), false);
        populateIconPackList(context, preferences, fragment.findPreference(IconPackChoices.KEY_PINNED), false);
    }

    private static void populateIconPackList(
        Context context,
        TermuxAppSharedPreferences preferences,
        Preference preference,
        boolean themedOnly
    ) {
        if (preference == null) return;
        String key = preference.getKey();
        List<IconPackChoices.Entry> entries = IconPackChoices.entries(
            IconPackChoices.KEY_PINNED.equals(key) ? "Use global icon pack" : "System default",
            new IconPackRepository(context).discoverIconPacks(), themedOnly);
        preference.setSummary(entries.get(IconPackChoices.indexOf(entries,
            IconPackChoices.current(preferences, key))).label);
        preference.setOnPreferenceClickListener(clickedPreference -> {
            showIconPackDialog(context, preferences, preference, entries);
            return true;
        });
    }

    private static void showIconPackDialog(
        @NonNull Context context,
        TermuxAppSharedPreferences preferences,
        @NonNull Preference preference,
        @NonNull List<IconPackChoices.Entry> entries
    ) {
        String key = preference.getKey();
        int selectedIndex = IconPackChoices.indexOf(entries, IconPackChoices.current(preferences, key));
        CharSequence[] labels = new CharSequence[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).label;

        new MaterialAlertDialogBuilder(context)
            .setTitle(preference.getTitle())
            .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                if (which < 0 || which >= entries.size()) return;
                String selectedValue = entries.get(which).value;
                if (key != null && preference.callChangeListener(selectedValue)) {
                    IconPackChoices.apply(context, preferences, key, selectedValue);
                    preference.setSummary(entries.get(which).label);
                }
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
