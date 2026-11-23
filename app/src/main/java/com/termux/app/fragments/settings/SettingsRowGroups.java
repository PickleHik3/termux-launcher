package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.termux.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Which rows of a settings page share one rounded tonal container, and where in it each sits.
 *
 * <p>The rows of one parent (a {@code PreferenceCategory}, or the page itself for rows outside
 * any category) that follow each other in the list form a group. A section header or a row that
 * is already a card of its own ends the group. Pure: it reads the adapter order through
 * {@link Rows} and never touches a view, so {@link SettingsGroupDecoration} only draws the answer.
 */
public final class SettingsRowGroups {

    private SettingsRowGroups() {}

    public enum Kind {
        /** A section header: never inside a group, and ends the one before it. */
        HEADER,
        /** An ordinary row: shares its group's container. */
        ROW,
        /** A row that is a card already (status cards, the search field): stands alone. */
        STANDALONE
    }

    /** A row's place in its group, which decides which of its corners are rounded. */
    public enum Shape {
        NONE, SOLO, FIRST, MIDDLE, LAST;

        public boolean grouped() {
            return this != NONE;
        }

        /** Whether the row opens its group: rounded top, no hairline above it. */
        public boolean opens() {
            return this == SOLO || this == FIRST;
        }

        /** Whether the row closes its group: rounded bottom. */
        public boolean closes() {
            return this == SOLO || this == LAST;
        }
    }

    /** The adapter order, as far as grouping needs it. */
    public interface Rows {
        int count();

        @NonNull Kind kindAt(int position);

        /** The row's parent; rows of different parents never share a container. */
        @Nullable Object groupAt(int position);
    }

    @NonNull
    public static Shape shapeAt(@NonNull Rows rows, int position) {
        if (position < 0 || position >= rows.count() || rows.kindAt(position) != Kind.ROW) {
            return Shape.NONE;
        }
        boolean opens = !continues(rows, position - 1, position);
        boolean closes = !continues(rows, position + 1, position);
        if (opens && closes) return Shape.SOLO;
        if (opens) return Shape.FIRST;
        if (closes) return Shape.LAST;
        return Shape.MIDDLE;
    }

    /**
     * Whether a group that opens at {@code position} needs space above it: a header already pads
     * its section, but a group at the top of the list or straight after another group or a card
     * would otherwise touch it.
     */
    public static boolean needsLeadingSpace(@NonNull Rows rows, int position) {
        return position == 0 || rows.kindAt(position - 1) != Kind.HEADER;
    }

    private static boolean continues(@NonNull Rows rows, int neighbour, int position) {
        if (neighbour < 0 || neighbour >= rows.count()) return false;
        return rows.kindAt(neighbour) == Kind.ROW
            && Objects.equals(rows.groupAt(neighbour), rows.groupAt(position));
    }

    /**
     * Layouts that draw their own card. A group container around them would be a card in a card,
     * so they stand alone.
     */
    private static final Set<Integer> STANDALONE_LAYOUTS = new HashSet<>(Arrays.asList(
        R.layout.preference_settings_card,
        R.layout.preference_settings_status_card,
        R.layout.preference_status_action_card,
        R.layout.preference_settings_search,
        R.layout.preference_speech_model_card,
        R.layout.preference_tai_model));

    @NonNull
    public static Kind kindOf(@NonNull Preference preference) {
        if (preference instanceof PreferenceCategory) return Kind.HEADER;
        if (STANDALONE_LAYOUTS.contains(preference.getLayoutResource())) return Kind.STANDALONE;
        return Kind.ROW;
    }
}
