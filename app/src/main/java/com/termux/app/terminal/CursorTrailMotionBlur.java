package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import com.termux.view.KittyCursorTrail;

/**
 * Paints the Motion blur cursor trail, named after kitty's {@code cursor-trail-motion-blur}; see
 * {@link CursorTrailBlurShape} for where it is. Each ghost is a soft oval in the trail colour,
 * opaque in the middle and feathered to nothing at its rim, laid along the direction of travel;
 * the tail is painted first so the head sits on top. It never draws a hard edge, which is what
 * sets it apart from Default's crisp quad.
 *
 * <p>Allocation-free per frame: the gradient is built once in unit space for the current colour
 * and every ghost is placed with the canvas matrix, so nothing about the shader changes between
 * ghosts; it is rebuilt only when the colour does.
 */
public final class CursorTrailMotionBlur implements CursorTrailEffect {

    /** Ghost alpha from its centre out, at full strength: a soft plateau, then the feather. */
    private static final float[] GHOST_STOPS = {0f, 0.45f, 0.75f, 1f};
    private static final float[] GHOST_ALPHA = {1f, 0.8f, 0.3f, 0f};

    private final CursorTrailBlurShape mShape = new CursorTrailBlurShape();
    private final RectF mBounds = new RectF();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] mColors = new int[GHOST_STOPS.length];
    private Shader mShader;
    private int mShaderRgb = -1;

    public CursorTrailMotionBlur() {
        mPaint.setStyle(Paint.Style.FILL);
    }

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
        int baseAlpha = Color.alpha(color) > 0 ? Color.alpha(color) : 255;
        if (Math.round(baseAlpha * s) <= 0) return;
        ensureShader(color & 0x00FFFFFF);
        float along = mShape.along(), across = mShape.across(), degrees = mShape.degrees();
        for (int i = mShape.ghostCount() - 1; i >= 0; i--) {
            int alpha = Math.round(baseAlpha * s * mShape.ghostAlpha(i));
            if (alpha <= 0) continue;
            mPaint.setAlpha(alpha);
            canvas.save();
            canvas.translate(mShape.ghostX(i), mShape.ghostY(i));
            canvas.rotate(degrees);
            canvas.scale(along, across);
            canvas.drawCircle(0f, 0f, 1f, mPaint);
            canvas.restore();
        }
    }

    private void ensureShader(int rgb) {
        if (rgb == mShaderRgb && mShader != null) return;
        mShaderRgb = rgb;
        for (int i = 0; i < GHOST_ALPHA.length; i++)
            mColors[i] = (Math.round(255f * GHOST_ALPHA[i]) << 24) | rgb;
        mShader = new RadialGradient(0f, 0f, 1f, mColors, GHOST_STOPS, Shader.TileMode.CLAMP);
        mPaint.setShader(mShader);
    }
}
