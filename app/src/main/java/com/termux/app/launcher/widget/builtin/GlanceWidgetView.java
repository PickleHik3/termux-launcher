package com.termux.app.launcher.widget.builtin;

import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.statusbar.WeatherController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The first look of the day (glossary: Glance), at 4×1 and 2×1: the date and the weather now on
 * the first line, and on the second the one item that matters most — an event within two hours,
 * a low battery, an alarm within twelve hours, or the next event today — hidden when there is
 * none. It never shows the time. Always drawn Bare ({@link #asksBare}): no card, its ink straight
 * on the wallpaper, measured there. The view only draws its plan ({@link GlanceLayoutPlanner}),
 * from the sources the other widgets read: {@link CalendarEventsSource}, the shared
 * {@link WeatherController} and {@link BatterySource}, with the platform's next alarm clock read
 * on the minute. A tap opens what line two is about, or the calendar.
 */
public class GlanceWidgetView extends BuiltinWidgetView implements BatterySource.Listener {
    private final WeatherController.Listener weatherListener = this::onWeather;
    private final BuiltinWidgetServices.TickListener tickListener = this::onTick;
    private final CalendarEventsSource.Listener calendarListener = snapshot -> refresh();

    @Nullable private WeatherController.Weather weather;
    @Nullable private BatterySource.State battery;
    /** What the plan on screen was drawn from, so an unchanged minute redraws nothing. */
    @NonNull private String shownSignature = "";
    /** The item line two shows, for a tap; null when it shows none. */
    @Nullable private GlanceLayoutPlanner.Item shownItem;

    public GlanceWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                            @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.GLANCE, services, style);
    }

    /** Glance has no card: it is always Bare. */
    @Override protected boolean asksBare() { return true; }

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        services.addTickListener(tickListener);
        CalendarEventsSource.of(services).subscribe(services, calendarListener);
        BatterySource source = BatterySource.get(getContext());
        battery = source.latest();
        source.acquire(this);
        // A fresh forecast is replayed synchronously through the listener; a stale one fetches.
        services.acquireWeather(weatherListener);
        refresh();
    }

    @Override protected void onStop() {
        services.removeTickListener(tickListener);
        CalendarEventsSource.of(services).unsubscribe(calendarListener);
        BatterySource.get(getContext()).release(this);
        services.releaseWeather(weatherListener);
    }

    @Override public void onBatteryChanged(@NonNull BatterySource.State state) {
        battery = state;
        refresh();
    }

    private void onWeather(@NonNull WeatherController.Weather next) {
        // A failed refresh keeps the forecast already shown: stale beats blank.
        if (next.valid || weather == null || !weather.valid) weather = next;
        refresh();
    }

    /** The minute: the date, "in 40 min" and the alarm move on. */
    private void onTick() { refresh(); }

    @Override protected void onTap() {
        if (isPreview()) return;
        GlanceLayoutPlanner.Item item = shownItem;
        Context context = getContext();
        if (item != null && item.event != null) {
            CalendarWidgetSupport.openEvent(context, item.event);
        } else if (item != null && item.kind == GlanceLayoutPlanner.Kind.ALARM) {
            ClockWidgetSupport.openClockApp(context);
        } else if (item != null && item.kind == GlanceLayoutPlanner.Kind.BATTERY_LOW) {
            try {
                context.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // No battery screen on this build; the widget stays as it is.
            }
        } else {
            CalendarWidgetSupport.openCalendarAt(context, System.currentTimeMillis());
        }
    }

    // ----- layouts --------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        render(frame, span, glance());
    }

    /** Draws the plan again when what it says has moved. */
    private void refresh() {
        if (!isStarted()) return;
        GlanceLayoutPlanner.Content next = glance();
        if (signature(next).equals(shownSignature)) return;
        FrameLayout frame = content();
        frame.removeAllViews();
        render(frame, span(), next);
    }

    private void render(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                        @NonNull GlanceLayoutPlanner.Content shown) {
        shownSignature = signature(shown);
        shownItem = shown.item;
        setContentDescription(describe(shown));
        float fontScale = getResources().getConfiguration().fontScale;
        frame.addView(new WidgetPlanView(getContext(), style(), (widthDp, heightDp, measure) ->
            GlanceLayoutPlanner.plan(span, widthDp, heightDp, fontScale, shown, measure)),
            new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** What the widget has to say now; sample content on a picker card. */
    @NonNull private GlanceLayoutPlanner.Content glance() {
        ZonedDateTime now = ZonedDateTime.now();
        String date = ClockWidgetSupport.date("EEEMMMd", now);
        GlanceLayoutPlanner.Words words = words();
        if (isPreview()) {
            WeatherWidgetModel sample = WeatherWidgetModel.sample();
            long soon = now.toInstant().toEpochMilli() + 40L * 60_000L;
            GlanceLayoutPlanner.Item item = new GlanceLayoutPlanner.Item(
                GlanceLayoutPlanner.Kind.EVENT_SOON,
                words.eventSoon(getContext().getString(R.string.bw_glance_sample_event),
                    words.time(soon), 40L),
                GlanceLayoutPlanner.GLYPH_EVENT, WidgetLayoutPlan.Ink.STRONG, null);
            return new GlanceLayoutPlanner.Content(date, sample.temp, sample.glyph,
                skyInk(sample.tone), item);
        }
        String temp = "", glyph = "";
        WidgetLayoutPlan.Ink sky = WidgetLayoutPlan.Ink.CLOUD;
        WeatherController.Weather forecast = weather;
        if (forecast != null && forecast.valid) {
            WeatherWidgetModel model = weatherModel(forecast);
            temp = model.temp;
            glyph = model.glyph;
            sky = skyInk(model.tone);
        }
        long nowMs = now.toInstant().toEpochMilli();
        long tomorrow = LocalDate.now(now.getZone()).plusDays(1).atStartOfDay(now.getZone())
            .toInstant().toEpochMilli();
        CalendarEventsSource.Snapshot snapshot = CalendarEventsSource.of(services).snapshot();
        BatterySource.State state = battery;
        int percent = state == null || !state.known() ? -1 : state.level;
        boolean charging = state != null && (state.charging || state.plugged);
        GlanceLayoutPlanner.Item item = GlanceLayoutPlanner.pick(nowMs, tomorrow,
            snapshot.permitted ? snapshot.events : Collections.<CalendarEvent>emptyList(), percent, charging,
            nextAlarmMs(), words);
        return new GlanceLayoutPlanner.Content(date, temp, glyph, sky, item);
    }

    @NonNull private static WidgetLayoutPlan.Ink skyInk(@NonNull WeatherWidgetModel.Tone tone) {
        switch (tone) {
            case SUN: return WidgetLayoutPlan.Ink.SUN;
            case MOON: return WidgetLayoutPlan.Ink.MOON;
            case CLOUD:
            default: return WidgetLayoutPlan.Ink.CLOUD;
        }
    }

    /** The platform's next alarm clock, or 0 for none. */
    private long nextAlarmMs() {
        try {
            AlarmManager alarms = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo next = alarms == null ? null : alarms.getNextAlarmClock();
            return next == null ? 0L : next.getTriggerTime();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private boolean is24() { return DateFormat.is24HourFormat(getContext()); }

    private boolean fahrenheit() {
        TermuxAppSharedPreferences preferences = preferences();
        return preferences != null && preferences.isStatusWidgetWeatherFahrenheit();
    }

    /**
     * The weather now, as line one shows it: built again only when the forecast or the unit
     * changed, not on every battery broadcast and minute tick. The temperature, glyph and tone it
     * reads come from the forecast's current conditions alone, never from the time.
     */
    @NonNull private WeatherWidgetModel weatherModel(@NonNull WeatherController.Weather forecast) {
        boolean fahrenheit = fahrenheit();
        WeatherWidgetModel model = shownModel;
        if (model == null || shownModelFor != forecast
            || shownModelFetchedAt != forecast.fetchedAtMs || shownModelFahrenheit != fahrenheit) {
            model = WeatherWidgetModel.from(forecast, fahrenheit, is24(), Calendar.getInstance());
            shownModel = model;
            shownModelFor = forecast;
            shownModelFetchedAt = forecast.fetchedAtMs;
            shownModelFahrenheit = fahrenheit;
        }
        return model;
    }

    @Nullable private WeatherWidgetModel shownModel;
    @Nullable private WeatherController.Weather shownModelFor;
    private long shownModelFetchedAt;
    private boolean shownModelFahrenheit;

    /** Line two's words, from the app's strings. */
    @NonNull private GlanceLayoutPlanner.Words words() {
        Context context = getContext();
        boolean is24 = is24();
        return new GlanceLayoutPlanner.Words() {
            @NonNull @Override public String time(long epochMs) {
                ZonedDateTime at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs),
                    ZoneId.systemDefault());
                return is24
                    ? String.format(Locale.ROOT, "%02d:%02d", at.getHour(), at.getMinute())
                    : (ClockWidgetFormats.hoursMinutes(at.toLocalTime(), false) + " "
                        + ClockWidgetFormats.amPm(at.toLocalTime(), false)).trim();
            }
            @NonNull @Override public String untitled() {
                return context.getString(R.string.bw_calendar_untitled);
            }
            @NonNull @Override public String eventSoon(@NonNull String title, @NonNull String time,
                                                       long minutesAway) {
                return context.getString(R.string.bw_glance_event_soon, title, time,
                    away(context, minutesAway));
            }
            @NonNull @Override public String eventToday(@NonNull String title, @NonNull String time) {
                return context.getString(R.string.bw_glance_event_today, title, time);
            }
            @NonNull @Override public String alarm(@NonNull String time) {
                return context.getString(R.string.bw_clock_alarm, time);
            }
            @NonNull @Override public String batteryLow(int percent) {
                return context.getString(R.string.bw_glance_battery_low, percent);
            }
        };
    }

    /** "in 40 min", "in 1 h 20 min", "in 2 h", "now". */
    @NonNull private static String away(@NonNull Context context, long minutes) {
        if (minutes <= 0L) return context.getString(R.string.bw_glance_now);
        int hours = (int) (minutes / 60L);
        int rest = (int) (minutes % 60L);
        if (hours == 0) return context.getString(R.string.bw_glance_in_minutes, rest);
        if (rest == 0) return context.getString(R.string.bw_glance_in_hours, hours);
        return context.getString(R.string.bw_glance_in_hours_minutes, hours, rest);
    }

    /** "Wed, Oct 21, 21 degrees, Design review · 15:00 in 40 min". */
    @NonNull private String describe(@NonNull GlanceLayoutPlanner.Content content) {
        List<String> said = new ArrayList<>();
        said.add(content.date);
        if (content.hasWeather()) {
            said.add(getContext().getString(R.string.bw_glance_cd_weather,
                content.temp.replace("°", "")));
        }
        if (content.item != null) said.add(content.item.text);
        return android.text.TextUtils.join(", ", said);
    }

    /** What a plan was drawn from: the same signature draws the same plan. */
    @NonNull private static String signature(@NonNull GlanceLayoutPlanner.Content content) {
        GlanceLayoutPlanner.Item item = content.item;
        return content.date + '\u0000' + content.temp + '\u0000' + content.weatherGlyph
            + '\u0000' + content.weatherInk + '\u0000'
            + (item == null ? "" : item.kind + "\u0000" + item.text);
    }
}
