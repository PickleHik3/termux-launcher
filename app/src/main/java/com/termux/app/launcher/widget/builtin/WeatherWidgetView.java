package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.text.format.DateFormat;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.fragments.settings.termux.WeatherPlacePickerDialog;
import com.termux.app.statusbar.WeatherController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

/**
 * The weather where the phone is: the shared keyless forecast the status bar's card reads, at five
 * spans — a glance at 1×1, the condition and the next sun time at 2×1, the next hours at 2×2 and
 * 4×1, the coming days at 4×2. The view only draws its layout plan ({@link WeatherLayoutPlanner}).
 * A tap fetches it again.
 */
public class WeatherWidgetView extends BuiltinWidgetView {
    private final WeatherController.Listener weatherListener = this::onWeather;
    private final BuiltinWidgetServices.TickListener tickListener = this::onTick;

    @Nullable private WeatherController controller;
    /** The last valid forecast; kept across a failed refresh, which shows the old one. */
    @Nullable private WeatherController.Weather latest;
    /** Why there is no forecast yet, when there is none. */
    @Nullable private String error;
    /** The place picked in Settings that {@link #error} is about, empty for the device. */
    @NonNull private String errorPlace = "";

    // What the current layout was drawn from, so a replayed cache does not redraw it.
    private long shownFetchedAt = -1L;
    private boolean shownFahrenheit;
    @NonNull private String shownHour = "";
    private int shownStale = -1;
    @Nullable private String shownError;

