package com.termux.app.launcher.notifications;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wraps a notification card so a horizontal drag past a third of its width dismisses it, while a
 * vertical or short drag falls through to the card's own clickable content (the message body, reply
 * composer, action buttons) untouched.
 *
 * <p>Interception has to happen here, one level up: the message body is clickable whenever the
 * notification has a content intent, and a plain {@code OnTouchListener} on the card itself would
 * never see a drag that starts on it — the child already claimed the touch stream.
 */
public final class SwipeDismissFrame extends FrameLayout {
    private static final float DISMISS_FRACTION = 0.34f;

    private boolean mSwipeEnabled = true;
    private boolean mDragging;
    private float mDownRawX;
    private float mDownRawY;
    @Nullable private Runnable mOnDismiss;

    public SwipeDismissFrame(@NonNull Context context) {
        super(context);
    }

    public void setSwipeEnabled(boolean enabled) {
        mSwipeEnabled = enabled;
    }

    public void setOnDismiss(@Nullable Runnable onDismiss) {
        mOnDismiss = onDismiss;
    }

    /** Whether a release at {@code translationX} has travelled far enough to clear the card. */
    public static boolean shouldDismiss(float translationX, int width) {
        return width > 0 && Math.abs(translationX) > width * DISMISS_FRACTION;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mSwipeEnabled) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownRawX = ev.getRawX();
                mDownRawY = ev.getRawY();
                mDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) {
                    float dx = ev.getRawX() - mDownRawX;
                    float dy = ev.getRawY() - mDownRawY;
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        mDragging = true;
                    }
                }
                if (mDragging) return true;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mSwipeEnabled) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getRawX() - mDownRawX;
                setTranslationX(dx);
                int width = getWidth();
                if (width > 0) setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / width));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = getTranslationX();
                int width = getWidth();
                if (mDragging && shouldDismiss(dx, width)) {
                    animate().translationX(dx > 0 ? width : -width).alpha(0f)
                        .setDuration(160L)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> { if (mOnDismiss != null) mOnDismiss.run(); })
                        .start();
                } else {
                    animate().translationX(0f).alpha(1f).setDuration(180L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                }
                mDragging = false;
                return true;
            }
            default:
                return true;
        }
    }
}
