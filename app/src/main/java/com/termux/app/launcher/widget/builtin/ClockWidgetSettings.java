package com.termux.app.launcher.widget.builtin;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Clock widget's settings, as its record stores them: the face, Minimal's weight, the seconds
 * and the 24-hour choice. A new Clock takes the status bar's face and its 12/24-hour setting once,
 * when it is placed ({@link #initial}); after that the widget's settings and the bar's are
 * independent, since each lives in its own store. Android-free apart from the bundle it reads.
 */
public final class ClockWidgetSettings {
    private ClockWidgetSettings() { }

    public static final String KEY_FACE = "face";
    /** Minimal's weight: {@link #WEIGHT_THIN} (the default) or {@link #WEIGHT_MEDIUM}. */
    public static final String KEY_WEIGHT = "weight";
    public static final String KEY_SECONDS = "seconds";
    /** 24-hour time; absent follows the phone's format, as the old clocks did. */
    public static final String KEY_24_HOUR = "h24";
    /** The two cities the analog clock kept; carried for the Clock's time zones (#113). */
    public static final String KEY_ZONE1 = "zone1";
    public static final String KEY_ZONE2 = "zone2";
    /** What the analog clock showed while no city was set. */
    public static final String LEGACY_ZONE1 = "Europe/London";
    public static final String LEGACY_ZONE2 = "Asia/Kolkata";

    public static final String ON = "on";
    public static final String OFF = "off";
    public static final String WEIGHT_THIN = "thin";
    public static final String WEIGHT_MEDIUM = "medium";

    /** The face a Clock with no face stored draws: the bar's default. */
    public static final ClockFace FALLBACK_FACE = ClockFace.SLAB;

    /**
     * The settings a new Clock is placed with: the bar's current face (Minimal's medium weight as
     * Minimal, medium) and its 12/24-hour setting, seconds off (spec #101: off at widget size).
     *
     * @param barStyle the bar's stored clock style
     * @param barAmPm  the bar's 12-hour setting ({@code top_pane_clock_am_pm})
     */
    @NonNull public static Map<String, String> initial(@Nullable String barStyle, boolean barAmPm) {
        Map<String, String> values = new LinkedHashMap<>();
        ClockFace face = ClockFace.fromBarStyle(barStyle);
        values.put(KEY_FACE, face.key);
        if (face == ClockFace.MINIMAL) {
            values.put(KEY_WEIGHT, TermuxPreferenceConstants.TERMUX_APP
                .TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(barStyle) ? WEIGHT_MEDIUM : WEIGHT_THIN);
        }
        values.put(KEY_SECONDS, OFF);
        values.put(KEY_24_HOUR, barAmPm ? OFF : ON);
        return values;
    }

    /** {@link #initial} as the bundle a record keeps. */
    @NonNull public static Bundle initialBundle(@Nullable String barStyle, boolean barAmPm) {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : initial(barStyle, barAmPm).entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        return bundle;
    }

    @NonNull public static ClockFace face(@NonNull Bundle settings) {
        ClockFace face = ClockFace.fromKey(settings.getString(KEY_FACE));
        return face == null ? FALLBACK_FACE : face;
    }

    public static boolean medium(@NonNull Bundle settings) {
        return WEIGHT_MEDIUM.equals(settings.getString(KEY_WEIGHT));
    }

    public static boolean seconds(@NonNull Bundle settings) {
        return ON.equals(settings.getString(KEY_SECONDS));
    }

    /** The widget's 24-hour choice, or {@code phone24} where it stores none. */
    public static boolean is24Hour(@NonNull Bundle settings, boolean phone24) {
        String value = settings.getString(KEY_24_HOUR);
        if (ON.equals(value)) return true;
        if (OFF.equals(value)) return false;
        return phone24;
    }

    /**
     * The next alarm as the 2×2 shows it: its time ("07:00", "7:00 AM") when it rings within a
     * day, else its weekday before it ("Thu 07:00"); empty for no alarm or one already past.
     */
    @NonNull public static String alarmTime(@NonNull ZonedDateTime now,
                                            @Nullable ZonedDateTime alarm, boolean is24) {
        if (alarm == null || alarm.isBefore(now)) return "";
        ZonedDateTime local = alarm.withZoneSameInstant(now.getZone());
        String time = is24
            ? String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute())
            : ClockWidgetFormats.hoursMinutes(local.toLocalTime(), false) + " "
                + ClockWidgetFormats.amPm(local.toLocalTime(), false);
        if (Duration.between(now, alarm).toHours() < 24) return time.trim();
        String day = local.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        return (day + " " + time).trim();
    }
}
