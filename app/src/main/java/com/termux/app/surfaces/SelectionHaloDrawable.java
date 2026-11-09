package com.termux.app.surfaces;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The page card's selection: a soft glow in the ring colour round the mini UI, outside its own
 * rounded outline. The outline is the drawable's bounds inset by the glow's reach on every side,
 * at the frame's device-corner radius; the glow is many thin strokes stepping outward, each a
 * little fainter on a smooth curve, so it reads as light rather than bands. Nothing is allocated
 * per draw.
 */
final class SelectionHaloDrawable extends Drawable {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private int mColor;
    private float mRadiusPx;
    private float mReachPx;
    private int mAlpha = 255;

    SelectionHaloDrawable() {
        mPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * The glow's colour, the outline's corner radius and how far the glow reaches outside it, in
     * the drawable's own px. The bounds must leave {@code reachPx} round the outline.
     */
    void set(int color, float radiusPx, float reachPx) {
        if (mColor == color && mRadiusPx == radiusPx && mReachPx == reachPx)
            return;
        mColor = color;
        mRadiusPx = Math.max(0f, radiusPx);
        mReachPx = Math.max(1f, reachPx);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty())
            return;
        int layers = SelectionRingShape.HALO_LAYERS;
        float step = mReachPx / layers;
        // A hair wider than the step so neighbouring strokes meet without a seam.
        mPaint.setStrokeWidth(step * 1.2f);
        float left = bounds.left + mReachPx;
        float top = bounds.top + mReachPx;
        float right = bounds.right - mReachPx;
        float bottom = bounds.bottom - mReachPx;
        int base = mColor >>> 24;
        for (int i = 0; i < layers; i++) {
            int alpha = Math.round(base * SelectionRingShape.haloLayerAlpha(i) * (mAlpha / 255f));
            if (alpha <= 0)
                continue;
            float out = step * (i + 0.5f);
            mPaint.setColor((alpha << 24) | (mColor & 0x00FFFFFF));
            mRect.set(left - out, top - out, right + out, bottom + out);
            float r = SelectionRingShape.haloLayerRadius(mRadiusPx, out);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
