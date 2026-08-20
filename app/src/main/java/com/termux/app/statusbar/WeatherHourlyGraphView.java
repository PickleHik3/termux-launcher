package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The next few hours as a scatter of dots, each sitting at the height of its temperature, with the
 * reading above it and the hour below.
 *
 * <p>Replaces the strip of equal-sized tiles the card used to show. A tile row states five numbers
 * and leaves the reader to compare them; plotted against a shared scale the shape of the evening —
 * cooling off, a warm hour, flat — is the first thing visible, and the numbers are still there for
 * anyone who wants the exact figure. The leading point is the current hour and is drawn with a halo
 * so "now" is never in question.
 *
 * <p>No connecting line: hourly forecasts are samples, not a continuous measurement, and drawing a
 * curve through them claims a precision the data does not have.
 */
public final class WeatherHourlyGraphView extends android.view.View {

    /** One plotted hour. */
    public static final class Point {
        @NonNull final String hourLabel;
        @NonNull final String tempLabel;
        final double tempC;
        /** Precipitation probability in percent, -1 when unknown. */
        final int precipProb;

        public Point(@NonNull String hourLabel, @NonNull String tempLabel, double tempC,
                     int precipProb) {
            this.hourLabel = hourLabel;
            this.tempLabel = tempLabel;
            this.tempC = tempC;
            this.precipProb = precipProb;
        }
    }

    private static final int MAX_POINTS = 5;
    private static final float DOT_RADIUS_DP = 3.5f;
    private static final float NOW_DOT_RADIUS_DP = 4.5f;
    private static final float HALO_RADIUS_DP = 8f;
    /** Vertical room the dots may travel through, inside the label bands above and below. */
    private static final float PLOT_HEIGHT_DP = 34f;
    private static final float HEIGHT_DP = 96f;
    /** Extra band under the hours for rain chances, taken only when there is one to print. */
    private static final float PRECIP_BAND_DP = 13f;
    /** A chance below this reads as dry; printing "0%" five times says nothing. */
    private static final int PRECIP_MIN_PERCENT = 10;

    private final List<Point> mPoints = new ArrayList<>();
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTempPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mHourPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPrecipPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int mAccent;
    private int mOnSurface;
    private boolean mShowPrecip;

    public WeatherHourlyGraphView(@NonNull Context context) {
        super(context);
        mTempPaint.setTextAlign(Paint.Align.CENTER);
        mTempPaint.setTextSize(sp(12.5f));
        mHourPaint.setTextAlign(Paint.Align.CENTER);
        mHourPaint.setTextSize(sp(10.5f));
        mHourPaint.setTypeface(Typeface.MONOSPACE);
        mPrecipPaint.setTextAlign(Paint.Align.CENTER);
        mPrecipPaint.setTextSize(sp(9f));
        mPrecipPaint.setTypeface(Typeface.MONOSPACE);
    }

    /** @param accent colour of the current hour's dot and label; the rest fade back from it. */
    public void setColors(int accent, int onSurface) {
        mAccent = accent;
        mOnSurface = onSurface;
        invalidate();
    }

    public void setPoints(@Nullable List<Point> points) {
        mPoints.clear();
        boolean showPrecip = false;
        if (points != null) {
            for (Point p : points) {
                if (Double.isNaN(p.tempC)) continue;
                mPoints.add(p);
                showPrecip |= p.precipProb >= PRECIP_MIN_PERCENT;
                if (mPoints.size() >= MAX_POINTS) break;
            }
        }
        if (showPrecip != mShowPrecip) {
            mShowPrecip = showPrecip;
            requestLayout();
        }
        invalidate();
    }

    public boolean hasPoints() {
        return !mPoints.isEmpty();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec),
            Math.round(dp(HEIGHT_DP) + (mShowPrecip ? dp(PRECIP_BAND_DP) : 0f)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int count = mPoints.size();
        if (count == 0) return;
        float width = getWidth();
        if (width <= 0) return;

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Point p : mPoints) {
            min = Math.min(min, p.tempC);
            max = Math.max(max, p.tempC);
        }
        // A flat run would divide by zero and, worse, would draw five dots on one line as though
        // the scale were meaningful. Give it a nominal span so they sit mid-plot instead.
        double span = max - min;
        boolean flat = span < 0.5;
        if (flat) span = 1;

        float plotTop = dp(20f);
        float plotHeight = dp(PLOT_HEIGHT_DP);
        // Inset so the first and last labels stay inside the card rather than hanging off it.
        float slotWidth = width / count;
        float first = slotWidth * 0.5f;

        for (int i = 0; i < count; i++) {
            Point point = mPoints.get(i);
            float x = first + slotWidth * i;
            float normalised = flat ? 0.5f : (float) ((point.tempC - min) / span);
            // Warmer is higher, so the plot reads the way a thermometer does.
            float y = plotTop + plotHeight * (1f - normalised);
            boolean now = i == 0;

            // Later hours are less certain and less urgent; fade them back rather than drawing
            // five points of identical weight and letting the reader work out which is which.
            int fade = 255 - Math.round(i * (70f / Math.max(1, count - 1)));
            int dotColor = now ? mAccent : ColorUtils.setAlphaComponent(mOnSurface,
                Math.max(90, fade - 60));

            if (now) {
                mHaloPaint.setColor(ColorUtils.setAlphaComponent(mAccent, 46));
                canvas.drawCircle(x, y, dp(HALO_RADIUS_DP), mHaloPaint);
            }
            mDotPaint.setColor(dotColor);
            canvas.drawCircle(x, y, dp(now ? NOW_DOT_RADIUS_DP : DOT_RADIUS_DP), mDotPaint);

            mTempPaint.setColor(now ? mOnSurface : ColorUtils.setAlphaComponent(mOnSurface, fade));
            canvas.drawText(point.tempLabel, x, y - dp(11f), mTempPaint);

            mHourPaint.setColor(now ? mAccent : ColorUtils.setAlphaComponent(mOnSurface, 133));
            canvas.drawText(point.hourLabel, x, y + dp(21f), mHourPaint);

            // Rain chance rides under its hour, and only when it is worth an umbrella thought;
            // a row of "0%" would spend the band saying the sky is dry five different ways.
            if (mShowPrecip && point.precipProb >= PRECIP_MIN_PERCENT) {
                mPrecipPaint.setColor(ColorUtils.setAlphaComponent(mAccent, 200));
                canvas.drawText(point.precipProb + "%", x, y + dp(21f) + dp(PRECIP_BAND_DP),
                    mPrecipPaint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
