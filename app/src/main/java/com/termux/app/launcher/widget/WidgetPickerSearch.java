package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the picker's search field means by "matches".
 *
 * <p>Widget names are whatever their author wrote — accents, capitals, a language the keyboard in
 * front of the user is not set to. Folding both sides to bare lowercase letters once, here, is what
 * lets "cafe" find "Café" and "uhr" find "Uhr"; app labels are matched too, because people look for
 * a widget by the app it came from at least as often as by its own name.
 */
public final class WidgetPickerSearch {
    private WidgetPickerSearch() { }

    /** Accent- and case-folded form; the empty string means "no query". */
    @NonNull public static String normalize(@Nullable String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char character = decomposed.charAt(i);
            if (Character.getType(character) != Character.NON_SPACING_MARK) folded.append(character);
        }
        return folded.toString().toLowerCase(Locale.ROOT).trim();
    }

    /**
     * An app whose own label matches keeps every widget it offers; otherwise the group survives
     * only with the widgets that match, so a result is never shown without its app.
     */
    @NonNull public static List<WidgetAppGroup> filter(@NonNull List<WidgetAppGroup> groups,
                                                       @Nullable String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) return groups;
        ArrayList<WidgetAppGroup> matched = new ArrayList<>();
        for (WidgetAppGroup group : groups) {
            if (normalize(group.label).contains(needle)) { matched.add(group); continue; }
            ArrayList<WidgetProviderItem> items = new ArrayList<>();
            for (WidgetProviderItem item : group.providers) {
                if (normalize(item.label).contains(needle)) items.add(item);
            }
            if (!items.isEmpty()) matched.add(group.withProviders(items));
        }
        return matched;
    }
}
