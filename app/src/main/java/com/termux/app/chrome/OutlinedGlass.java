package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.NonNull;

/**
 * A glass stack cut to its own rounded outline, for a plain view that wears it without clipping
 * it (the marks' title capsule, Home's bar title). Every layer of a stack paints its full bounds
 * (the frame, the ground, a scheme's tint), so without this the view shows a square of glass
 * around the rounded rim. The cut is drawn on the canvas, so it holds on a software pass as well
 * as a hardware one, where a view's clip to outline would not. The path is rebuilt only when the
 * bounds change; drawing allocates nothing.
 */
public final class OutlinedGlass extends DrawableWrapper {

    private final float mRadiusPx;
    @NonNull private final Path mShape = new Path();
    @NonNull private final RectF mShapeRect = new RectF();

    public OutlinedGlass(@NonNull Drawable material, float radiusPx) {
        super(material);
        mRadiusPx = radiusPx;
    }

    /** {@code radiusPx} held to what a {@code width} by {@code height} box can round: its half. */
    static float fittedRadiusPx(float radiusPx, float width, float height) {
        return Math.max(0f, Math.min(radiusPx, Math.min(width, height) / 2f));
    }

    /** The radius asked for, before it is fitted to the bounds. */
    float radiusPx() {
        return mRadiusPx;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        float r = fittedRadiusPx(mRadiusPx, bounds.width(), bounds.height());
        mShapeRect.set(bounds);
        mShape.rewind();
        mShape.addRoundRect(mShapeRect, r, r, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(mShape);
        super.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect bounds = getBounds();
        outline.setRoundRect(bounds, fittedRadiusPx(mRadiusPx, bounds.width(), bounds.height()));
    }
}
