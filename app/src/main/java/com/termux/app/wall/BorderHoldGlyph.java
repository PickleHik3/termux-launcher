package com.termux.app.wall;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.annotation.NonNull;

/**
 * The small {@code <} or {@code >} a border hold shows at its border as it fills ({@link
 * BorderHold}): a chevron in the rim's accent that grows and brightens with the hold and points the
 * way the row will page. One instance per owner; it keeps its paint and path, so drawing it
 * allocates nothing. The size and alpha are pure functions of the strength so they are tested
 * without a canvas.
 */
public final class BorderHoldGlyph {

    /** The chevron's height at full, in dp. */
    public static final float SIZE_DP = 28f;
    /** Its size when it first shows, as a share of full. */
    static final float MIN_SCALE = 0.6f;
    /** How far its tip stands from the border, in dp. */
    public static final float INSET_DP = 10f;
    static final float STROKE_DP = 3f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    public BorderHoldGlyph() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** The chevron's size for a hold's {@code strength}: it grows from {@link #MIN_SCALE} to full. */
    static float scale(float strength) {
        float s = strength < 0f ? 0f : strength > 1f ? 1f : strength;
        return MIN_SCALE + (1f - MIN_SCALE) * s;
    }

    /** Its alpha, 0..255, for a hold's {@code strength}. */
    static int alpha(float strength) {
        float s = strength < 0f ? 0f : strength > 1f ? 1f : strength;
        return Math.round(255f * s);
    }

    /**
     * Draws the glyph for {@code side} (LEFT points {@code <}, RIGHT {@code >}) on the border at
     * {@code edgeX}, centred on {@code centreY}, in {@code color} at {@code strength}.
     */
    public void draw(@NonNull Canvas canvas, @NonNull BorderDrag.Border side, float edgeX,
                     float centreY, float strength, float density, int color) {
        int dir = BorderHold.steps(side);
        if (dir == 0 || strength <= 0f) return;
        float scale = scale(strength);
        float half = SIZE_DP * density * scale / 2f;
        float inset = INSET_DP * density;
        // Left: the tip points left, standing inset into the screen from the edge; right mirrors it.
        float tipX = edgeX - dir * inset;
        float backX = tipX - dir * half * 0.6f;
        mPath.rewind();
        mPath.moveTo(backX, centreY - half);
        mPath.lineTo(tipX, centreY);
        mPath.lineTo(backX, centreY + half);
        mPaint.setColor(color);
        mPaint.setAlpha(Math.round(alpha(strength) * (android.graphics.Color.alpha(color) / 255f)));
        mPaint.setStrokeWidth(STROKE_DP * density);
        canvas.drawPath(mPath, mPaint);
    }
}
