package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

/**
 * The launcher's own widgets: the catalogue the picker offers and the key a record is stored
 * under. The {@link #id} is durable — it is what the repository persists — so it never changes
 * once shipped; the label and the default span are presentation. A kind that merged into another
 * keeps its id as an alias ({@link BuiltinWidgetAliases}) and leaves this list, so the picker
 * no longer offers it while the records placed under it keep loading.
 */
public enum BuiltinWidgetKind {
    /** The one Clock, on eight faces; {@code clock.analog} and {@code clock.digital} open as it. */
    CLOCK("clock", R.string.builtin_widget_clock, 2, 1),
    /** The first look of the day, drawn Bare: the date and the weather, then one thing that matters. */
    GLANCE("glance", R.string.builtin_widget_glance, 4, 1),
    WEATHER("weather", R.string.builtin_widget_weather, 2, 1),
    BATTERY("battery", R.string.builtin_widget_battery, 1, 1),
    SYSTEM("system", R.string.builtin_widget_system, 2, 1),
    MEDIA("media", R.string.builtin_widget_media, 2, 1),
    NOTIFICATIONS("notifications", R.string.builtin_widget_notifications, 2, 2),
    TASKS("tasks", R.string.builtin_widget_tasks, 2, 2),
    NOTES("notes", R.string.builtin_widget_notes, 2, 2),
    /** The one Calendar, events first; {@code agenda} and {@code calendar.month} open as it. */
    CALENDAR("calendar", R.string.builtin_widget_calendar, 2, 2),
    TERMINAL("terminal", R.string.builtin_widget_terminal, 4, 2);

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

    /**
     * The kind a record stored under {@code id} opens as: a current kind's own id, or a retired
     * kind's alias ({@link BuiltinWidgetAliases#kindOf}); null for one this build does not know.
     */
    @Nullable public static BuiltinWidgetKind fromId(@Nullable String id) {
        return BuiltinWidgetAliases.kindOf(id);
    }

    /** The current kind whose own id is {@code id}, aliases left out. */
    @Nullable static BuiltinWidgetKind currentFromId(@Nullable String id) {
        if (id == null) return null;
        for (BuiltinWidgetKind kind : values()) if (kind.id.equals(id)) return kind;
        return null;
    }
}
