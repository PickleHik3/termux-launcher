package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The stops the corner tab's grid-size wheels offer, which are the stops the Drawer layout
 * settings page offers: the stored words, Auto first. A wheel reports a stop's index and this maps
 * it to the word the preference stores, so the wheel and the page can never disagree about what a
 * grid may be. Pure.
 */
final class AppDrawerGridChoices {

    static final String AUTO = "auto";

    /** Columns: Auto, 4, 5, 6 (the page's {@code app_launcher_drawer_columns_values}). */
    static final String[] COLUMN_VALUES = {AUTO, "4", "5", "6"};

    /** Rows: Auto, 3, 4, 5 (the page's {@code app_launcher_drawer_rows_values}). */
    static final String[] ROW_VALUES = {AUTO, "3", "4", "5"};

    private AppDrawerGridChoices() {
    }

    /** Rows are a choice on the Pages type only; the other types size their rows themselves. */
    static boolean showsRows(@NonNull AppDrawerViewType type) {
        return type == AppDrawerViewType.HORIZONTAL;
    }

    /** The stop a stored word stands on; a word that is not a stop reads as Auto. */
    static int indexOf(@NonNull String[] values, @Nullable String stored) {
        if (stored != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(stored)) return i;
            }
        }
        return 0;
    }

    /** The word stored for stop {@code index}, held to the stops there are. */
    @NonNull
    static String valueAt(@NonNull String[] values, int index) {
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
