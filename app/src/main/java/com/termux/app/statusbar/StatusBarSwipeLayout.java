package com.termux.app.statusbar;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

import com.termux.R;

/** Status pane gesture observer with a frozen DOWN snapshot and one-way claims. */
public final class StatusBarSwipeLayout extends FrameLayout implements NestedScrollingParent3 {

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
        default void onFullStateRequested(@NonNull TopStatusBarState priorState) { }
        default boolean isStatusGestureBlocked() { return false; }
    }

    private final int mTouchSlop;
    private final int mLongPressTimeout;
    private final NestedScrollingParentHelper mNestedParentHelper;
    @Nullable private Listener mListener;
    @Nullable private StatusBarGesturePolicy mGesture;
    private TopStatusBarState mState = TopStatusBarState.EXPANDED;
    private TopStatusBarState mNormalTarget = TopStatusBarState.EXPANDED;
    private boolean mAnotherSurfaceEngaged;
    private long mNextToken;
    private long mPostedToken;
    private boolean mFullCallbackDelivered;
    private boolean mDispatchInProgress;
    private boolean mDeferredReset;
    private final Runnable mLongPress = this::commitLongPress;

    public StatusBarSwipeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        mNestedParentHelper = new NestedScrollingParentHelper(this);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    public void setCollapsed(boolean collapsed) {
        mNormalTarget = TopStatusBarState.fromCollapsedPreference(collapsed);
        if (mState != TopStatusBarState.FULL) mState = mNormalTarget;
    }

    public void setStatusState(@NonNull TopStatusBarState state,
                               @NonNull TopStatusBarState normalTarget) {
        mState = state;
        mNormalTarget = normalTarget == TopStatusBarState.FULL
            ? TopStatusBarState.EXPANDED : normalTarget;
        requestStructuralReset();
    }

    public void setAnotherSurfaceEngaged(boolean engaged) { mAnotherSurfaceEngaged = engaged; }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mDispatchInProgress = true;
        try {
            observe(event);
            return super.dispatchTouchEvent(event);
        } finally {
            mDispatchInProgress = false;
            if (mDeferredReset) {
                mDeferredReset = false;
                clearTracking();
            }
        }
    }

    /** Child streams are never stolen; ownership is frozen at DOWN. */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return false; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING;
        }
        if (gesture == null) return false;
        switch (gesture.claim()) {
            case PENDING:
            case HORIZONTAL_SWIPE:
            case LONG_PRESS:
                return true;
            default:
                return false;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void observe(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mGesture != null) {
                    StatusBarGesturePolicy.Claim before = mGesture.claim();
                    StatusBarGesturePolicy.Claim after = mGesture.move(event.getX(), event.getY());
                    if (before == StatusBarGesturePolicy.Claim.PENDING
                        && after != StatusBarGesturePolicy.Claim.PENDING) cancelLongPressTimer();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mGesture != null) mGesture.secondPointer();
                cancelLongPressTimer();
                break;
            case MotionEvent.ACTION_UP:
                finish(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mGesture != null) mGesture.cancel();
                cancelLongPressTimer();
                requestStructuralReset();
                break;
            default:
                break;
        }
    }

    private void begin(MotionEvent event) {
        clearTracking();
        long token = ++mNextToken;
        boolean inWindowBar = isInsideView(findViewById(R.id.terminal_window_bar), event);
        boolean interactive = isInsideInteractiveChild(this, event);
        boolean nestedChildOwned = isInsideNestedScrollingChild(this, event);
        Listener listener = mListener;
        boolean blocked = mAnotherSurfaceEngaged
            || (listener != null && listener.isStatusGestureBlocked());
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, mNormalTarget, inWindowBar, interactive, nestedChildOwned,
            blocked, mTouchSlop, token);
        mGesture = new StatusBarGesturePolicy(down);
        mFullCallbackDelivered = false;
        if (mGesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            mPostedToken = token;
            postDelayed(mLongPress, mLongPressTimeout);
        }
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        cancelLongPressTimer();
        if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE) {
            boolean collapsed = gesture.horizontalDelta() < 0f;
            if (collapsed != (gesture.down().normalTarget == TopStatusBarState.COMPACT)
                && mListener != null) {
                mListener.onCollapsedStateRequested(collapsed);
            }
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            performClick();
        }
        requestStructuralReset();
    }

    private void commitLongPress() {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture == null || mPostedToken == 0L) return;
        if (gesture.timeout(mPostedToken) != StatusBarGesturePolicy.Claim.LONG_PRESS
            || mFullCallbackDelivered) return;
        mFullCallbackDelivered = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Listener listener = mListener;
        if (listener != null) listener.onFullStateRequested(gesture.down().normalTarget);
    }

    private void cancelLongPressTimer() {
        removeCallbacks(mLongPress);
        mPostedToken = 0L;
    }

    /** Reentrant FULL callbacks cannot clear the dispatch latch until dispatchTouchEvent returns. */
    private void requestStructuralReset() {
        cancelLongPressTimer();
        if (mDispatchInProgress) mDeferredReset = true;
        else clearTracking();
    }

    private void clearTracking() {
        cancelLongPressTimer();
        mGesture = null;
        mFullCallbackDelivered = false;
    }

    @Override protected void onDetachedFromWindow() {
        if (mGesture != null) mGesture.cancel();
        clearTracking();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            if (mGesture != null) mGesture.cancel();
            requestStructuralReset();
        }
    }

    private boolean isInsideInteractiveChild(ViewGroup parent, MotionEvent event) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE || !isInsideView(child, event)) continue;
            if (child.getId() == R.id.terminal_window_bar || child instanceof AppWidgetHostView
                || child.isClickable() || child.isLongClickable() || child.isFocusable()) return true;
            if (child instanceof ViewGroup
                && isInsideInteractiveChild((ViewGroup) child, event)) return true;
        }
        return false;
    }

    private boolean isInsideNestedScrollingChild(ViewGroup parent, MotionEvent event) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE || !isInsideView(child, event)) continue;
            if (ViewCompat.isNestedScrollingEnabled(child)) return true;
            if (child instanceof ViewGroup
                && isInsideNestedScrollingChild((ViewGroup) child, event)) return true;
        }
        return false;
    }

    private boolean isInsideView(@Nullable View view, MotionEvent event) {
        if (view == null || view.getVisibility() != VISIBLE || view.getWidth() <= 0
            || view.getHeight() <= 0) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return event.getRawX() >= location[0] && event.getRawX() < location[0] + view.getWidth()
            && event.getRawY() >= location[1] && event.getRawY() < location[1] + view.getHeight();
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return (axes & (ViewCompat.SCROLL_AXIS_HORIZONTAL | ViewCompat.SCROLL_AXIS_VERTICAL)) != 0;
    }

    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target,
                                                  int axes, int type) {
        mNestedParentHelper.onNestedScrollAccepted(child, target, axes, type);
        if (mGesture != null) mGesture.nestedScrollStarted();
        cancelLongPressTimer();
    }
    @Override public void onStopNestedScroll(@NonNull View target, int type) {
        mNestedParentHelper.onStopNestedScroll(target, type);
    }
    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy,
                                            @NonNull int[] consumed, int type) { }
    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed, int type,
                                         @NonNull int[] consumed) { }
    @Override public int getNestedScrollAxes() { return mNestedParentHelper.getNestedScrollAxes(); }

    @Override public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onStopNestedScroll(@NonNull View target) {
        onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy,
                                            @NonNull int[] consumed) {
        onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
    }
    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed) { }
    @Override public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
                                         int dxUnconsumed, int dyUnconsumed, int type) { }
}
