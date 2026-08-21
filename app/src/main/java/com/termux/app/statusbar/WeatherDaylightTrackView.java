package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * The hairline between sunrise and sunset in the card's footer, with a marker at the current time.
 *
 * <p>Two clock times alone make the reader do the arithmetic. The marker answers the question they
 * were actually asking — how much daylight is left — at a glance.
 */
public final class WeatherDaylightTrackView extends android.view.View {

    private static final float TRACK_HEIGHT_DP = 2f;
    private static final float MARKER_RADIUS_DP = 4f;
    /** Radius of the soft ring behind the marker. */
    private static final float MARKER_GLOW_RADIUS_DP = 8f;
    /**
     * Tall enough for the whole marker glow plus a hairline of slack. The view used to be 12dp,
     * which is shorter than the glow's 16dp diameter, so the marker was drawn with its top and
     * bottom sliced flat against the view bounds.
     */
    private static final float HEIGHT_DP = MARKER_GLOW_RADIUS_DP * 2f + 4f;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int mEdgeColor;
    private int mMiddleColor;
    private int mMarkerColor;
    /** 0 at the left end of the span, 1 at the right; negative hides the marker. */
    private float mProgress = -1f;

    public WeatherDaylightTrackView(@NonNull Context context) {
        super(context);
        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * @param edge  colour at both ends of the span, where the sun is on the horizon
     * @param middle colour through the middle of the span
     * @param marker colour of the "now" dot
     */
    public void setColors(int edge, int middle, int marker) {
        mEdgeColor = edge;
        mMiddleColor = middle;
        mMarkerColor = marker;
        invalidate();
    }

    /** @param progress fraction through the span, or negative when the time is outside it. */
    public void setProgress(float progress) {
        mProgress = progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(0, widthMeasureSpec), Math.round(dp(HEIGHT_DP)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float centreY = getHeight() * 0.5f;
        if (width <= 0) return;

        float stroke = dp(TRACK_HEIGHT_DP);
        mTrackPaint.setStrokeWidth(stroke);
        mTrackPaint.setShader(new LinearGradient(0, 0, width, 0,
            new int[] {mEdgeColor, mMiddleColor, mEdgeColor},
            new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawLine(stroke, centreY, width - stroke, centreY, mTrackPaint);

        if (mProgress < 0f || mProgress > 1f) return;
        // Inset by the glow radius at both ends: at dawn or dusk the marker sits on the very edge
        // of the span, and without this its halo would be cut off by the view bounds.
        float glow = dp(MARKER_GLOW_RADIUS_DP);
        float x = glow + (width - glow * 2) * mProgress;
        mGlowPaint.setColor(ColorUtils.setAlphaComponent(mMarkerColor, 70));
        canvas.drawCircle(x, centreY, dp(MARKER_GLOW_RADIUS_DP), mGlowPaint);
        mMarkerPaint.setColor(mMarkerColor);
        canvas.drawCircle(x, centreY, dp(MARKER_RADIUS_DP), mMarkerPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
