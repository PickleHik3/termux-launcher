package com.termux.app.surfaces;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The page card's selection: a soft glow in the ring colour along the mini UI's own rounded
 * outline (its bounds, at the frame's device-corner radius), falling off inward. Drawn as a few
 * concentric strokes at falling alpha, clipped to the outline so nothing stands outside the mini
 * UI. Nothing is allocated per draw; the path is rebuilt only when the bounds or radius change.
 */
final class SelectionHaloDrawable extends Drawable {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private final RectF mRect = new RectF();
    private int mColor;
    private float mRadiusPx;
    private float mWidthPx;
    private int mAlpha = 255;

    SelectionHaloDrawable() {
        mPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * The glow's colour, the outline's corner radius and the glow's whole width, all in the
     * drawable's own px.
     */
    void set(int color, float radiusPx, float widthPx) {
        if (mColor == color && mRadiusPx == radiusPx && mWidthPx == widthPx)
            return;
        mColor = color;
        mRadiusPx = Math.max(0f, radiusPx);
        mWidthPx = Math.max(1f, widthPx);
        rebuildClip(getBounds());
        invalidateSelf();
    }

    float radiusPx() {
        return mRadiusPx;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        rebuildClip(bounds);
    }

    private void rebuildClip(@NonNull Rect bounds) {
        mClip.reset();
        mRect.set(bounds);
        mClip.addRoundRect(mRect, mRadiusPx, mRadiusPx, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty())
            return;
        int layers = SelectionRingShape.HALO_LAYERS;
        float step = mWidthPx / layers;
        int save = canvas.save();
        canvas.clipPath(mClip);
        mPaint.setStrokeWidth(step);
        for (int i = 0; i < layers; i++) {
            float inset = step * (i + 0.5f);
            int alpha = Math.round(colorAlpha(mColor) * SelectionRingShape.haloLayerAlpha(i)
                * (mAlpha / 255f));
            if (alpha <= 0)
                continue;
            mPaint.setColor((alpha << 24) | (mColor & 0x00FFFFFF));
            mRect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset,
                bounds.bottom - inset);
            float r = SelectionRingShape.haloLayerRadius(mRadiusPx, inset);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }
        canvas.restoreToCount(save);
    }

    private static int colorAlpha(int color) {
        return color >>> 24;
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
