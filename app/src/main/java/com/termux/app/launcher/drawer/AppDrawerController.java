package com.termux.app.launcher.drawer;

import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.SuggestionBarView;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * Owner of the app drawer plane: its state, its motion, and every transform the transition applies
 * to the dock and the accessory bands underneath it.
 *
 * <p>Deliberately shaped like {@code TerminalCommandPaletteController} — one {@link Spring}
 * channel ticked on a {@link Choreographer} callback, one {@code applyFrame} that is the single
 * writer of every animated property, views bound lazily on first use and left {@code INVISIBLE}
 * rather than {@code GONE} so the host stays measured and {@code RealtimeBlurView} can gate its own
 * work on {@code isShown()}.
 *
 * <p><b>The transition is one number.</b> {@code p ∈ [0,1]}, 0 = dock, 1 = full drawer. Under a
 * finger {@code p} is the raw travelled fraction with no easing at all, so the plane tracks 1:1;
 * only the release is animated. Everything else — the plane rectangle, the corner radius, the glass
 * cross-fade, the dock's lift, the row fades, the accessory bands — is a pure function of {@code p}
 * evaluated in {@link #applyFrame}. Nothing else may write those properties while the drawer is
 * engaged.
 *
 * <p><b>The invisible handoff.</b> At {@code p == 0} the plane rectangle <em>is</em> the dock glass
 * rect: same bounds from {@code accessory_surface_host}, same corner radius from
 * {@link TermuxActivity#resolveDockCapsuleCornerRadiusPx}, same horizontal inset from
 * {@link TermuxActivity#getDockHorizontalInsetPx()}. The first tenth of the transition is then a
 * cross-fade between two identical rectangles, which is what makes the drawer look like the dock
 * growing rather than a sheet appearing over it. Any change that lets the seed rect drift from the
 * dock rect breaks that, visibly.
 *
 * <p><b>Nothing is measured per frame.</b> While the drawer is engaged the accessory stack's layout
 * is frozen ({@code setTerminalToolbarHeight} and {@code applyAccessoryGeometryIfNeeded} early-return
 * and set a pending flag), because that path runs the flush-padding solver and a terminal resize —
 * a SIGWINCH per frame. The band rectangles and the dock↔keyboard gap are captured once, at drag
 * begin, and fed back to {@link AppDrawerAccessoryChoreography} unchanged on every frame;
 * re-measuring them is what reopens the accessory feedback loop. The freeze is replayed on close,
 * from a {@code finally}, so a throw can never leave the dock deaf to style changes.
 *
 * <h2>Public API</h2>
 *
 * <p>Step 6 ({@code SuggestionBarView} arbitration) drives the opening drag through:
 *
 * <ul>
 *   <li>{@link #beginDrag(float)} — the dock's arbiter latched {@code DRAWER_DRAG}; pass the
 *       {@code ACTION_DOWN} raw Y. Captures geometry and takes ownership of the stack.
 *   <li>{@link #updateDrag(float)} — every {@code ACTION_MOVE}, raw Y.
 *   <li>{@link #endDrag(float)} — {@code ACTION_UP}, with the release velocity in px/s, positive
 *       downwards. Applies {@link AppDrawerCommitPolicy} and springs to the outcome.
 *   <li>{@link #cancelDrag()} — {@code ACTION_CANCEL}: springs back to where the drag started.
 * </ul>
 *
 * <p>and reads state through {@link #isOpen()} / {@link #isEngaged()}. {@link #close(boolean)}
 * (back press, palette summon) and {@link #closeImmediate()} (lifecycle, HOME) put it away.
 * {@link #setDockChoreographyTarget} registers the dock's own choreography sink — Step 6 implements
 * {@link AppDrawerDockChoreographyTarget#setDrawerTransitionProgress(float)} on
 * {@code SuggestionBarView} to stagger the pinned icons out; until then the controller no-ops
 * safely with no target set.
 */
public final class AppDrawerController implements Choreographer.FrameCallback,
    AppDrawerPlaneView.Callbacks, AppDrawerContentView.Callbacks,
    AppDrawerSearchController.Host {

    /**
     * The dock's half of the choreography. Implemented by {@code SuggestionBarView} in Step 6: the
     * controller owns the plane and the bands, the dock owns its own children (the pinned-icon
     * stagger), and this is the only line between them.
     */
    public interface AppDrawerDockChoreographyTarget {

        /** @param progress 0 = dock, 1 = full drawer */
        void setDrawerTransitionProgress(float progress);
    }

    /**
     * Critically damped ({@code 2·√420 ≈ 41}), settling in about 260ms — the same arrival the top
     * status bar's collapse uses, and stiff enough that a committed drawer is there before the
     * finger has left the glass. Safe only because {@link Spring} substeps.
     */
    private static final float STIFFNESS = 420f;
    private static final float DAMPING = 41f;

    private static final float MIN_TRAVEL_DP = 120f;
    private static final float MAX_TRAVEL_DP = 260f;
    /** The dock's hop as the drag starts, paid back before the plane fills the screen. */
    private static final float DOCK_LIFT_DP = 8f;

    /** Glass handoff: two identical rectangles swapping over the first tenth of the transition. */
    private static final float GLASS_FADE_END = 0.10f;
    /** A-Z row and indicator band leave early — they are the dock's, not the drawer's. */
    private static final float ROW_FADE_START = 0.02f;
    private static final float ROW_FADE_END = 0.26f;

    /** Below this the closing spring is close enough to shut to tear the plane down. */
    private static final float CLOSED_EPSILON = 0.002f;

    private final TermuxActivity mActivity;
    private final float mDensity;
    private final Spring mProgress = new Spring(0f, STIFFNESS, DAMPING);
    /**
     * The search keyboard's reveal, {@code k ∈ [0,1]}. A second, independent transition: the drawer
     * is already open and {@code p} pinned at 1 when a keystroke brings the keyboard up
     * <em>through</em> the plane, so the bands the open drawer pushed away have to come back without
     * {@code p} moving at all. Same spring constants as the drawer's own, and ticked in the same
     * {@link #doFrame} — a second {@link Choreographer} loop would render the two on different
     * frames, and the plane's bottom edge and the keyboard's top are the same edge to the eye.
     */
    private final Spring mReveal = new Spring(0f, STIFFNESS, DAMPING);
    private final AppDrawerSearchController mSearch = new AppDrawerSearchController();
    @NonNull private AppDrawerLayoutConfig mLayoutConfig = AppDrawerLayoutConfig.defaults();

    private final int[] mHostLocation = new int[2];
    private final int[] mViewLocation = new int[2];
    /** Reused by {@code setClipBounds}, which copies; never handed out. */
    private final Rect mClipScratch = new Rect();

    @Nullable private FrameLayout mHost;
    @Nullable private FrameLayout mGlass;
    @Nullable private AppDrawerPlaneView mPlane;
    @Nullable private AppDrawerContentView mContent;
    @Nullable private AppDrawerDockChoreographyTarget mDockTarget;
    /**
     * Registered on the host while the drawer is engaged. The system-IME fallback relayouts the
     * content root under an open plane, which moves the window bar and therefore the open rect; the
     * captured bands are deliberately <em>not</em> re-read, because re-measuring them is what
     * reopens the accessory feedback loop this transition freezes.
     */
    @Nullable private View.OnLayoutChangeListener mHostLayoutListener;

    // Captured once per gesture. Everything below is read-only until the next capture.
    @Nullable private Frame mDockRect;
    @Nullable private Frame mOpenRect;
    @Nullable private AppDrawerAccessoryChoreography.Band mExtraKeysBand;
    @Nullable private AppDrawerAccessoryChoreography.Band mKeyboardBand;
    @Nullable private AppDrawerAccessoryChoreography.Band mStatusBand;
    @Nullable private View mAccessorySurface;
    @Nullable private View mAppsPager;
    @Nullable private View mAzRow;
    @Nullable private View mIndicatorBand;
    @Nullable private View mAzFxUnderlay;
    @Nullable private View mAzFxOverlay;
    @Nullable private View mAzLabelOverlay;
    @Nullable private View mExtraKeysView;
    @Nullable private View mKeyboardView;
    /** The app-owned top status bar, the one band above the plane. */
    @Nullable private View mStatusBarView;
    private float mStatusCompactHeightPx;
    private boolean mRoundedStyle;
    private boolean mHasBands;
    private float mSeedRadiusPx;
    private float mOpenRadiusPx;
    private float mTravelPx;
    private float mLiftPx;
    private float mSlopPx;
    private float mCapturedGapPx;
    /** Radius of the frame currently painted; shared with the glass pane's outline provider. */
    private float mCurrentRadiusPx;

    private boolean mEngaged;
    private boolean mOpen;
    private boolean mDragging;
    @NonNull private AppDrawerCommitPolicy.Direction mDirection =
        AppDrawerCommitPolicy.Direction.OPENING;
    private float mDownRawY;
    /** Progress the current drag started from; non-zero only when catching a settling plane. */
    private float mGrabProgress;

    private boolean mFrameScheduled;
    private long mLastFrameTimeNanos;

    public AppDrawerController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mDensity = activity.getResources().getDisplayMetrics().density;
        // Wired here rather than with the views: the three intake channels are routed through the
        // activity, which has no idea whether the plane has been built yet, and a search that only
        // answered once a RecyclerView existed would silently let the first keystrokes through to
        // the shell. isSearchActive() reads mOpen, so an unbuilt drawer still claims nothing.
        mSearch.setHost(this);
        TermuxAppSharedPreferences preferences = activity.getPreferences();
        if (preferences != null) mLayoutConfig = AppDrawerLayoutConfig.from(preferences);
    }

    // ------------------------------------------------------------------ state

    /** True while the drawer is the target state — what the back press consumer asks. */
    public boolean isOpen() {
        return mOpen;
    }

    /**
     * True while the plane owns the accessory stack's transforms: open, dragging, or still
     * settling. Every geometry seam in {@code TermuxActivity} gates on this.
     */
    public boolean isEngaged() {
        return mEngaged;
    }

    public void setDockChoreographyTarget(@Nullable AppDrawerDockChoreographyTarget target) {
        mDockTarget = target;
    }

    // ------------------------------------------------------------------ gesture

    /**
     * Takes ownership of the transition. Direction is read from the current state rather than
     * passed in: a drag that starts with the drawer up is a close, and one that starts on the dock
     * is an open, and there is no third case.
     *
     * @param downRawY the gesture's {@code ACTION_DOWN} raw screen Y
     */
    public void beginDrag(float downRawY) {
        if (!bindViews()) return;
        // Engaged means the plane is already on screen, so the only gesture that can reach this is
        // the plane's own — and the plane only claims downward drags.
        boolean closing = mEngaged || mOpen;
        // Cold start only: geometry captured mid-transition would bake the transforms already
        // applied to the bands into their captured tops.
        if (!closing && !captureGeometry()) return;
        mDirection = closing
            ? AppDrawerCommitPolicy.Direction.CLOSING
            : AppDrawerCommitPolicy.Direction.OPENING;
        mDownRawY = downRawY;
        mDragging = true;
        mProgress.vel = 0f;
        if (!closing) {
            mProgress.reset(0f);
            prepareOverlay();
        }
        // Where the finger picked the transition up. Zero on a cold open and one on a settled
        // drawer; anything between is a plane caught mid-settle, which continues from there rather
        // than snapping to an end.
        mGrabProgress = mProgress.value;
        mEngaged = true;
        applyFrame(mProgress.value);
        // The rope needs frames for the whole drag, not just for the release: its anchor is a
        // function of p, so the chain has to be integrated while the finger is moving or the letters
        // would arrive already settled on the frame the finger left the glass.
        kick();
    }

    /** Every {@code ACTION_MOVE} of a claimed drag. The plane tracks this 1:1, unsmoothed. */
    public void updateDrag(float rawY) {
        if (!mDragging) return;
        float travelled = AppDrawerTransitionGeometry.progressForDrag(rawY, mDownRawY, mSlopPx,
            mTravelPx);
        float p = AppDrawerTransitionGeometry.clamp01(
            mDirection == AppDrawerCommitPolicy.Direction.OPENING
                ? mGrabProgress + travelled : mGrabProgress - travelled);
        mProgress.value = p;
        mProgress.target = p;
        mProgress.vel = 0f;
        applyFrame(p);
        // A finger that pauses mid-drag still leaves the chain travelling, and the progress spring
        // reports settled the moment it is told where the finger is: without this the loop would end
        // on the first stationary frame and the rope would freeze mid-swing under the finger.
        kick();
    }

    /** @param velocityPxPerSec release velocity, positive downwards */
    public void endDrag(float velocityPxPerSec) {
        if (!mDragging) return;
        mDragging = false;
        AppDrawerCommitPolicy.Decision decision = AppDrawerCommitPolicy.decide(mProgress.value,
            velocityPxPerSec, mDirection);
        boolean open;
        switch (decision) {
            case COMMIT_OPEN: open = true; break;
            case COMMIT_CLOSE: open = false; break;
            case CANCEL:
            default:
                open = cancelTarget();
                break;
        }
        settle(open, velocityPxPerSec);
    }

    /** A second pointer, a window losing the stream, an ancestor stealing it: revert. */
    public void cancelDrag() {
        if (!mDragging) return;
        mDragging = false;
        settle(cancelTarget(), 0f);
    }

    /**
     * "Put it back" — the end the drag was picked up from. That is the direction's own end for the
     * two ordinary cases (a cold pull off the dock, a push down on a settled drawer) and the nearer
     * end for a plane caught mid-settle, which has no direction end to return to.
     */
    private boolean cancelTarget() {
        return mGrabProgress >= 0.5f;
    }

    /**
     * Springs shut. {@code fromBack} carries no velocity by construction — a back press is a
     * decision, not a throw — and exists so callers do not have to invent one.
     */
    public void close(boolean fromBack) {
        if (!mEngaged && !mOpen) return;
        mDragging = false;
        settle(false, 0f);
    }

    /**
     * Drops the drawer with no animation, for {@code onStop}, HOME and configuration changes, where
     * a settling spring would otherwise be resumed against geometry that no longer exists.
     */
    public void closeImmediate() {
        mDragging = false;
        mOpen = false;
        mProgress.reset(0f);
        if (!mEngaged) return;
        applyFrame(0f);
        onClosed();
    }

    private void settle(boolean open, float velocityPxPerSec) {
        mOpen = open;
        // Interactivity and the interceptor slot follow the decision, not the animation: a plane
        // still springing shut must already have stopped taking touches and already have handed the
        // in-app keyboard's single interceptor slot back, or a keystroke during the close would be
        // typed into a drawer that is on its way out.
        applyContentOpenState();
        retargetReveal();
        mProgress.target = open ? 1f : 0f;
        // Progress runs with the finger when opening and against it when closing, so the injected
        // velocity is negated in the closing direction. Feeding the raw sign there makes a hard
        // downward flick launch the spring away from the target it was just told to reach.
        float sign = mDirection == AppDrawerCommitPolicy.Direction.CLOSING ? -1f : 1f;
        mProgress.vel = mTravelPx > 0f ? (velocityPxPerSec / mTravelPx) * sign : 0f;
        mEngaged = true;
        kick();
    }

    // ------------------------------------------------------------------ plane callbacks

    @Override
    public void onPlaneDragBegin(float downRawY) {
        beginDrag(downRawY);
    }

    @Override
    public void onPlaneDrag(float rawY) {
        updateDrag(rawY);
    }

    @Override
    public void onPlaneDragEnd(float velocityPxPerSec) {
        endDrag(velocityPxPerSec);
    }

    @Override
    public void onPlaneDragCancel() {
        cancelDrag();
    }

    // ------------------------------------------------------------------ content callbacks

    // Routed back through the plane rather than straight into beginDrag/updateDrag: the plane holds
    // the arbiter that decides whether it is already driving this stream, and one gesture must
    // produce exactly one begin. Everything below therefore lands in onPlaneDrag* above.

    @Override
    public void onContentCloseDragBegin(float downRawY) {
        if (mPlane != null) mPlane.beginCloseDragFromContent(downRawY);
    }

    @Override
    public void onContentCloseDragUpdate(float rawY) {
        if (mPlane != null) mPlane.updateCloseDragFromContent(rawY);
    }

    @Override
    public void onContentCloseDragEnd(float velocityPxPerSec) {
        if (mPlane != null) mPlane.endCloseDragFromContent(velocityPxPerSec);
    }

    @Override
    public void onContentCloseDragCancel() {
        if (mPlane != null) mPlane.cancelCloseDragFromContent();
    }

    // ------------------------------------------------------------------ search

    /** The intake the activity's three channels route into. */
    @NonNull
    public AppDrawerSearchController getSearchController() {
        return mSearch;
    }

    /** Hardware and external-keyboard strokes, claimed while the drawer is open. */
    public boolean handleSearchKey(int keyCode, @NonNull KeyEvent event) {
        return mSearch.handleKeyDown(keyCode, event);
    }

    /** Text committed by a system IME, claimed while the drawer is open. */
    public boolean handleSearchCodePoint(int codePoint, boolean ctrlDown) {
        return mSearch.handleCodePoint(codePoint, ctrlDown);
    }

    @Override
    public boolean isSearchActive() {
        return mOpen;
    }

    @Override
    public void onSearchCommitRequested() {
        AppDrawerContentView content = mContent;
        // The launch itself is the dock's ladder, transitions and usage recording included; the
        // drawer is put away by the lifecycle path the launch triggers.
        if (content != null) content.launchFirstResult();
    }

    @Override
    public void onSearchDismissRequested() {
        // Esc and Back arrive here through the key channels, which the terminal's client runs
        // before the activity's onBackPressed can walk its hierarchy — so this is the only place
        // that hierarchy exists for a keystroke. Spend the press inside the drawer first: clear a
        // typed query, or collapse an expanded (or mid-transition) category back to the tile grid.
        // Only a press nothing inside claimed may close the whole plane.
        AppDrawerContentView content = mContent;
        if (content != null && content.handleBackInDrawer()) return;
        close(true);
    }

    /**
     * A package installed, removed or changed. Idempotent, and re-driven from the activity's own
     * package-state refresh rather than from a one-shot provider callback: {@code invalidate()}
     * clears pending callbacks, so a drawer that trusted one would sit on a stale grid.
     */
    public void onAppCatalogChanged() {
        AppDrawerContentView content = mContent;
        if (content != null) content.onAppCatalogChanged();
    }

    /** Applies a launcher preference reload without rebuilding the activity or drawer tree. */
    public void onPreferencesReloaded() {
        TermuxAppSharedPreferences preferences = mActivity.getPreferences();
        AppDrawerLayoutConfig config = preferences == null ? AppDrawerLayoutConfig.defaults()
            : AppDrawerLayoutConfig.from(preferences);
        if (config.equals(mLayoutConfig)) return;
        if (mEngaged || mOpen) closeImmediate();
        mLayoutConfig = config;
        applyLayoutConfig();
    }

    /**
     * Back, while the drawer is open.
     *
     * @return true when the press was spent clearing a non-empty query, i.e. when the drawer must
     *     stay open; false when the caller should close it
     */
    public boolean onBackPressedInDrawer() {
        AppDrawerContentView content = mContent;
        return content != null && content.handleBackInDrawer();
    }

    // ------------------------------------------------------------------ motion

    private void kick() {
        if (mFrameScheduled) return;
        mFrameScheduled = true;
        mLastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    /**
     * Restarts the loop for the drawer's effects. Called by the content when a scrub starts or is
     * released on a drawer that has finished settling, where nothing else would ask for a frame.
     *
     * <p>Gated on {@link #mEngaged}: a frame requested by a drawer that is already torn down would
     * enter {@link #doFrame} with {@code p} at zero and run the teardown a second time, against
     * geometry that has already been handed back.
     */
    public void requestFrames() {
        if (!mEngaged) return;
        kick();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        float dt = mLastFrameTimeNanos == 0L
            ? Spring.MIN_DT
            : (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000f;
        mLastFrameTimeNanos = frameTimeNanos;
        dt = Spring.clampDelta(dt);
        boolean reducedMotion = isReducedMotion();
        boolean moving = mProgress.tick(reducedMotion, dt);
        // The reveal rides this loop rather than one of its own: the plane's bottom edge and the
        // keyboard's top are the same edge to the eye, and two Choreographer callbacks would show
        // them a frame apart.
        boolean revealMoving = mReveal.tick(reducedMotion, dt);
        // The rope and the scrub highlight, on this loop rather than one of their own — the growing
        // rectangle and the letters inside it are one surface.
        AppDrawerContentView content = mContent;
        boolean fxMoving = content != null
            && content.advanceDrawerFx(mProgress.value, dt, reducedMotion);
        applyFrame(mProgress.value);
        // The !mDragging guard is the single most dangerous line in this slice. Before B-3 the loop
        // never ran during a drag, so this branch could not be reached with a finger down. The rope
        // needs frames *during* the opening drag, where mOpen is false and p starts at zero — which
        // without the guard tears the drawer down on the first frame of every open and presents as
        // "the drawer sometimes refuses to open".
        if (!mDragging && !mOpen && mProgress.value < CLOSED_EPSILON) {
            mProgress.reset(0f);
            mReveal.reset(0f);
            applyFrame(0f);
            onClosed();
            return;
        }
        if (moving || revealMoving || fxMoving) kick();
    }

    /**
     * Retargets the search-keyboard reveal. Driven by the content — a first keystroke, a query
     * emptied, a pill tap — never polled.
     */
    private void retargetReveal() {
        AppDrawerContentView content = mContent;
        float target = content != null && mOpen && hasRevealableKeyboard()
            ? AppDrawerTransitionGeometry.clamp01(content.getRevealFraction()) : 0f;
        if (mReveal.target == target) return;
        mReveal.target = target;
        kick();
    }

    private boolean isReducedMotion() {
        return Settings.Global.getFloat(mActivity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    // ------------------------------------------------------------------ views

    private boolean bindViews() {
        if (mPlane != null) return true;
        mHost = mActivity.findViewById(R.id.app_drawer_host);
        mGlass = mActivity.findViewById(R.id.app_drawer_glass);
        if (mHost == null || mGlass == null) return false;
        // The handoff fades a ViewGroup, which by default means an offscreen layer allocated on
        // every frame of the ramp. The children it holds are two translucent surfaces at alpha
        // below 0.1 for a tenth of the transition; the layer buys a blend nobody can see.
        mHost.forceHasOverlappingRendering(false);
        mGlass.setClipToOutline(true);
        mGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // The backdrop pane is clipped to the same rounded rect the plane paints, so the
                // frost/blur can never show outside the glass. Rounded inward for the same reason
                // the plane's own outline is.
                AppDrawerPlaneView plane = mPlane;
                if (plane == null) return;
                Frame frame = plane.getFrame();
                outline.setRoundRect((int) Math.ceil(frame.left), (int) Math.ceil(frame.top),
                    (int) Math.floor(frame.right), (int) Math.floor(frame.bottom),
                    mCurrentRadiusPx);
            }
        });
        AppDrawerPlaneView plane = new AppDrawerPlaneView(mActivity);
        plane.setCallbacks(this);
        // Added after the glass pane, so it paints over the blur without needing an elevation that
        // would cast a shadow from a full-screen caster.
        mHost.addView(plane, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mPlane = plane;
        buildContent(plane);
        return true;
    }

    /**
     * The grid and its search, built into the plane's content host.
     *
     * <p>Built here — from {@link #bindViews()}, and therefore on the first {@link #beginDrag} —
     * rather than at activity start: a session that never pulls the drawer down never inflates a
     * {@code RecyclerView}, an adapter or a search controller. The cost is paid once, on the frame
     * a drag begins, where the plane is still a dock-sized rectangle and nothing of the grid is
     * visible yet. If that first frame ever measures badly on a real device, the warm-up moves to
     * {@code TermuxActivity#scheduleLauncherCatalogWarmup()} (the 450ms post-resume hook, which
     * already exists for exactly this shape of work) — never onto the touch path itself.
     */
    private void buildContent(@NonNull AppDrawerPlaneView plane) {
        AppDrawerContentView content = new AppDrawerContentView(mActivity,
            mActivity.getSuggestionBarView());
        content.setCallbacks(this);
        content.setRevealListener(this::retargetReveal);
        content.setFrameRequestListener(this::requestFrames);
        content.setSearchKeyboardRequestListener(mActivity::requestAppDrawerSearchKeyboard);
        // A drawer that is not open is not a surface: interactivity is turned on by prepareOverlay
        // and off again on close, and never follows p.
        content.setInteractive(false);
        content.setVisibility(View.INVISIBLE);
        plane.getContentHost().addView(content, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // The plane asks the grid before it claims anything: see AppDrawerPlaneView.CloseDragGate.
        plane.setCloseDragGate(content);
        mContent = content;
    }

    /**
     * Everything the transition reads, sampled once per gesture.
     *
     * @return false when the dock has not laid out yet, in which case the drag is refused rather
     *     than run against a zero rect.
     */
    private boolean captureGeometry() {
        FrameLayout host = mHost;
        View dock = mActivity.findViewById(R.id.accessory_surface_host);
        if (host == null || dock == null || host.getWidth() <= 0 || host.getHeight() <= 0
            || dock.getWidth() <= 0 || dock.getHeight() <= 0) return false;

        host.getLocationOnScreen(mHostLocation);
        Frame dockRect = frameOf(dock);
        if (dockRect == null) return false;
        mDockRect = dockRect;

        mRoundedStyle = mActivity.isRoundedDockStyle();
        mSeedRadiusPx = mRoundedStyle
            ? mActivity.resolveDockCapsuleCornerRadiusPx(dock.getHeight()) : 0f;
        mOpenRadiusPx = resolveOpenRadiusPx();

        mOpenRect = resolveOpenRect();

        mTravelPx = AppDrawerTransitionGeometry.resolveOpenTravelPx(host.getHeight(),
            dp(MIN_TRAVEL_DP), dp(MAX_TRAVEL_DP));
        mLiftPx = dp(DOCK_LIFT_DP);
        mSlopPx = ViewConfiguration.get(mActivity).getScaledTouchSlop();

        mAccessorySurface = dock;
        mAppsPager = mActivity.findViewById(R.id.apps_bar_viewpager);
        mAzRow = mActivity.findViewById(R.id.apps_bar_az_row);
        mIndicatorBand = mActivity.findViewById(R.id.apps_bar_indicator_band);
        // The A-Z scrub's three effect layers fade on the row's ramp with the row itself. The label
        // overlay is the one that actually matters: it is a match_parent child of
        // activity_termux_root_relative_layout, a sibling of the drawer host that wins in z, so a
        // scrub label left painting would sit on top of the plane rather than behind it.
        mAzFxUnderlay = mActivity.findViewById(R.id.apps_bar_az_fx_underlay);
        mAzFxOverlay = mActivity.findViewById(R.id.apps_bar_az_fx_overlay);
        mAzLabelOverlay = mActivity.findViewById(R.id.apps_bar_az_label_overlay);
        captureBands(dockRect);
        captureStatusBand();
        return true;
    }

    /**
     * The top status bar, measured once — whichever form it is in.
     *
     * <p>Captured like the bottom bands and for the same reason: the pane's height <em>is</em> the
     * terminal's height, so the transition may only transform it. The compact height comes from the
     * activity's own resolver rather than being assumed, because the rounded style's pane is a
     * different size and the collapse channel is the difference between the two.
     *
     * <p>A hidden bar (terminal-only styles, fullscreen) leaves a null band and no writes at all,
     * which is what keeps a pane that is {@code GONE} from being handed a translation it would still
     * be wearing the next time something makes it visible.
     */
    private void captureStatusBand() {
        mStatusBarView = mActivity.findViewById(R.id.terminal_window_bar_host);
        Frame bar = isBandVisible(mStatusBarView) ? frameOf(mStatusBarView) : null;
        mStatusBand = bar == null ? null
            : new AppDrawerAccessoryChoreography.Band(bar.top, bar.height());
        mStatusCompactHeightPx = mActivity.getCompactTopStatusBarHeightPx();
    }

    /**
     * The rectangle the plane grows into. Depends only on the host and the two radius/inset
     * preferences — nothing about the accessory stack, and nothing about the top status bar —
     * which is what makes it the one thing the host layout listener may safely recompute while the
     * drawer is open.
     *
     * <p>The top edge is the host's own, not the status bar's bottom: the plane swallows the bar,
     * which leaves through {@link AppDrawerStatusBandChoreography} instead of standing over the
     * drawer under the backdrop tint. The host itself begins below the system status-bar inset —
     * the root consumes it as padding — so the inset strip above stays the system's, exactly as it
     * does for the command palette.
     *
     * @return null when the host has not laid out; callers keep the rect they already had
     */
    @Nullable
    private Frame resolveOpenRect() {
        FrameLayout host = mHost;
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return null;
        host.getLocationOnScreen(mHostLocation);
        // The drawer keeps the dock's outer margin rather than inventing one — same preference,
        // same edge. The horizontal lerp is carried by the plane rect itself, whose seed left/right
        // are the dock's and whose open left/right are this inset.
        float inset = AppDrawerTransitionGeometry.resolveInsetPx(
            mActivity.getDockHorizontalInsetPx(), mActivity.getDockHorizontalInsetPx(), 1f);
        // Square bottom corners in default style are expressed by pushing the bottom edge one
        // radius past the host: Outline clipping is a single-radius round rect, and a Path clip
        // would cost the cheap outline clip for two corners nobody can see.
        float bottomBleed = mRoundedStyle ? 0f : mOpenRadiusPx;
        return new Frame(inset, 0f, host.getWidth() - inset, host.getHeight() + bottomBleed);
    }

    /**
     * The extra-keys row and the in-app keyboard, measured once.
     *
     * <p>A band that is not showing becomes a zero-height band pinned at the top of whichever band
     * <em>is</em> showing. That keeps both recipes honest with one keyboard or none: the default
     * style's combined slide adds no height for the missing band, and the rounded style's pin still
     * has a real edge to hold the captured gap against.
     */
    private void captureBands(@NonNull Frame dockRect) {
        mExtraKeysView = mActivity.findViewById(R.id.terminal_toolbar_view_pager);
        mKeyboardView = mActivity.findViewById(R.id.inapp_keyboard_container);
        Frame extraKeys = isBandVisible(mExtraKeysView) ? frameOf(mExtraKeysView) : null;
        Frame keyboard = isBandVisible(mKeyboardView) ? frameOf(mKeyboardView) : null;
        mHasBands = extraKeys != null || keyboard != null;
        if (!mHasBands) {
            mExtraKeysBand = null;
            mKeyboardBand = null;
            mCapturedGapPx = 0f;
            return;
        }
        float pinTop = extraKeys != null ? extraKeys.top : keyboard.top;
        // The gap the rounded recipe must hold constant: the 4dp topMargin plus stack spacing,
        // measured rather than assumed, because the dock's height scale moves it.
        mCapturedGapPx = Math.max(0f, pinTop - dockRect.bottom);
        mExtraKeysBand = extraKeys != null
            ? new AppDrawerAccessoryChoreography.Band(extraKeys.top, extraKeys.height())
            : new AppDrawerAccessoryChoreography.Band(pinTop, 0f);
        mKeyboardBand = keyboard != null
            ? new AppDrawerAccessoryChoreography.Band(keyboard.top, keyboard.height())
            : new AppDrawerAccessoryChoreography.Band(pinTop, 0f);
    }

    private static boolean isBandVisible(@Nullable View view) {
        return view != null && view.getVisibility() == View.VISIBLE && view.getHeight() > 0;
    }

    /** A view's on-screen bounds, in host coordinates. */
    @Nullable
    private Frame frameOf(@Nullable View view) {
        if (view == null || mHost == null) return null;
        view.getLocationOnScreen(mViewLocation);
        float left = mViewLocation[0] - mHostLocation[0];
        float top = mViewLocation[1] - mHostLocation[1];
        return new Frame(left, top, left + view.getWidth(), top + view.getHeight());
    }

    /** {@code -1} on the preference means "follow the rounded-surface token", as the dock's does. */
    private float resolveOpenRadiusPx() {
        TermuxAppSharedPreferences preferences = mActivity.getPreferences();
        int configured = preferences == null
            ? -1 : preferences.getAppLauncherDrawerCornerRadius();
        if (configured < 0)
            configured = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP;
        return dp(configured);
    }

    /** Raises the overlay and picks its backdrop material for this open. */
    private void prepareOverlay() {
        FrameLayout host = mHost;
        FrameLayout glass = mGlass;
        AppDrawerPlaneView plane = mPlane;
        if (host == null || glass == null || plane == null) return;
        TermuxAppSharedPreferences preferences = mActivity.getPreferences();
        // Capped below the user's dock opacity so the drawer glass always stays see-through:
        // the terminal keeps running visibly behind the open drawer.
        float opacity = Math.min(0.45f,
            preferences == null ? 0.5f : preferences.getAppBarOpacity() / 100f);
        int grain = preferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN
            : preferences.getDockGlassGrain();
        plane.applyGlassMaterial(
            InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(mActivity),
            MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(mActivity, R.color.termux_primary)),
            opacity, grain);
        glass.setVisibility(View.VISIBLE);
        host.setVisibility(View.VISIBLE);
        applyBackdropMaterial();
        prepareContent(plane);
        addHostLayoutListener();
    }

    /**
     * The grid, made ready for one open.
     *
     * <p>Every one of these is re-resolved rather than cached: the column count is a function of the
     * plane's width and the icon size of the density, so a rotation that reused the last open's
     * metrics would lay a portrait grid out in landscape. The catalogue is re-pushed for the same
     * reason a package change re-pushes it — {@code LauncherAppDataProvider.invalidate()} drops
     * pending callbacks, so nothing here may depend on a one-shot registration surviving.
     */
    private void prepareContent(@NonNull AppDrawerPlaneView plane) {
        AppDrawerContentView content = mContent;
        Frame openRect = mOpenRect;
        if (content == null || openRect == null) return;
        content.setDock(mActivity.getSuggestionBarView());
        plane.setContentInsets(openRect);
        content.setSurfaceRadiusPx(mOpenRadiusPx);
        AppDrawerLayoutConfig config = mLayoutConfig;
        AppDrawerViewType viewType = config.viewType;
        content.setViewType(viewType);
        float labelHeightPx = resolveCellLabelHeightPx();
        switch (viewType) {
            case VERTICAL:
                float columnWidthPx = AppDrawerRopeMetrics.resolveColumnWidthPx(mDensity);
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(
                    openRect.width() - columnWidthPx, mDensity, labelHeightPx,
                    config.verticalColumns, config.iconSizeDp));
                break;
            case HORIZONTAL:
                content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(openRect.width(),
                    content.horizontalPagerUsableHeight(openRect.height()), mDensity, labelHeightPx,
                    config.horizontalColumns, config.horizontalRows, config.iconSizeDp));
                break;
            case CATEGORIES:
                SuggestionBarView dock = mActivity.getSuggestionBarView();
                int budget = dock == null ? 6 * 1024 * 1024
                    : dock.getRenderedIconCacheBudgetBytes();
                // Category search temporarily reuses the shipped vertical grid at full width. It
                // gets AUTO geometry here and deliberately reads no vertical grid preference.
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(openRect.width(),
                    mDensity, labelHeightPx, 0, config.iconSizeDp));
                content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(openRect.width(),
                    content.horizontalPagerUsableHeight(openRect.height()), mDensity,
                    resolveCategoryTileHeadingHeightPx(), labelHeightPx, mOpenRadiusPx, budget,
                    config.categoryColumns, config.iconSizeDp));
                break;
        }
        content.bind(LauncherAppDataProvider.getInstance(mActivity), mSearch);
        // Visible, but not yet interactive: interactivity is settle()'s to grant, and it grants it
        // from mOpen alone. The plane's own alpha-driven visibility flip on the content host keeps
        // this hidden until the sprout has actually reached it.
        content.setVisibility(View.VISIBLE);
        retargetReveal();
    }

    /** Reconfigures the existing content tree; never enters styling/accessory/activity paths. */
    private void applyLayoutConfig() {
        AppDrawerContentView content = mContent;
        Frame openRect = mOpenRect;
        if (content == null || openRect == null) return;
        content.cancelTransientFolderState();
        content.setViewType(mLayoutConfig.viewType);
        float labelHeightPx = resolveCellLabelHeightPx();
        switch (mLayoutConfig.viewType) {
            case VERTICAL:
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(openRect.width()
                    - AppDrawerRopeMetrics.resolveColumnWidthPx(mDensity), mDensity, labelHeightPx,
                    mLayoutConfig.verticalColumns, mLayoutConfig.iconSizeDp));
                break;
            case HORIZONTAL:
                content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(openRect.width(),
                    content.horizontalPagerUsableHeight(openRect.height()), mDensity, labelHeightPx,
                    mLayoutConfig.horizontalColumns, mLayoutConfig.horizontalRows,
                    mLayoutConfig.iconSizeDp));
                break;
            case CATEGORIES:
                SuggestionBarView dock = mActivity.getSuggestionBarView();
                int budget = dock == null ? 6 * 1024 * 1024 : dock.getRenderedIconCacheBudgetBytes();
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(openRect.width(), mDensity,
                    labelHeightPx, 0, mLayoutConfig.iconSizeDp));
                content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(openRect.width(),
                    content.horizontalPagerUsableHeight(openRect.height()), mDensity,
                    resolveCategoryTileHeadingHeightPx(), labelHeightPx, mOpenRadiusPx, budget,
                    mLayoutConfig.categoryColumns, mLayoutConfig.iconSizeDp));
                break;
        }
        content.rebindCurrentResults();
    }

    /**
     * The height of one cell's single-line label, at the user's font scale.
     *
     * <p>Measured off a {@link Paint} rather than by inflating and measuring a {@code TextView}:
     * the cell's label sets {@code includeFontPadding(false)}, so its single-line height is exactly
     * the font's descent minus its ascent, and this runs once per open instead of laying a throwaway
     * view out.
     */
    private float resolveCellLabelHeightPx() {
        Paint paint = new Paint();
        paint.setTextSize(AppDrawerAppsAdapter.LABEL_TEXT_SP
            * mActivity.getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    /**
     * The tile heading is 13sp, independently of the expanded app rows' 11sp labels.
     *
     * <p>One line-height, not two. The band was two because "Communication &amp; Social" was
     * expected to wrap, but at the shipped two-column width nothing does, so every card carried an
     * empty line between its title and its icons. The label is ellipsized to one line instead.
     */
    private float resolveCategoryTileHeadingHeightPx() {
        Paint paint = new Paint();
        paint.setTextSize(AppDrawerCategoryTileView.HEADING_TEXT_SP
            * mActivity.getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    private void addHostLayoutListener() {
        FrameLayout host = mHost;
        if (host == null || mHostLayoutListener != null) return;
        mHostLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
            -> onHostLayoutChanged();
        host.addOnLayoutChangeListener(mHostLayoutListener);
    }

    private void removeHostLayoutListener() {
        FrameLayout host = mHost;
        if (host == null || mHostLayoutListener == null) return;
        host.removeOnLayoutChangeListener(mHostLayoutListener);
        mHostLayoutListener = null;
    }

    /**
     * Recomputes <em>only</em> the open rect. Not the bands, not the gap, not the dock rect: those
     * are frozen for the life of the transition on purpose, and a layout pass provoked by the system
     * IME rising is precisely the moment re-reading them would bake the transforms already applied
     * into their captured tops.
     */
    private void onHostLayoutChanged() {
        if (!mEngaged) return;
        Frame openRect = resolveOpenRect();
        if (openRect == null || openRect.equals(mOpenRect)) return;
        mOpenRect = openRect;
        if (mPlane != null) mPlane.setContentInsets(openRect);
        applyFrame(mProgress.value);
    }

    /**
     * Over the system wallpaper the plane's {@code RealtimeBlurView} can only blur the window's own
     * (mostly transparent) content and renders grey mud, so a crop of the shared pre-blurred
     * wallpaper frame stands in. When that crop cannot be cut — the user turned dock blur off, or a
     * live wallpaper makes the pre-blur impossible — the live blur is <em>not</em> a fallback in
     * wallpaper mode: the plane is simply tinted glass over whatever is behind it.
     */
    private void applyBackdropMaterial() {
        ImageView frost = mActivity.findViewById(R.id.app_drawer_wallpaper_backdrop);
        View blur = mActivity.findViewById(R.id.app_drawer_blur);
        boolean frosted = frost != null && mActivity.applyAppDrawerWallpaperFrost(frost);
        if (blur == null) return;
        TermuxAppSharedPreferences preferences = mActivity.getPreferences();
        boolean wallpaperMode = preferences != null && preferences.isUseSystemWallpaperEnabled();
        // Frosted wallpaper mode keeps the live blur ON TOP of the frost: the blur can see the
        // window's own content (the running terminal), so it ghosts through the glass while the
        // frost keeps covering the wallpaper the blur cannot see. Only the unfrosted live
        // wallpaper case still drops the blur (grey mud).
        blur.setVisibility(frosted || !wallpaperMode ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ per-frame

    /**
     * The single writer of the transition. Order matters only in that the plane frame is resolved
     * first — the rounded accessory recipe pins the keyboard to its bottom edge.
     */
    private void applyFrame(float progress) {
        AppDrawerPlaneView plane = mPlane;
        Frame dockRect = mDockRect;
        Frame openRect = mOpenRect;
        if (plane == null || mHost == null || dockRect == null || openRect == null) return;
        float p = AppDrawerTransitionGeometry.clamp01(progress);

        float lift = -mLiftPx * AppDrawerTransitionGeometry.dockLiftFraction(p);
        Frame frame = AppDrawerTransitionGeometry.resolvePlaneFrame(dockRect, openRect, p, lift);
        // The search keyboard's reveal only ever shortens the plane from the bottom. At k = 0 this
        // is skipped entirely, so an open drawer with nothing typed into it draws the same rectangle
        // it drew in B-1 — down to the allocation, which is why the branch is on k and not on
        // whether a keyboard exists.
        float k = revealFraction();
        if (k > 0f) {
            frame = new Frame(frame.left, frame.top, frame.right,
                AppDrawerTransitionGeometry.resolveSearchPlaneBottom(frame.bottom,
                    capturedPinTopPx(), mCapturedGapPx, k));
        }
        mCurrentRadiusPx = AppDrawerTransitionGeometry.resolveRadiusPx(mSeedRadiusPx,
            mOpenRadiusPx, p);
        plane.setFrame(frame, mCurrentRadiusPx, p);
        if (mGlass != null) mGlass.invalidateOutline();

        // Inverted standardized dim: the scene behind the drawer (terminal, dock) darkens with
        // the drawer's own spring while the glass keeps its opacity. setBackgroundColor reuses
        // the host's ColorDrawable after the first frame, so this stays allocation-free.
        mHost.setBackgroundColor(com.termux.app.GlassBackdropTint.colorFor(p));

        // Glass handoff: the host carries the fade so the frost/blur pane and the painted slab
        // cross over as one surface.
        float handoff = AppDrawerTransitionGeometry.ramp(p, 0f, GLASS_FADE_END);
        mHost.setAlpha(handoff);
        applyAlpha(mAccessorySurface, 1f - handoff);

        // Dock lift rides the two rows, never accessory_stack_container: applyDockImeOffset owns
        // that view's translationY, and writing it here makes the dock jump by the IME lift.
        applyTranslationY(mAppsPager, lift);
        applyTranslationY(mAzRow, lift);

        float rowAlpha = 1f - AppDrawerTransitionGeometry.ramp(p, ROW_FADE_START, ROW_FADE_END);
        applyAlpha(mAzRow, rowAlpha);
        applyAlpha(mIndicatorBand, rowAlpha);
        applyAlpha(mAzFxUnderlay, rowAlpha);
        applyAlpha(mAzFxOverlay, rowAlpha);
        applyAlpha(mAzLabelOverlay, rowAlpha);

        applyAccessoryBands(p, frame.bottom, k);
        applyStatusBand(p);

        AppDrawerDockChoreographyTarget target = mDockTarget;
        if (target != null) target.setDrawerTransitionProgress(p);
    }

    /**
     * The reveal, clamped and gated on there being an in-app keyboard to reveal.
     *
     * <p>Without a keyboard band the fallback channel is the system IME, which is a window of its
     * own that the plane cannot pin itself to — {@code pinTop} would be a band that does not exist —
     * so the reveal stays at zero and the plane keeps its full rectangle.
     */
    private float revealFraction() {
        if (!hasRevealableKeyboard()) return 0f;
        return AppDrawerTransitionGeometry.clamp01(mReveal.value);
    }

    private boolean hasRevealableKeyboard() {
        return mHasBands && mExtraKeysBand != null && mKeyboardBand != null
            && mKeyboardBand.heightPx > 0f;
    }

    /** The captured keyboard top; the plane covers the terminal extra-keys band above this edge. */
    private float capturedPinTopPx() {
        AppDrawerAccessoryChoreography.Band keyboard = mKeyboardBand;
        return keyboard == null ? 0f : keyboard.topPx;
    }

    private void applyAccessoryBands(float p, float planeBottomPx, float reveal) {
        if (!mHasBands || mExtraKeysBand == null || mKeyboardBand == null) return;
        AppDrawerAccessoryChoreography.Result result = AppDrawerAccessoryChoreography.resolve(
            mRoundedStyle, p, mExtraKeysBand, mKeyboardBand, mCapturedGapPx, planeBottomPx);
        // Byte-identical to `result` at reveal 0, so this is unconditional rather than branched.
        result = AppDrawerAccessoryChoreography.blendTowardIdentity(result, reveal);
        applyBand(mExtraKeysView, result.extraKeysTranslationY, result.extraKeysClipTopPx,
            result.extraKeysAlpha);
        applyBand(mKeyboardView, result.keyboardTranslationY, result.keyboardClipTopPx,
            result.keyboardAlpha);
    }

    /**
     * The top band, on the same three channels the bottom ones use. Skipped entirely when no bar
     * was captured — the plane still grows to the host's top edge either way.
     */
    private void applyStatusBand(float p) {
        AppDrawerAccessoryChoreography.Band band = mStatusBand;
        if (band == null) return;
        AppDrawerStatusBandChoreography.Result result = AppDrawerStatusBandChoreography.resolve(
            p, band.heightPx, mStatusCompactHeightPx);
        applyBand(mStatusBarView, result.translationY, result.clipTopPx, result.alpha);
    }

    /**
     * One band, one frame: a translation, a top clip and an alpha. The clip is what shrinks the
     * rounded style's capsule without touching its layout — and therefore without disturbing the
     * padding inside it.
     */
    private void applyBand(@Nullable View view, float translationY, float clipTopPx, float alpha) {
        if (view == null) return;
        view.setTranslationY(translationY);
        view.setAlpha(alpha);
        if (clipTopPx <= 0f) {
            view.setClipBounds(null);
            return;
        }
        int top = Math.min(view.getHeight(), Math.round(clipTopPx));
        mClipScratch.set(0, top, view.getWidth(), view.getHeight());
        view.setClipBounds(mClipScratch);
    }

    private static void applyAlpha(@Nullable View view, float alpha) {
        if (view != null) view.setAlpha(alpha);
    }

    private static void applyTranslationY(@Nullable View view, float translationY) {
        if (view != null) view.setTranslationY(translationY);
    }

    // ------------------------------------------------------------------ teardown

    /**
     * Settled shut: hand every transformed view back exactly as it was found, drop the full-screen
     * frost bitmap, and replay the accessory geometry the freeze suppressed.
     *
     * <p>The flush is in a {@code finally} and the engaged flag is cleared beside it, because a
     * throw anywhere in the restore would otherwise leave {@code setTerminalToolbarHeight} and
     * {@code applyAccessoryGeometryIfNeeded} suppressed for the life of the activity — a dock that
     * silently stops responding to every style and height change.
     */
    private void onClosed() {
        try {
            // First, and before anything that can throw: a full-screen grid left interactive and
            // VISIBLE over the terminal swallows every touch, and does it silently.
            applyContentOpenState();
            AppDrawerContentView content = mContent;
            if (content != null) {
                content.cancelCloseDrag();
                // Routed through the grid rather than straight at the search, so the pill, the
                // scroll position and the reveal request are emptied with the query.
                content.resetSearch();
                content.disarm();
                content.stopOverpullSpring();
                // A re-open must never start from a stale chain or a dim cell: the rope goes back to
                // the straight rest line and the highlight releases every attached child to 1/1.
                content.resetDrawerFx();
                content.setVisibility(View.INVISIBLE);
            } else {
                // The query lives on the controller and outlives any view: a drawer closed before
                // its grid was ever built must still reopen empty.
                mSearch.reset();
            }
            mReveal.reset(0f);
            removeHostLayoutListener();
            applyAlpha(mAccessorySurface, 1f);
            applyTranslationY(mAppsPager, 0f);
            applyTranslationY(mAzRow, 0f);
            applyAlpha(mAzRow, 1f);
            applyAlpha(mIndicatorBand, 1f);
            applyAlpha(mAzFxUnderlay, 1f);
            applyAlpha(mAzFxOverlay, 1f);
            applyAlpha(mAzLabelOverlay, 1f);
            applyBand(mExtraKeysView, 0f, 0f, 1f);
            applyBand(mKeyboardView, 0f, 0f, 1f);
            // Unconditional, unlike applyStatusBand's null guard: a bar that was captured and then
            // hidden mid-transition must still be handed back untransformed.
            applyBand(mStatusBarView, 0f, 0f, 1f);
            AppDrawerDockChoreographyTarget target = mDockTarget;
            if (target != null) target.setDrawerTransitionProgress(0f);
            if (mHost != null) {
                mHost.setAlpha(1f);
                mHost.setVisibility(View.INVISIBLE);
            }
            if (mGlass != null) mGlass.setVisibility(View.INVISIBLE);
            ImageView frost = mActivity.findViewById(R.id.app_drawer_wallpaper_backdrop);
            if (frost != null) {
                frost.setImageDrawable(null);
                frost.setVisibility(View.GONE);
            }
            mHasBands = false;
            mStatusBand = null;
        } finally {
            mEngaged = false;
            mActivity.flushPendingAccessoryGeometry();
        }
    }

    /**
     * The two things that must track {@link #mOpen} exactly and never {@code p}: whether the grid
     * answers touches, and whether the in-app keyboard's single interceptor slot is the drawer's.
     *
     * <p>The slot is shared with the command palette, which is why this is driven from the open
     * state and released the instant the drawer decides to close — the palette closes the drawer
     * before installing its own interceptor, and the two must never both think they hold it.
     */
    private void applyContentOpenState() {
        AppDrawerContentView content = mContent;
        if (content != null) content.setInteractive(mOpen);
        mActivity.setAppDrawerInterceptorActive(mOpen);
    }

    private float dp(float value) {
        return value * mDensity;
    }
}
