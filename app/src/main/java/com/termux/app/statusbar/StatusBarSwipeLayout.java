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
import com.termux.app.place.PlaceLayout.Edge;

/** Status pane gesture observer with a frozen DOWN snapshot and one-way claims. */
public final class StatusBarSwipeLayout extends FrameLayout implements NestedScrollingParent3 {

    public interface Listener {
        void onCollapsedStateRequested(boolean collapsed);
        default boolean isStatusGestureBlocked() { return false; }
        /**
         * A drag along the bar claimed the stream and the pane wall may take it; return true to
         * drive the wall from this drag.
         */
        default boolean onWallDragBegin() { return false; }
        /**
         * @param alongPx finger travel along the bar since the DOWN — to the right on a bar that
         *                stands along the top or the bottom, downward on one down a side.
         */
        default void onWallDrag(float alongPx) { }
        /** @param velocityPxPerSec release velocity along the bar, in that same direction */
        default void onWallDragEnd(float velocityPxPerSec) { }
        default void onWallDragCancel() { }
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
    /**
     * Drag hint: two glowing chevrons that bloom at the row's bottom edge on a tap of the bar's
     * chrome, pointing the way the bar can go from here - down while it is folded, up while it
     * is open - and drifting that way as they fade.
     */
    private static final long HINT_DURATION_MS = 680L;
    private static final float HINT_CHEVRON_WIDTH_DP = 14f;
    private static final float HINT_CHEVRON_HEIGHT_DP = 5f;
    private static final float HINT_CHEVRON_GAP_DP = 4f;
    private static final float HINT_INSET_DP = 4f;
    private static final float HINT_TRAVEL_DP = 6f;
    @Nullable private ValueAnimator mHintAnimator;
    private float mHintProgress;
    @Nullable private Paint mHintPaint;
    @Nullable private android.graphics.Path mHintPath;
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
     * The edge the bar stands on. It decides which way a drag pages the wall and which way it
     * folds the bar; a live stream is dropped rather than reinterpreted mid-gesture.
     */
    public void setEdge(@NonNull Edge edge) {
        if (mEdge == edge) return;
        mEdge = edge;
        if (mGesture != null) mGesture.cancel();
        if (mWallDragActive && mListener != null) mListener.onWallDragCancel();
        mWallDragActive = false;
        requestStructuralReset();
    }

    @NonNull public Edge edge() { return mEdge; }

    /** Whether the pane wall has a place to go from here; off, a sideways drag means nothing. */
    public void setWallAvailable(boolean available) {
        mWallAvailable = available;
        if (!available && mWallDragActive) {
            mWallDragActive = false;
            if (mListener != null) mListener.onWallDragCancel();
        }
    }

    public boolean isWallAvailable() { return mWallAvailable; }

    /**
     * The wall was moved from elsewhere mid-drag (a tile tap, {@code wall.go}, Home). The drag
     * is over: the rest of this finger's stream is ignored rather than fed to a wall that has
     * stopped listening, and the next touch starts clean.
     */
    public void cancelWallDrag() {
        if (!mWallDragActive) return;
        mWallDragActive = false;
        if (mGesture != null) mGesture.cancel();
        requestStructuralReset();
    }

