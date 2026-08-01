package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

/**
 * Status-panel host that expands with a right swipe and collapses with a left swipe. Taps remain
 * available to the child widgets and deliberately have no panel-level action.
 */
public final class StatusBarSwipeLayout extends FrameLayout {

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
    }

    private final int mTouchSlop;
    @Nullable private Listener mListener;
    @Nullable private Boolean mPendingCollapsedState;
    private boolean mCollapsed;
    private boolean mTracking;
    private float mDownX;
    private float mDownY;

    public StatusBarSwipeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setCollapsed(boolean collapsed) {
        mCollapsed = collapsed;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTracking(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                updateSwipeRequest(event);
                return mPendingCollapsedState != null;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTracking();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTracking(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateSwipeRequest(event);
                return true;
            case MotionEvent.ACTION_UP:
                Boolean requestedState = mPendingCollapsedState;
                resetTracking();
                if (requestedState != null && mListener != null) {
                    mListener.onCollapsedStateRequested(requestedState);
                } else {
                    // Preserve Android's touch contract without assigning an action to empty taps.
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                resetTracking();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void startTracking(MotionEvent event) {
        mDownX = event.getX();
        mDownY = event.getY();
        mTracking = true;
        mPendingCollapsedState = null;
    }

    private void updateSwipeRequest(MotionEvent event) {
        if (!mTracking || mPendingCollapsedState != null) return;
        float deltaX = event.getX() - mDownX;
        float deltaY = event.getY() - mDownY;
        float horizontalDistance = Math.abs(deltaX);
        float verticalDistance = Math.abs(deltaY);
        if (verticalDistance > mTouchSlop && verticalDistance >= horizontalDistance) {
            mTracking = false;
            return;
        }
        if (horizontalDistance <= mTouchSlop || horizontalDistance <= verticalDistance) return;

        // Right reveals the panel; left dismisses it. Ignore a swipe that already points toward
        // the current state so horizontal window-list scrolling remains available in that direction.
        boolean requestedCollapsedState = deltaX < 0f;
        if (requestedCollapsedState == mCollapsed) {
            mTracking = false;
            return;
        }
        mPendingCollapsedState = requestedCollapsedState;
    }

    private void resetTracking() {
        mTracking = false;
        mPendingCollapsedState = null;
    }
}
