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
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.statusbar.WeatherController;

import java.time.LocalTime;
import java.time.ZonedDateTime;

import static com.termux.app.launcher.widget.builtin.ClockWidgetSupport.wide;

/**
 * A digital clock: the time and date, with the day's and the year's progress at 2×2 and 4×2,
 * today's sunrise and sunset at 4×1 (from the shared weather, hidden when there is none), and
 * ticking seconds at 4×2.
 *
 * <p>Everything changes once a minute on the shared tick; only the 4×2 seconds repaint every
 * second, and only while the widget is started and visible.</p>
 */
public class DigitalClockWidgetView extends BuiltinWidgetView {
    /** A labelled progress line: "Day 57%" over a bar. */
    private static final class Progress {
        @NonNull final TextView percent;
        @NonNull final BuiltinWidgetUi.BarView bar;
        @ColorInt final int color;

        Progress(@NonNull TextView percent, @NonNull BuiltinWidgetUi.BarView bar, @ColorInt int color) {
            this.percent = percent; this.bar = bar; this.color = color;
        }
    }

    @Nullable private ClockNumeralView hoursView;
    @Nullable private ClockNumeralView minutesView;
    @Nullable private ClockNumeralView timeView;
    @Nullable private TextView captionDate;
    @Nullable private TextView amPmView;
    @Nullable private TextView dateView;
    @Nullable private TextView zoneView;
    @Nullable private Progress dayProgress;
    @Nullable private Progress yearProgress;
    @Nullable private View sunRow;
    @Nullable private TextView sunriseView;
    @Nullable private TextView sunsetView;
    private boolean seconds;
    /** The user's 12/24-hour choice as of the last minute; a change of it arrives as a tick. */
    private boolean is24 = true;

    private final ClockWidgetSupport.SecondTicker ticker;
    private final BuiltinWidgetServices.TickListener minuteTick = this::onMinute;
    private final WeatherController.Listener weatherListener = this::onWeather;
    @Nullable private WeatherController weather;
    @Nullable private WeatherController.Weather lastWeather;
    private boolean shown;
    private long shownMinute = Long.MIN_VALUE;