    public WeatherWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                             @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.WEATHER, services, style);
    }

    /** Bare is this widget's own choice, off by default (its Glass row). */
    @Override protected boolean asksBare() { return bareChosen(); }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        services.addTickListener(tickListener);
        // A fresh cache is replayed synchronously through the listener; a stale one fetches.
        controller = services.acquireWeather(weatherListener);
        if (latest != null && fahrenheit() != shownFahrenheit) redraw();
    }

    @Override protected void onStop() {
        services.removeTickListener(tickListener);
        services.releaseWeather(weatherListener);
        controller = null;
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        if (latest == null && "no-location".equals(error)) {
            // No fix to refresh from: offer the status bar's place picker, then fetch for it.
            WeatherPlacePickerDialog.show(getContext(), () -> {
                WeatherController c = controller;
                if (c != null) c.forceRefresh();
            });
            return;
        }
        WeatherController current = controller;
        if (current != null) current.forceRefresh();
    }

    private void onWeather(@NonNull WeatherController.Weather weather) {
        if (!isStarted()) return;
        if (weather.valid) {
            boolean unchanged = weather == latest && weather.fetchedAtMs == shownFetchedAt
                && fahrenheit() == shownFahrenheit
                && currentHour().equals(shownHour)
                && staleNow(weather) == shownStale;
            latest = weather;
            error = null;
            if (unchanged) return;
        } else {
            // A failed refresh keeps the forecast already on the card; stale beats blank. Not once
            // the place has changed (the controller emptied the cache latest points at, so it reads
            // invalid) or the picked place cannot be found: that forecast is for somewhere else.
            if (latest != null && latest.valid && !"unknown-place".equals(weather.error)) return;
            latest = null;
            error = weather.error;
            errorPlace = weather.requestedPlace;
            if (shownFetchedAt < 0 && Objects.equals(error, shownError)) return;
        }
        redraw();
    }

    /** The strip is "the next hours": move it on when the hour turns. */
    private void onTick() {
        if (latest != null && (!currentHour().equals(shownHour)
            || staleNow(latest) != shownStale)) redraw();
    }

    private static int staleNow(@NonNull WeatherController.Weather weather) {
        return WeatherWidgetModel.staleMinutes(weather.fetchedAtMs, System.currentTimeMillis());
    }

    private void redraw() {
        FrameLayout frame = content();
        frame.removeAllViews();
        render(frame, span(), ui());
    }

    @NonNull private static String currentHour() {
        return WeatherWidgetModel.currentHourIso(Calendar.getInstance());
    }

    private boolean fahrenheit() {
        TermuxAppSharedPreferences preferences = preferences();
        return preferences != null && preferences.isStatusWidgetWeatherFahrenheit();
    }

    // ----- layouts --------------------------------------------------------------------------

    /** The plan is laid out at the card's own size, so the card is never zoomed to fill. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        render(frame, span, ui);
    }

    /** Draws the Weather plan ({@link WeatherLayoutPlanner}) for {@code span} at the card's size. */
    private void render(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                        @NonNull BuiltinWidgetUi ui) {
        WeatherWidgetModel model = null;
        boolean is24 = DateFormat.is24HourFormat(getContext());
        if (isPreview()) {
            model = WeatherWidgetModel.sample();
        } else if (latest != null && latest.valid) {
            Calendar now = Calendar.getInstance();
            boolean fahrenheit = fahrenheit();
            model = WeatherWidgetModel.from(latest, fahrenheit, is24, now);
            shownFetchedAt = latest.fetchedAtMs;
            shownFahrenheit = fahrenheit;
            shownHour = WeatherWidgetModel.currentHourIso(now);
            shownStale = model.staleMinutes;
        }
        String empty = "";
        if (model == null) {
            shownFetchedAt = -1L;
            shownError = error;
            empty = emptyMessage();
            setContentDescription(getContext().getString(R.string.bw_feeds_weather_cd_empty, empty));
        } else {
            setContentDescription(describe(model));
        }
        WeatherWidgetModel shown = model;
        String message = empty;
        float fontScale = getResources().getConfiguration().fontScale;
        WeatherLayoutPlanner.Words words = words();
        boolean offerPlace = model == null && !isPreview() && "no-location".equals(error);
        frame.addView(new WidgetPlanView(getContext(), style(), (widthDp, heightDp, measure) ->
            WeatherLayoutPlanner.plan(span, widthDp, heightDp, fontScale, shown, message,
                offerPlace, words, measure)), matchParent());
    }

    /** The plan's words, from the app's strings. */
    @NonNull private WeatherLayoutPlanner.Words words() {
        Context context = getContext();
        return new WeatherLayoutPlanner.Words() {
            @NonNull @Override public String placeUnknown() {
                return context.getString(R.string.bw_feeds_weather_place_unknown);
            }
            @NonNull @Override public String range(@NonNull String high, @NonNull String low) {
                return context.getString(R.string.bw_feeds_weather_range_short, high, low);
            }
            @NonNull @Override public String highLow(@NonNull String high, @NonNull String low) {
                return context.getString(R.string.bw_feeds_weather_high_low, high, low);
            }
            @NonNull @Override public String rain(int percent) {
                return context.getString(R.string.bw_feeds_weather_rain, percent);
            }
            @NonNull @Override public String sunrise(@NonNull String time) {
                return context.getString(R.string.bw_feeds_weather_sunrise, time);
            }
            @NonNull @Override public String sunset(@NonNull String time) {
                return context.getString(R.string.bw_feeds_weather_sunset, time);
            }
            @NonNull @Override public String nextDays(int count) {
                return context.getResources().getQuantityString(
                    R.plurals.bw_feeds_weather_next_days, count, count);
            }
            @NonNull @Override public String updatedAgo(int minutes) {
                return context.getString(R.string.bw_feeds_weather_updated_ago, minutes);
            }
            @NonNull @Override public String setAPlace() {
                return context.getString(R.string.bw_feeds_weather_set_place);
            }
            @NonNull @Override public String tapToRefresh() {
                return context.getString(R.string.bw_feeds_weather_tap_refresh);
            }
        };
    }

    @NonNull private String emptyMessage() {
        if ("unknown-place".equals(error)) {
            return getContext().getString(R.string.weather_card_unavailable_place, errorPlace);
        }
        return getContext().getString("no-location".equals(error)
            ? R.string.bw_feeds_weather_no_location : R.string.bw_feeds_weather_empty);
    }

    // ----- helpers --------------------------------------------------------------------------

    /** "Weather in Kuwait City: 34 degrees, clear, high 36 low 24". */
    @NonNull private String describe(@NonNull WeatherWidgetModel m) {
        StringBuilder text = new StringBuilder(m.place.isEmpty()
            ? getContext().getString(R.string.bw_feeds_weather_cd, m.tempBare)
            : getContext().getString(R.string.bw_feeds_weather_cd_place, m.place, m.tempBare));
        if (!m.condition.isEmpty()) {
            text.append(", ").append(m.condition.toLowerCase(Locale.getDefault()));
        }
        if (m.hasRange()) {
            text.append(", ").append(getContext().getString(R.string.bw_feeds_weather_cd_range,
                m.high, m.low));
        }
        return text.toString();
    }

    @NonNull private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
