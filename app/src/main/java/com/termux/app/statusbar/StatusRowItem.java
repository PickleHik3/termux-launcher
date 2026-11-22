package com.termux.app.statusbar;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * One segment of the status bar's row of readings and marks, in its shipped order: the order the
 * row draws them in until the user reorders it ({@link StatusRowOrder}). Each item is one widget
 * view and the dot that parts it from the item before it, and keeps its ink slots
 * ({@link StatusInkSlots}) wherever it stands, so a crossing never blends two items' colours.
 */
public enum StatusRowItem {
    CPU("cpu", R.id.terminal_status_widget_cpu, R.id.terminal_status_dot_cpu,
        StatusBarWidgetView.ColorRole.SECONDARY),
    GPU("gpu", R.id.terminal_status_widget_gpu, R.id.terminal_status_dot_gpu,
        StatusBarWidgetView.ColorRole.SECONDARY),
    RAM("ram", R.id.terminal_status_widget_ram, R.id.terminal_status_dot_ram,
        StatusBarWidgetView.ColorRole.SECONDARY),
    BATTERY("battery", R.id.terminal_status_widget_battery, R.id.terminal_status_dot_battery,
        StatusBarWidgetView.ColorRole.SECONDARY),
    WEATHER("weather", R.id.terminal_status_widget_weather, R.id.terminal_status_dot_weather,
        StatusBarWidgetView.ColorRole.SECONDARY),
    TEXT_SIZE("text_size", R.id.terminal_status_widget_font_toggle,
        R.id.terminal_status_dot_font_toggle, StatusBarWidgetView.ColorRole.PRIMARY),
    NOTIFICATIONS("notifications", R.id.terminal_status_widget_notifications,
        R.id.terminal_status_dot_notifications, StatusBarWidgetView.ColorRole.SECONDARY),
    /** The one tertiary mark: its dot wears its tier. */
    AI("ai", R.id.terminal_status_widget_ai, R.id.terminal_status_dot_ai,
        StatusBarWidgetView.ColorRole.TERTIARY),
    MOUSE("mouse", R.id.terminal_status_widget_mouse, R.id.terminal_status_dot_mouse,
        StatusBarWidgetView.ColorRole.PRIMARY);

    /** The name the order preference stores; never changes once shipped. */
    @NonNull public final String key;
    @IdRes public final int widgetId;
    /** The dot before this item, shown only while a shown item stands before it. */
    @IdRes public final int dotId;
    /** The tier the dot is drawn in: the item's own. */
    @NonNull public final StatusBarWidgetView.ColorRole dotRole;

    StatusRowItem(@NonNull String key, @IdRes int widgetId, @IdRes int dotId,
                  @NonNull StatusBarWidgetView.ColorRole dotRole) {
        this.key = key;
        this.widgetId = widgetId;
        this.dotId = dotId;
        this.dotRole = dotRole;
    }

    /** The item stored as {@code key}; null for one this build does not know. */
    @Nullable
    public static StatusRowItem forKey(@Nullable String key) {
        if (key == null) return null;
        for (StatusRowItem item : values())
            if (item.key.equals(key)) return item;
        return null;
    }
}
