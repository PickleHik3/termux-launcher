package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.termux.view.KittyCursorTrail;

/**
 * Paints the Railgun cursor trail; {@link CursorTrailRailgunShape} says where it is. The beam is
 * three strokes over one another: a faint wide glow and a narrower body in the trail colour, and a
 * thin white-hot core down the middle. Sparks are short white-hot streaks.
 *
 * <p>Allocation-free per frame: one paint, recoloured per stroke.
 */
public final class CursorTrailRailgun implements CursorTrailEffect {

    /** The glow's and the body's widths, in core widths, and their alpha. */
    private static final float GLOW_WIDTH = 3.4f;
    private static final float GLOW_ALPHA = 0.3f;
    private static final float BODY_WIDTH = 1.9f;
    private static final float BODY_ALPHA = 0.9f;
    /** A spark's width, in core widths, never under a pixel. */
    private static final float SPARK_WIDTH = 0.6f;

    private final CursorTrailRailgunShape mShape = new CursorTrailRailgunShape();
    private final RectF mBounds = new RectF();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CursorTrailRailgun() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void reset() {
        mShape.reset();
    }

    @Override
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs,
                      @NonNull KittyCursorTrail.Config config) {
        mShape.start(fromL, fromT, fromR, fromB, toL, toT, toR, toB, nowMs, config.decayFast);
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
        int hot = CursorTrailRailgunShape.hotColor(rgb);

        if (mShape.beamVisible()) {
            float a = base * mShape.beamAlpha();
            float w = mShape.beamWidth();
            float x0 = mShape.beamTailX(), y0 = mShape.beamTailY();
            float x1 = mShape.beamHeadX(), y1 = mShape.beamHeadY();
            stroke(canvas, rgb, a * GLOW_ALPHA, w * GLOW_WIDTH, x0, y0, x1, y1);
            stroke(canvas, rgb, a * BODY_ALPHA, w * BODY_WIDTH, x0, y0, x1, y1);
            stroke(canvas, hot, a, w, x0, y0, x1, y1);
        }

        float sparkWidth = Math.max(1f, mShape.coreWidth() * SPARK_WIDTH);
        float[] sparks = mShape.sparks();
        for (int i = 0; i < mShape.sparkCount(); i++) {
            int o = i * 5;
            stroke(canvas, hot, base * sparks[o + 4], sparkWidth,
                sparks[o], sparks[o + 1], sparks[o + 2], sparks[o + 3]);
        }
    }

    private void stroke(Canvas canvas, int rgb, float alpha, float width,
                        float x0, float y0, float x1, float y1) {
        int a = Math.round(alpha);
        if (a <= 0) return;
        mPaint.setColor((Math.min(a, 255) << 24) | rgb);
        mPaint.setStrokeWidth(width);
        canvas.drawLine(x0, y0, x1, y1, mPaint);
    }
}
