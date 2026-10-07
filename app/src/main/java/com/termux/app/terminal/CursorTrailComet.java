package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import com.termux.view.KittyCursorTrail;

/**
 * Paints the Comet cursor trail, this app's own style; {@link CursorTrailCometShape} says where it
 * is and what it looks like. A tapered streak in the trail colour, brightest at the cursor's
 * trailing edge and fading to nothing at its tail, with a soft glow on the head.
 *
 * <p>Allocation-free per frame: both gradients are built once in unit space for the current
 * colour and moved into place with a local matrix; they are rebuilt only when the colour changes.
 */
public final class CursorTrailComet implements CursorTrailEffect {

    /** Streak alpha along its length, tail to head, at full strength. */
    private static final float[] BODY_STOPS = {0f, 0.55f, 1f};
    private static final float[] BODY_ALPHA = {0f, 0.35f, 1f};
    /** Head glow alpha from its centre out, at full strength. */
    private static final float[] GLOW_STOPS = {0f, 0.35f, 1f};
    private static final float[] GLOW_ALPHA = {0.9f, 0.4f, 0f};

    private final CursorTrailCometShape mShape = new CursorTrailCometShape();
    private final Path mPath = new Path();
    private final RectF mBounds = new RectF();
    private final Paint mBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mBodyMatrix = new Matrix();
    private final Matrix mGlowMatrix = new Matrix();
    private final int[] mBodyColors = new int[BODY_STOPS.length];
    private final int[] mGlowColors = new int[GLOW_STOPS.length];
    private Shader mBodyShader;
    private Shader mGlowShader;
    private int mShaderRgb = -1;

    public CursorTrailComet() {
        mBodyPaint.setStyle(Paint.Style.FILL);
        mGlowPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void reset() {
        mShape.reset();
    }

    /** Whether a comet was started and has not yet aged out; clears itself once it has. */
    @Override
    public boolean alive(long nowMs) {
        return mShape.alive(nowMs);
    }

    public boolean active() {
        return mShape.active();
    }

    /** The box the last started comet can paint inside, glow included. */
    @Override
    @NonNull
    public RectF bounds() {
        mBounds.set(mShape.boundsLeft(), mShape.boundsTop(), mShape.boundsRight(),
            mShape.boundsBottom());
        return mBounds;
    }

    /** The comet keeps kitty's blaze timing whatever the decay; {@code config} is not read. */
    @Override
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs,
                      @NonNull KittyCursorTrail.Config config) {
        mShape.start(fromL, fromT, fromR, fromB, toL, toT, toR, toB, nowMs);
    }

    /**
     * Paints the comet as it stands at {@code nowMs}. The caller masks the cursor cell out first,
     * as for every trail.
     *
     * @param color    trail colour; its alpha channel, when set, scales everything
     * @param strength the trail's own opacity, 0..1 (it fades while the cursor is hidden)
     */
    @Override
    public void draw(@NonNull Canvas canvas, int color, float strength, long nowMs) {
        if (!mShape.layout(nowMs)) return;
        float s = Math.max(0f, Math.min(1f, strength)) * mShape.strength();
        int baseAlpha = Color.alpha(color) > 0 ? Color.alpha(color) : 255;
        int alpha = Math.round(baseAlpha * s);
        if (alpha <= 0) return;
        ensureShaders(color & 0x00FFFFFF);

        float length = mShape.length();
        if (length >= 0.5f) {
            float[] outline = mShape.outline();
            mPath.reset();
            mPath.moveTo(outline[0], outline[1]);
            for (int i = 1; i < mShape.pointCount(); i++) {
                mPath.lineTo(outline[i * 2], outline[i * 2 + 1]);
            }
            mPath.close();
            // Unit gradient: x = 0 at the tail, 1 at the head.
            float tailX = mShape.tailX(), tailY = mShape.tailY();
            float degrees = (float) Math.toDegrees(
                Math.atan2(mShape.headY() - tailY, mShape.headX() - tailX));
            mBodyMatrix.setScale(length, length);
            mBodyMatrix.postRotate(degrees);
            mBodyMatrix.postTranslate(tailX, tailY);
            mBodyShader.setLocalMatrix(mBodyMatrix);
            mBodyPaint.setAlpha(alpha);
            canvas.drawPath(mPath, mBodyPaint);
        }

        float radius = mShape.halfWidth() * CursorTrailCometShape.HEAD_GLOW_RADIUS;
        if (radius > 0f) {
            mGlowMatrix.setScale(radius, radius);
            mGlowMatrix.postTranslate(mShape.headX(), mShape.headY());
            mGlowShader.setLocalMatrix(mGlowMatrix);
            mGlowPaint.setAlpha(alpha);
            canvas.drawCircle(mShape.headX(), mShape.headY(), radius, mGlowPaint);
        }
    }

    private void ensureShaders(int rgb) {
        if (rgb == mShaderRgb && mBodyShader != null) return;
        mShaderRgb = rgb;
        for (int i = 0; i < BODY_ALPHA.length; i++)
            mBodyColors[i] = (Math.round(255f * BODY_ALPHA[i]) << 24) | rgb;
        for (int i = 0; i < GLOW_ALPHA.length; i++)
            mGlowColors[i] = (Math.round(255f * GLOW_ALPHA[i]) << 24) | rgb;
        mBodyShader = new LinearGradient(0f, 0f, 1f, 0f, mBodyColors, BODY_STOPS,
            Shader.TileMode.CLAMP);
        mGlowShader = new RadialGradient(0f, 0f, 1f, mGlowColors, GLOW_STOPS,
            Shader.TileMode.CLAMP);
        mBodyPaint.setShader(mBodyShader);
        mGlowPaint.setShader(mGlowShader);
    }
}
