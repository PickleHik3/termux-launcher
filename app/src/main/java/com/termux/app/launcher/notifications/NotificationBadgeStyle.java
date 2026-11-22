package com.termux.app.launcher.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How an icon says it has notifications: a plain dot, as it always did, or the number of active
 * notifications for the app (or, on a folder, for every app in it).
 */
public enum NotificationBadgeStyle {
    DOT("dot"),
    COUNT("count");

    /** Settings › Notifications › Badge style, in the app's default preferences. */
    public static final String KEY = "app_launcher_notification_badge_style";

    /** The largest number shown as itself; more reads {@code 99+}. */
    public static final int MAX_SHOWN = 99;

    @NonNull public final String value;

    NotificationBadgeStyle(@NonNull String value) {
        this.value = value;
    }

    /** A missing or unknown value is the dot, which is what the launcher drew before the choice. */
    @NonNull
    public static NotificationBadgeStyle fromValue(@Nullable String value) {
        return COUNT.value.equals(value) ? COUNT : DOT;
    }

    /** The text for a count, capped at {@code 99+}; empty when there is nothing to say. */
    @NonNull
    public static String countLabel(int count) {
        if (count <= 0) return "";
        return count > MAX_SHOWN ? MAX_SHOWN + "+" : Integer.toString(count);
    }
}
