package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

/**
 * The launcher's own widgets: the catalogue the picker offers and the key a record is stored
 * under. The {@link #id} is durable — it is what the repository persists — so it never changes
 * once shipped; the label and the default span are presentation.
 */
public enum BuiltinWidgetKind {
    CLOCK_ANALOG("clock.analog", R.string.builtin_widget_clock_analog, 1, 1),
    CLOCK_DIGITAL("clock.digital", R.string.builtin_widget_clock_digital, 2, 1),
    AGENDA("agenda", R.string.builtin_widget_agenda, 2, 1),
    WEATHER("weather", R.string.builtin_widget_weather, 2, 1),
    BATTERY("battery", R.string.builtin_widget_battery, 1, 1),
    SYSTEM("system", R.string.builtin_widget_system, 2, 1),
    MEDIA("media", R.string.builtin_widget_media, 2, 1),
    NOTIFICATIONS("notifications", R.string.builtin_widget_notifications, 2, 2),
    TASKS("tasks", R.string.builtin_widget_tasks, 2, 2),
    NOTES("notes", R.string.builtin_widget_notes, 2, 2),
    SHELL("shell", R.string.builtin_widget_shell, 2, 1),
    CALENDAR("calendar.month", R.string.builtin_widget_calendar, 2, 2);

    @NonNull public final String id;
    @StringRes public final int label;
    /** The span the picker offers and a tap places; every kind resizes to any of the five. */
    public final int defaultColumns;
    public final int defaultRows;

    BuiltinWidgetKind(@NonNull String id, @StringRes int label, int defaultColumns,
                      int defaultRows) {
        this.id = id; this.label = label;
        this.defaultColumns = defaultColumns; this.defaultRows = defaultRows;
    }

    /** The kind stored under {@code id}, or null for one this build does not know. */
    @Nullable public static BuiltinWidgetKind fromId(@Nullable String id) {
        if (id == null) return null;
        for (BuiltinWidgetKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }
}
