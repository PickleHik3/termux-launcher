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
    CLOCK("clock", R.string.builtin_widget_clock, 2, 1,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_clock_description),
    /** The first look of the day, drawn Bare: the date and the weather, then one thing that matters. */
    GLANCE("glance", R.string.builtin_widget_glance, 4, 1,
        BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.FOUR_BY_ONE, R.string.builtin_widget_glance_description),
    WEATHER("weather", R.string.builtin_widget_weather, 2, 1,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_weather_description),
    /** The phone's own state, battery first; {@code battery} and {@code system} open as it. */
    DEVICE("device", R.string.builtin_widget_device, 2, 1,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_device_description),
    MEDIA("media", R.string.builtin_widget_media, 2, 1,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_media_description),
    NOTIFICATIONS("notifications", R.string.builtin_widget_notifications, 2, 2,
        BuiltinWidgetSpan.TWO_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_notifications_description),
    TASKS("tasks", R.string.builtin_widget_tasks, 2, 2,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_tasks_description),
    NOTES("notes", R.string.builtin_widget_notes, 2, 2,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_notes_description),
    /** The one Calendar, events first; {@code agenda} and {@code calendar.month} open as it. */
    CALENDAR("calendar", R.string.builtin_widget_calendar, 2, 2,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_calendar_description),
    TERMINAL("terminal", R.string.builtin_widget_terminal, 4, 2,
        BuiltinWidgetSpan.ONE_BY_ONE, BuiltinWidgetSpan.FOUR_BY_TWO, R.string.builtin_widget_terminal_description);

    @NonNull public final String id;
    @StringRes public final int label;
    /** The span the picker offers and a tap places; every kind resizes to any of the five. */
    public final int defaultColumns;
    public final int defaultRows;
    /**
     * The smallest span the kind may be resized to or placed at; a record stored below it keeps
     * loading and is not resized for it.
     */
    @NonNull public final BuiltinWidgetSpan minSpan;
    /**
     * The largest span that still adds content; past it a widget only gains room. The one source
     * the layout planners read for {@code WidgetLayoutPlan.maxSpan}.
     */
    @NonNull public final BuiltinWidgetSpan maxSpan;
    /** What the picker's card says the widget is for; it starts with a verb. */
    @StringRes public final int description;

    BuiltinWidgetKind(@NonNull String id, @StringRes int label, int defaultColumns,
                      int defaultRows, @NonNull BuiltinWidgetSpan minSpan,
                      @NonNull BuiltinWidgetSpan maxSpan, @StringRes int description) {
        this.id = id; this.label = label;
        this.defaultColumns = defaultColumns; this.defaultRows = defaultRows;
        this.minSpan = minSpan; this.maxSpan = maxSpan; this.description = description;
    }

    /**
     * The sizes the picker groups on one card: every design bucket from {@link #minSpan} to
     * {@link #maxSpan}, smallest first.
     */
    @NonNull public java.util.List<BuiltinWidgetSpan> sizes() {
        java.util.List<BuiltinWidgetSpan> sizes = new java.util.ArrayList<>();
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            if (span.columns >= minSpan.columns && span.rows >= minSpan.rows
                && span.columns <= maxSpan.columns && span.rows <= maxSpan.rows) sizes.add(span);
        }
        return sizes;
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