    public DigitalClockWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.CLOCK_DIGITAL, services, style);
        ticker = new ClockWidgetSupport.SecondTicker(services.main(), this::onSecond);
    }

    // ----- layouts --------------------------------------------------------------------------

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        hoursView = minutesView = timeView = null;
        captionDate = amPmView = dateView = zoneView = null;
        dayProgress = yearProgress = null;
        sunRow = null;
        sunriseView = sunsetView = null;
        lastWeather = null;
        seconds = span == BuiltinWidgetSpan.FOUR_BY_TWO;
        shownMinute = Long.MIN_VALUE;

        switch (span) {
            case ONE_BY_ONE: buildOneByOne(frame, ui); break;
            case TWO_BY_ONE: buildTwoByOne(frame, ui); break;
            case TWO_BY_TWO: buildTwoByTwo(frame, ui); break;
            case FOUR_BY_ONE: buildFourByOne(frame, ui); break;
            case FOUR_BY_TWO: buildFourByTwo(frame, ui); break;
        }
        if (isPreview() && sunRow != null) {
            WeatherController.Weather sample = new WeatherController.Weather();
            sample.valid = true;
            sample.sunrise = ClockWidgetSupport.SAMPLE_SUNRISE;
            sample.sunset = ClockWidgetSupport.SAMPLE_SUNSET;
            lastWeather = sample;
        }
        refresh();
    }

    private void buildOneByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        hoursView = numeral(30f, 0f, Gravity.CENTER_HORIZONTAL, style().onSurface);
        minutesView = numeral(30f, 0f, Gravity.CENTER_HORIZONTAL, style().primary);
        LinearLayout column = ui.column(2, hoursView, minutesView);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        frame.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    private void buildTwoByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        timeView = wide(numeral(38f, -0.03f, Gravity.START, style().onSurface));
        dateView = ui.text("", 12f, style().sansMedium, style().onSurfaceVariant);
        LinearLayout column = ui.column(6, timeView, dateView);
        column.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inset(column, 14, 0, 14, 0, ui);
        frame.addView(column, fill());
    }

    private void buildTwoByTwo(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        View caption;
        if (style().isPane()) {
            caption = ui.caption("", ClockWidgetSupport.GLYPH_CLOCK,
                getContext().getString(R.string.bw_clocks_pane_caption));
        } else {
            captionDate = ui.caption("");
            caption = captionDate;
        }
        amPmView = ui.mono("", 10.5f);
        LinearLayout captionRow = wide(ui.row(8, BuiltinWidgetUi.flex(caption), amPmView));

        // The design's margin-top:auto on the time, after a 10dp gap: the time sits low.
        View spacer = BuiltinWidgetUi.flexTall(new View(getContext()));
        ((LinearLayout.LayoutParams) spacer.getLayoutParams()).topMargin = ui.dp(10);
        timeView = wide(numeral(46f, -0.04f, Gravity.START, style().onSurface));
        LinearLayout bars = wide(ui.column(7,
            progress(ui, R.string.bw_clocks_day, style().primary, true),
            progress(ui, R.string.bw_clocks_year, style().warm, false)));

        LinearLayout column = ui.column(0, captionRow, spacer, timeView, bars);
        ((LinearLayout.LayoutParams) bars.getLayoutParams()).topMargin = ui.dp(10);
        inset(column, 14, 14, 14, 14, ui);
        frame.addView(column, fill());
    }

    private void buildFourByOne(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        timeView = numeral(44f, -0.04f, Gravity.START, style().onSurface);
        dateView = ui.sans("", 13f, true);
        sunriseView = ui.mono("", 11f);
        sunsetView = ui.mono("", 11f);
        LinearLayout sun = ui.row(12,
            ui.row(4, ui.glyph(ClockWidgetSupport.GLYPH_SUN, 11f, style().warm), sunriseView),
            ui.row(4, ui.glyph(ClockWidgetSupport.GLYPH_MOON, 11f, style().primary), sunsetView));
        sun.setVisibility(View.GONE);
        sunRow = sun;
        LinearLayout column = ui.column(6, dateView, sun);
        LinearLayout root = ui.row(16, timeView, BuiltinWidgetUi.flex(column));
        inset(root, 16, 0, 16, 0, ui);
        frame.addView(root, fill());
    }

    private void buildFourByTwo(@NonNull FrameLayout frame, @NonNull BuiltinWidgetUi ui) {
        dateView = ui.text("", 13f, style().sansBold, style().onSurfaceVariant);
        zoneView = ui.mono("", 11f);
        LinearLayout header = wide(ui.row(8, BuiltinWidgetUi.flex(dateView), zoneView));

        timeView = BuiltinWidgetUi.flexTall(numeral(64f, -0.04f, Gravity.CENTER_HORIZONTAL,
            style().onSurface));
        timeView.setTailStyle(22f, style().onSurfaceVariant);

        LinearLayout bars = wide(ui.row(14,
            BuiltinWidgetUi.flex(progress(ui, R.string.bw_clocks_day, style().primary, true)),
            BuiltinWidgetUi.flex(progress(ui, R.string.bw_clocks_year, style().warm, false))));
        bars.setGravity(Gravity.TOP);

        LinearLayout column = ui.column(12, header, timeView, bars);
        inset(column, 16, 16, 16, 16, ui);
        frame.addView(column, fill());
    }

    /** "Day ··· 57%" over a 6dp bar; remembered as the day's or the year's line. */
    @NonNull private LinearLayout progress(@NonNull BuiltinWidgetUi ui, @StringRes int label,
                                           @ColorInt int color, boolean day) {
        TextView name = ui.mono(getContext().getString(label), 10.5f);
        TextView percent = ui.mono("", 10.5f);
        LinearLayout head = wide(ui.row(8, BuiltinWidgetUi.flex(name), percent));
        BuiltinWidgetUi.BarView bar = ui.bar(0f, color, 6);
        Progress progress = new Progress(percent, bar, color);
        if (day) dayProgress = progress; else yearProgress = progress;
        return wide(ui.column(4, head, bar));
    }

    @NonNull private ClockNumeralView numeral(float sp, float letterSpacing, int gravity,
                                              @ColorInt int color) {
        ClockNumeralView view = new ClockNumeralView(getContext(), style().numerals, sp, color,
            letterSpacing);
        view.setTailStyle(Math.max(10f, sp * 0.4f), style().onSurfaceVariant);
        view.setHorizontalGravity(gravity);
        return view;
    }

    @NonNull private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ----- time -----------------------------------------------------------------------------

    /** Everything the clock shows, for the current minute. */
    private void refresh() {
        Context context = getContext();
        ZonedDateTime now = ClockWidgetSupport.now(isPreview());
        shownMinute = now.toEpochSecond() / 60L;
        is24 = android.text.format.DateFormat.is24HourFormat(context);
        LocalTime time = now.toLocalTime();
        String hoursMinutes = ClockWidgetFormats.hoursMinutes(time, is24);
        String amPm = ClockWidgetFormats.amPm(time, is24);
        String marker = amPm.isEmpty() ? "" : " " + amPm;

        if (hoursView != null) hoursView.setText(ClockWidgetFormats.hours(time, is24), "");
        if (minutesView != null) minutesView.setText(ClockWidgetFormats.minutes(time), "");
        if (timeView != null) {
            // 2×2 has no room beside the time; its marker goes in the caption row instead.
            String tail = seconds ? ":" + ClockWidgetFormats.seconds(time) + marker
                : amPmView != null ? "" : marker;
            timeView.setText(hoursMinutes, tail);
        }
        if (amPmView != null) {
            amPmView.setText(amPm);
            amPmView.setVisibility(amPm.isEmpty() ? View.GONE : View.VISIBLE);
        }
        String longDate = ClockWidgetSupport.date(seconds ? "EEEEdMMMMy" : "EEEEdMMMM", now);
        if (captionDate != null) captionDate.setText(ClockWidgetSupport.date("EEEdMMM", now));
        if (dateView != null) dateView.setText(longDate);
        if (zoneView != null) {
            zoneView.setText(ClockWidgetFormats.zoneLabel(now.getOffset().getTotalSeconds()));
        }

        int dayPercent = ClockWidgetFormats.percent(ClockWidgetFormats.dayFraction(now));
        int yearPercent = ClockWidgetFormats.percent(ClockWidgetFormats.yearFraction(now));
        setProgress(dayProgress, ClockWidgetFormats.dayFraction(now), dayPercent);
        setProgress(yearProgress, ClockWidgetFormats.yearFraction(now), yearPercent);

        StringBuilder description = new StringBuilder(context.getString(
            R.string.bw_clocks_description, (hoursMinutes + marker).trim(), longDate));
        if (dayProgress != null) {
            description.append(". ").append(context.getString(
                R.string.bw_clocks_description_progress, dayPercent, yearPercent));
        }
        String sun = applySun(is24);
        if (sun != null) description.append(". ").append(sun);
        setContentDescription(description);
    }

    private void setProgress(@Nullable Progress progress, float fraction, int percent) {
        if (progress == null) return;
        progress.percent.setText(getContext().getString(R.string.bw_clocks_percent, percent));
        progress.bar.set(fraction, progress.color);
    }

    /** Shows or hides the sunrise/sunset row; returns its spoken form when it shows. */
    @Nullable private String applySun(boolean is24) {
        if (sunRow == null || sunriseView == null || sunsetView == null) return null;
        WeatherController.Weather value = lastWeather;
        String rise = value != null && value.valid ? ClockWidgetFormats.clockText(value.sunrise, is24) : "";
        String set = value != null && value.valid ? ClockWidgetFormats.clockText(value.sunset, is24) : "";
        boolean show = !rise.isEmpty() && !set.isEmpty();
        if (show) {
            sunriseView.setText(rise);
            sunsetView.setText(set);
        }
        int visibility = show ? View.VISIBLE : View.GONE;
        if (sunRow.getVisibility() != visibility) sunRow.setVisibility(visibility);
        return show ? getContext().getString(R.string.bw_clocks_description_sun, rise, set) : null;
    }

    private void onMinute() {
        refresh();
        // Sunrise moves day to day: keep the shared forecast fresh on the hour.
        if (weather != null && ClockWidgetSupport.now(false).getMinute() == 0) weather.refreshIfStale();
    }

    private void onSecond() {
        ZonedDateTime now = ClockWidgetSupport.now(false);
        if (now.toEpochSecond() / 60L != shownMinute) {
            refresh();
            return;
        }
        if (timeView == null) return;
        LocalTime time = now.toLocalTime();
        String amPm = ClockWidgetFormats.amPm(time, is24);
        timeView.setText(ClockWidgetFormats.hoursMinutes(time, is24),
            ":" + ClockWidgetFormats.seconds(time) + (amPm.isEmpty() ? "" : " " + amPm));
    }

    private void onWeather(@NonNull WeatherController.Weather value) {
        if (!isStarted()) return;
        WeatherController held = weather;
        lastWeather = value.valid ? value : held != null ? held.cache() : null;
        applySun(is24);
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        services.addTickListener(minuteTick);
        if (sunRow != null) {
            weather = services.acquireWeather(weatherListener);
            WeatherController.Weather cached = weather.cache();
            if (cached.valid) lastWeather = cached;
        }
        shown = isShown() && getWindowVisibility() == View.VISIBLE;
        refresh();
        updateTicker();
    }

    @Override protected void onStop() {
        services.removeTickListener(minuteTick);
        if (weather != null) {
            services.releaseWeather(weatherListener);
            weather = null;
        }
        ticker.setRunning(false);
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        shown = isVisible;
        updateTicker();
    }

    /** Only the 4×2 shows seconds, and only while someone can see them. */
    private void updateTicker() {
        boolean run = seconds && isStarted() && shown && !isPreview();
        if (run && !ticker.isRunning()) onSecond();
        ticker.setRunning(run);
    }

    @Override protected void onTap() {
        if (isPreview()) return;
        ClockWidgetSupport.openClockApp(getContext());
    }
}
