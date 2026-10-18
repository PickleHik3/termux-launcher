package com.termux.app.statusbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
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
import com.termux.app.place.HomeChromePolicy;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarFormPolicy.Form;
import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Status pane gesture observer with a frozen DOWN snapshot and one-way claims, and the bar's
 * capsule.
 *
 * <p>The capsule ({@link Form#CAPSULE}) is a state of this view, not a window of its own: the bar
 * keeps the compact band's room, draws its glass in to a 36 dp pill at the centre of its edge
 * and paints the capsule's glyph, clock and dots inside it, its own content (lens, chips, pins)
 * fading out on the way. Compact and expanded stay the host's to land (the height, the terminal's
 * resize); the stretch between the capsule and compact is this view's alone, so folding to the
 * capsule never resizes a terminal. The idle fold ({@link StatusBarFormPolicy#idle}) runs here
 * too, since this is where every touch on the bar passes.
 */
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
        /**
         * The bar's form changed, the capsule included (which the two-form callbacks above never
         * see: the capsule rests compact for the host).
         */
        default void onFormChanged(@NonNull StatusBarFormPolicy.Form form) { }
        /**
         * A sheet hangs off the bar (a status card is seen without asking): the idle fold waits
         * while it is open.
         */
        default boolean isSheetOpen() { return false; }
    }

    // ---------------------------------------------------------------- the capsule's state

    /** The form the bar rests in, or is landing in. */
    @NonNull private Form mForm = Form.EXPANDED;
    /** A row can be a capsule; a column cannot. */
    private boolean mCapsuleAllowed = true;
    /** How far into the capsule the bar is drawn: 0 its compact or expanded self, 1 the capsule. */
    private float mCapsuleProgress;
    @Nullable private ValueAnimator mCapsuleAnimator;
    /** The compact-to-expanded travel the host lands; derived from the edge until it says. */
    private int mFoldTravelPx = -1;
    /** A drag across the bar is moving its form (any stop), from {@link #mDragStartPx}. */
    private boolean mFormDragActive;
    @NonNull private Form mDragStartForm = Form.COMPACT;
    private float mDragStartPx;
    private float mDragPositionPx;
    private boolean mMinimal;
    @NonNull private final StatusBarCapsuleDrawing mCapsule;
    private final RectF mCapsuleRest = new RectF();
    private final RectF mCapsuleNow = new RectF();
    private final android.graphics.Path mCapsuleClip = new android.graphics.Path();

    // ---------------------------------------------------------------- the idle fold's inputs

    private long mLastTouchMs;
    private long mLastNotificationMs;
    private long mLastWindowChangeMs;
    private boolean mFingerDown;
    @Nullable private StatusCardHost mCardHost;
    private final Runnable mIdleCheck = this::checkIdle;
    private final Runnable mClockTick = this::tickClock;
    private final TopPaneFeed.Observer mFeedObserver = this::onFeedChanged;
    @NonNull private Set<String> mPinnedKeys = Collections.emptySet();
    private long mPinnedLatestMs;
    private boolean mPinnedKnown;
    private int mPinnedCount;
    @Nullable private StatusBarWindowColumn mWindowColumn;
    /** The bar's glass layer: whether it has a drawable says glass or the plain panel. */
    @Nullable private View mGlassBackground;
    @Nullable private StatusBarLensView mLens;
    /** Home or the window on screen, as the last content pass saw it; a change is a window change. */
    @Nullable private String mWindowKey;
    @Nullable private String mWindowSpoken;

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
     * Drag hint: one small chevron that fades in at the row's inner edge on a tap of the bar's
     * chrome, pointing the way the bar can go from here - down while it is folded, up while it
     * is open - and drifting that way as it fades. A single thin stroke, no glow: the bar is
     * quiet chrome and the hint should read as a whisper, not a widget.
     */
    private static final long HINT_DURATION_MS = 680L;
    private static final float HINT_CHEVRON_WIDTH_DP = 10f;
    private static final float HINT_CHEVRON_HEIGHT_DP = 4f;
    private static final float HINT_STROKE_DP = 1.5f;
    /**
     * Distance from the bar's inner edge to where the chevron starts. Generous, so that the whole
     * glyph sits inside the compact bar and its drift ends before the edge: it must never look as
     * if it is sinking into the border.
     */
    private static final float HINT_INSET_DP = 10f;
    private static final float HINT_TRAVEL_DP = 5f;
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
        mCapsule = new StatusBarCapsuleDrawing(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        // The capsule reads what it shows from the bar's own children: the window column is
        // handed the windows on every edge, and the lens knows the place on screen.
        View column = findViewById(R.id.terminal_status_window_column);
        if (column instanceof StatusBarWindowColumn) {
            mWindowColumn = (StatusBarWindowColumn) column;
            mWindowColumn.setWindowsObserver(this::refreshCapsuleContent);
        }
        mGlassBackground = findViewById(R.id.terminal_window_bar_background);
        View lens = findViewById(R.id.terminal_status_lens);
        if (lens instanceof StatusBarLensView) {
            mLens = (StatusBarLensView) lens;
            mLens.setCurrentObserver(this::refreshCapsuleContent);
        }
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    /**
     * The host's two-form memory: folded or open. Open is always {@link Form#EXPANDED}; folded
     * keeps a capsule a capsule, since the capsule rests compact for the host.
     */
    public void setCollapsed(boolean collapsed) {
        Form next = !collapsed ? Form.EXPANDED
            : mForm == Form.CAPSULE ? Form.CAPSULE : Form.COMPACT;
        mState = TopStatusBarState.fromCollapsedPreference(collapsed);
        if (next == mForm) return;
        setFormInternal(next, isAttachedToWindow() && isLaidOut(), 0f);
    }

    /** The form the bar rests in, or is landing in. */
    @NonNull public Form form() { return mForm; }

    /**
     * The compact-to-expanded travel the host lands, in px ({@code expanded - compact}), so a
     * slow swipe's nearest form is read on the host's own heights. Until it is set the edge's
     * Docked heights stand in, a few dp off under Floating.
     */
    public void setFoldTravelPx(int foldTravelPx) { mFoldTravelPx = foldTravelPx; }

    /**
     * Minimal mode turned on or off: on, the bar folds to the capsule, its default form there;
     * off, a capsule unfolds to compact. Pass false on Home, which has no Minimal mode.
     */
    public void setMinimalMode(boolean minimal) {
        if (mMinimal == minimal) return;
        mMinimal = minimal;
        Form next = StatusBarFormPolicy.formOnMinimalChange(minimal, mForm, mCapsuleAllowed);
        if (next == mForm) return;
        if (next == Form.CAPSULE) foldToCapsule(false);
        else setFormInternal(next, false, 0f);
    }

    /** The card host whose cards hang off this bar; an open card holds the bar's form. */
    void setCardHost(@Nullable StatusCardHost host) { mCardHost = host; }

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
        cancelFormDrag();
        requestStructuralReset();
        mCapsuleAllowed = StatusBarFormPolicy.capsuleAllowed(edge);
        // A column has no capsule: one standing as a capsule comes back compact on its new edge.
        if (!mCapsuleAllowed && mForm == Form.CAPSULE) setFormInternal(Form.COMPACT, false, 0f);
        else scheduleIdle();
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

    /**
     * A form drag lost its stream: the capsule's morph goes back to the form the bar had, and the
     * host puts back the height it was dragging, if it was.
     */
    private void cancelFormDrag() {
        if (mFormDragActive) {
            mFormDragActive = false;
            animateCapsuleTo(mForm == Form.CAPSULE ? 1f : 0f, 0f);
        }
        cancelFoldDrag();
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

    /**
     * A tap on the bar's own chrome answers with the direction the bar does not show at rest:
     * one chevron that fades in at the row's inner edge, drifts the way a swipe would take the
     * bar and fades out. Taps that belong to a child (window chips, the stat and weather widgets,
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
        // One rise-and-fall envelope over the whole animation, so the chevron never snaps off.
        float envelope = (float) Math.sin(Math.PI * mHintProgress);
        if (envelope <= 0.01f) return;
        float density = getResources().getDisplayMetrics().density;
        if (mHintPaint == null) {
            mHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mHintPaint.setStyle(Paint.Style.STROKE);
            mHintPaint.setStrokeCap(Paint.Cap.ROUND);
            mHintPaint.setStrokeJoin(Paint.Join.ROUND);
            mHintPaint.setStrokeWidth(HINT_STROKE_DP * density);
        }
        if (mHintPath == null) mHintPath = new android.graphics.Path();
        // Folded, the bar opens by a pull down; open, it folds by a push up. The chevron points
        // that way and travels that way.
        boolean opening = mState.toCollapsedPreference();
        float expand = StatusBarGesturePolicy.expandSign(mEdge);
        // Folded, the bar opens away from its edge; open, it folds back towards it.
        boolean down = opening == (expand > 0f);
        float direction = down ? 1f : -1f;
        float width = HINT_CHEVRON_WIDTH_DP * density;
        float height = HINT_CHEVRON_HEIGHT_DP * density;
        float travel = HINT_TRAVEL_DP * density * mHintProgress * direction;
        boolean vertical = StatusBarGesturePolicy.isVertical(mEdge);
        // The chevron sits at the bar's inner edge — the one facing the terminal — and points
        // the way a drag would take it, whichever edge the bar stands on.
        float cx = vertical ? getHeight() / 2f : getWidth() / 2f;
        float span = vertical ? getWidth() : getHeight();
        float top = expand > 0f
            ? span - HINT_INSET_DP * density - height + travel
            : HINT_INSET_DP * density + travel;
        int chevronLayer = vertical ? canvas.save() : -1;
        if (vertical) {
            // One rotation, so the chevron path below stays the single description of the shape:
            // (x, y) drawn here lands at (y, height - x) on screen, which turns the row's
            // "along the width, across the height" into the column's own two axes.
            canvas.rotate(-90f, 0f, 0f);
            canvas.translate(-getHeight(), 0f);
        }
        mHintPaint.setColor(pullHintColor());
        mHintPaint.setAlpha(Math.round(200 * envelope));
        // The tip leads: pointing down it is at the bottom, pointing up at the top.
        float tipY = down ? top + height : top;
        float tailY = down ? top : top + height;
        mHintPath.reset();
        mHintPath.moveTo(cx - width / 2f, tailY);
        mHintPath.lineTo(cx, tipY);
        mHintPath.lineTo(cx + width / 2f, tailY);
        canvas.drawPath(mHintPath, mHintPaint);
        if (chevronLayer >= 0) canvas.restoreToCount(chevronLayer);
    }

    /** The hint's colour: the accent, from the theme like every other status widget. */
    int pullHintColor() {
        return MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(getContext(), R.color.termux_primary));
    }

    @Override protected void dispatchDraw(@NonNull android.graphics.Canvas canvas) {
        float progress = mCapsuleProgress;
        if (progress <= 0f) {
            super.dispatchDraw(canvas);
            drawPullHint(canvas);
            return;
        }
        // The bar's glass draws in from its whole band to the capsule's pill, centred on its
        // edge: the band's rectangle walks to the capsule's and the corners round as it goes.
        // The children are the glass and the content alike, so one clip shapes them all.
        int width = getWidth();
        int height = getHeight();
        mCapsule.restingBounds(width, height, mCapsuleRest);
        float radius = mCapsule.radiusPx(mCapsuleRest) * progress;
        mCapsuleNow.set(lerp(0f, mCapsuleRest.left, progress),
            lerp(0f, mCapsuleRest.top, progress), lerp(width, mCapsuleRest.right, progress),
            lerp(height, mCapsuleRest.bottom, progress));
        int saved = canvas.save();
        mCapsuleClip.reset();
        mCapsuleClip.addRoundRect(mCapsuleNow, radius, radius, android.graphics.Path.Direction.CW);
        canvas.clipPath(mCapsuleClip);
        super.dispatchDraw(canvas);
        syncCapsuleInk();
        mCapsule.draw(canvas, mCapsuleNow, radius,
            StatusBarFoldMotion.capsuleContentAlpha(progress));
        canvas.restoreToCount(saved);
    }

    /**
     * Folding into the capsule, the bar's own content (lens, chips, pins, widgets) fades out
     * before the capsule's arrives ({@link StatusBarFoldMotion#barContentAlpha}); the glass
     * layers stay whole, since they become the capsule's glass.
     */
    @Override protected boolean drawChild(@NonNull android.graphics.Canvas canvas,
                                          @NonNull View child, long drawingTime) {
        float progress = mCapsuleProgress;
        if (progress <= 0f || isGlassLayer(child)) {
            return super.drawChild(canvas, child, drawingTime);
        }
        float alpha = StatusBarFoldMotion.barContentAlpha(progress);
        if (alpha <= 0f) return false;
        int layer = canvas.saveLayerAlpha(0f, 0f, getWidth(), getHeight(),
            Math.round(255 * alpha));
        boolean more = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(layer);
        return more;
    }

    /**
     * The capsule's ink follows the chrome's polarity, whether the bar is glass, and the band the
     * window chips measured ({@link StatusBarFormPolicy#capsuleInk}); a cheap compare per frame.
     */
    private void syncCapsuleInk() {
        boolean glass = mGlassBackground != null && mGlassBackground.getVisibility() == VISIBLE
            && mGlassBackground.getBackground() != null;
        com.termux.app.terminal.WindowChipInk.Palette chips =
            mWindowColumn != null ? mWindowColumn.glassPalette() : null;
        mCapsule.syncInk(glass, chips != null ? chips.band : 0);
    }

    private static boolean isGlassLayer(@NonNull View child) {
        int id = child.getId();
        return id == R.id.terminal_window_bar_wallpaper_backdrop
            || id == R.id.terminal_window_bar_blur
            || id == R.id.terminal_window_bar_background;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mDispatchInProgress = true;
        try {
            // The capsule's folded content is drawn away, so none of it takes a touch: the
            // stream is the capsule's alone (a tap opens it, a swipe unfolds it). Read before
            // the event lands, so the release that opens the capsule is still the capsule's.
            boolean capsule = mForm == Form.CAPSULE;
            observe(event);
            if (capsule) return true;
            return super.dispatchTouchEvent(event);
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

    /** A click on the capsule (a tap, or an accessibility click) opens it to compact. */
    @Override public boolean performClick() {
        boolean handled = super.performClick();
        if (mForm != Form.CAPSULE) return handled;
        openCapsule();
        return true;
    }

    private void observe(@NonNull MotionEvent event) {
        if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Every touch on the bar, a chip's or a card's included, is an interaction: the
                // idle fold waits for the finger to lift and then a whole quiet stretch.
                mFingerDown = true;
                mLastTouchMs = SystemClock.uptimeMillis();
                begin(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mGesture != null) {
                    StatusBarGesturePolicy.Claim after = mGesture.move(event.getX(), event.getY());
                    if (isFoldClaim(after)) {
                        // The fold is the finger's while the finger is down: the bar stands
                        // where the travel across it says, the way it opens being positive.
                        dragForm(StatusBarGesturePolicy.acrossAxis(mEdge,
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
                liftFinger();
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelFormDrag();
                if (mGesture != null) mGesture.cancel();
                requestStructuralReset();
                liftFinger();
                break;
            default:
                break;
        }
    }

    private void liftFinger() {
        mFingerDown = false;
        mLastTouchMs = SystemClock.uptimeMillis();
        scheduleIdle();
    }

    /**
     * One move of a form drag. The finger moves the bar along the three stops' one axis
     * ({@link StatusBarFoldMotion#stopPx}): between the capsule and compact the capsule's morph
     * follows it here, past compact the host's height does ({@link Listener#onFoldDrag}), told
     * only once the drag has reached the height's stretch, so a drag between the capsule and
     * compact never takes the host's resize lease.
     */
    private void dragForm(float towardOpenPx) {
        int capsuleTravel = capsuleTravelPx();
        int foldTravel = foldTravelPx();
        if (!mFormDragActive) {
            // A landing still under way is taken over from where it has the capsule.
            mFormDragActive = true;
            cancelCapsuleAnimator();
            mDragStartForm = mForm;
            mDragStartPx = mForm == Form.EXPANDED
                ? StatusBarFoldMotion.stopPx(Form.EXPANDED, capsuleTravel, foldTravel)
                : (1f - mCapsuleProgress) * capsuleTravel;
        }
        mDragPositionPx = StatusBarFoldMotion.positionForDrag(mDragStartPx, towardOpenPx,
            StatusBarFoldMotion.stopPx(lowestForm(), capsuleTravel, foldTravel),
            StatusBarFoldMotion.stopPx(highestForm(), capsuleTravel, foldTravel));
        setCapsuleProgress(StatusBarFoldMotion.capsuleProgress(mDragPositionPx, capsuleTravel));
        float fold = StatusBarFoldMotion.foldTowardOpenPx(mDragStartPx, mDragPositionPx,
            capsuleTravel);
        if (mListener != null && (mFoldDragActive || fold != 0f)) {
            mFoldDragActive = true;
            mListener.onFoldDrag(fold);
        }
    }

    /**
     * The release of a form drag lands where {@link StatusBarFormPolicy#release} says: the
     * nearest form for a slow swipe, one step for a quick one. Compact and expanded are the
     * host's to land, as they always were; the capsule's morph is landed here. Whatever the host
     * was dragging is always landed, so its lease is returned.
     */
    private void landForm(float velocityTowardOpenPxPerSec) {
        float velocity = Float.isNaN(velocityTowardOpenPxPerSec) ? 0f : velocityTowardOpenPxPerSec;
        boolean heightDragged = mFoldDragActive;
        mFoldDragActive = false;
        mFormDragActive = false;
        Form lowest = lowestForm();
        Form highest = highestForm();
        Form nearest = StatusBarFoldMotion.nearest(mDragPositionPx, capsuleTravelPx(),
            foldTravelPx(), lowest, highest);
        float density = getResources().getDisplayMetrics().density;
        Form target = StatusBarFormPolicy.release(mDragStartForm, nearest,
            velocity / Math.max(0.1f, density), lowest, highest);
        if (target == Form.EXPANDED) {
            if (mForm == Form.CAPSULE) setFormInternal(Form.COMPACT, true, velocity);
            else animateCapsuleTo(0f, velocity);
            if (mListener != null) mListener.onCollapsedStateRequested(false, velocity);
            return;
        }
        if (mListener != null && (heightDragged || mForm == Form.EXPANDED)) {
            mListener.onCollapsedStateRequested(true, velocity);
        }
        // The host may hold the bar open (an editor does): then there is no capsule to land.
        if (mForm == Form.EXPANDED) {
            animateCapsuleTo(0f, 0f);
            return;
        }
        setFormInternal(target, true, velocity);
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
        //
        // The capsule has no children to yield to: its content is drawn away and takes no touch.
        boolean capsule = mForm == Form.CAPSULE;
        boolean childOwnsFoldAxis = !capsule && isInsideFoldAxisOwner(this, event);
        boolean formEligible = !blocked && !childOwnsFoldAxis
            && (mExpansionAllowed || mCapsuleAllowed);
        StatusBarGesturePolicy.Down down = new StatusBarGesturePolicy.Down(
            event.getPointerId(0), event.getRawX(), event.getRawY(), event.getX(), event.getY(),
            event.getEventTime(), mState, inWindowBar && !capsule, interactive && !capsule,
            nestedChildOwned && !capsule, blocked, formEligible, mTouchSlop, mEdge, capsule,
            mCapsuleAllowed);
        mGesture = new StatusBarGesturePolicy(down);
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(event);
    }

    private void finish(MotionEvent event) {
        StatusBarGesturePolicy gesture = mGesture;
        if (gesture != null && isFoldClaim(gesture.claim())) {
            // The release lands the fold where the finger let it go, at the speed it let go.
            if (mFormDragActive) landForm(foldVelocityTowardOpen());
        } else if (gesture != null && gesture.claim() == StatusBarGesturePolicy.Claim.PENDING) {
            if (gesture.down().capsule) {
                // A tap on the capsule opens it; one on the bare band beside it is nobody's.
                mCapsule.restingBounds(getWidth(), getHeight(), mCapsuleRest);
                if (mCapsuleRest.contains(gesture.down().localX, gesture.down().localY)) {
                    performClick();
                }
            } else {
                // eligible() is the "this touch was the bar's own, not a child's" test — the
                // form drag alone keeps a chip's stream PENDING too, and a chip tap must not
                // answer with a hint.
                if (gesture.down().eligible()) showPullHint();
                performClick();
            }
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
        mFormDragActive = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TopPaneFeed.addObserver(mFeedObserver);
        onFeedChanged();
        refreshCapsuleContent();
        // The bar on screen gets a whole quiet stretch before it folds by itself.
        mLastTouchMs = SystemClock.uptimeMillis();
        scheduleIdle();
        if (mCapsuleProgress > 0f) tickClock();
    }

    @Override protected void onDetachedFromWindow() {
        if (mGesture != null) mGesture.cancel();
        cancelPullHint();
        clearTracking();
        mFingerDown = false;
        TopPaneFeed.removeObserver(mFeedObserver);
        removeCallbacks(mIdleCheck);
        removeCallbacks(mClockTick);
        // A landing cut short lands where it was going.
        cancelCapsuleAnimator();
        setCapsuleProgress(mForm == Form.CAPSULE ? 1f : 0f);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // A bar first laid out after it came on screen starts its quiet stretch here.
        if (oldw <= 0 && w > 0) scheduleIdle();
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        // A bar coming on screen (Home's first float slides it in) shows for a whole quiet
        // stretch before it folds; one going away has nothing to fold.
        if (isVisible) {
            mLastTouchMs = SystemClock.uptimeMillis();
            scheduleIdle();
        } else {
            removeCallbacks(mIdleCheck);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            if (mGesture != null) mGesture.cancel();
            cancelFormDrag();
            requestStructuralReset();
            if (mFingerDown) liftFinger();
        }
    }

    // ---------------------------------------------------------------- forms

    @NonNull private Form lowestForm() { return StatusBarFormPolicy.lowest(mCapsuleAllowed); }

    @NonNull private Form highestForm() { return StatusBarFormPolicy.highest(mExpansionAllowed); }

    private int capsuleTravelPx() {
        return Math.round(StatusBarFoldMotion.CAPSULE_TRAVEL_DP
            * getResources().getDisplayMetrics().density);
    }

    private int foldTravelPx() {
        if (mFoldTravelPx > 0) return mFoldTravelPx;
        float density = getResources().getDisplayMetrics().density;
        return Math.max(0, StatusBarEdgeGeometry.thicknessPx(mEdge, false, false, density)
            - StatusBarEdgeGeometry.thicknessPx(mEdge, false, true, density));
    }

    /**
     * Moves the bar to {@code form}: the capsule's morph lands (animated, at the speed the finger
     * let go, or at once), the accessibility node follows, and the host hears of it. A change of
     * form the user or the host made restarts the idle fold's quiet stretch.
     */
    private void setFormInternal(@NonNull Form form, boolean animate,
                                 float velocityTowardOpenPxPerSec) {
        Form previous = mForm;
        mForm = form;
        mState = form.state();
        float target = form == Form.CAPSULE ? 1f : 0f;
        if (animate) {
            animateCapsuleTo(target, form == Form.CAPSULE
                ? -velocityTowardOpenPxPerSec : velocityTowardOpenPxPerSec);
        } else {
            cancelCapsuleAnimator();
            setCapsuleProgress(target);
        }
        if (previous == form) return;
        if (form != Form.CAPSULE) mLastTouchMs = SystemClock.uptimeMillis();
        refreshCapsuleAccessibility();
        if (mListener != null) mListener.onFormChanged(form);
        scheduleIdle();
    }

    /**
     * Folds the bar to the capsule, by itself (idle) or for Minimal mode. An expanded bar is the
     * host's to fold first; a host that holds it open keeps it open.
     */
    private void foldToCapsule(boolean animate) {
        if (!mCapsuleAllowed || mForm == Form.CAPSULE) return;
        if (mForm == Form.EXPANDED) {
            if (mListener != null) mListener.onCollapsedStateRequested(true, 0f);
            if (mForm == Form.EXPANDED) return;
        }
        setFormInternal(Form.CAPSULE, animate, 0f);
    }

    /**
     * The capsule opens to compact: a tap, an accessibility click, or the host (a Home float
     * minimising into its chip lands on a compact bar, spec §4.4). Nothing for any other form.
     */
    public void openCapsule() {
        if (mForm != Form.CAPSULE) return;
        setFormInternal(Form.COMPACT, true, 0f);
    }

    private void setCapsuleProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (clamped == mCapsuleProgress) return;
        boolean appearing = mCapsuleProgress <= 0f && clamped > 0f;
        mCapsuleProgress = clamped;
        invalidate();
        if (appearing) {
            mCapsule.refreshColors();
            tickClock();
        }
    }

    /** The capsule's morph lands at {@code target}, on the settle curve, like the fold. */
    private void animateCapsuleTo(float target, float velocityTowardTargetPxPerSec) {
        cancelCapsuleAnimator();
        float from = mCapsuleProgress;
        if (from == target) return;
        if (!isAttachedToWindow() || !isLaidOut()
            || com.termux.app.ReducedMotion.isEnabled(getContext())) {
            setCapsuleProgress(target);
            return;
        }
        int travel = Math.max(1, capsuleTravelPx());
        long duration = StatusBarFoldMotion.durationMs(Math.abs(target - from),
            velocityTowardTargetPxPerSec / travel);
        ValueAnimator animator = ValueAnimator.ofFloat(from, target);
        animator.setDuration(duration);
        animator.setInterpolator(com.termux.app.terminal.Motion.settle());
        animator.addUpdateListener(value -> setCapsuleProgress((float) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mCapsuleAnimator == animation) mCapsuleAnimator = null;
            }
        });
        mCapsuleAnimator = animator;
        animator.start();
    }

    private void cancelCapsuleAnimator() {
        ValueAnimator animator = mCapsuleAnimator;
        if (animator == null) return;
        mCapsuleAnimator = null;
        animator.cancel();
    }

    // ---------------------------------------------------------------- the idle fold

    /** Looks at the idle rule now and acts on it, or asks again when it says. */
    private void scheduleIdle() {
        removeCallbacks(mIdleCheck);
        checkIdle();
    }

    private void checkIdle() {
        removeCallbacks(mIdleCheck);
        if (!isAttachedToWindow() || !isShown() || getWidth() <= 0) return;
        long now = SystemClock.uptimeMillis();
        StatusBarFormPolicy.Idle idle = StatusBarFormPolicy.idle(mForm, mCapsuleAllowed,
            mLastTouchMs, mLastNotificationMs, mLastWindowChangeMs, now,
            mFingerDown || mFormDragActive, isCardOpen(), isFormHeld(), isTouchExploring());
        if (idle.held) mLastTouchMs = now;
        if (idle.fold) {
            foldToCapsule(true);
            return;
        }
        if (idle.delayMs >= 0L) postDelayed(mIdleCheck, idle.delayMs);
    }

    private boolean isCardOpen() {
        return (mCardHost != null && mCardHost.isShowing())
            || (mListener != null && mListener.isSheetOpen());
    }

    /** Another surface has the bar (the palette, the drawer, an editor): its form is held. */
    private boolean isFormHeld() {
        return mAnotherSurfaceEngaged
            || (mListener != null && mListener.isStatusGestureBlocked());
    }

    private boolean isTouchExploring() {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        return manager != null && manager.isEnabled() && manager.isTouchExplorationEnabled();
    }

    /** The pinned feed moved: an arrival restarts the quiet stretch, and the dots recount. */
    private void onFeedChanged() {
        List<PinnedNotification> pinned = TopPaneFeed.getPinned();
        Set<String> keys = new HashSet<>();
        long latest = 0L;
        for (PinnedNotification notification : pinned) {
            keys.add(notification.key);
            latest = Math.max(latest, notification.postTime);
        }
        boolean arrived = mPinnedKnown && StatusBarFormPolicy.notificationArrived(mPinnedKeys,
            mPinnedLatestMs, keys, latest);
        mPinnedKeys = keys;
        mPinnedLatestMs = latest;
        mPinnedKnown = true;
        mPinnedCount = pinned.size();
        if (arrived) {
            mLastNotificationMs = SystemClock.uptimeMillis();
            scheduleIdle();
        }
        refreshCapsuleContent();
    }

    /**
     * Reads what the capsule shows from the bar's children (StatusBarFormPolicy#content): Home or
     * the window on screen, its glyph, the attention cues among the windows, and the pins. A move
     * to another window, or between Home and the Workspace, is a window change.
     */
    private void refreshCapsuleContent() {
        boolean onHome = mLens != null && HomeChromePolicy.isHome(mLens.current());
        List<WindowItem> windows = mWindowColumn != null
            ? mWindowColumn.windows() : Collections.<WindowItem>emptyList();
        int selected = mWindowColumn != null ? mWindowColumn.selectedIndex() : -1;
        WindowItem current = selected >= 0 && selected < windows.size()
            ? windows.get(selected) : null;
        int attention = 0;
        for (WindowItem window : windows) {
            if (window.attention) attention++;
        }
        String key = (onHome ? "home" : "window") + ":" + selected + "/" + windows.size();
        if (mWindowKey != null && !mWindowKey.equals(key)) {
            mLastWindowChangeMs = SystemClock.uptimeMillis();
            scheduleIdle();
        }
        mWindowKey = key;
        mWindowSpoken = onHome ? getResources().getString(R.string.status_bar_capsule_home)
            : current != null ? current.spokenLabel : null;
        if (mCapsule.setContent(StatusBarFormPolicy.content(onHome,
            current != null ? current.label : null, mPinnedCount, attention))) {
            if (mCapsuleProgress > 0f) invalidate();
        }
        refreshCapsuleAccessibility();
    }

    /** The capsule's clock follows the minute while any of the capsule shows. */
    private void tickClock() {
        removeCallbacks(mClockTick);
        if (!isAttachedToWindow() || mCapsuleProgress <= 0f) return;
        if (mCapsule.refreshClock()) {
            invalidate();
            refreshCapsuleAccessibility();
        }
        long now = System.currentTimeMillis();
        postDelayed(mClockTick, 60_000L - now % 60_000L + 50L);
    }

    // ---------------------------------------------------------------- accessibility

    /**
     * As the capsule, the bar is one node: what it shows, said in words, and a click that opens
     * it. Otherwise the bar itself is not a node and its children speak for themselves.
     */
    private void refreshCapsuleAccessibility() {
        boolean capsule = mForm == Form.CAPSULE;
        setImportantForAccessibility(capsule ? IMPORTANT_FOR_ACCESSIBILITY_YES
            : IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(capsule ? capsuleLabel() : null);
    }

    @NonNull
    private String capsuleLabel() {
        android.content.res.Resources resources = getResources();
        String where = mWindowSpoken != null ? mWindowSpoken : "";
        String label = resources.getString(R.string.status_bar_capsule_description, where,
            mCapsule.clock());
        int count = mCapsule.content().count;
        if (count <= 0) return label;
        return label + ", " + resources.getQuantityString(R.plurals.status_bar_capsule_alerts,
            count, count);
    }

    @Override public void addChildrenForAccessibility(ArrayList<View> outChildren) {
        // The capsule's folded content is not on screen, so it is not read either.
        if (mForm == Form.CAPSULE) return;
        super.addChildrenForAccessibility(outChildren);
    }

    @Override public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (mForm != Form.CAPSULE) return;
        info.setClickable(true);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLICK,
            getResources().getString(R.string.status_bar_capsule_open)));
    }

    @Override public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (mForm == Form.CAPSULE && action == AccessibilityNodeInfo.ACTION_CLICK) {
            mLastTouchMs = SystemClock.uptimeMillis();
            return performClick();
        }
        return super.performAccessibilityAction(action, arguments);
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
