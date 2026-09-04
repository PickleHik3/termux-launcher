package com.termux.app.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Status pane gesture observer with a frozen DOWN snapshot and one-way claims. */
public final class StatusBarSwipeLayout extends FrameLayout implements NestedScrollingParent3 {

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
        default boolean isStatusGestureBlocked() { return false; }
        /**
         * A sideways drag claimed the stream and the pane wall may take it; return true to drive
         * the wall from this drag.
         */
        default boolean onWallDragBegin() { return false; }
        /** @param dxPx finger travel since the DOWN, positive to the right */
        default void onWallDrag(float dxPx) { }
        /** @param velocityPxPerSec horizontal release velocity, positive to the right */
        default void onWallDragEnd(float velocityPxPerSec) { }
        default void onWallDragCancel() { }
    }

    private final int mTouchSlop;
    private final NestedScrollingParentHelper mNestedParentHelper;
    @Nullable private Listener mListener;
    @Nullable private StatusBarGesturePolicy mGesture;
    private TopStatusBarState mState = TopStatusBarState.EXPANDED;
    private boolean mAnotherSurfaceEngaged;
    private boolean mDispatchInProgress;
    private boolean mDeferredReset;
    @Nullable private android.view.VelocityTracker mVelocityTracker;
    /** Drag hint: a grabber pill that blooms below the row on a tap of the bar's chrome. */
    private static final long HINT_DURATION_MS = 620L;
    private static final float HINT_WIDTH_DP = 30f;
    private static final float HINT_HEIGHT_DP = 3f;
    private static final float HINT_INSET_DP = 3f;
    private static final float HINT_TRAVEL_DP = 4f;
    @Nullable private ValueAnimator mHintAnimator;
    private float mHintProgress;
    @Nullable private Paint mHintPaint;
    @Nullable private RectF mHintRect;
    private int mPullHintCount;

    /** How many times the drag hint has played — the animation itself is not observable. */
    int pullHintCount() { return mPullHintCount; }

    public StatusBarSwipeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mNestedParentHelper = new NestedScrollingParentHelper(this);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    public void setCollapsed(boolean collapsed) {
        mState = TopStatusBarState.fromCollapsedPreference(collapsed);
    }

    public void setAnotherSurfaceEngaged(boolean engaged) { mAnotherSurfaceEngaged = engaged; }

    /**
     * Whether the pane wall has a place to go from here. Off, a sideways drag keeps its older
     * meaning and toggles the bar's own form; on, it moves the wall.
     */
    public void setWallAvailable(boolean available) {
        mWallAvailable = available;
        if (!available && mWallDragActive) {
            mWallDragActive = false;
            if (mListener != null) mListener.onWallDragCancel();
        }
    }

    public boolean isWallAvailable() { return mWallAvailable; }

    /**
     * A tap on the bar's own chrome answers with the grabber the bar does not wear at rest: a
     * short pill that fades in below the row, sinks a few dp and fades out — the drag saying
     * it is there. Taps that belong to a child (window chips, the stat and weather widgets, the
     * sessions chip) never reach here, so switching windows or opening a card stays silent.
     */
    private void showPullHint() {
        mPullHintCount++;
        if (mHintAnimator != null) mHintAnimator.cancel();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(HINT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            mHintProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                mHintProgress = 0f;
                mHintAnimator = null;
                invalidate();
            }
        });
        mHintAnimator = animator;
        animator.start();
    }

    private void cancelPullHint() {
        if (mHintAnimator != null) {
            mHintAnimator.cancel();
            mHintAnimator = null;
        }
        if (mHintProgress != 0f) {
            mHintProgress = 0f;
            invalidate();
        }
    }

    private void drawPullHint(@NonNull android.graphics.Canvas canvas) {
        if (mHintProgress <= 0f) return;
        // One rise-and-fall envelope over the whole animation, so the pill never snaps off.
        float envelope = (float) Math.sin(Math.PI * mHintProgress);
        if (envelope <= 0.01f) return;
        float density = getResources().getDisplayMetrics().density;
        if (mHintPaint == null) {
            mHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mHintPaint.setStyle(Paint.Style.FILL);
        }
        mHintPaint.setColor(pullHintColor());
        mHintPaint.setAlpha(Math.round(150 * envelope));
        float width = HINT_WIDTH_DP * density;
        float height = HINT_HEIGHT_DP * density;
        float travel = HINT_TRAVEL_DP * density * mHintProgress;
        float left = (getWidth() - width) / 2f;
        float top = getHeight() - height - HINT_INSET_DP * density + travel;
        if (mHintRect == null) mHintRect = new RectF();
        mHintRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(mHintRect, height / 2f, height / 2f, mHintPaint);
    }

    /** The hint's colour, from the theme like every other status widget, never a literal grey. */
    int pullHintColor() {
        return MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }

    @Override protected void dispatchDraw(@NonNull android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        drawPullHint(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mDispatchInProgress = true;
        try {
            int action = event.getActionMasked();
            observe(event);
            boolean handled = super.dispatchTouchEvent(event);
            return handled;
        } finally {
            mDispatchInProgress = false;
            if (mDeferredReset) {
                mDeferredReset = false;
                clearTracking();
            }
        }
    }

    private boolean mWallAvailable;
    private boolean mWallDragActive;

    /**
     * Child streams are frozen at DOWN with one exception: a claimed wall drag takes over — the
     * platform then delivers the children their CANCEL, exactly like a scroll container would.
     */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture == null) return false;
        // A wall drag can start on the clock or a tile, which own their own touches until the
        // sideways intent is clear; taking the stream then delivers them their CANCEL.
        return mWallDragActive
            && gesture.claim() == StatusBarGesturePolicy.Claim.WALL_HORIZONTAL;
    }

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
            case WALL_HORIZONTAL:
            case EXPAND_SWIPE:
            case COLLAPSE_SWIPE:
                return true;
            default:
                return false;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    private void observe(@NonNull MotionEvent event) {
        if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mGesture != null) {
                    StatusBarGesturePolicy.Claim before = mGesture.claim();
                    StatusBarGesturePolicy.Claim after = mGesture.move(event.getX(), event.getY());
                    if (before == StatusBarGesturePolicy.Claim.PENDING
                        && after == StatusBarGesturePolicy.Claim.WALL_HORIZONTAL) {
                        beginWallDrag();
                    }
                    if (mWallDragActive && after == StatusBarGesturePolicy.Claim.WALL_HORIZONTAL
                        && mListener != null) {
                        mListener.onWallDrag(event.getRawX() - mGesture.down().rawX);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mGesture != null) mGesture.secondPointer();
                        break;
            case MotionEvent.ACTION_UP:
                finish(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mWallDragActive && mListener != null) mListener.onWallDragCancel();
                mWallDragActive = false;
                if (mGesture != null) mGesture.cancel();
                        requestStructuralReset();
                break;
            default:
                break;
        }
    }

    private void beginWallDrag() {
        Listener listener = mListener;
        if (listener == null) return;
        mWallDragActive = listener.onWallDragBegin();
        if (mWallDragActive) performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
        else if (mGesture != null) mGesture.cancel();
    }

    private void begin(MotionEvent event) {
        clearTracking();
        boolean inWindowBar = isInsideView(findViewById(R.id.terminal_window_bar), event);
        boolean interactive = isInsideInteractiveChild(this, event);
        boolean nestedChildOwned = isInsideNestedScrollingChild(this, event);
        Listener listener = mListener;
        boolean blocked = mAnotherSurfaceEngaged
            || (listener != null && listener.isStatusGestureBlocked());
        // The vertical drag toggles the pane's own form and works along the bar's whole length.
        // In the EXPANDED form the top slot above keeps its own touch — the clock, the tiles and
        // any pinned card are targets, not bar chrome.
        boolean inTopSlot = isInsideView(findViewById(R.id.terminal_top_widget_area), event);
        boolean verticalEligible = !blocked
            && !(mState == TopStatusBarState.EXPANDED && inTopSlot);
        // The wall takes a sideways drag from anywhere on the bar except the window strip, whose
        // chips scroll first and hand over their own surplus distance. It works over the clock,
        // the tiles and the stat widgets too: a horizontal drag on one of those is not a tap.
        boolean wallEligible = mWallAvailable && !blocked && !inWindowBar && !nestedChildOwned;
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, inWindowBar, interactive, nestedChildOwned,
            blocked, verticalEligible, wallEligible, mTouchSlop);
        mGesture = new StatusBarGesturePolicy(down);
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.WALL_HORIZONTAL) {
            if (mWallDragActive && mListener != null) {
                float velocity = 0f;
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    velocity = mVelocityTracker.getXVelocity();
                }
                mListener.onWallDragEnd(velocity);
            }
            mWallDragActive = false;
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE) {
            boolean collapsed = gesture.horizontalDelta() < 0f;
            if (collapsed != (gesture.down().state == TopStatusBarState.COMPACT)
                && mListener != null) {
                mListener.onCollapsedStateRequested(collapsed);
            }
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE) {
            if (mListener != null) mListener.onCollapsedStateRequested(true);
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.EXPAND_SWIPE) {
            if (mListener != null) mListener.onCollapsedStateRequested(false);
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            // eligible() is the "this touch was the bar's own, not a child's" test — the
            // vertical drag alone keeps a chip's stream PENDING too, and a chip tap must not
            // answer with a hint.
            if (gesture.down().eligible()) showPullHint();
            performClick();
        }
        requestStructuralReset();
    }

    /**
     * A live wall drag owns its stream outright: state flips it causes must not reset the
     * tracking that is driving them; the drag resets itself at UP/CANCEL. A reentrant callback
     * cannot clear the dispatch latch until dispatchTouchEvent returns either.
     */
    private void requestStructuralReset() {
        if (mWallDragActive) return;
        if (mDispatchInProgress) mDeferredReset = true;
        else clearTracking();
    }

    private void clearTracking() {
        mGesture = null;
        mWallDragActive = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (mGesture != null) mGesture.cancel();
        cancelPullHint();
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
            // The window bar is the documented long-press surface, so the scrolling container is
            // transparent to this hit test. Its actual controls are still found recursively: a
            // session chip or the add button remains child-owned, while space between/after them
            // can arm the status gesture.
            if (child.getId() == R.id.terminal_window_bar) {
                if (child instanceof ViewGroup
                    && isInsideInteractiveChild((ViewGroup) child, event)) return true;
                continue;
            }
            if (child instanceof AppWidgetHostView || child.isClickable()
                || child.isLongClickable() || child.isFocusable()) return true;
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
