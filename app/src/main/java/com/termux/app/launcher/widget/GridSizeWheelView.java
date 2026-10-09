package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.app.haptics.Haptics;

/**
 * One column of numbers, dragged up or down. The number in the middle is the value; its
 * neighbours sit a step away either side and fade towards the edges, so a finger can see where
 * the next one is coming from.
 *
 * <p>The arithmetic — how far a step is, where a drag lands, what the wheel may not leave — is
 * {@link GridSizeWheelPolicy}'s; this view only draws the answer and reports it, once per number,
 * with a tick.
 *
 * <p>A finger is not the only way in: a screen reader sees a slider and can step or set it, and a
 * hardware keyboard's arrow, plus and minus keys step it. Every route lands on the same
 * {@link #commitValue}, so the grid changes — and is kept — exactly as it does for a drag.
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
    /** How far apart the numbers sit: the digits' own height and air, never under 28dp. */
    private float mPitchPx;

    GridSizeWheelView(@NonNull Context context, @NonNull GridSizeWheelPolicy policy, int value) {
        super(context);
        mPolicy = policy;
        mValue = policy.clamp(value);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextSize(sp(20));
        mPitchPx = pitch();
        setClickable(true);
        setFocusable(true);
        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                          @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.SeekBar.class.getName());
                info.setRangeInfo(AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                    AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_INT,
                    mPolicy.minimum(), mPolicy.maximum(), mValue));
                info.setStateDescription(String.valueOf(mValue));
                if (mValue < mPolicy.maximum()) {
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat
                        .ACTION_SCROLL_FORWARD);
                }
                if (mValue > mPolicy.minimum()) {
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat
                        .ACTION_SCROLL_BACKWARD);
                }
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat
                    .ACTION_SET_PROGRESS);
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action,
                                                      @Nullable Bundle args) {
                if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
                    return stepBy(1);
                }
                if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
                    return stepBy(-1);
                }
                if (action == android.R.id.accessibilityActionSetProgress && args != null
                    && args.containsKey(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE)) {
                    float wanted = args.getFloat(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE);
                    return setFromUser(Math.round(wanted));
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    /** One number up or down, the way a key or a screen reader's swipe asks for it. */
    private boolean stepBy(int delta) {
        return setFromUser(mValue + delta);
    }

    /**
     * Move to {@code value} (held to the range) from a route other than a drag. Always true: the
     * request was understood, even when the wheel is already at that end and there is nothing to do.
     */
    private boolean setFromUser(int value) {
        int next = mPolicy.clamp(value);
        mDragStartValue = next;
        mLeftoverPx = 0f;
        commitValue(next);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
            case KeyEvent.KEYCODE_PAGE_UP:
                stepBy(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                stepBy(-1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean performClick() {
        // Enter or a tap without a drag: read the number back rather than doing nothing.
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return super.performClick();
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
        // Both follow the digits: at a large font scale the numbers are further apart and the
        // wheel wider, and at the default one they come out at the old 28dp and 52dp.
        mPaint.setTextSize(sp(20));
        mPitchPx = pitch();
        setMeasuredDimension(resolveSize(Math.round(widthPx()), widthMeasureSpec),
            resolveSize(Math.round(mPitchPx * (VISIBLE_EITHER_SIDE * 2 + 1)), heightMeasureSpec));
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
        int next = mPolicy.valueForPitch(mDragStartValue, dragPx, mPitchPx);
        float leftover = GridSizeWheelPolicy.leftoverForPitch(dragPx, mPitchPx);
        // At either end there is nothing more to slide towards, so the column stops dead rather
        // than hanging off the edge of its own range.
        if ((next == mPolicy.maximum() && leftover < 0f)
            || (next == mPolicy.minimum() && leftover > 0f)) {
            leftover = 0f;
        }
        mLeftoverPx = leftover;
        commitValue(next);
    }

    /**
     * The one place the wheel's number changes for the person: a drag, a key or a screen reader
     * all end here, so the tick, the read-out and the listener — which is what keeps the grid —
     * are the same whichever way in.
     */
    private void commitValue(int next) {
        boolean moved = next != mValue;
        mValue = next;
        invalidate();
        if (!moved) return;
        if (Haptics.isEnabled(getContext())) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
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
        ViewCompat.setStateDescription(this, number);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
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
        float step = mPitchPx;
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

    private float pitch() {
        Paint.FontMetrics metrics = mPaint.getFontMetrics();
        android.graphics.Rect digits = new android.graphics.Rect();
        mPaint.getTextBounds("0123456789", 0, 10, digits);
        float glyphHeight = digits.height() > 0 ? digits.height() : metrics.descent - metrics.ascent;
        return GridSizeWheelPolicy.pitchPx(density(), glyphHeight, mPaint.getTextSize());
    }

    private float widthPx() {
        float widest = 0f;
        for (char digit = '0'; digit <= '9'; digit++) {
            widest = Math.max(widest, mPaint.measureText(String.valueOf(digit)));
        }
        return GridSizeWheelPolicy.widthPx(density(),
            widest * mPolicy.maximumDigits(), mPaint.getTextSize());
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    /** Font-scale-aware px for a size in sp. */
    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

}
