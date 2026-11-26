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

    // ---- the thin row (Status bar › Place: In the pane, #333) ------------------------------

    /** Whether the host presents as the thin row over the top pane rather than as the strip. */
    private boolean mRowPresented;
    /** Where the row stands, in this host's own coordinates; empty until placed. */
    private final android.graphics.Rect mRowRect = new android.graphics.Rect();
    /** The band drawn behind the row, 0 for none (the Display page's dark band). */
    private int mRowBandArgb;
    private final android.graphics.Paint mRowBandPaint =
        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    /** The press that started outside the row: the whole stream goes to the pane below. */
    private boolean mRowPassThrough;

    /** The screen density, read once: draw and touch never ask the resources for it. */
    private float mDensity;

    /**
     * Presents the host as the thin row ({@code row}) or as the strip. As the row the host stays
     * laid out where the strip stands, so the top edge's stack keeps its band and no page moves;
     * it draws only its status row, which it lays out itself at {@link #setRowRect}, with no
     * glass, lens or widget slot behind it, no fold and no expansion, and it hands every press
     * outside the row to the pane under it. Redraws only on a change.
     */
    public void setRowPresented(boolean row) {
        if (row == mRowPresented) return;
        mRowPresented = row;
        mRowPassThrough = false;
        requestLayout();
        invalidate();
    }

    public boolean isRowPresented() {
        return mRowPresented;
    }

    /**
     * Where the row stands at rest in this host's own coordinates and the band behind it
     * ({@code bandArgb}, 0 for none). Lays out only when the rect changes: once per change of
     * the top pane's place, never per frame. The row never changes size: a touch on its marks
     * draws its zoom in a view of its own (MarksLineView).
     */
    public void setRowRect(int left, int top, int right, int bottom, int bandArgb) {
        if (mRowBandArgb != bandArgb) {
            mRowBandArgb = bandArgb;
            mRowBandPaint.setColor(bandArgb);
            invalidate();
        }
        if (mRowRect.left == left && mRowRect.top == top && mRowRect.right == right
            && mRowRect.bottom == bottom) return;
        mRowRect.set(left, top, right, bottom);
        if (mRowPresented) requestLayout();
    }

    /** The row's rect in this host's coordinates (read, never kept). */
    @NonNull
    public android.graphics.Rect rowRect() {
        return mRowRect;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!mRowPresented || mRowRect.isEmpty()) return;
        // The row is laid out here, over the top pane, whatever the strip's writers asked of its
        // layout params: they keep writing the strip's geometry, which comes back with the strip.
        View row = findViewById(R.id.terminal_status_row);
        if (row == null || row.getParent() != this) return;
        int rowBottom = mRowRect.bottom;
        int width = mRowRect.width();
        int height = rowBottom - mRowRect.top;
        if (row.getMeasuredWidth() != width || row.getMeasuredHeight() != height) {
            row.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        row.layout(mRowRect.left, mRowRect.top, mRowRect.right, rowBottom);
    }

    private float density() {
        if (mDensity == 0f) mDensity = getResources().getDisplayMetrics().density;
        return mDensity;
    }

    @Override
    protected void dispatchDraw(@NonNull android.graphics.Canvas canvas) {
        if (mRowPresented && !mRowRect.isEmpty()) {
            if (mRowBandArgb != 0) canvas.drawRect(mRowRect, mRowBandPaint);
            if (hairline(mHairlineRect)) {
                // The Marks line's ink, at its low alpha: it repaints with the row and follows
                // the ink the line solved on its band.
                View marks = findViewById(R.id.terminal_status_marks_row);
                int ink = marks instanceof MarksLineView ? ((MarksLineView) marks).hairlineInk()
                    : 0xFFFFFFFF;
                mHairlinePaint.setColor(PaneRowPolicy.hairlineArgb(ink));
                canvas.drawRect(mHairlineRect, mHairlinePaint);
            }
        }
        super.dispatchDraw(canvas);
    }

    private final android.graphics.Rect mHairlineRect = new android.graphics.Rect();
    private final android.graphics.Paint mHairlinePaint = new android.graphics.Paint();

    /**
     * Whether the hairline stands under the thin row, and where: only while the host presents as
     * the row in the pane, and not over the Display page's dark band, which already separates it.
     * Written into {@code out}.
     */
    boolean hairline(@NonNull android.graphics.Rect out) {
        if (!mRowPresented || mRowRect.isEmpty() || mRowBandArgb != 0) return false;
        PaneRowPolicy.hairlineRect(mRowRect.left, mRowRect.top, mRowRect.right, mRowRect.bottom,
            out);
        return true;
    }

    @Override
    protected boolean drawChild(@NonNull android.graphics.Canvas canvas, @NonNull View child,
                                long drawingTime) {
        // As the row only the status row draws: the strip's glass, lens and widget slot stay laid
        // out for the strip's return and cost nothing meanwhile.
        // Unplaced (no pane laid out yet), it draws nothing rather than the strip's row.
        if (mRowPresented && (mRowRect.isEmpty() || child.getId() != R.id.terminal_status_row))
            return false;
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mRowPresented) return dispatchRowTouch(event);
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

    /**
     * A press on the row (PaneRowPolicy.hitsRow) goes to its marks and readings at once, with no
     * fold to claim it; the host owns the whole stream so the release always reaches it. A press
     * that starts anywhere else is not the host's (false), so the pane under it gets the whole
     * stream and the terminal's first row keeps its own taps.
     */
    private boolean dispatchRowTouch(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mRowPassThrough = mRowRect.isEmpty() || !PaneRowPolicy.hitsRow(event.getX(),
                event.getY(), mRowRect.left, mRowRect.top, mRowRect.right, density());
        }
        if (mRowPassThrough) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                mRowPassThrough = false;
            return false;
        }
        super.dispatchTouchEvent(event);
        return true;
    }

    /** The window's stream that the row owns, from its down on the row to the lift. */
    private boolean mWindowRowStream;
    /** This host's origin in the window, read once per down the row takes. */
    private final int[] mWindowOrigin = new int[2];

    /**
     * Offers one window touch ({@code event} in window coordinates, as the activity gets it) to the
     * thin row before any view sees it, and owns the whole stream when the down lands on the row
     * (PaneRowPolicy.hitsRow). The row lies over the top pane, outside this host's own bounds and
     * under the pane in draw order, so the views' own dispatch would hand those presses to the pane
     * and never to this host. Anything else is not the row's: the terminal's first row of text and
     * the pane's clearance keep their taps. Allocates nothing; the event is shifted into this
     * host's coordinates for the row and shifted back.
     *
     * @return whether the row owns this event and the views are to see nothing of it
     */
    public boolean dispatchWindowRowTouch(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mWindowRowStream = false;
            if (!mRowPresented || mRowRect.isEmpty() || !isShown()) return false;
            getLocationInWindow(mWindowOrigin);
            if (!PaneRowPolicy.hitsRow(event.getX() - mWindowOrigin[0],
                event.getY() - mWindowOrigin[1], mRowRect.left, mRowRect.top, mRowRect.right,
                density())) return false;
            mWindowRowStream = true;
        }
        if (!mWindowRowStream) return false;
        float dx = -mWindowOrigin[0];
        float dy = -mWindowOrigin[1];
        event.offsetLocation(dx, dy);
        try {
            dispatchRowTouch(event);
        } finally {
            event.offsetLocation(-dx, -dy);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                mWindowRowStream = false;
        }
        return true;
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
        if (gesture == null || mRowPresented) return false;
        // The fold works from anywhere on the bar, so it starts over the clock, a chip or a tile
        // as readily as over bare chrome. Taking the stream the moment it is claimed is what keeps
        // those children usable: they get their CANCEL, exactly as a scroll container's children
        // do, so the finger that folded the bar never also opens what it started on. A tap is
        // untouched — it never travels far enough to make this claim.
        return isFoldClaim(gesture.claim());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // As the row there is no fold: a press between its marks falls through to the pane.
        if (mRowPresented) return false;
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
                    // No touch gesture folds the bar (the status bar's long-press menu does):
                    // a move past the slop hands the stream to whoever else would take it.
                    if (mGesture.claim() == StatusBarGesturePolicy.Claim.PENDING
                        && movedPastSlop(event)) {
                        mGesture.nestedScrollStarted();
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
        boolean inWindowBar = isInsideView(findViewById(R.id.terminal_window_bar), event)
            || isInsideView(findViewById(R.id.terminal_home_float_bar), event);
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

    private boolean movedPastSlop(MotionEvent event) {
        StatusBarGesturePolicy.Down d = mGesture.down();
        float dx = event.getX() - d.localX;
        float dy = event.getY() - d.localY;
        return dx * dx + dy * dy > (float) mTouchSlop * mTouchSlop;
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
            // A tap on the bar does not fold it: only the status bar menu folds it.
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
            if (child.getId() == R.id.terminal_window_bar
                || child.getId() == R.id.terminal_home_float_bar) {
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
