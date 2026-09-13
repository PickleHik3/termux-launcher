package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The mark a frame puts on the corner a finger is holding: two short arms meeting in a rounded
 * elbow, in the outline colour. It is drawn for as long as the touch lasts and never at rest —
 * a frame that marked its corners all the time would be four permanent scratches on the wall.
 *
 * <p>One instance per view, so the path and the paint are allocated once rather than per frame.
 */
public final class CornerBracket {

    /** How far along each edge an arm runs. */
    private static final float ARM_DP = 14f;
    /** How far in from the frame's own edge the elbow sits. */
    private static final float INSET_DP = 4f;
    private static final float STROKE_DP = 2f;
    private static final float ELBOW_RADIUS_DP = 4f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    public CornerBracket() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Draws the bracket on one corner of {@code bounds}, in whatever coordinates the canvas is
     * already in — a page draws its own frame, a pane host draws the pane's rect.
     */
    public void draw(@NonNull Canvas canvas, int corner, @NonNull RectF bounds, float density,
                     int color) {
        if (corner == CornerZones.NONE || bounds.isEmpty()) return;
        float inset = INSET_DP * density;
        float arm = Math.min(ARM_DP * density,
            Math.min(bounds.width(), bounds.height()) / 2f - inset);
        if (arm <= 0f) return;
        float elbow = Math.min(ELBOW_RADIUS_DP * density, arm);
        boolean left = CornerZones.isLeft(corner);
        boolean top = CornerZones.isTop(corner);
        float x = left ? bounds.left + inset : bounds.right - inset;
        float y = top ? bounds.top + inset : bounds.bottom - inset;
        float alongX = left ? 1f : -1f;
        float alongY = top ? 1f : -1f;
        mPath.reset();
        mPath.moveTo(x + alongX * arm, y);
        mPath.lineTo(x + alongX * elbow, y);
        mPath.quadTo(x, y, x, y + alongY * elbow);
        mPath.lineTo(x, y + alongY * arm);
        mPaint.setStrokeWidth(STROKE_DP * density);
        mPaint.setColor(color);
        canvas.drawPath(mPath, mPaint);
    }

    /** The colour every frame marks its corners in: the outline, so the mark is chrome. */
    public static int color(@NonNull Context context) {
        return MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
    }
}
