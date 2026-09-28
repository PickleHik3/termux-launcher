package com.termux.app.terminal.inappkeyboard;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import com.termux.app.terminal.Motion;

import java.util.function.BooleanSupplier;

import juloo.keyboard2.Keyboard2View;

/**
 * Swipe down from the keyboard's top edge to put it away. Takes the presses {@link Keyboard2View}
 * offers from the strip above its first row, classifies them with {@link KeyboardHideSwipe}, and
 * lets the keys follow the finger down ({@code translationY} only, no layout). A release past the
 * threshold settles the keys off the bottom and then runs the very same hide the keyboard's own
 * hide key runs, so the pane room, the dock and the wall see nothing new; a short release springs
 * them back.
 */
final class KeyboardHideSwipeGesture implements Keyboard2View.TopEdgeTouchDelegate {

    /** The band grows to this much when the gap above the keys is thinner, never onto a key. */
    static final float MIN_BAND_DP = 12f;
    /** Released this far below the press, the keyboard goes. */
    static final float THRESHOLD_DP = 28f;
    /** A flick this fast, having moved at least the fling distance, goes too. */
    static final float FLING_DP_PER_S = 500f;
    static final float FLING_MIN_DISTANCE_DP = 12f;
    /** The settle at full travel; a shorter remaining way takes proportionally less. */
    static final long SETTLE_MS = 220L;
    static final long SETTLE_MIN_MS = 120L;

    private final Keyboard2View mView;
    private final BooleanSupplier mCanHide;
    private final Runnable mHide;
    private final KeyboardHideSwipe mSwipe;
    private final float mMinBandPx;
    private int mPointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean mSettling;

    /**
     * @param canHide asked at the press; false leaves the press to the keys (which ignore it)
     * @param hide the hide path to run once the keys have settled off screen
     */
    KeyboardHideSwipeGesture(@NonNull Keyboard2View view, @NonNull BooleanSupplier canHide,
                             @NonNull Runnable hide) {
        mView = view;
        mCanHide = canHide;
        mHide = hide;
        float density = view.getResources().getDisplayMetrics().density;
        mMinBandPx = MIN_BAND_DP * density;
        mSwipe = new KeyboardHideSwipe(
            ViewConfiguration.get(view.getContext()).getScaledTouchSlop(),
            THRESHOLD_DP * density,
            FLING_DP_PER_S * density,
            FLING_MIN_DISTANCE_DP * density);
    }

    @Override
    public boolean onTopEdgeTouchDown(MotionEvent event, float nullBandPx, float firstCapTopPx) {
        if (mSettling || !mCanHide.getAsBoolean())
            return false;
        if (!KeyboardHideSwipe.bandContains(event.getY(), nullBandPx, firstCapTopPx, mMinBandPx))
            return false;
        mPointerId = event.getPointerId(0);
        mSwipe.begin(event.getX(), event.getY(), event.getEventTime());
        return true;
    }

    @Override
    public void onTopEdgeTouchEvent(MotionEvent event) {
        int index = event.findPointerIndex(mPointerId);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (index < 0) return;
                mSwipe.move(event.getX(index), event.getY(index), event.getEventTime());
                mView.setTranslationY(mSwipe.dragPx());
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getActionIndex() != index) return;
                // fall through: the swiping finger lifted, whatever else is still down
            case MotionEvent.ACTION_UP:
                if (index < 0) {
                    finish(mSwipe.cancel());
                    return;
                }
                finish(mSwipe.release(event.getX(index), event.getY(index),
                    event.getEventTime()));
                break;
            case MotionEvent.ACTION_CANCEL:
                finish(mSwipe.cancel());
                break;
            default:
                break;
        }
    }

    private void finish(KeyboardHideSwipe.Outcome outcome) {
        mPointerId = MotionEvent.INVALID_POINTER_ID;
        switch (outcome) {
            case HIDE:
                settleOut();
                break;
            case SPRING_BACK:
                springBack();
                break;
            default:
                mView.setTranslationY(0f);
                break;
        }
    }

    private void settleOut() {
        float height = mView.getHeight();
        float from = mView.getTranslationY();
        if (height <= 0f || from >= height) {
            hideNow();
            return;
        }
        mSettling = true;
        mView.animate()
            .translationY(height)
            .setDuration(settleDuration(height - from, height))
            .setInterpolator(Motion.settle())
            .withEndAction(this::hideNow)
            .start();
    }

    /** The hide first, the reset after: the keys must not stand at rest for a frame on the way out. */
    private void hideNow() {
        mSettling = false;
        mHide.run();
        mView.setTranslationY(0f);
    }

    private void springBack() {
        float from = mView.getTranslationY();
        if (from <= 0f) {
            mView.setTranslationY(0f);
            return;
        }
        mView.animate()
            .translationY(0f)
            .setDuration(settleDuration(from, Math.max(from, mView.getHeight())))
            .setInterpolator(Motion.settle())
            .start();
    }

    static long settleDuration(float remainingPx, float fullPx) {
        if (fullPx <= 0f) return SETTLE_MIN_MS;
        float share = Math.max(0f, Math.min(1f, remainingPx / fullPx));
        return Math.max(SETTLE_MIN_MS, Math.round(SETTLE_MS * share));
    }
}
