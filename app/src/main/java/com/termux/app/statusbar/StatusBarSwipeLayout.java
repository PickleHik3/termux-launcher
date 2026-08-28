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
        default void onFullStateRequested(@NonNull TopStatusBarState priorState) { }
        default boolean isStatusGestureBlocked() { return false; }
        /** A pull-down claimed the stream; return true to drive the FULL pane from this drag. */
        default boolean onFullDragBegin(@NonNull TopStatusBarState priorState) { return false; }
        /** FULL is open and a pull-up claimed the stream; return true to drag it closed. */
        default boolean onFullCloseDragBegin() { return false; }
        /** @param dragPx finger travel since the DOWN, positive downward */
        default void onFullDrag(float dragPx) { }
        /** @param velocityPxPerSec vertical release velocity, positive downward */
        default void onFullDragEnd(float velocityPxPerSec) { }
        default void onFullDragCancel() { }
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
    private boolean mFullDragActive;
    private boolean mFullPaneAvailable = true;
    @Nullable private android.view.VelocityTracker mVelocityTracker;
    private int mFullStatusRowBottomInset;
    private final Runnable mLongPress = this::commitLongPress;
    /** Pull-down hint: a grabber pill that blooms below the row on a tap of the bar's chrome. */
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

    /** How many times the pull-down hint has played — the animation itself is not observable. */
    int pullHintCount() { return mPullHintCount; }
    @Nullable private com.termux.app.GlassRimRenderer mRim;
    private float mRimRadiusPx;
    private float mRimProgress;

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
        if (state != TopStatusBarState.FULL) mFullStatusRowBottomInset = 0;
        requestStructuralReset();
    }

    /** The parent is the final FULL row-position authority; the inset comes from the existing style. */
    public void setFullStatusRowBottomInset(int bottomInsetPx) {
        int resolved = Math.max(0, bottomInsetPx);
        if (resolved == mFullStatusRowBottomInset) return;
        mFullStatusRowBottomInset = resolved;
        requestLayout();
    }

    public void setAnotherSurfaceEngaged(boolean engaged) { mAnotherSurfaceEngaged = engaged; }

    /**
     * Whether the FULL pane exists at all. Off for a terminal-only install: the pane is a home
     * surface, and leaving the pull-down armed would open an empty notification panel over a
     * terminal. Also silences the pull-down hint, which must never advertise a dead gesture.
     */
    public void setFullPaneAvailable(boolean available) {
        if (mFullPaneAvailable == available) return;
        mFullPaneAvailable = available;
        if (!available) cancelPullHint();
    }

    public boolean isFullPaneAvailable() { return mFullPaneAvailable; }

    /**
     * Glass rim over the FULL pane's outline: fades in with the expansion, shimmers while the
     * transition (or the pull-down drag) is live, and disappears entirely in the normal forms.
     */
    public void setGlassRim(float radiusPx, float fullProgress) {
        float progress = FullStatusBarGeometry.finiteUnit(fullProgress);
        if (progress == mRimProgress && radiusPx == mRimRadiusPx) return;
        mRimRadiusPx = Math.max(0f, radiusPx);
        mRimProgress = progress;
        invalidate();
    }

    /**
     * A tap on the bar's own chrome answers with the grabber the bar does not wear at rest: a
     * short pill that fades in below the row, sinks a few dp and fades out — the pull-down saying
     * it is there. Taps that belong to a child (window chips, the stat and weather widgets, the
     * sessions chip) never reach here, so switching windows or opening a card stays silent.
     */
    private void showPullHint() {
        if (!mFullPaneAvailable || mState == TopStatusBarState.FULL) return;
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
        if (mRimProgress <= 0f) return;
        if (mRim == null) {
            mRim = new com.termux.app.GlassRimRenderer(
                getResources().getDisplayMetrics().density);
        }
        float shimmerPhase = mRimProgress < 1f ? mRimProgress : -1f;
        mRim.draw(canvas, 0f, 0f, getWidth(), getHeight(), mRimRadiusPx, shimmerPhase,
            mRimProgress);
    }

    /**
     * FULL has three measured vertical bands. The top slot and status row are the chrome owners;
     * the widget pane owns exactly the half-open rectangle between their laid-out bounds. Keeping
     * this in their common parent prevents a body child from guessing either band's dp geometry.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutFullStatusRowAtMovingBottom();
        layoutFullBodyBetweenChrome();
    }

    private void layoutFullStatusRowAtMovingBottom() {
        if (mState != TopStatusBarState.FULL) return;
        View statusRow = findViewById(R.id.terminal_status_row);
        if (statusRow == null || statusRow.getVisibility() == GONE) return;
        int rowBottom = Math.max(getPaddingTop(), getHeight() - getPaddingBottom()
            - mFullStatusRowBottomInset);
        int rowTop = Math.max(getPaddingTop(), rowBottom - statusRow.getMeasuredHeight());
        int rowLeft = getPaddingLeft();
        int rowRight = Math.max(rowLeft, getWidth() - getPaddingRight());
        statusRow.layout(rowLeft, rowTop, rowRight, rowBottom);
    }

    private void layoutFullBodyBetweenChrome() {
        if (mState != TopStatusBarState.FULL) return;
        View body = findViewById(R.id.widget_pane);
        View topSlot = findViewById(R.id.terminal_top_widget_area);
        View statusRow = findViewById(R.id.terminal_status_row);
        if (body == null || topSlot == null || statusRow == null) return;

        int bodyLeft = getPaddingLeft();
        int bodyRight = Math.max(bodyLeft, getWidth() - getPaddingRight());
        int bodyTop = Math.max(getPaddingTop(), topSlot.getBottom());
        int bodyBottom = Math.max(bodyTop,
            Math.min(getHeight() - getPaddingBottom(), statusRow.getTop()));
        int bodyWidth = bodyRight - bodyLeft;
        int bodyHeight = bodyBottom - bodyTop;
        if (body.getMeasuredWidth() != bodyWidth || body.getMeasuredHeight() != bodyHeight) {
            body.measure(MeasureSpec.makeMeasureSpec(bodyWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY));
        }
        body.layout(bodyLeft, bodyTop, bodyRight, bodyBottom);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mDispatchInProgress = true;
        try {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // With the widgets pane open, the status row is display-only: window switching,
                // stat/weather popups and the sessions chip all pause until the pane closes. The
                // stream is still observed, so the pull-up works from the row too.
                mMuteChildStream = mState == TopStatusBarState.FULL
                    && isInsideView(findViewById(R.id.terminal_status_row), event);
            }
            observe(event);
            boolean handled = mMuteChildStream || super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mMuteChildStream = false;
            }
            return handled;
        } finally {
            mDispatchInProgress = false;
            if (mDeferredReset) {
                mDeferredReset = false;
                if (!mFullDragActive) clearTracking();
            }
        }
    }

    private boolean mMuteChildStream;

    /**
     * Child streams are frozen at DOWN with one exception: a claimed pull-down takes over — the
     * platform then delivers the children their CANCEL, exactly like a scroll container would.
     */
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        return gesture != null && mFullDragActive && isFullDragClaim(gesture.claim());
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
            case LONG_PRESS:
            case PULL_DOWN:
            case PULL_UP:
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
                        && after != StatusBarGesturePolicy.Claim.PENDING) cancelLongPressTimer();
                    if (before == StatusBarGesturePolicy.Claim.PENDING
                        && (after == StatusBarGesturePolicy.Claim.PULL_DOWN
                            || after == StatusBarGesturePolicy.Claim.PULL_UP)) {
                        beginFullDrag(after == StatusBarGesturePolicy.Claim.PULL_UP);
                    }
                    if (mFullDragActive && isFullDragClaim(after) && mListener != null) {
                        mListener.onFullDrag(event.getRawY() - mGesture.down().rawY);
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mGesture != null && !mFullDragActive) mGesture.secondPointer();
                cancelLongPressTimer();
                break;
            case MotionEvent.ACTION_UP:
                finish(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mFullDragActive && mListener != null) mListener.onFullDragCancel();
                mFullDragActive = false;
                if (mGesture != null) mGesture.cancel();
                cancelLongPressTimer();
                requestStructuralReset();
                break;
            default:
                break;
        }
    }

    private void beginFullDrag(boolean closing) {
        cancelLongPressTimer();
        Listener listener = mListener;
        StatusBarGesturePolicy gesture = mGesture;
        if (listener == null || gesture == null) return;
        // Guarded BEFORE the callback: beginning the drag re-enters setStatusState (engagement
        // flips the pane), whose structural reset would otherwise kill this live stream.
        mFullDragActive = true;
        boolean accepted = closing ? listener.onFullCloseDragBegin()
            : listener.onFullDragBegin(gesture.down().normalTarget);
        mFullDragActive = accepted;
        if (accepted) {
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
        } else {
            gesture.cancel();
        }
    }

    private static boolean isFullDragClaim(@NonNull StatusBarGesturePolicy.Claim claim) {
        return claim == StatusBarGesturePolicy.Claim.PULL_DOWN
            || claim == StatusBarGesturePolicy.Claim.PULL_UP;
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
        // Pull-down works along the bar's whole length. In the EXPANDED form only the bar itself
        // arms it — the top widget slot above (clock, notifications, media) keeps its own touch.
        boolean inTopSlot = isInsideView(findViewById(R.id.terminal_top_widget_area), event);
        boolean pullDownEligible = mFullPaneAvailable && !blocked && mState != TopStatusBarState.FULL
            && !(mState == TopStatusBarState.EXPANDED && inTopSlot);
        // With FULL open, an upward drag anywhere on the pane drags it closed — mirroring the
        // pull-down. Only live drag owners veto it: a provider's own nested scroll, the widget
        // edit overlay, and the picker sheet.
        View editOverlay = findViewById(R.id.widget_edit_overlay);
        boolean editActive = editOverlay != null && editOverlay.getVisibility() == VISIBLE;
        View picker = findViewById(R.id.widget_picker_sheet);
        boolean pickerOpen = picker instanceof com.termux.app.launcher.widget.WidgetPickerSheetView
            && ((com.termux.app.launcher.widget.WidgetPickerSheetView) picker).isOpen();
        boolean pullUpEligible = !blocked && mState == TopStatusBarState.FULL
            && !nestedChildOwned && !editActive && !pickerOpen;
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, mNormalTarget, inWindowBar, interactive, nestedChildOwned,
            blocked, pullDownEligible, pullUpEligible, mTouchSlop, token);
        mGesture = new StatusBarGesturePolicy(down);
        mFullCallbackDelivered = false;
        mFullDragActive = false;
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
        if (mGesture.claim() == StatusBarGesturePolicy.Claim.PENDING && down.eligible()) {
            mPostedToken = token;
            postDelayed(mLongPress, mLongPressTimeout);
        }
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        cancelLongPressTimer();
        if (gesture != null && isFullDragClaim(gesture.claim())) {
            if (mFullDragActive && mListener != null) {
                float velocity = 0f;
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    velocity = mVelocityTracker.getYVelocity();
                }
                mListener.onFullDragEnd(velocity);
            }
            mFullDragActive = false;
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.HORIZONTAL_SWIPE) {
            boolean collapsed = gesture.horizontalDelta() < 0f;
            if (collapsed != (gesture.down().normalTarget == TopStatusBarState.COMPACT)
                && mListener != null) {
                mListener.onCollapsedStateRequested(collapsed);
            }
        } else if (gesture != null
            && gesture.claim() == StatusBarGesturePolicy.Claim.COLLAPSE_SWIPE) {
            if (gesture.down().normalTarget != TopStatusBarState.COMPACT && mListener != null) {
                mListener.onCollapsedStateRequested(true);
            }
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            // eligible() is the "this touch was the bar's own, not a child's" test — pull-down
            // alone keeps a chip's stream PENDING too, and a chip tap must not answer with a hint.
            if (gesture.down().eligible()) showPullHint();
            performClick();
        }
        requestStructuralReset();
    }

    private void commitLongPress() {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture == null || mPostedToken == 0L) return;
        if (gesture.timeout(mPostedToken) != StatusBarGesturePolicy.Claim.LONG_PRESS
            || mFullCallbackDelivered || !mFullPaneAvailable) return;
        mFullCallbackDelivered = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Listener listener = mListener;
        if (listener != null) listener.onFullStateRequested(gesture.down().normalTarget);
    }

    private void cancelLongPressTimer() {
        removeCallbacks(mLongPress);
        mPostedToken = 0L;
    }

    /**
     * Reentrant FULL callbacks cannot clear the dispatch latch until dispatchTouchEvent returns.
     * A live pull-down owns its stream outright: state flips it causes (engagement → FULL) must
     * not reset the tracking that is driving them; the drag resets itself at UP/CANCEL.
     */
    private void requestStructuralReset() {
        if (mFullDragActive) return;
        cancelLongPressTimer();
        if (mDispatchInProgress) mDeferredReset = true;
        else clearTracking();
    }

    private void clearTracking() {
        cancelLongPressTimer();
        mGesture = null;
        mFullCallbackDelivered = false;
        mFullDragActive = false;
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
