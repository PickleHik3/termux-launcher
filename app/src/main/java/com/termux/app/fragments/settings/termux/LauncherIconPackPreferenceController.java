package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.model.IconPackInfo;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class LauncherIconPackPreferenceController {
    private LauncherIconPackPreferenceController() {
    }

    static void configure(@NonNull PreferenceFragmentCompat fragment, @NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        populateIconPackList(context, preferences, fragment.findPreference("app_launcher_icon_pack_package"), false);
        populateIconPackList(context, preferences, fragment.findPreference("app_launcher_pinned_icon_pack_package"), false);
    }

    private static void populateIconPackList(
        Context context,
        TermuxAppSharedPreferences preferences,
        Preference preference,
        boolean themedOnly
    ) {
        if (preference == null) return;
        List<IconPackInfo> packs = new IconPackRepository(context).discoverIconPacks();
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add("app_launcher_pinned_icon_pack_package".equals(preference.getKey())
            ? "Use global icon pack"
            : "System default");
        values.add("");
        for (IconPackInfo pack : packs) {
            if (themedOnly && !pack.themed) continue;
            entries.add(pack.label);
            values.add(pack.packageName);
        }
        String currentValue = "";
        if (preferences != null) {
            if ("app_launcher_pinned_icon_pack_package".equals(preference.getKey())) {
                currentValue = preferences.getAppLauncherPinnedIconPackPackage();
            } else {
                currentValue = preferences.getAppLauncherIconPackPackage();
            }
        }
        preference.setSummary(labelForValue(entries, values, currentValue));
        preference.setOnPreferenceClickListener(clickedPreference -> {
            showIconPackDialog(context, preferences, preference, entries, values);
            return true;
        });
    }

    private static void showIconPackDialog(
        @NonNull Context context,
        TermuxAppSharedPreferences preferences,
        @NonNull Preference preference,
        @NonNull List<CharSequence> entries,
        @NonNull List<CharSequence> values
    ) {
        String currentValue = "";
        if (preferences != null) {
            currentValue = "app_launcher_pinned_icon_pack_package".equals(preference.getKey())
                ? preferences.getAppLauncherPinnedIconPackPackage()
                : preferences.getAppLauncherIconPackPackage();
        }
        int selectedIndex = 0;
        for (int i = 0; i < values.size(); i++) {
            if (String.valueOf(values.get(i)).equals(currentValue)) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(preference.getTitle())
            .setSingleChoiceItems(entries.toArray(new CharSequence[0]), selectedIndex, (dialog, which) -> {
                if (which < 0 || which >= values.size()) return;
                String selectedValue = String.valueOf(values.get(which));
                if (preference.callChangeListener(selectedValue)) {
                    saveIconPackPreference(context, preferences, preference.getKey(), selectedValue);
                    preference.setSummary(entries.get(which));
                }
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void saveIconPackPreference(
        @NonNull Context context,
        TermuxAppSharedPreferences preferences,
        String key,
        @NonNull String value
    ) {
        if (preferences == null || key == null) return;
        if ("app_launcher_pinned_icon_pack_package".equals(key)) {
            preferences.setAppLauncherPinnedIconPackPackage(value);
        } else {
            preferences.setAppLauncherIconPackPackage(value);
        }
        // Not invalidate(): that resets catalogue state only, and the artwork the launcher is
        // still holding is the previous pack's.
        LauncherAppDataProvider.getInstance(context).invalidateIconArtwork();
        TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
    }

    @NonNull
    private static CharSequence labelForValue(
        @NonNull List<CharSequence> entries,
        @NonNull List<CharSequence> values,
        String value
    ) {
        for (int i = 0; i < values.size(); i++) {
            if (String.valueOf(values.get(i)).equals(value)) {
                return entries.get(i);
            }
        }
        return entries.isEmpty() ? "" : entries.get(0);
    }
}
