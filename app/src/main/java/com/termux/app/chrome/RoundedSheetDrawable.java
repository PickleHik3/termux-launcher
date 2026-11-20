package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A sheet of material drawn as a view's background: inset from the view's sides, clipped to a
 * rounded rect, and taking no part in layout.
 *
 * <p>The bands under the keyboard wear the dock's glass this way. Their stack is both the host of
 * the rows and the sheet behind them, so the rounding cannot be an outline clip — that would cut
 * the letters' lift and the icons' press, which draw outside their rows — and the material cannot
 * be a plain {@link android.graphics.drawable.InsetDrawable}: that reports its insets as the view's
 * padding and its layers' sizes as the view's minimum height, and a background that sizes its host
 * made the stack taller than the room the accessory stack counted for it, squeezing the dock's rows
 * over the keyboard into a strip and pushing the bands down into the navigation area. This reports
 * no size and no padding, so the rows alone decide the height.
 *
 * <p>The radius is clamped to half the sheet's height on every draw, so a caller can hand over the
 * dock's radius and a one-row band still reads as a true capsule rather than a lozenge.
 */
public final class RoundedSheetDrawable extends Drawable {

    @NonNull private final Drawable mSource;
    private final int mSideInsetPx;
    private final float mCornerRadiusPx;
    @NonNull private final Path mClip = new Path();
    @NonNull private final RectF mSheet = new RectF();
    @NonNull private final Rect mSourceBounds = new Rect();

    /**
     * @param source         the material: the glass's frame and tint, stacked
     * @param sideInsetPx    how far the sheet stands in from the view's left and right
     * @param cornerRadiusPx the sheet's corners, before the half-height clamp
     */
    public RoundedSheetDrawable(@NonNull Drawable source, int sideInsetPx, float cornerRadiusPx) {
        mSource = source;
        mSideInsetPx = Math.max(0, sideInsetPx);
        mCornerRadiusPx = Math.max(0f, cornerRadiusPx);
    }

    /** The material this stands in for, for whoever scans a background for what it draws. */
    @NonNull
    public Drawable source() {
        return mSource;
    }

    /** The sheet's rect inside {@code bounds}: inset at the sides only, never inverted. */
    @NonNull
    public static Rect sheetBounds(@NonNull Rect bounds, int sideInsetPx) {
        int inset = Math.max(0, Math.min(sideInsetPx, bounds.width() / 2));
        return new Rect(bounds.left + inset, bounds.top, bounds.right - inset, bounds.bottom);
    }

    /** The radius the sheet is drawn at: {@code cornerRadiusPx}, no more than half its height or width. */
    public static float clampedRadiusPx(float cornerRadiusPx, int sheetWidthPx, int sheetHeightPx) {
        float half = Math.min(Math.max(0, sheetWidthPx), Math.max(0, sheetHeightPx)) / 2f;
        return Math.max(0f, Math.min(cornerRadiusPx, half));
    }

    /** The radius this sheet is drawn at in its current bounds. */
    public float drawnRadiusPx() {
        Rect sheet = sheetBounds(getBounds(), mSideInsetPx);
        return clampedRadiusPx(mCornerRadiusPx, sheet.width(), sheet.height());
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        Rect sheet = sheetBounds(bounds, mSideInsetPx);
        mSourceBounds.set(sheet);
        mSource.setBounds(sheet);
        mSheet.set(sheet);
        float radius = clampedRadiusPx(mCornerRadiusPx, sheet.width(), sheet.height());
        mClip.reset();
        mClip.addRoundRect(mSheet, radius, radius, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mSourceBounds.isEmpty()) return;
        int save = canvas.save();
        canvas.clipPath(mClip);
        mSource.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        mSource.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mSource.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // No size and no padding: see the class comment.
    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }
    @Override public int getMinimumWidth() { return 0; }
    @Override public int getMinimumHeight() { return 0; }
    @Override public boolean getPadding(@NonNull Rect padding) {
        padding.set(0, 0, 0, 0);
        return false;
    }
}
