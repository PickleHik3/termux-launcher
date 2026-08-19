package com.termux.app.statusbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Weather detail card with a top-right Day / Week toggle. Day shows an hourly forecast for the
 * upcoming hours; Week shows daily highs and lows. Rebuilt from a {@link WeatherController.Weather}
 * snapshot; keeps the last-selected mode across rebinds.
 */
public final class WeatherCardView extends LinearLayout {

    private static final String ATTRIBUTION_URL = "https://open-meteo.com/";

    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mPrimary;
    private final int mSecondary;
    private final int mTertiary;
    private final int mPanel;

    private final LottieAnimationView mCurrentIcon;
    private final TextView mCurrent;
    private final TextView mDayToggle;
    private final TextView mWeekToggle;
    private final LinearLayout mList;
    private final TextView mAttribution;

    private boolean mWeekMode;
    private boolean mFahrenheit;
    @NonNull private WeatherController.Weather mWeather = new WeatherController.Weather();

    public WeatherCardView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mSecondary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mTertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, mPrimary);
        mPanel = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mCurrentIcon = weatherAnimation(context, true);
        LayoutParams currentIconParams = new LayoutParams(dp(20), dp(20));
        currentIconParams.setMarginEnd(dp(7));
        header.addView(mCurrentIcon, currentIconParams);

        mCurrent = new TextView(context);
        mCurrent.setTextColor(mOnSurface);
        mCurrent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mCurrent.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(mCurrent, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout toggle = new LinearLayout(context);
        toggle.setOrientation(HORIZONTAL);
        toggle.setBackground(pill(ColorUtils.setAlphaComponent(mPanel, 48), 0));
        header.addView(toggle);
        mDayToggle = toggleChip(context, "Day");
        mWeekToggle = toggleChip(context, "Week");
        toggle.addView(mDayToggle);
        toggle.addView(mWeekToggle);
        mDayToggle.setOnClickListener(v -> setMode(false));
        mWeekToggle.setOnClickListener(v -> setMode(true));

        mList = new LinearLayout(context);
        mList.setOrientation(HORIZONTAL);
        mList.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView forecastStrip = new HorizontalScrollView(context);
        forecastStrip.setHorizontalScrollBarEnabled(false);
        forecastStrip.setOverScrollMode(OVER_SCROLL_NEVER);
        forecastStrip.setFillViewport(false);
        forecastStrip.addView(mList, new HorizontalScrollView.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams listParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        listParams.topMargin = dp(8);
        addView(forecastStrip, listParams);

        // Open-Meteo's forecasts are CC BY 4.0, which asks for this credit beside the data itself.
        mAttribution = new TextView(context);
        mAttribution.setText(R.string.weather_attribution);
        mAttribution.setTextColor(mOnSurfaceVariant);
        mAttribution.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        mAttribution.setOnClickListener(v -> openAttributionLink());
        LayoutParams attributionParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        attributionParams.topMargin = dp(6);
        addView(mAttribution, attributionParams);
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
            mCurrentIcon.setVisibility(INVISIBLE);
            mCurrent.setText("no-location".equals(weather.error)
                ? "Weather · location unavailable" : "Weather · unavailable");
            mList.removeAllViews();
            // Nothing of theirs is on screen to credit.
            mAttribution.setVisibility(GONE);
            return;
        }
        mAttribution.setVisibility(VISIBLE);
        mCurrentIcon.setVisibility(VISIBLE);
        playAnimation(mCurrentIcon,
            WeatherController.animationAssetFor(weather.currentCode, weather.currentIsDay));
        mCurrent.setText(String.format(Locale.ROOT, "%s  %s",
            fmtTemp(weather.currentC), WeatherController.describe(weather.currentCode)));
        rebuildList();
        applyToggleState();
    }

    private void setMode(boolean week) {
        if (mWeekMode == week) return;
        mWeekMode = week;
        applyToggleState();
        rebuildList();
    }

    private void applyToggleState() {
        mDayToggle.setTextColor(mWeekMode ? mOnSurfaceVariant : mOnSurface);
        mWeekToggle.setTextColor(mWeekMode ? mOnSurface : mOnSurfaceVariant);
        mDayToggle.setBackground(mWeekMode ? null : pill(ColorUtils.setAlphaComponent(mPrimary, 46), dp(1)));
        mWeekToggle.setBackground(mWeekMode ? pill(ColorUtils.setAlphaComponent(mPrimary, 46), dp(1)) : null);
    }

    private void rebuildList() {
        mList.removeAllViews();
        if (!mWeather.valid) return;
        if (mWeekMode) {
            for (WeatherController.Daily d : mWeather.daily) {
                // A daily summary has no hour, so it is drawn as its daytime cut.
                mList.addView(row(weekdayLabel(d.date),
                    WeatherController.animationAssetFor(d.code, true),
                    String.format(Locale.ROOT, "%s / %s", fmtTemp(d.maxC), fmtTemp(d.minC))));
            }
        } else {
            String cutoff = hourCutoff();
            int shown = 0;
            for (WeatherController.Hourly h : mWeather.hourly) {
                if (h.iso.compareTo(cutoff) < 0) continue;   // ISO strings sort chronologically
                mList.addView(row(hourLabel(h.iso),
                    WeatherController.animationAssetFor(h.code, h.isDay), fmtTemp(h.tempC)));
                if (++shown >= 12) break;
            }
            if (shown == 0) {
                // All hourly entries are in the past (stale cache) — fall back to showing the first few.
                for (WeatherController.Hourly h : mWeather.hourly) {
                    mList.addView(row(hourLabel(h.iso),
                        WeatherController.animationAssetFor(h.code, h.isDay), fmtTemp(h.tempC)));
                    if (++shown >= 12) break;
                }
            }
        }
    }

    private View row(@NonNull String label, @NonNull String animationAsset,
                     @NonNull String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(VERTICAL);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));

        TextView time = new TextView(getContext());
        time.setTextColor(mSecondary);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        time.setGravity(Gravity.CENTER);
        time.setText(label);
        row.addView(time, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LottieAnimationView icon = weatherAnimation(getContext(), false);
        playAnimation(icon, animationAsset);
        LayoutParams iconParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(26));
        iconParams.topMargin = dp(4);
        iconParams.bottomMargin = dp(3);
        row.addView(icon, iconParams);

        TextView temp = new TextView(getContext());
        temp.setTextColor(mOnSurface);
        temp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        temp.setGravity(Gravity.CENTER);
        temp.setText(value);
        row.addView(temp, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        row.setBackground(pill(ColorUtils.setAlphaComponent(mPanel, 54), 0));
        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
            dp(mWeekMode ? 68 : 58), LayoutParams.WRAP_CONTENT);
        cellParams.setMarginEnd(dp(5));
        row.setLayoutParams(cellParams);
        return row;
    }

    /**
     * Bundled Meteocons animation cell, the same set the status-bar widget plays.
     *
     * @param loop true for the single headline icon, which animates for as long as the card is
     *             open. The hourly and weekly cells pass false: a dozen looping animations behind
     *             a scrolling strip is a redraw of the whole card every frame, and they are read
     *             as a row of conditions rather than watched one at a time.
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

    private TextView toggleChip(@NonNull Context context, @NonNull String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(3), dp(10), dp(3));
        tv.setTextColor(mOnSurfaceVariant);
        return tv;
    }

    private GradientDrawable pill(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(color);
        if (stroke > 0) d.setStroke(stroke, ColorUtils.setAlphaComponent(mPrimary, 70));
        return d;
    }

    private String fmtTemp(double c) {
        return WeatherController.formatTemp(c, mFahrenheit);
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
}