    /**
     * A tap on the bar's own chrome answers with the direction the bar does not show at rest:
     * two chevrons that glow in at the row's bottom edge, drift the way a swipe would take the
     * bar and fade out. Taps that belong to a child (window chips, the stat and weather widgets,
     * the sessions chip) never reach here, so switching windows or opening a card stays silent.
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
        // One rise-and-fall envelope over the whole animation, so the chevrons never snap off.
        float envelope = (float) Math.sin(Math.PI * mHintProgress);
        if (envelope <= 0.01f) return;
        float density = getResources().getDisplayMetrics().density;
        if (mHintPaint == null) {
            mHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mHintPaint.setStyle(Paint.Style.STROKE);
            mHintPaint.setStrokeCap(Paint.Cap.ROUND);
            mHintPaint.setStrokeJoin(Paint.Join.ROUND);
        }
        if (mHintPath == null) mHintPath = new android.graphics.Path();
        // Folded, the bar opens by a pull down; open, it folds by a push up. The chevrons point
        // that way and travel that way.
        boolean opening = mState.toCollapsedPreference();
        float expand = StatusBarGesturePolicy.expandSign(mEdge);
        // Folded, the bar opens away from its edge; open, it folds back towards it.
        boolean down = opening == (expand > 0f);
        float direction = down ? 1f : -1f;
        float width = HINT_CHEVRON_WIDTH_DP * density;
        float height = HINT_CHEVRON_HEIGHT_DP * density;
        float gap = HINT_CHEVRON_GAP_DP * density;
        float travel = HINT_TRAVEL_DP * density * mHintProgress * direction;
        boolean vertical = StatusBarGesturePolicy.isVertical(mEdge);
        // The chevrons bloom at the bar's inner edge — the one facing the terminal — and point
        // the way a drag would take it, whichever edge the bar stands on.
        float cx = vertical ? getHeight() / 2f : getWidth() / 2f;
        float span = vertical ? getWidth() : getHeight();
        float base = expand > 0f
            ? span - HINT_INSET_DP * density - height - gap - height + travel
            : HINT_INSET_DP * density + travel;
        int chevronLayer = vertical ? canvas.save() : -1;
        if (vertical) {
            // One rotation, so the chevron path below stays the single description of the shape.
            canvas.rotate(90f, 0f, 0f);
            canvas.translate(0f, -getWidth());
        }
        int color = pullHintColor();
        for (int pass = 0; pass < 2; pass++) {
            // A wide, faint stroke under a thin bright one is the glow.
            boolean glow = pass == 0;
            mHintPaint.setStrokeWidth((glow ? 6f : 1.75f) * density);
            mHintPaint.setColor(color);
            mHintPaint.setAlpha(Math.round((glow ? 70 : 230) * envelope));
            for (int i = 0; i < 2; i++) {
                float top = base + i * (height + gap);
                // The tip leads: pointing down it is at the bottom, pointing up at the top.
                float tipY = down ? top + height : top;
                float tailY = down ? top : top + height;
                mHintPath.reset();
                mHintPath.moveTo(cx - width / 2f, tailY);
                mHintPath.lineTo(cx, tipY);
                mHintPath.lineTo(cx + width / 2f, tailY);
                canvas.drawPath(mHintPath, mHintPaint);
            }
        }
        if (chevronLayer >= 0) canvas.restoreToCount(chevronLayer);
    }

    /** The hint's colour: the accent, from the theme like every other status widget. */
    int pullHintColor() {
        return MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(getContext(), R.color.termux_primary));
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
        // intent along the bar is clear; taking the stream then delivers them their CANCEL.
        return mWallDragActive
            && gesture.claim() == StatusBarGesturePolicy.Claim.WALL_PAGING;
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
            case WALL_PAGING:
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
                        && after == StatusBarGesturePolicy.Claim.WALL_PAGING) {
                        beginWallDrag();
                    }
                    if (mWallDragActive && after == StatusBarGesturePolicy.Claim.WALL_PAGING
                        && mListener != null) {
                        mListener.onWallDrag(StatusBarGesturePolicy.alongAxis(mEdge,
                            event.getRawX() - mGesture.down().rawX,
                            event.getRawY() - mGesture.down().rawY));
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
        // The drag across the bar toggles its own form and works along the bar's whole length.
        // In the EXPANDED form the top slot above keeps its own touch — the clock, the tiles and
        // any pinned card are targets, not bar chrome.
        boolean inTopSlot = isInsideView(findViewById(R.id.terminal_top_widget_area), event);
        boolean formEligible = !blocked
            && !(mState == TopStatusBarState.EXPANDED && inTopSlot);
        // The wall takes a drag along the bar from anywhere on it except the window strip, whose
        // chips scroll first and hand over their own surplus distance. It works over the clock,
        // the tiles and the stat widgets too: a drag along the bar on one of those is not a tap.
        boolean wallEligible = mWallAvailable && !blocked && !inWindowBar && !nestedChildOwned;
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, inWindowBar, interactive, nestedChildOwned,
            blocked, formEligible, wallEligible, mTouchSlop, mEdge);
        mGesture = new StatusBarGesturePolicy(down);
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.WALL_PAGING) {
            if (mWallDragActive && mListener != null) {
                float velocity = 0f;
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    velocity = StatusBarGesturePolicy.alongAxis(mEdge,
                        mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
                }
                mListener.onWallDragEnd(velocity);
            }
            mWallDragActive = false;
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE) {
            if (mListener != null) mListener.onCollapsedStateRequested(true);
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.EXPAND_SWIPE) {
            if (mListener != null) mListener.onCollapsedStateRequested(false);
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            // eligible() is the "this touch was the bar's own, not a child's" test — the
            // form drag alone keeps a chip's stream PENDING too, and a chip tap must not
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
