package com.termux.app.launcher.widget.builtin;

import android.content.Context;

import androidx.annotation.NonNull;

/** One place that knows which view class draws which kind. */
public final class BuiltinWidgetFactory {
    private BuiltinWidgetFactory() { }

    @NonNull public static BuiltinWidgetView create(@NonNull Context context,
                                                    @NonNull BuiltinWidgetKind kind,
                                                    @NonNull BuiltinWidgetServices services,
                                                    @NonNull BuiltinWidgetStyle style) {
        switch (kind) {
            case CLOCK_ANALOG: return new AnalogClockWidgetView(context, services, style);
            case CLOCK_DIGITAL: return new DigitalClockWidgetView(context, services, style);
            case AGENDA: return new AgendaWidgetView(context, services, style);
            case WEATHER: return new WeatherWidgetView(context, services, style);
            case BATTERY: return new BatteryWidgetView(context, services, style);
            case SYSTEM: return new SystemWidgetView(context, services, style);
            case MEDIA: return new MediaWidgetView(context, services, style);
            case NOTIFICATIONS: return new NotificationsWidgetView(context, services, style);
            case TASKS: return new TasksWidgetView(context, services, style);
            case NOTES: return new NotesWidgetView(context, services, style);
            case SHELL: return new ShellWidgetView(context, services, style);
            case CALENDAR: return new CalendarWidgetView(context, services, style);
            default: throw new IllegalArgumentException("unknown kind " + kind);
        }
    }
}
