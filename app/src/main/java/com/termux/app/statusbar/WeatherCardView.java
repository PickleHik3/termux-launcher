package com.termux.app.statusbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Weather detail card: place and a Day / Week toggle across the top, the current reading as a
 * headline beside its animated condition icon, then either the next few hours plotted against a
 * shared scale or the week as low-to-high range bars, and a daylight track in the footer.
 *
 * <p>Rebuilt from the Weather card redesign handoff. What it replaced was a horizontally scrolling
 * strip of identical tiles — five boxes each holding a number, which reads as a list to be
 * compared item by item rather than as a shape. The headline now carries what someone opening a
 * weather card actually wants first (what it is out there, what it feels like, today's range), and
 * the forecast underneath is plotted so its trend is visible before any individual figure is read.
 *
 * <p>The card draws no panel of its own: {@link StatusCardHost} supplies the glass, its corner
 * radius and its inset from the live status-bar styling, so the card matches whatever surface the
 * user has configured. The handoff's corner glow is drawn here, since it belongs to this card
 * rather than to every card the host shows.
 */
public final class WeatherCardView extends LinearLayout {

    private static final String ATTRIBUTION_URL = "https://open-meteo.com/";
    /** Hours plotted in Day view; the graph caps at this too. */
    private static final int HOURLY_POINTS = 5;
    /** Rows in Week view. Seven days of forecast, the first labelled as today. */
    private static final int WEEKLY_ROWS = 7;
    /** Corner bloom, capped at half the card so it always dies out inside its own bounds. */
    private static final float GLOW_RADIUS_DP = 96f;
    private static final int GLOW_ALPHA = 64;

    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mPrimary;
    private final int mTertiary;
    private final int mPanel;

    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TextView mLocation;
    private final TextView mDayToggle;
    private final TextView mWeekToggle;
    private final TextView mHeadlineTemp;
    private final TextView mHeadlineUnit;
    private final TextView mConditionLine;
    private final TextView mRangeLine;
    private final LottieAnimationView mCurrentIcon;
    private final WeatherHourlyGraphView mGraph;
    private final LinearLayout mWeekList;
    private final LinearLayout mFooter;
    private final TextView mSunsetLabel;
    private final TextView mSunriseLabel;
    private final WeatherDaylightTrackView mDaylight;
    private final TextView mAttribution;
    private final TextView mUnavailable;
    private final View mHeadline;

    private boolean mWeekMode;
    private boolean mFahrenheit;
    @NonNull private WeatherController.Weather mWeather = new WeatherController.Weather();

    public WeatherCardView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setWillNotDraw(false);

        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mTertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, mPrimary);
        mPanel = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));

        // ---- header: place, and the Day / Week toggle
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mLocation = new TextView(context);
        mLocation.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 217));
        mLocation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mLocation.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLocation.setSingleLine(true);
        mLocation.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(mLocation, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout toggle = new LinearLayout(context);
        toggle.setOrientation(HORIZONTAL);
        toggle.setPadding(dp(2), dp(2), dp(2), dp(2));
        toggle.setBackground(pill(ColorUtils.setAlphaComponent(mPanel, 48), 0, 999));
        header.addView(toggle);
        mDayToggle = toggleChip(context, "DAY");
        mWeekToggle = toggleChip(context, "WEEK");
        toggle.addView(mDayToggle);
        toggle.addView(mWeekToggle);
        mDayToggle.setOnClickListener(v -> setMode(false));
        mWeekToggle.setOnClickListener(v -> setMode(true));

        // ---- headline: the reading, and the condition icon beside it
        LinearLayout headline = new LinearLayout(context);
        headline.setOrientation(HORIZONTAL);
        headline.setGravity(Gravity.TOP);
        LayoutParams headlineParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = dp(14);
        addView(headline, headlineParams);
        mHeadline = headline;

        LinearLayout readout = new LinearLayout(context);
        readout.setOrientation(VERTICAL);
        headline.addView(readout, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout tempRow = new LinearLayout(context);
        tempRow.setOrientation(HORIZONTAL);
        tempRow.setGravity(Gravity.TOP);
        readout.addView(tempRow, new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mHeadlineTemp = new TextView(context);
        mHeadlineTemp.setTextColor(mOnSurface);
        mHeadlineTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        mHeadlineTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mHeadlineTemp.setIncludeFontPadding(false);
        mHeadlineTemp.setLetterSpacing(-0.05f);
        tempRow.addView(mHeadlineTemp, new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mHeadlineUnit = new TextView(context);
        mHeadlineUnit.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 128));
        mHeadlineUnit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mHeadlineUnit.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mHeadlineUnit.setIncludeFontPadding(false);
        LayoutParams unitParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        unitParams.topMargin = dp(6);
        unitParams.setMarginStart(dp(2));
        tempRow.addView(mHeadlineUnit, unitParams);

        mConditionLine = new TextView(context);
        mConditionLine.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 179));
        mConditionLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LayoutParams conditionParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        conditionParams.topMargin = dp(8);
        readout.addView(mConditionLine, conditionParams);

        mRangeLine = new TextView(context);
        mRangeLine.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 133));
        mRangeLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        mRangeLine.setTypeface(Typeface.MONOSPACE);
        mRangeLine.setLetterSpacing(0.1f);
        LayoutParams rangeParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = dp(3);
        readout.addView(mRangeLine, rangeParams);

        mCurrentIcon = weatherAnimation(context, true);
        LayoutParams iconParams = new LayoutParams(dp(66), dp(66));
        iconParams.topMargin = dp(4);
        iconParams.setMarginStart(dp(12));
        headline.addView(mCurrentIcon, iconParams);

        // ---- forecast: one of these two is visible at a time
        mGraph = new WeatherHourlyGraphView(context);
        mGraph.setColors(mTertiary, mOnSurface);
        LayoutParams graphParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        graphParams.topMargin = dp(10);
        addView(mGraph, graphParams);

        mWeekList = new LinearLayout(context);
        mWeekList.setOrientation(VERTICAL);
        LayoutParams weekParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        weekParams.topMargin = dp(14);
        addView(mWeekList, weekParams);

        // ---- footer: sunset, the daylight track, sunrise
        mFooter = new LinearLayout(context);
        mFooter.setOrientation(HORIZONTAL);
        mFooter.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams footerParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(14);
        addView(mFooter, footerParams);

        mSunriseLabel = footerClock(context);
        mFooter.addView(mSunriseLabel);
        mDaylight = new WeatherDaylightTrackView(context);
        mDaylight.setColors(ColorUtils.setAlphaComponent(mTertiary, 140),
            ColorUtils.setAlphaComponent(mPrimary, 77), mTertiary);
        LayoutParams daylightParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        daylightParams.setMarginStart(dp(10));
        daylightParams.setMarginEnd(dp(10));
        mFooter.addView(mDaylight, daylightParams);
        mSunsetLabel = footerClock(context);
        mFooter.addView(mSunsetLabel);

        // Open-Meteo's forecasts are CC BY 4.0, which asks for this credit beside the data itself.
        mAttribution = new TextView(context);
        mAttribution.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 128));
        mAttribution.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        mAttribution.setTypeface(Typeface.MONOSPACE);
        mAttribution.setLetterSpacing(0.08f);
        mAttribution.setOnClickListener(v -> openAttributionLink());
        LayoutParams attributionParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        attributionParams.topMargin = dp(12);
        addView(mAttribution, attributionParams);

        mUnavailable = new TextView(context);
        mUnavailable.setTextColor(mOnSurfaceVariant);
        mUnavailable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mUnavailable.setVisibility(GONE);
        addView(mUnavailable, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * The handoff's corner glow: a soft bloom behind the condition icon, in the card's accent.
     * Drawn rather than added as a view so it cannot take a touch or push the headline around.
     *
     * <p>Sized and placed so it fades to nothing before it reaches any edge of this view. The
     * first cut sat the centre near the top-right corner with a radius three times the room it
     * had, so the parts of the circle past the card's own bounds were simply not painted and the
     * bloom ended in two straight lines — a lit rectangle over the corner instead of a glow. The
     * card cannot paint into the host's panel inset, so the glow has to live entirely inside it.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (mWeather.valid) {
            float radius = Math.min(dp(GLOW_RADIUS_DP),
                Math.min(getWidth(), getHeight()) * 0.5f);
            if (radius > 0f) {
                float cx = getWidth() - radius;
                float cy = radius;
                mGlowPaint.setShader(new RadialGradient(cx, cy, radius,
                    new int[] {ColorUtils.setAlphaComponent(mTertiary, GLOW_ALPHA),
                        ColorUtils.setAlphaComponent(mTertiary, 0)},
                    new float[] {0f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, radius, mGlowPaint);
            }
        }
        super.onDraw(canvas);
    }

    private void openAttributionLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ATTRIBUTION_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception ignored) {
            // No browser to take it; the credit still reads as text.
        }
    }

    public void bind(@NonNull WeatherController.Weather weather) {
        mWeather = weather;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(getContext(), false);
        mFahrenheit = preferences != null && preferences.isStatusWidgetWeatherFahrenheit();

        if (!weather.valid) {
            showUnavailable(weather);
            return;
        }
        setVisibleSections(true);

        mLocation.setText(weather.locationName.isEmpty()
            ? getContext().getString(R.string.weather_card_location_unknown)
            : weather.locationName);

        mHeadlineTemp.setText(String.valueOf(roundedTemp(weather.currentC)));
        mHeadlineUnit.setText(mFahrenheit ? "°F" : "°C");
        mConditionLine.setText(conditionLine(weather));
        mRangeLine.setText(rangeLine(weather));

        mCurrentIcon.setVisibility(VISIBLE);
        playAnimation(mCurrentIcon,
            WeatherController.animationAssetFor(weather.currentCode, weather.currentIsDay));

        bindFooter(weather);
        mAttribution.setText(attributionLine(weather));
        rebuildForecast();
        applyToggleState();
        invalidate();
    }

    private void showUnavailable(@NonNull WeatherController.Weather weather) {
        setVisibleSections(false);
        mUnavailable.setVisibility(VISIBLE);
        mUnavailable.setText("no-location".equals(weather.error)
            ? getContext().getString(R.string.weather_card_unavailable_location)
            : getContext().getString(R.string.weather_card_unavailable));
        // Nothing of theirs is on screen to credit.
        mAttribution.setVisibility(GONE);
        invalidate();
    }

    private void setVisibleSections(boolean valid) {
        mUnavailable.setVisibility(valid ? GONE : VISIBLE);
        mHeadline.setVisibility(valid ? VISIBLE : GONE);
        mFooter.setVisibility(valid ? VISIBLE : GONE);
        mAttribution.setVisibility(valid ? VISIBLE : GONE);
        mLocation.setVisibility(valid ? VISIBLE : GONE);
        if (!valid) {
            mGraph.setVisibility(GONE);
            mWeekList.setVisibility(GONE);
        }
    }

    /** "Clear night · feels 35°", dropping the feels-like clause when the provider omitted it. */
    @NonNull
    private String conditionLine(@NonNull WeatherController.Weather weather) {
        String condition = WeatherController.describe(weather.currentCode);
        if (!weather.currentIsDay) condition = condition + " night";
        if (Double.isNaN(weather.feelsLikeC)) return condition;
        // A feels-like within a degree of the reading is noise, and saying it twice invites the
        // reader to look for a difference that is not there.
        if (Math.abs(weather.feelsLikeC - weather.currentC) < 1) return condition;
        return getContext().getString(R.string.weather_card_condition_feels,
            condition, fmtTemp(weather.feelsLikeC));
    }

    /** "H 47° · L 36°" for today, empty when there is no daily entry to read them from. */
    @NonNull
    private String rangeLine(@NonNull WeatherController.Weather weather) {
        if (weather.daily.isEmpty()) return "";
        WeatherController.Daily today = weather.daily.get(0);
        return getContext().getString(R.string.weather_card_range,
            fmtTemp(today.maxC), fmtTemp(today.minC));
    }

    @NonNull
    private String attributionLine(@NonNull WeatherController.Weather weather) {
        long ageMinutes = Math.max(0,
            (System.currentTimeMillis() - weather.fetchedAtMs) / 60_000L);
        return getContext().getString(R.string.weather_card_attribution, ageMinutes);
    }

    private void bindFooter(@NonNull WeatherController.Weather weather) {
        boolean known = !weather.sunrise.isEmpty() && !weather.sunset.isEmpty();
        mFooter.setVisibility(known ? VISIBLE : GONE);
        if (!known) return;
        mSunriseLabel.setText(weather.sunrise);
        mSunsetLabel.setText(weather.sunset);
        mDaylight.setProgress(daylightProgress(weather.sunrise, weather.sunset));
    }

    /** Where the clock sits between sunrise and sunset, or -1 when it is outside that span. */
    private static float daylightProgress(@NonNull String sunrise, @NonNull String sunset) {
        int start = minutesOf(sunrise);
        int end = minutesOf(sunset);
        if (start < 0 || end < 0 || end <= start) return -1f;
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (nowMinutes < start || nowMinutes > end) return -1f;
        return (nowMinutes - start) / (float) (end - start);
    }

    private static int minutesOf(@NonNull String clock) {
        int colon = clock.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(clock.substring(0, colon)) * 60
                + Integer.parseInt(clock.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void setMode(boolean week) {
        if (mWeekMode == week) return;
        mWeekMode = week;
        applyToggleState();
        rebuildForecast();
    }

    private void applyToggleState() {
        mDayToggle.setTextColor(mWeekMode ? mOnSurfaceVariant : mOnSurface);
        mWeekToggle.setTextColor(mWeekMode ? mOnSurface : mOnSurfaceVariant);
        mDayToggle.setBackground(mWeekMode ? null
            : pill(ColorUtils.setAlphaComponent(mPrimary, 51), dp(1), 999));
        mWeekToggle.setBackground(mWeekMode
            ? pill(ColorUtils.setAlphaComponent(mPrimary, 51), dp(1), 999) : null);
    }

    private void rebuildForecast() {
        if (!mWeather.valid) return;
        if (mWeekMode) {
            mGraph.setVisibility(GONE);
            mWeekList.setVisibility(VISIBLE);
            rebuildWeek();
        } else {
            mWeekList.setVisibility(GONE);
            mGraph.setPoints(hourlyPoints());
            mGraph.setVisibility(mGraph.hasPoints() ? VISIBLE : GONE);
        }
    }

    @NonNull
    private List<WeatherHourlyGraphView.Point> hourlyPoints() {
        List<WeatherHourlyGraphView.Point> points = new ArrayList<>();
        String cutoff = hourCutoff();
        for (WeatherController.Hourly h : mWeather.hourly) {
            if (h.iso.compareTo(cutoff) < 0) continue;   // ISO strings sort chronologically
            points.add(point(points.isEmpty(), h));
            if (points.size() >= HOURLY_POINTS) break;
        }
        if (points.isEmpty()) {
            // Every hourly entry is in the past (a stale cache); show the head of it rather than
            // an empty plot, since the shape is still worth something.
            for (WeatherController.Hourly h : mWeather.hourly) {
                points.add(point(points.isEmpty(), h));
                if (points.size() >= HOURLY_POINTS) break;
            }
        }
        return points;
    }

    @NonNull
    private WeatherHourlyGraphView.Point point(boolean first, @NonNull WeatherController.Hourly h) {
        return new WeatherHourlyGraphView.Point(
            first ? getContext().getString(R.string.weather_card_now) : hourLabel(h.iso),
            fmtTemp(h.tempC), h.tempC);
    }

    private void rebuildWeek() {
        mWeekList.removeAllViews();
        if (mWeather.daily.isEmpty()) return;
        // One scale for the whole week, so a bar's length and offset mean the same thing on every
        // row; per-row scaling would make a mild day look identical to a scorching one.
        double weekMin = Double.MAX_VALUE;
        double weekMax = -Double.MAX_VALUE;
        int rows = Math.min(WEEKLY_ROWS, mWeather.daily.size());
        for (int i = 0; i < rows; i++) {
            WeatherController.Daily d = mWeather.daily.get(i);
            weekMin = Math.min(weekMin, d.minC);
            weekMax = Math.max(weekMax, d.maxC);
        }
        double span = weekMax - weekMin;
        if (span < 0.5) span = 1;
        for (int i = 0; i < rows; i++) {
            mWeekList.addView(weekRow(mWeather.daily.get(i), i == 0, weekMin, span));
        }
    }

    @NonNull
    private View weekRow(@NonNull WeatherController.Daily day, boolean today,
                         double weekMin, double span) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(getContext());
        label.setText(today ? getContext().getString(R.string.weather_card_today)
            : weekdayLabel(day.date).toUpperCase(Locale.getDefault()));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        label.setTypeface(Typeface.MONOSPACE);
        label.setLetterSpacing(0.1f);
        label.setTextColor(today ? mOnSurface : ColorUtils.setAlphaComponent(mOnSurface, 153));
        row.addView(label, new LayoutParams(dp(42), LayoutParams.WRAP_CONTENT));

        TextView low = new TextView(getContext());
        low.setText(fmtTemp(day.minC));
        low.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        low.setGravity(Gravity.END);
        low.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 140));
        row.addView(low, new LayoutParams(dp(32), LayoutParams.WRAP_CONTENT));

        WeatherRangeBarView bar = new WeatherRangeBarView(getContext());
        bar.setColors(ColorUtils.setAlphaComponent(mOnSurface, 23), mPrimary, mTertiary);
        bar.setRange((float) ((day.minC - weekMin) / span), (float) ((day.maxC - weekMin) / span));
        LayoutParams barParams = new LayoutParams(0, dp(5), 1f);
        barParams.setMarginStart(dp(10));
        barParams.setMarginEnd(dp(10));
        row.addView(bar, barParams);

        TextView high = new TextView(getContext());
        high.setText(fmtTemp(day.maxC));
        high.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        high.setTextColor(today ? mOnSurface : ColorUtils.setAlphaComponent(mOnSurface, 230));
        row.addView(high, new LayoutParams(dp(32), LayoutParams.WRAP_CONTENT));

        LayoutParams rowParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(5);
        rowParams.bottomMargin = dp(5);
        row.setLayoutParams(rowParams);
        return row;
    }

    /**
     * The headline icon, which loops for as long as the card is open — unlike the status bar's,
     * which holds still, this one is on screen only while the user is looking straight at it.
     */
    private LottieAnimationView weatherAnimation(@NonNull Context context, boolean loop) {
        LottieAnimationView icon = new LottieAnimationView(context);
        icon.setRepeatCount(loop ? LottieDrawable.INFINITE : 0);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    /** Loads and starts an asset, tolerating a missing file rather than taking the card down. */
    private void playAnimation(@NonNull LottieAnimationView view, @NonNull String assetPath) {
        view.setFailureListener(error -> view.setVisibility(INVISIBLE));
        view.setAnimation(assetPath);
        view.playAnimation();
    }

    private TextView footerClock(@NonNull Context context) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLetterSpacing(0.1f);
        tv.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 140));
        return tv;
    }

    private TextView toggleChip(@NonNull Context context, @NonNull String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLetterSpacing(0.1f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(4), dp(12), dp(4));
        tv.setTextColor(mOnSurfaceVariant);
        return tv;
    }

    private GradientDrawable pill(int color, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        if (stroke > 0) d.setStroke(stroke, ColorUtils.setAlphaComponent(mPrimary, 77));
        return d;
    }

    private String fmtTemp(double c) {
        return WeatherController.formatTemp(c, mFahrenheit);
    }

    /** The headline draws its degree sign separately, so it needs the bare number. */
    private long roundedTemp(double celsius) {
        if (Double.isNaN(celsius)) return 0;
        return Math.round(mFahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    @SuppressLint("SimpleDateFormat")
    private static String hourLabel(@NonNull String iso) {
        int t = iso.indexOf('T');
        return t >= 0 && iso.length() >= t + 6 ? iso.substring(t + 1, t + 6) : iso;
    }

    private static String weekdayLabel(@NonNull String date) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(date);
            if (d != null) return new SimpleDateFormat("EEE", Locale.getDefault()).format(d);
        } catch (Exception ignored) { }
        return date;
    }

    private static String hourCutoff() {
        Calendar now = Calendar.getInstance();
        return new SimpleDateFormat("yyyy-MM-dd'T'HH':00'", Locale.ROOT).format(now.getTime());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
