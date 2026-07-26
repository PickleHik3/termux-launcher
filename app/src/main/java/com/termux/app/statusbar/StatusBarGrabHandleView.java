package com.termux.app.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Tap-only chevron that collapses or expands the top clock surface. */
public final class StatusBarGrabHandleView extends View {

    private static final int IDLE_ALPHA = 42;
    private static final long DIRECTION_MORPH_DURATION_MS = 220L;
    private static final long EMPHASIS_RAMP_DURATION_MS = 260L;
    private static final long EMPHASIS_BREATH_DURATION_MS = 1400L;
    private static final long EMPHASIS_MINIMUM_HOLD_MS = 900L;
    private static final long EMPHASIS_FADE_DURATION_MS = 520L;

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mChevronPath = new Path();
    private int mIdleColor;
    private int mActiveColor;
    @Nullable private Listener mListener;
    @Nullable private ValueAnimator mDirectionAnimator;
    @Nullable private ValueAnimator mEmphasisAnimator;
    private boolean mCollapsed;
    private boolean mTransitioning;
    /** -1 draws an upward chevron, +1 draws a downward chevron. */
    private float mDirection = -1f;
    private float mTransitionEmphasis;
    private int mEmphasisGeneration;
    private long mEmphasisHoldUntil;
    private final Runnable mDeferredFade = () -> {
        if (!mTransitioning && !isPressed()) fadeEmphasis();
    };

    public StatusBarGrabHandleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.termux_status_bar_collapse_content_description));
        int color = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mIdleColor = ColorUtils.setAlphaComponent(color, IDLE_ALPHA);
        mActiveColor = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setCollapsed(boolean collapsed) {
        setCollapsed(collapsed, false);
    }

    /** Updates the action glyph: up collapses an expanded panel; down expands a collapsed panel. */
    public void setCollapsed(boolean collapsed, boolean animate) {
        float targetDirection = collapsed ? 1f : -1f;
        boolean changed = mCollapsed != collapsed;
        mCollapsed = collapsed;
        setContentDescription(getContext().getString(collapsed
            ? R.string.termux_status_bar_expand_content_description
            : R.string.termux_status_bar_collapse_content_description));
        if (!changed || mDirection == targetDirection) {
            mDirection = targetDirection;
            invalidate();
            return;
        }
        if (mDirectionAnimator != null) mDirectionAnimator.cancel();
        if (!animate || !isLaidOut()) {
            mDirection = targetDirection;
            invalidate();
            return;
        }
        mDirectionAnimator = ValueAnimator.ofFloat(mDirection, targetDirection);
        mDirectionAnimator.setDuration(DIRECTION_MORPH_DURATION_MS);
        mDirectionAnimator.addUpdateListener(animation -> {
            mDirection = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mDirectionAnimator.start();
    }

    /** Keeps the Material accent lit until the status-panel height animation has settled. */
    public void setTransitioning(boolean transitioning) {
        if (mTransitioning == transitioning) return;
        mTransitioning = transitioning;
        if (transitioning) {
            beginInteractionBreath();
            return;
        }
        finishInteractionBreathWhenReady();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                beginInteractionBreath();
                return true;
            case MotionEvent.ACTION_MOVE:
                setPressed(containsTouch(event));
                return true;
            case MotionEvent.ACTION_UP:
                boolean clicked = isPressed() && containsTouch(event);
                setPressed(false);
                if (clicked) performClick();
                if (!mTransitioning) finishInteractionBreathWhenReady();
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                finishInteractionBreathWhenReady();
                return true;
            default:
                return true;
        }
    }

    private boolean containsTouch(MotionEvent event) {
        return event.getX() >= 0f && event.getX() < getWidth()
            && event.getY() >= 0f && event.getY() < getHeight();
    }

    private void beginInteractionBreath() {
        removeCallbacks(mDeferredFade);
        mEmphasisHoldUntil = Math.max(mEmphasisHoldUntil,
            SystemClock.uptimeMillis() + EMPHASIS_MINIMUM_HOLD_MS);
        int generation = ++mEmphasisGeneration;
        if (mEmphasisAnimator != null) mEmphasisAnimator.cancel();
        mEmphasisAnimator = ValueAnimator.ofFloat(mTransitionEmphasis, 1f);
        mEmphasisAnimator.setDuration(EMPHASIS_RAMP_DURATION_MS);
        mEmphasisAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mEmphasisAnimator.addUpdateListener(animation -> {
            mTransitionEmphasis = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mEmphasisAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (generation != mEmphasisGeneration
                    || (!mTransitioning && !isPressed()
                        && SystemClock.uptimeMillis() >= mEmphasisHoldUntil)) return;
                startBreathingCycle(generation);
            }
        });
        mEmphasisAnimator.start();
    }

    private void startBreathingCycle(int generation) {
        if (generation != mEmphasisGeneration) return;
        mEmphasisAnimator = ValueAnimator.ofFloat(1f, .52f, 1f);
        mEmphasisAnimator.setDuration(EMPHASIS_BREATH_DURATION_MS);
        mEmphasisAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mEmphasisAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mEmphasisAnimator.addUpdateListener(animation -> {
            mTransitionEmphasis = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mEmphasisAnimator.start();
    }

    private void finishInteractionBreathWhenReady() {
        removeCallbacks(mDeferredFade);
        long remaining = mEmphasisHoldUntil - SystemClock.uptimeMillis();
        if (remaining > 0L) {
            postDelayed(mDeferredFade, remaining);
        } else {
            fadeEmphasis();
        }
    }

    private void fadeEmphasis() {
        removeCallbacks(mDeferredFade);
        ++mEmphasisGeneration;
        if (mEmphasisAnimator != null) mEmphasisAnimator.cancel();
        if (!isLaidOut() || mTransitionEmphasis <= 0f) {
            mTransitionEmphasis = 0f;
            invalidate();
            return;
        }
        mEmphasisAnimator = ValueAnimator.ofFloat(mTransitionEmphasis, 0f);
        mEmphasisAnimator.setDuration(EMPHASIS_FADE_DURATION_MS);
        mEmphasisAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mEmphasisAnimator.addUpdateListener(animation -> {
            mTransitionEmphasis = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mEmphasisAnimator.start();
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        if (mListener == null) return handled;
        mListener.onCollapsedStateRequested(!mCollapsed);
        return true;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float emphasis = Math.max(mTransitionEmphasis, isPressed() ? 1f : 0f);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float halfWidth = dp(4.75f + .35f * emphasis);
        float peakY = centerY + dp(1.9f) * mDirection;
        float endY = centerY - dp(1.5f) * mDirection;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1.15f + .35f * emphasis));
        mPaint.setColor(ColorUtils.blendARGB(mIdleColor, mActiveColor, emphasis));
        mChevronPath.reset();
        mChevronPath.moveTo(centerX - halfWidth, endY);
        mChevronPath.lineTo(centerX, peakY);
        mChevronPath.lineTo(centerX + halfWidth, endY);
        canvas.drawPath(mChevronPath, mPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mDeferredFade);
        ++mEmphasisGeneration;
        if (mDirectionAnimator != null) mDirectionAnimator.cancel();
        if (mEmphasisAnimator != null) mEmphasisAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
