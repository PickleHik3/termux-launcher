package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.termux.view.KittyCursorTrail;

/**
 * Paints the Torpedo cursor trail; {@link CursorTrailTorpedoShape} says where it is. The body is
 * one solid, crisp-edged fill in the trail colour, a single object rather than a streak, and its
 * wake is thin rings in the same colour, faint and drawn underneath it.
 *
 * <p>Allocation-free per frame: one path and one paint, reused.
 */
public final class CursorTrailTorpedo implements CursorTrailEffect {

    /** A wake ring's stroke, as a fraction of its radius, never under a pixel. */
    private static final float RING_STROKE = 0.3f;

    private final CursorTrailTorpedoShape mShape = new CursorTrailTorpedoShape();
    private final RectF mBounds = new RectF();
    private final Path mPath = new Path();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void reset() {
        mShape.reset();
    }

    @Override
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs,
                      @NonNull KittyCursorTrail.Config config) {
        mShape.start(fromL, fromT, fromR, fromB, toL, toT, toR, toB, nowMs, config.decaySlow);
    }

    @Override
    public boolean alive(long nowMs) {
        return mShape.alive(nowMs);
    }

    @Override
    @NonNull
    public RectF bounds() {
        mBounds.set(mShape.boundsLeft(), mShape.boundsTop(), mShape.boundsRight(),
            mShape.boundsBottom());
        return mBounds;
    }

    @Override
    public void draw(@NonNull Canvas canvas, int color, float strength, long nowMs) {
        if (!mShape.layout(nowMs)) return;
        float s = Math.max(0f, Math.min(1f, strength));
        float base = (Color.alpha(color) > 0 ? Color.alpha(color) : 255) * s;
        if (Math.round(base) <= 0) return;
        int rgb = color & 0x00FFFFFF;

        mPaint.setStyle(Paint.Style.STROKE);
        float[] rings = mShape.rings();
        for (int i = 0; i < mShape.ringCount(); i++) {
            int o = i * 4;
            int a = Math.round(base * rings[o + 3]);
            if (a <= 0) continue;
            mPaint.setColor((a << 24) | rgb);
            mPaint.setStrokeWidth(Math.max(1f, rings[o + 2] * RING_STROKE));
            canvas.drawCircle(rings[o], rings[o + 1], rings[o + 2], mPaint);
        }

        if (mShape.bodyVisible()) {
            float[] outline = mShape.outline();
            mPath.reset();
            mPath.moveTo(outline[0], outline[1]);
            for (int i = 1; i < mShape.pointCount(); i++) {
                mPath.lineTo(outline[i * 2], outline[i * 2 + 1]);
            }
            mPath.close();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor((Math.round(base) << 24) | rgb);
            canvas.drawPath(mPath, mPaint);
        }
    }
}
