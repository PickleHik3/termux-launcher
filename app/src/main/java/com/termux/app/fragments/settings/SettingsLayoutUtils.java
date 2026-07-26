package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.fragments.settings.termux.TaiCatalogControlsPreference;
import com.termux.app.fragments.settings.termux.TaiCatalogFilterPreference;
import com.termux.app.fragments.settings.termux.TaiModelPreference;
import com.termux.app.fragments.settings.termux.TaiOverridesPreference;
import com.termux.app.fragments.settings.termux.TaiRuntimeActionsPreference;

public final class SettingsLayoutUtils {

    private SettingsLayoutUtils() {}

    public static void applyRootLayout(@NonNull PreferenceFragmentCompat fragment) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        if (screen == null) return;
        applyLayouts(screen, true);
    }

    public static void applyScreenLayout(@NonNull PreferenceFragmentCompat fragment) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        if (screen == null) return;
        applyLayouts(screen, false);
    }

    private static void applyLayouts(@NonNull PreferenceGroup screen, boolean rootRows) {
        boolean seenCategory = false;
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof PreferenceCategory) {
                preference.setIconSpaceReserved(false);
                // The first section header on a page has no hairline divider above it.
                preference.setLayoutResource(seenCategory
                    ? R.layout.preference_settings_category
                    : R.layout.preference_settings_category_first);
                seenCategory = true;
                applyChildLayouts((PreferenceGroup) preference, rootRows);
            } else {
                applyItemLayout(preference, rootRows);
            }
        }
    }

    private static void applyChildLayouts(@NonNull PreferenceGroup group, boolean rootRows) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            applyItemLayout(preference, rootRows);
            if (preference instanceof PreferenceGroup) {
                applyChildLayouts((PreferenceGroup) preference, rootRows);
            }
        }
    }

    private static void applyItemLayout(@NonNull Preference preference, boolean rootRows) {
        preference.setIconSpaceReserved(false);

        // Preferences that fully own their layout.
        if (preference instanceof SegmentedPillPreference
            || preference instanceof StatusActionPreference
            || preference instanceof SettingsSearchPreference
            || preference instanceof StatusCardPreference
            || preference instanceof TaiRuntimeActionsPreference
            || preference instanceof TaiOverridesPreference
            || preference instanceof TaiCatalogFilterPreference
            || preference instanceof TaiCatalogControlsPreference
            || preference instanceof TaiModelPreference) {
            return;
        }

        if (preference instanceof SeekBarPreference) {
            preference.setLayoutResource(R.layout.preference_settings_seekbar);
            return;
        }

        if (usesCardLayout(preference)) {
            preference.setLayoutResource(R.layout.preference_settings_card);
            return;
        }

        if (usesValueRowLayout(preference)) {
            preference.setLayoutResource(R.layout.preference_settings_value_row);
        } else {
            preference.setLayoutResource(rootRows
                ? R.layout.preference_settings_root_row
                : R.layout.preference_settings_row);
        }

        // PillPreference keeps its own trailing pill widget.
        if (preference instanceof PillPreference) return;

        if (usesChevron(preference)) {
            preference.setWidgetLayoutResource(R.layout.preference_widget_chevron);
        }
    }

    private static boolean usesCardLayout(@NonNull Preference preference) {
        return "tai_model_privacy_notice".equals(preference.getKey());
    }

    private static boolean usesValueRowLayout(@NonNull Preference preference) {
        return "tai_role_default_assistant".equals(preference.getKey());
    }

    private static boolean usesChevron(@NonNull Preference preference) {
        if (!preference.isSelectable()) return false;
        if (preference instanceof SwitchPreferenceCompat) return false;
        return !(preference instanceof PreferenceCategory);
    }
}
