package com.termux.app.terminal;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The attention border: a solid coloured line with a soft glow fading inward from it, pulsing
 * gently. Drawn inside the frame's bounds (the frame's parent clips, so an outer halo would be
 * cut), at the pane's own corner radius capped for its size like every other rim.
 *
 * <p>The pulse is opt-in ({@link #setPulsing}); off it holds the bright end of the pulse, a static
 * glow, which is what reduced motion, a disabled animator scale and Lazy mode get.
 */
final class PaneAttentionGlow extends Drawable {

    /** One pulse, dim to bright and back. */
    static final long PULSE_PERIOD_MS = 1200L;
    private static final float DIM = 0.55f;
    private static final int GLOW_STEPS = 5;
    private static final float LINE_DP = 1.5f;
    private static final float STEP_DP = 1.6f;
    /** The glow's strongest ring, as a share of the colour's alpha. */
    private static final float GLOW_PEAK = 0.42f;

    private final float mRadiusPx;
    private final int mColour;
    private final float mLinePx;
    private final float mStepPx;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private float mPulse = 1f;
    private int mAlpha = 255;
    @Nullable private ValueAnimator mAnimator;
    private boolean mPulsing;

    PaneAttentionGlow(float density, float radiusPx, int colour) {
        mRadiusPx = radiusPx;
        mColour = colour;
        mLinePx = Math.max(1f, LINE_DP * density);
        mStepPx = Math.max(1f, STEP_DP * density);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    /** Start or stop the pulse; stopping leaves the glow at full strength. */
    void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) return;
        mPulsing = pulsing;
        if (pulsing) startPulse();
        else stopPulse();
    }

    private void startPulse() {
        if (mAnimator != null) return;
        ValueAnimator a = ValueAnimator.ofFloat(DIM, 1f);
        a.setDuration(PULSE_PERIOD_MS / 2L);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.addUpdateListener(v -> {
            mPulse = (float) v.getAnimatedValue();
            invalidateSelf();
        });
        mAnimator = a;
        a.start();
    }

    private void stopPulse() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mPulse = 1f;
        invalidateSelf();
    }

    /** A drawable off screen has no business animating; it resumes when shown again. */
    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (mPulsing) {
            if (visible) startPulse();
            else if (mAnimator != null) {
                mAnimator.cancel();
                mAnimator = null;
            }
        }
        return changed;
    }

    /** Stop for good; the drawable is being replaced. */
    void release() {
        mPulsing = false;
        stopPulse();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float radius = PaneShape.radiusForBounds(mRadiusPx, b.width(), b.height());
        float strength = mPulse * (mAlpha / 255f);
        int baseAlpha = alphaOf(mColour);
        // Glow first, outermost ring strongest, so the line sits on top of it.
        for (int i = GLOW_STEPS - 1; i >= 0; i--) {
            float falloff = 1f - (float) i / GLOW_STEPS;
            float ringAlpha = GLOW_PEAK * falloff * falloff * strength;
            mPaint.setColor(ColorUtils.setAlphaComponent(mColour,
                Math.round(baseAlpha * Math.min(1f, ringAlpha))));
            mPaint.setStrokeWidth(mStepPx);
            float inset = mLinePx + mStepPx * (i + 0.5f);
            drawRing(canvas, b, inset, radius);
        }
        mPaint.setColor(ColorUtils.setAlphaComponent(mColour,
            Math.round(baseAlpha * Math.min(1f, strength))));
        mPaint.setStrokeWidth(mLinePx);
        drawRing(canvas, b, mLinePx / 2f, radius);
    }

    private static int alphaOf(int colour) {
        return (colour >>> 24) & 0xFF;
    }

    private void drawRing(Canvas canvas, Rect b, float inset, float radius) {
        mRect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        if (mRect.width() <= 0f || mRect.height() <= 0f) return;
        float r = Math.max(0f, radius - inset);
        canvas.drawRoundRect(mRect, r, r, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
