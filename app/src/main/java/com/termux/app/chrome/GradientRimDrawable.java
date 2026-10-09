package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The gradient rim: a rounded stroke whose colour runs diagonally from {@code start} at the
 * top-left to {@code end} at the bottom-right, over the drawable's bounds. Fills nothing. Its
 * outline is the rounded rect, so it can stand in for the hairline as a capsule's background.
 */
final class GradientRimDrawable extends Drawable {

    private final float mRadiusPx;
    private final float mStrokePx;
    private final int mStart;
    private final int mEnd;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    GradientRimDrawable(float radiusPx, float strokePx, int start, int end) {
        mRadiusPx = radiusPx;
        mStrokePx = Math.max(1f, strokePx);
        mStart = start;
        mEnd = end;
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokePx);
    }

    /** The corner radius it strokes, in px. */
    float radiusPx() {
        return mRadiusPx;
    }

    /** The colour at the top-left end of the gradient. */
    int startColor() {
        return mStart;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        mPaint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            mStart, mEnd, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float half = mStrokePx / 2f;
        mRect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half);
        float radius = Math.max(0f, mRadiusPx - half);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        outline.setRoundRect(getBounds(), mRadiusPx);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
