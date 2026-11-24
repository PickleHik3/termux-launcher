package com.termux.app.surfaces;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The drag handle beside a bar's name on the editor's frame: two columns of three dots, drawn
 * centred in its own bounds, so a row that centres the drawable on its label centres the grip on
 * the label. A drawable, not a text glyph (#326), and it draws from fields set once.
 */
final class BarHandleDrawable extends Drawable {

    private static final int COLUMNS = 2;
    private static final int ROWS = 3;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDotRadius;
    private final float mPitch;

    /** {@code dotRadiusPx} is the dot, {@code pitchPx} the distance between dot centres. */
    BarHandleDrawable(int color, float dotRadiusPx, float pitchPx) {
        mPaint.setColor(color);
        mDotRadius = dotRadiusPx;
        mPitch = pitchPx;
    }

    /** The grip's own width, the dots' extent. */
    static float widthFor(float dotRadiusPx, float pitchPx) {
        return (COLUMNS - 1) * pitchPx + 2f * dotRadiusPx;
    }

    /** The grip's own height. */
    static float heightFor(float dotRadiusPx, float pitchPx) {
        return (ROWS - 1) * pitchPx + 2f * dotRadiusPx;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(widthFor(mDotRadius, mPitch));
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(heightFor(mDotRadius, mPitch));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().exactCenterX();
        float cy = getBounds().exactCenterY();
        float left = cx - (COLUMNS - 1) * mPitch / 2f;
        float top = cy - (ROWS - 1) * mPitch / 2f;
        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row < ROWS; row++)
                canvas.drawCircle(left + column * mPitch, top + row * mPitch, mDotRadius, mPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        mPaint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
