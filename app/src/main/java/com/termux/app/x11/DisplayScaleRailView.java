package com.termux.app.x11;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The scale rail on the Display page: a vertical slider along the page's leading edge, out
 * together with the border tab, with one stop per {@link DisplayScaleSteps#STEPS} entry and a
 * read-out beside the thumb.
 *
 * <p>Like the tab, the view only draws; the page routes the touches, so nothing sits between a
 * finger and X while the rail is away.
 */
public final class DisplayScaleRailView extends View {

    /** How far in from the leading edge the track runs. */
    private static final float TRACK_X_DP = 16f;
    /** The band, from the leading edge, a finger has to land in to take the thumb. */
    private static final float HIT_BAND_DP = 40f;
    /** Room past the track's ends that still counts as the rail. */
    private static final float HIT_SLOP_DP = 24f;
    private static final float THUMB_RADIUS_DP = 9f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mLabel = new RectF();
    private ValueAnimator mAnimator;
    private float mProgress;
    private boolean mShown;
    private boolean mRetracting;
    private int mStep = DisplayScaleSteps.STEPS[0];
    /** The stop under the finger while it drags; the read-out follows it. */
    private int mPreviewStep = mStep;
    private boolean mDragging;

    public DisplayScaleRailView(@NonNull Context context) {
        super(context);
        mLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setTextSize(dp(12));
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public boolean isRailShown() {
        return mShown && !mRetracting;
    }

    public void show() {
        if (mShown && !mRetracting) return;
        animateTo(1f, false);
        mShown = true;
        mRetracting = false;
    }

    public void dismiss() {
        if (!mShown || mRetracting) return;
        mDragging = false;
        animateTo(0f, true);
        mRetracting = true;
    }

    /** The stop the rail rests on; a drag in flight is not it until it lands. */
    public int step() {
        return mStep;
    }

    public void setStep(int step) {
        mStep = DisplayScaleSteps.nearest(step);
        if (!mDragging) mPreviewStep = mStep;
        invalidate();
    }

    /** Whether a finger at {@code (x, y)} takes the thumb; nothing answers while half shown. */
    public boolean hits(float x, float y) {
        if (!mShown || mProgress < .35f) return false;
        float slop = dp(HIT_SLOP_DP);
        return x >= 0 && x <= dp(HIT_BAND_DP) && y >= trackTop() - slop && y <= trackBottom() + slop;
    }

    /** A finger landed on the rail. */
    public void beginDrag(float y) {
        mDragging = true;
        mPreviewStep = stepAtY(y);
        invalidate();
    }

    /**
     * The finger moved; true when it crossed onto another stop, so the page can tick.
     */
    public boolean dragTo(float y) {
        int step = stepAtY(y);
        boolean changed = step != mPreviewStep;
        mPreviewStep = step;
        if (changed) invalidate();
        return changed;
    }

    /** The finger lifted: the stop it was on becomes the rail's, and is returned. */
    public int endDrag() {
        mDragging = false;
        mStep = mPreviewStep;
        invalidate();
        return mStep;
    }

    public void cancelDrag() {
        mDragging = false;
        mPreviewStep = mStep;
        invalidate();
    }

    private int stepAtY(float y) {
        float top = trackTop();
        float length = Math.max(1f, trackBottom() - top);
        return DisplayScaleSteps.stepAt((y - top) / length);
    }

    /** The track keeps to the middle half of the page, clear of the tab and the corners. */
    private float trackTop() {
        return getHeight() * .25f;
    }

    private float trackBottom() {
        return getHeight() * .75f;
    }

    private float yFor(int step) {
        return trackTop() + DisplayScaleSteps.fraction(step) * (trackBottom() - trackTop());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!mShown || mProgress <= 0f || getWidth() <= 0 || getHeight() <= 0) return;
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int surface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        int alpha = Math.round(255f * mProgress);
        int save = canvas.save();
        // Slides in from the leading edge, as the tab drops from the top.
        canvas.translate(-dp(HIT_BAND_DP) * (1f - mProgress), 0f);

        float x = dp(TRACK_X_DP);
        float top = trackTop();
        float bottom = trackBottom();
        float radius = dp(THUMB_RADIUS_DP);

        // The slab behind the track, so the rail reads over any desktop.
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(ColorUtils.setAlphaComponent(surface, Math.round(232f * mProgress)));
        float slab = radius + dp(6);
        canvas.drawRoundRect(x - slab, top - slab, x + slab, bottom + slab, slab, slab, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(225f * mProgress)));
        canvas.drawRoundRect(x - slab, top - slab, x + slab, bottom + slab, slab, slab, mPaint);

        // The track and its stops.
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(dp(2));
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(110f * mProgress)));
        canvas.drawLine(x, top, x, bottom, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        for (int step : DisplayScaleSteps.STEPS) {
            canvas.drawCircle(x, yFor(step), dp(2.5f), mPaint);
        }

        // The thumb, on the stop under the finger while dragging.
        int shown = mDragging ? mPreviewStep : mStep;
        float thumbY = yFor(shown);
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, alpha));
        canvas.drawCircle(x, thumbY, mDragging ? radius + dp(2) : radius, mPaint);

        // The read-out, in a pill to the thumb's right.
        String label = DisplayScaleSteps.label(shown);
        float textWidth = mLabelPaint.measureText(label);
        float pillHeight = dp(24);
        float left = x + slab + dp(8);
        mLabel.set(left, thumbY - pillHeight / 2f, left + textWidth + dp(16), thumbY + pillHeight / 2f);
        mPaint.setColor(ColorUtils.setAlphaComponent(surface, Math.round(232f * mProgress)));
        canvas.drawRoundRect(mLabel, pillHeight / 2f, pillHeight / 2f, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setColor(ColorUtils.setAlphaComponent(primary, Math.round(225f * mProgress)));
        canvas.drawRoundRect(mLabel, pillHeight / 2f, pillHeight / 2f, mPaint);
        mLabelPaint.setColor(ColorUtils.setAlphaComponent(primary, alpha));
        float baseline = mLabel.centerY() - (mLabelPaint.ascent() + mLabelPaint.descent()) / 2f;
        canvas.drawText(label, mLabel.centerX(), baseline, mLabelPaint);
        canvas.restoreToCount(save);
    }

    private void animateTo(float target, boolean clearOnEnd) {
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = ValueAnimator.ofFloat(mProgress, target);
        mAnimator.setDuration(190L);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (Float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (clearOnEnd && mProgress <= 0f) {
                    mShown = false;
                    mRetracting = false;
                }
            }
        });
        mAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) mAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
