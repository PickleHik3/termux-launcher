package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

/**
 * One day's low-to-high span, drawn as a filled segment inside the week's overall range.
 *
 * <p>The offset is what carries the week: a bar that starts far to the right is a warm day, one
 * that starts at the left is a cold one, and their lengths say how much the day itself moved. The
 * numbers on either side are still the precise answer; the bar is what makes seven of them
 * comparable without reading fourteen figures.
 */
public final class WeatherRangeBarView extends android.view.View {

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private int mColdColor;
    private int mWarmColor;
    private float mStart;
    private float mEnd = 1f;

    public WeatherRangeBarView(@NonNull Context context) {
        super(context);
    }

    /**
     * @param track background colour of the full week range
     * @param cold  colour at the low end of this day's segment
     * @param warm  colour at its high end
     */
    public void setColors(int track, int cold, int warm) {
        mTrackPaint.setColor(track);
        mColdColor = cold;
        mWarmColor = warm;
        invalidate();
    }

    /** Both fractions of the week's overall span, clamped so a bad forecast cannot draw outside. */
    public void setRange(float start, float end) {
        mStart = Math.max(0f, Math.min(1f, start));
        mEnd = Math.max(mStart, Math.min(1f, end));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = height * 0.5f;

        mRect.set(0f, 0f, width, height);
        canvas.drawRoundRect(mRect, radius, radius, mTrackPaint);

        float left = width * mStart;
        // Never thinner than the cap it is drawn with: a day whose high and low are the same still
        // has to appear as a mark at the right place, not vanish.
        float right = Math.max(left + height, width * mEnd);
        if (right > width) {
            left = Math.max(0f, width - height);
            right = width;
        }
        mFillPaint.setShader(new LinearGradient(left, 0f, right, 0f,
            mColdColor, mWarmColor, Shader.TileMode.CLAMP));
        mRect.set(left, 0f, right, height);
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
    }
}
