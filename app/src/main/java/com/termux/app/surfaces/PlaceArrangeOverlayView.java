package com.termux.app.surfaces;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceArrangePolicy.Slot;
import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a lifted bar looks like while it is in the air: the edges it may be dropped on as dashed
 * outlines, the tray that puts it away, and the bar's own outline following the finger.
 *
 * <p>One view for all of it, drawn from rectangles the editor hands in, so nothing here decides
 * anything — {@code PlaceArrangePolicy} owns which slots exist and which one the finger is over,
 * and this only paints the answer. It is never touchable: the gesture that lifted the bar keeps the
 * whole drag, and every touch offered here falls through to the capture layer beneath.
 */
public final class PlaceArrangeOverlayView extends View {

    private static final long SPRING_BACK_MS = 180L;

    private final Paint mSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGhostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCaptionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTmpRect = new RectF();

    @NonNull private List<Slot> mSlots = Collections.emptyList();
    @Nullable private Slot mHighlighted;
    @Nullable private CharSequence mTrayCaption;

    private final RectF mGhostOrigin = new RectF();
    private float mGhostRadiusPx;
    private float mGhostDx;
    private float mGhostDy;
    private boolean mGhostShown;
    @Nullable private ValueAnimator mSpring;

    private int mAccent = Color.WHITE;
    private float mDensity = 1f;

    public PlaceArrangeOverlayView(@NonNull Context context) {
        this(context, null);
    }

    public PlaceArrangeOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mSlotPaint.setStyle(Paint.Style.STROKE);
        mSlotPaint.setStrokeWidth(px(2f));
        mSlotPaint.setPathEffect(new DashPathEffect(new float[] {px(6f), px(5f)}, 0f));
        mFillPaint.setStyle(Paint.Style.FILL);
        mGhostPaint.setStyle(Paint.Style.STROKE);
        mGhostPaint.setStrokeWidth(px(2f));
        mCaptionPaint.setTextAlign(Paint.Align.CENTER);
        mCaptionPaint.setTextSize(px(12f));
        mCaptionPaint.setFakeBoldText(true);
        setWillNotDraw(false);
        applyAccent();
    }

    private float px(float dp) {
        return dp * mDensity;
    }

    /** The editor's own accent, so a slot reads as part of the same overlay as the outlines. */
    public void setAccent(int accent, int onAccent) {
        mAccent = accent;
        mCaptionPaint.setColor(onAccent);
        applyAccent();
        invalidate();
    }

    private void applyAccent() {
        mSlotPaint.setColor(withAlpha(mAccent, 150));
        mFillPaint.setColor(withAlpha(mAccent, 46));
        mGhostPaint.setColor(mAccent);
    }

    /**
     * The slots a lifted bar may land on. The caption is drawn in the tray slot, and is the only
     * words the layer carries.
     */
    public void showSlots(@NonNull List<Slot> slots, @Nullable CharSequence trayCaption) {
        mSlots = new ArrayList<>(slots);
        mTrayCaption = trayCaption;
        setVisibility(VISIBLE);
        invalidate();
    }

    /** The slot the finger is over, drawn solid rather than dashed. */
    public void setHighlighted(@Nullable Slot slot) {
        if (mHighlighted == slot) return;
        mHighlighted = slot;
        invalidate();
    }

    /** The lifted bar's own outline, at the rect it was lifted from. */
    public void setGhost(float left, float top, float right, float bottom, float radiusPx) {
        cancelSpring();
        mGhostOrigin.set(left, top, right, bottom);
        mGhostRadiusPx = radiusPx;
        mGhostDx = 0f;
        mGhostDy = 0f;
        mGhostShown = true;
        invalidate();
    }

    /** Where the finger has taken it since. */
    public void moveGhost(float dx, float dy) {
        if (!mGhostShown) return;
        mGhostDx = dx;
        mGhostDy = dy;
        invalidate();
    }

    /** A release over nothing: the bar goes back where it came from and the layer clears. */
    public void springBack(boolean animate, @Nullable Runnable onEnd) {
        cancelSpring();
        if (!animate || !mGhostShown || (mGhostDx == 0f && mGhostDy == 0f)) {
            clear();
            if (onEnd != null) onEnd.run();
            return;
        }
        final float fromX = mGhostDx;
        final float fromY = mGhostDy;
        mSlots = Collections.emptyList();
        mHighlighted = null;
        ValueAnimator spring = ValueAnimator.ofFloat(1f, 0f);
        spring.setDuration(SPRING_BACK_MS);
        spring.setInterpolator(Motion.settle());
        spring.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            moveGhost(fromX * fraction, fromY * fraction);
        });
        spring.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                mSpring = null;
                clear();
                if (onEnd != null) onEnd.run();
            }
        });
        mSpring = spring;
        spring.start();
    }

    /** Nothing in the air: no slots, no tray, no ghost. */
    public void clear() {
        cancelSpring();
        mSlots = Collections.emptyList();
        mHighlighted = null;
        mTrayCaption = null;
        mGhostShown = false;
        mGhostDx = 0f;
        mGhostDy = 0f;
        setVisibility(GONE);
        invalidate();
    }

    private void cancelSpring() {
        if (mSpring == null) return;
        ValueAnimator spring = mSpring;
        mSpring = null;
        spring.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSpring();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float radius = px(14f);
        for (Slot slot : mSlots) {
            mTmpRect.set(slot.left, slot.top, slot.right, slot.bottom);
            boolean lit = slot == mHighlighted;
            if (lit || slot.isTray())
                canvas.drawRoundRect(mTmpRect, radius, radius, mFillPaint);
            if (lit) {
                mGhostPaint.setAlpha(255);
                canvas.drawRoundRect(mTmpRect, radius, radius, mGhostPaint);
            } else {
                canvas.drawRoundRect(mTmpRect, radius, radius, mSlotPaint);
            }
            if (slot.isTray() && mTrayCaption != null) {
                String caption = mTrayCaption.toString();
                float baseline = mTmpRect.centerY()
                    - ((mCaptionPaint.descent() + mCaptionPaint.ascent()) / 2f);
                canvas.drawText(caption, mTmpRect.centerX(), baseline, mCaptionPaint);
            }
        }
        if (!mGhostShown) return;
        mTmpRect.set(mGhostOrigin);
        mTmpRect.offset(mGhostDx, mGhostDy);
        canvas.drawRoundRect(mTmpRect, mGhostRadiusPx, mGhostRadiusPx, mFillPaint);
        mGhostPaint.setAlpha(235);
        canvas.drawRoundRect(mTmpRect, mGhostRadiusPx, mGhostRadiusPx, mGhostPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
