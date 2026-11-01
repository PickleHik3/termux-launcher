package com.termux.app.statusbar;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
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
import com.termux.app.place.PlaceLayout.Edge;

/** Status pane gesture observer with a frozen DOWN snapshot and one-way claims. */
public final class StatusBarSwipeLayout extends FrameLayout implements NestedScrollingParent3 {

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
        /**
         * The fold was released toward {@code collapsed} with the finger moving at
         * {@code towardOpenVelocityPxPerSec} across the bar, positive the way the bar opens. The
         * default forgets the speed; a host that lands the fold from where the finger left it
         * reads it to set the landing's time.
         */
        default void onCollapsedStateRequested(boolean collapsed, float towardOpenVelocityPxPerSec) {
            onCollapsedStateRequested(collapsed);
        }
        /**
         * A fold drag is under way: {@code towardOpenPx} is the finger's travel across the bar
         * since it went down, positive the way the bar opens. Streamed for every move once the
         * fold has claimed the stream, so the bar can stand where the finger has it.
         */
        default void onFoldDrag(float towardOpenPx) { }
        /** The fold drag's stream was cancelled under it: the bar goes back to the form it had. */
        default void onFoldDragCancelled() { }
        default boolean isStatusGestureBlocked() { return false; }
    }

    private final int mTouchSlop;
    private final NestedScrollingParentHelper mNestedParentHelper;
    @Nullable private Listener mListener;
    @Nullable private StatusBarGesturePolicy mGesture;
    private TopStatusBarState mState = TopStatusBarState.EXPANDED;
    @NonNull private Edge mEdge = Edge.TOP;
    private boolean mAnotherSurfaceEngaged;
    private boolean mDispatchInProgress;
    private boolean mDeferredReset;
    @Nullable private android.view.VelocityTracker mVelocityTracker;
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
     * A child took the live stream for a gesture of its own — a window chip lifted to be dragged
     * along the row — so the fold stands down for the rest of it. A fold already under way is
     * left alone: it claimed first.
     */
    public void yieldStreamToChild() {
        if (mGesture != null) mGesture.nestedScrollStarted();
    }

    /**
     * The edge the bar stands on. It decides which way a drag folds the bar; a live stream is
     * dropped rather than reinterpreted mid-gesture.
     */
    public void setEdge(@NonNull Edge edge) {
        if (mEdge == edge) return;
        mEdge = edge;
        if (mGesture != null) mGesture.cancel();
        cancelFoldDrag();
        requestStructuralReset();
    }

    /** Whether {@code claim} is the fold, in either direction. */
    private static boolean isFoldClaim(@NonNull StatusBarGesturePolicy.Claim claim) {
        return claim == StatusBarGesturePolicy.Claim.EXPAND_SWIPE
            || claim == StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE;
    }

    /** A fold the finger was driving lost its stream; the listener puts the bar back. */
    private void cancelFoldDrag() {
        if (!mFoldDragActive) return;
        mFoldDragActive = false;
        if (mListener != null) mListener.onFoldDragCancelled();
    }

    /** The finger's speed across the bar at release, positive the way the bar opens. */
    private float foldVelocityTowardOpen() {
        if (mVelocityTracker == null) return 0f;
        mVelocityTracker.computeCurrentVelocity(1000);
        return StatusBarGesturePolicy.acrossAxis(mEdge, mVelocityTracker.getXVelocity(),
            mVelocityTracker.getYVelocity()) * StatusBarGesturePolicy.expandSign(mEdge);
    }

    @NonNull public Edge edge() { return mEdge; }

    /**
     * Whether the bar standing on this edge may rest expanded. A bar down a side stays compact
     * regardless: this only vetoes the expand swipe, it never folds a bar already open elsewhere.
     */
    public void setExpansionAllowed(boolean allowed) { mExpansionAllowed = allowed; }

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

    /** The fold has claimed the stream and the listener has heard at least one move of it. */
    private boolean mFoldDragActive;
    private boolean mExpansionAllowed = true;

    /**
     * Child streams are frozen at DOWN with one exception: a claimed fold takes over — the
     * platform then delivers the children their CANCEL, exactly like a scroll container would.
     */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture == null) return false;
        // The fold works from anywhere on the bar, so it starts over the clock, a chip or a tile
        // as readily as over bare chrome. Taking the stream the moment it is claimed is what keeps
        // those children usable: they get their CANCEL, exactly as a scroll container's children
        // do, so the finger that folded the bar never also opens what it started on. A tap is
        // untouched — it never travels far enough to make this claim.
        return isFoldClaim(gesture.claim());
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
                    StatusBarGesturePolicy.Claim after = mGesture.move(event.getX(), event.getY());
                    if (isFoldClaim(after) && mListener != null) {
                        // The fold is the finger's while the finger is down: the bar stands
                        // where the travel across it says, the way it opens being positive.
                        mFoldDragActive = true;
                        mListener.onFoldDrag(StatusBarGesturePolicy.acrossAxis(mEdge,
                            event.getRawX() - mGesture.down().rawX,
                            event.getRawY() - mGesture.down().rawY)
                            * StatusBarGesturePolicy.expandSign(mEdge));
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
                cancelFoldDrag();
                if (mGesture != null) mGesture.cancel();
                requestStructuralReset();
                break;
            default:
                break;
        }
    }

    private void begin(MotionEvent event) {
        clearTracking();
        boolean inWindowBar = isInsideView(findViewById(R.id.terminal_window_bar), event);
        boolean interactive = isInsideInteractiveChild(this, event);
        boolean nestedChildOwned = isInsideNestedScrollingChild(this, event);
        Listener listener = mListener;
        boolean blocked = mAnotherSurfaceEngaged
            || (listener != null && listener.isStatusGestureBlocked());
        // The drag across the bar toggles its own form and works from anywhere on it, open or
        // folded, whichever edge it stands on. The one surface it yields to is a child that
        // answers drags on the fold's own axis — a card that scrolls across the bar, a hosted
        // widget whose insides are not ours to read — because there the same finger means two
        // things and the child asked first. Children that only take taps and children that scroll
        // along the bar (the window strip) keep both: the fold claims the stream and cancels
        // theirs, which is what onInterceptTouchEvent is for.
        //
        // This used to be a blanket veto over the whole widget slot while the bar was open. On a
        // top bar that slot sits above the row and the veto never showed, because the finger comes
        // off the row. On a bottom bar the slot is the band the open bar grew upward into, so it
        // is exactly where a downward fold starts — and the bar could be opened but never closed.
        boolean childOwnsFoldAxis = isInsideFoldAxisOwner(this, event);
        boolean formEligible = !blocked && !childOwnsFoldAxis && mExpansionAllowed;
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, inWindowBar, interactive, nestedChildOwned,
            blocked, formEligible, mTouchSlop, mEdge);
        mGesture = new StatusBarGesturePolicy(down);
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture != null && isFoldClaim(gesture.claim())) {
            // The release lands the fold the way the claim said, from wherever the finger has
            // the bar, at the speed it let go.
            boolean collapsed = gesture.claim() == StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE;
            mFoldDragActive = false;
            if (mListener != null) {
                mListener.onCollapsedStateRequested(collapsed, foldVelocityTowardOpen());
            }
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            // eligible() is the "this touch was the bar's own, not a child's" test — the
            // form drag alone keeps a chip's stream PENDING too, and a chip tap must not
            // toggle the fold. A bar that may not change form ignores the tap as it ignores
            // the swipe.
            if (gesture.down().eligible() && gesture.down().formEligible && mListener != null) {
                mListener.onCollapsedStateRequested(mState == TopStatusBarState.EXPANDED, 0f);
            }
            performClick();
        }
        requestStructuralReset();
    }

    /**
     * A reentrant callback cannot clear the dispatch latch until dispatchTouchEvent returns; the
     * reset waits for it.
     */
    private void requestStructuralReset() {
        if (mDispatchInProgress) mDeferredReset = true;
        else clearTracking();
    }

    private void clearTracking() {
        mGesture = null;
        mFoldDragActive = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
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
            cancelFoldDrag();
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

    /**
     * Whether a child under this DOWN answers drags on the axis the bar folds along — the only
     * thing that takes the fold away from the bar.
     *
     * <p>Scrolling is asked of the child on that axis alone, so the window strip, which scrolls
     * along the bar, is not one of them: its chips and the fold never want the same finger. A
     * hosted app widget is one whatever it contains, because what it does with a drag is the
     * provider's business and we cannot ask it.
     */
    private boolean isInsideFoldAxisOwner(ViewGroup parent, MotionEvent event) {
        boolean acrossIsVertical = !StatusBarGesturePolicy.isVertical(mEdge);
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE || !isInsideView(child, event)) continue;
            if (child instanceof AppWidgetHostView) return true;
            boolean scrollsAcross = acrossIsVertical
                ? child.canScrollVertically(-1) || child.canScrollVertically(1)
                : child.canScrollHorizontally(-1) || child.canScrollHorizontally(1);
            if (scrollsAcross) return true;
            if (child instanceof ViewGroup
                && isInsideFoldAxisOwner((ViewGroup) child, event)) return true;
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
