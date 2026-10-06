package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.statusbar.WeatherController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The weather where the phone is: the shared keyless forecast the status bar's card reads, at five
 * spans — a glance at 1×1, the next hours from 2×2, the coming days at 4×2. A tap fetches it again.
 */
public class WeatherWidgetView extends BuiltinWidgetView {
    private static final String GLYPH_PLACE = "\uf041";
    private static final String GLYPH_HUMIDITY = "\uf043";
    private static final String GLYPH_NO_WEATHER = "\uf0c2";

    private final WeatherController.Listener weatherListener = this::onWeather;
    private final BuiltinWidgetServices.TickListener tickListener = this::onTick;

    @Nullable private WeatherController controller;
    /** The last valid forecast; kept across a failed refresh, which shows the old one. */
    @Nullable private WeatherController.Weather latest;
    /** Why there is no forecast yet, when there is none. */
    @Nullable private String error;

    // What the current layout was drawn from, so a replayed cache does not redraw it.
    private long shownFetchedAt = -1L;
    private boolean shownFahrenheit;
    @NonNull private String shownHour = "";
    @Nullable private String shownError;

    public WeatherWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                             @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.WEATHER, services, style);
    }

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
        WeatherController current = controller;
        if (current != null) current.forceRefresh();
    }

    private void onWeather(@NonNull WeatherController.Weather weather) {
        if (!isStarted()) return;
        if (weather.valid) {
            boolean unchanged = weather == latest && weather.fetchedAtMs == shownFetchedAt
                && fahrenheit() == shownFahrenheit
                && currentHour().equals(shownHour);
            latest = weather;
            error = null;
            if (unchanged) return;
        } else {
            // A failed refresh keeps the forecast already on the card; stale beats blank.
            if (latest != null) return;
            error = weather.error;
            if (shownFetchedAt < 0 && Objects.equals(error, shownError)) return;
        }
        redraw();
    }

    /** The strip is "the next hours": move it on when the hour turns. */
    private void onTick() {
        if (latest != null && !currentHour().equals(shownHour)) redraw();
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
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(getContext(), false);
        return preferences != null && preferences.isStatusWidgetWeatherFahrenheit();
    }

    // ----- layouts --------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        render(frame, span, ui);
    }

    private void render(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                        @NonNull BuiltinWidgetUi ui) {
        WeatherWidgetModel model = null;
        if (isPreview()) {
            model = WeatherWidgetModel.sample();
        } else if (latest != null && latest.valid) {
            Calendar now = Calendar.getInstance();
            boolean fahrenheit = fahrenheit();
            model = WeatherWidgetModel.from(latest, fahrenheit, now);
            shownFetchedAt = latest.fetchedAtMs;
            shownFahrenheit = fahrenheit;
            shownHour = WeatherWidgetModel.currentHourIso(now);
        }
        if (model == null) {
            shownFetchedAt = -1L;
            shownError = error;
            frame.addView(buildEmpty(span, ui), matchParent());
            setContentDescription(getContext().getString(R.string.bw_feeds_weather_cd_empty,
                emptyMessage()));
            return;
        }
        View root;
        switch (span) {
            case ONE_BY_ONE: root = buildOneByOne(model, ui); break;
            case TWO_BY_TWO: root = buildTwoByTwo(model, ui); break;
            case FOUR_BY_ONE: root = buildFourByOne(model, ui); break;
            case FOUR_BY_TWO: root = buildFourByTwo(model, ui); break;
            case TWO_BY_ONE:
            default: root = buildTwoByOne(model, ui); break;
        }
        frame.addView(root, matchParent());
        setContentDescription(describe(model));
    }

    @NonNull private View buildOneByOne(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        TextView range = ui.mono(getContext().getString(R.string.bw_feeds_weather_range_short,
            m.high, m.low), 10.5f);
        range.setVisibility(m.hasRange() ? VISIBLE : GONE);
        LinearLayout column = ui.column(4, ui.glyph(m.glyph, 22, tint(m.tone)),
            ui.numeral(m.temp, 26), range);
        column.setGravity(Gravity.CENTER);
        inset(column, 6, 0, 6, 0, ui);
        return column;
    }

    @NonNull private View buildTwoByOne(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        TextView line = ui.text(conditionWithRange(m), 11.5f, style().sansBold,
            style().onSurfaceVariant);
        LinearLayout text = ui.column(4, ui.numeral(m.temp, 30), line);
        LinearLayout row = ui.row(12, ui.glyph(m.glyph, 34, tint(m.tone)),
            BuiltinWidgetUi.flex(text));
        inset(row, 14, 0, 14, 0, ui);
        return row;
    }

    @NonNull private View buildTwoByTwo(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        TextView temp = ui.numeral(m.temp, 44);
        temp.setLetterSpacing(-0.03f);
        LinearLayout head = wide(ui.row(8, BuiltinWidgetUi.flex(temp),
            ui.glyph(m.glyph, 32, tint(m.tone))));
        LinearLayout column = ui.column(6, caption(m, ui), head, wide(conditionLine(m, 12, ui)),
            BuiltinWidgetUi.flexTall(new View(getContext())),
            wide(hourlyStrip(m.hours, 4, ui)));
        inset(column, 14, 14, 14, 14, ui);
        return column;
    }

    @NonNull private View buildFourByOne(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        TextView condition = ui.text(m.condition, 11, style().sansBold, style().onSurfaceVariant);
        condition.setVisibility(m.condition.isEmpty() ? GONE : VISIBLE);
        LinearLayout now = BuiltinWidgetUi.size(ui.column(4, ui.numeral(m.temp, 30), condition),
            ui.dp(84), ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout row = ui.row(14, ui.glyph(m.glyph, 32, tint(m.tone)), now,
            BuiltinWidgetUi.flex(hourlyStrip(m.hours, 5, ui)));
        inset(row, 16, 0, 16, 0, ui);
        return row;
    }

    @NonNull private View buildFourByTwo(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        TextView glyph = ui.glyph(m.glyph, 34, tint(m.tone));
        TextView temp = ui.numeral(m.temp, 44);
        temp.setLetterSpacing(-0.03f);
        TextView condition = ui.sans(m.condition, 12, true);
        condition.setVisibility(m.condition.isEmpty() ? GONE : VISIBLE);

        LinearLayout facts = ui.row(10);
        if (m.humidityPct >= 0) {
            facts.addView(ui.row(4, ui.glyph(GLYPH_HUMIDITY, 10.5f, style().onSurfaceVariant),
                ui.mono(m.humidityPct + "%", 10.5f)));
        }
        if (m.windKmh >= 0) {
            TextView wind = ui.mono(getContext().getString(R.string.bw_feeds_weather_wind,
                m.windKmh), 10.5f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (facts.getChildCount() > 0) params.leftMargin = ui.dp(10);
            facts.addView(wind, params);
        }

        LinearLayout left = ui.column(6, caption(m, ui), glyph, temp, condition,
            BuiltinWidgetUi.flexTall(new View(getContext())), facts);
        // The design adds 6dp above the glyph; a TextView's line box is taller than CSS's
        // line-height:1, so the column spends that 6dp on the numeral's ascent instead.
        left.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(120),
            ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout days = new LinearLayout(getContext());
        days.setOrientation(LinearLayout.VERTICAL);
        // justify-content: space-between — the first row at the top, the last at the bottom.
        for (int i = 0; i < m.days.size(); i++) {
            if (i > 0) days.addView(BuiltinWidgetUi.flexTall(new View(getContext())));
            days.addView(dayRow(m.days.get(i), ui), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        days.setLayoutParams(daysParams);

        LinearLayout row = ui.row(18, left, days);
        row.setGravity(Gravity.TOP);
        inset(row, 16, 16, 16, 16, ui);
        return row;
    }

    @NonNull private View dayRow(@NonNull WeatherWidgetModel.Day day, @NonNull BuiltinWidgetUi ui) {
        TextView name = BuiltinWidgetUi.size(ui.sans(day.name, 12, true), ui.dp(36),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        TextView glyph = BuiltinWidgetUi.size(ui.glyph(day.glyph, 14, tint(day.tone)), ui.dp(22),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        TextView low = ui.mono(day.low, 11);
        low.setMinWidth(ui.dp(20));
        low.setGravity(Gravity.END);
        TextView high = ui.text(day.high, 11, style().monoMedium, style().onSurface);
        high.setMinWidth(ui.dp(20));
        BuiltinWidgetUi.BarView bar = ui.bar(0f, style().warm, 5);
        bar.set(day.start, day.end, style().warm);
        bar.setLayoutParams(new LinearLayout.LayoutParams(0, ui.dp(5), 1f));
        LinearLayout range = ui.row(6, low, bar, high);
        range.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return ui.row(8, name, glyph, range);
    }

    /** The next {@code count} hours as equal columns: hour, sky, temperature. */
    @NonNull private LinearLayout hourlyStrip(@NonNull List<WeatherWidgetModel.Hour> hours,
                                              int count, @NonNull BuiltinWidgetUi ui) {
        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < count; i++) {
            View cell;
            if (i < hours.size()) {
                WeatherWidgetModel.Hour hour = hours.get(i);
                LinearLayout column = ui.column(4,
                    ui.text(hour.label, 10, style().monoMedium, style().onSurfaceVariant),
                    ui.glyph(hour.glyph, 14, tint(hour.tone)),
                    ui.text(hour.temp, 11, style().monoMedium, style().onSurface));
                column.setGravity(Gravity.CENTER_HORIZONTAL);
                cell = column;
            } else {
                cell = new View(getContext());
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) params.leftMargin = ui.dp(2);
            strip.addView(cell, params);
        }
        return strip;
    }

    /** "Clear" in the surface colour, then "· H 36 L 24" in the variant. */
    @NonNull private LinearLayout conditionLine(@NonNull WeatherWidgetModel m, float sp,
                                                @NonNull BuiltinWidgetUi ui) {
        TextView condition = ui.sans(m.condition, sp, true);
        String range = m.hasRange()
            ? getContext().getString(R.string.bw_feeds_weather_range_long, m.high, m.low) : "";
        if (!m.condition.isEmpty() && !range.isEmpty()) range = "· " + range;
        TextView tail = ui.text(range, sp, style().sansMedium, style().onSurfaceVariant);
        condition.setVisibility(m.condition.isEmpty() ? GONE : VISIBLE);
        tail.setVisibility(range.isEmpty() ? GONE : VISIBLE);
        return ui.row(4, condition, BuiltinWidgetUi.flex(tail));
    }

    @NonNull private View caption(@NonNull WeatherWidgetModel m, @NonNull BuiltinWidgetUi ui) {
        String place = m.place.isEmpty()
            ? getContext().getString(R.string.bw_feeds_weather_place_unknown) : m.place;
        return ui.caption(place, GLYPH_PLACE, place.toLowerCase(Locale.getDefault()));
    }

    @NonNull private String conditionWithRange(@NonNull WeatherWidgetModel m) {
        if (!m.hasRange()) return m.condition;
        if (m.condition.isEmpty()) {
            return getContext().getString(R.string.bw_feeds_weather_range_compact, m.high, m.low);
        }
        return getContext().getString(R.string.bw_feeds_weather_condition_range, m.condition,
            m.high, m.low);
    }

    // ----- empty ----------------------------------------------------------------------------

    @NonNull private View buildEmpty(@NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui) {
        boolean small = span == BuiltinWidgetSpan.ONE_BY_ONE;
        TextView message = ui.text(emptyMessage(), small ? 11 : 12, style().sansBold,
            style().onSurface);
        message.setSingleLine(false);
        message.setMaxLines(small ? 3 : 2);
        message.setGravity(Gravity.CENTER);
        TextView hint = ui.mono(getContext().getString(R.string.bw_feeds_weather_tap_refresh), 10.5f);
        hint.setVisibility(small ? GONE : VISIBLE);
        LinearLayout column = ui.column(6,
            ui.glyph(GLYPH_NO_WEATHER, small ? 20 : 24, style().onSurfaceVariant), message, hint);
        column.setGravity(Gravity.CENTER);
        inset(column, 10, 8, 10, 8, ui);
        return column;
    }

    @NonNull private String emptyMessage() {
        return getContext().getString("no-location".equals(error)
            ? R.string.bw_feeds_weather_no_location : R.string.bw_feeds_weather_empty);
    }

    // ----- helpers --------------------------------------------------------------------------

    @ColorInt private int tint(@NonNull WeatherWidgetModel.Tone tone) {
        switch (tone) {
            case SUN: return style().warm;
            case MOON: return style().primary;
            case CLOUD:
            default: return style().onSurfaceVariant;
        }
    }

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

    @NonNull private static <V extends View> V wide(@NonNull V view) {
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    @NonNull private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
