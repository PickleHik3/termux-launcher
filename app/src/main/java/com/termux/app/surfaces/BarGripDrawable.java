package com.termux.app.surfaces;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The grip on a bar's inner edge (#340): a row of three dots, drawn centred in its bounds from
 * fields set once. A drawable, not a text glyph.
 */
final class BarGripDrawable extends Drawable {

    private static final int DOTS = 3;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDotRadius;
    private final float mPitch;

    BarGripDrawable(int color, float dotRadiusPx, float pitchPx) {
        mPaint.setColor(color);
        mDotRadius = dotRadiusPx;
        mPitch = pitchPx;
    }

    static float widthFor(float dotRadiusPx, float pitchPx) {
        return (DOTS - 1) * pitchPx + 2f * dotRadiusPx;
    }

    static float heightFor(float dotRadiusPx) {
        return 2f * dotRadiusPx;
    }

    @Override public int getIntrinsicWidth() {
        return Math.round(widthFor(mDotRadius, mPitch));
    }

    @Override public int getIntrinsicHeight() {
        return Math.round(heightFor(mDotRadius));
    }

    @Override public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().exactCenterX();
        float cy = getBounds().exactCenterY();
        float left = cx - (DOTS - 1) * mPitch / 2f;
        for (int i = 0; i < DOTS; i++)
            canvas.drawCircle(left + i * mPitch, cy, mDotRadius, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
