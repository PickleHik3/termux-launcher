package com.termux.app.fragments.settings.terl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.fragments.settings.SliderPreference;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rows of a Terl group page, built from the tree: a setting row from its declaration (a switch
 * for on or off, segments for two or three choices, a list for more, a slider for a number), a
 * link as a row that opens v1's page, a heading as a section. Rows are keyed by registry id, so
 * the page's data store saves each through the registry.
 */
final class TerlSettingRows {

    private TerlSettingRows() {}

    /** The most choices shown as segments; more open a list. */
    private static final int MAX_SEGMENTS = 3;

    /** Registry id to registry id for every setting row: the rows map of the page's data store. */
    @NonNull
    static Map<String, String> rowsMap() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String id : TerlSettingsTree.settingIds()) rows.put(id, id);
        return Collections.unmodifiableMap(rows);
    }

    /** Adds {@code group}'s rows to {@code screen}, under their headings. */
    static void addRows(@NonNull Context context, @NonNull PreferenceGroup screen,
                        @NonNull TerlSettingsTree.Group group, @NonNull SettingsRegistry registry) {
        PreferenceGroup into = screen;
        for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
            switch (item.kind) {
                case HEADING: {
                    PreferenceCategory heading = new PreferenceCategory(context);
                    heading.setKey(item.key());
                    heading.setTitle(item.titleRes);
                    heading.setPersistent(false);
                    screen.addPreference(heading);
                    into = heading;
                    break;
                }
                case LINK:
                    into.addPreference(link(context, item));
                    break;
                default: {
                    if (!TerlSettings.has(context, item.need)) break;
                    Preference row = setting(context, item, registry);
                    if (row != null) into.addPreference(row);
                }
            }
        }
    }

    @NonNull
    private static Preference link(@NonNull Context context, @NonNull TerlSettingsTree.Item item) {
        Preference row = new Preference(context);
        row.setKey(item.key());
        row.setPersistent(false);
        row.setTitle(item.titleRes);
        if (item.summaryRes != 0) row.setSummary(item.summaryRes);
        row.setFragment(item.page);
        return row;
    }

    /** The row for one setting, or null for an id the registry does not know. */
    @Nullable
    static Preference setting(@NonNull Context context, @NonNull TerlSettingsTree.Item item,
                              @NonNull SettingsRegistry registry) {
        SettingDeclaration declaration = registry.declaration(item.id);
        if (declaration == null) return null;
        Preference row;
        switch (declaration.kind) {
            case BOOLEAN: {
                SwitchPreferenceCompat toggle = new SwitchPreferenceCompat(context);
                toggle.setSummary(declaration.summary);
                row = toggle;
                break;
            }
            case CHOICE: {
                CharSequence[] labels = TerlSettings.labels(context, declaration);
                CharSequence[] values = declaration.choices.toArray(new CharSequence[0]);
                if (values.length <= MAX_SEGMENTS) {
                    SegmentedPillPreference pills = new SegmentedPillPreference(context);
                    pills.setSegments(declaration.choices.toArray(new String[0]), labels);
                    row = pills;
                } else {
                    ListPreference list = new ListPreference(context);
                    list.setEntries(labels);
                    list.setEntryValues(values);
                    list.setDialogTitle(declaration.title);
                    list.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                    row = list;
                }
                break;
            }
            case INTEGER: {
                SliderPreference slider = new SliderPreference(context);
                slider.setMin(declaration.min);
                slider.setMax(declaration.max);
                slider.setShowValue(true);
                slider.setSummary(declaration.summary);
                row = slider;
                break;
            }
            default:
                return null;
        }
        row.setKey(declaration.id);
        row.setTitle(declaration.title);
        row.setDefaultValue(declaration.kind == SettingDeclaration.Kind.INTEGER
            ? (Object) declaration.defaultInt
            : declaration.kind == SettingDeclaration.Kind.BOOLEAN
                ? (Object) Boolean.parseBoolean(declaration.defaultValue())
                : declaration.defaultValue());
        return row;
    }
}
