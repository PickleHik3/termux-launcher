package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/**
 * One column of numbers, dragged up or down. The number in the middle is the value; its
 * neighbours sit a step away either side and fade towards the edges, so a finger can see where
 * the next one is coming from.
 *
 * <p>The arithmetic — how far a step is, where a drag lands, what the wheel may not leave — is
 * {@link GridSizeWheelPolicy}'s; this view only draws the answer and reports it, once per number,
 * with a tick.
 */
final class GridSizeWheelView extends View {

    /** Told each time the wheel settles on a different number, while the finger is still down. */
    interface Listener {
        void onValueChanged(int value);
    }

    /** How many numbers are drawn either side of the value. */
    private static final int VISIBLE_EITHER_SIDE = 1;

    @NonNull private final GridSizeWheelPolicy mPolicy;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Listener mListener;
    private int mValue;
    /** Where this drag started, and the value it started from. */
    private float mDownY;
    private int mDragStartValue;
    private boolean mDragging;
    /** How far the digits have slid towards the next number, in pixels. */
    private float mLeftoverPx;

    GridSizeWheelView(@NonNull Context context, @NonNull GridSizeWheelPolicy policy, int value) {
        super(context);
        mPolicy = policy;
        mValue = policy.clamp(value);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextSize(dp(20));
        setClickable(true);
        setFocusable(true);
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    int value() {
        return mValue;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Three numbers tall and wide enough for two digits: the wheel is a thumb's worth of
        // travel, not a list to scroll.
        setMeasuredDimension(resolveSize(Math.round(dp(52)), widthMeasureSpec),
            resolveSize(Math.round(step() * (VISIBLE_EITHER_SIDE * 2 + 1)), heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownY = event.getY();
                mDragStartValue = mValue;
                mDragging = true;
                mLeftoverPx = 0f;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) return true;
                applyDrag(event.getY() - mDownY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                mLeftoverPx = 0f;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void applyDrag(float dragPx) {
        float density = getResources().getDisplayMetrics().density;
        int next = mPolicy.valueFor(mDragStartValue, dragPx, density);
        float leftover = GridSizeWheelPolicy.leftoverPx(dragPx, density);
        // At either end there is nothing more to slide towards, so the column stops dead rather
        // than hanging off the edge of its own range.
        if ((next == mPolicy.maximum() && leftover < 0f)
            || (next == mPolicy.minimum() && leftover > 0f)) {
            leftover = 0f;
        }
        boolean moved = next != mValue;
        mValue = next;
        mLeftoverPx = leftover;
        invalidate();
        if (!moved) return;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        announce();
        if (mListener != null) mListener.onValueChanged(mValue);
    }

    /** Set from outside — the popup opening on the grid the page is already showing. */
    void setValue(int value) {
        int clamped = mPolicy.clamp(value);
        if (clamped == mValue) return;
        mValue = clamped;
        mDragStartValue = clamped;
        announce();
        invalidate();
    }

    private void announce() {
        CharSequence label = getContentDescription();
        String number = String.valueOf(mValue);
        if (label == null) {
            setContentDescription(number);
            return;
        }
        // The label the popup gave this wheel stays in front of the number it now reads.
        String text = label.toString();
        int space = text.lastIndexOf(' ');
        setContentDescription((space < 0 ? text : text.substring(0, space)) + " " + number);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int onSurface = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        float step = step();
        float centre = getHeight() / 2f;
        for (int delta = -VISIBLE_EITHER_SIDE - 1; delta <= VISIBLE_EITHER_SIDE + 1; delta++) {
            int number = mValue + delta;
            if (number < mPolicy.minimum() || number > mPolicy.maximum()) continue;
            float y = centre + delta * step + mLeftoverPx;
            // Fully lit in the middle, faded out by the time it reaches the edge.
            float distance = Math.min(1f, Math.abs(y - centre) / (step * (VISIBLE_EITHER_SIDE + 1)));
            int alpha = Math.round(255f * (1f - distance) * (1f - distance));
            if (alpha <= 0) continue;
            mPaint.setColor(ColorUtils.setAlphaComponent(onSurface, alpha));
            canvas.drawText(String.valueOf(number), getWidth() / 2f,
                y - (mPaint.ascent() + mPaint.descent()) / 2f, mPaint);
        }
    }

    private float step() {
        return dp(GridSizeWheelPolicy.STEP_DP);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
