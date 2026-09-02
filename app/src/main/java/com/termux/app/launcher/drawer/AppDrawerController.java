package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.SuggestionBarView;
import com.termux.app.dock.DockLayout;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherCategoryPendingApps;
import com.termux.app.launcher.data.LauncherCategorySortState;
import com.termux.app.notice.AppNotice;
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
 * rect: same bounds from {@code accessory_surface_host}, and the same corner radius and horizontal
 * inset the dock itself is laid out with, both read off the one {@link DockLayout} the activity
 * resolves through {@link Host#dockLayout()}. The first tenth of the transition is then a
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

    /** What the plane needs from the activity: its views, its prefs, the dock it grows out of. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int viewId);

        @Nullable TermuxAppSharedPreferences preferences();

        /** The one dock-geometry snapshot the seed rect, radius and inset all come off. */
        @NonNull DockLayout dockLayout();

        /** The launcher row the grid borrows icons, tint and launch ladder from; null before built. */
        @Nullable SuggestionBarView suggestionBar();

        /** Wallpaper frost for the plane's glass; true when the live blur should rest. */
        boolean applyWallpaperFrost(@NonNull ImageView frost);

        /** Re-applies the accessory geometry the engaged plane suppressed. Runs from a finally. */
        void flushPendingAccessoryGeometry();

        /** Claims (or releases) the in-app keyboard's single interceptor slot for the search. */
        void setInterceptorActive(boolean active);

        /** The search asked for a system IME while the terminal keeps focus. */
        void requestSearchKeyboard();
        /** Dismiss the system IME: the drawer is taking the screen and must not open under it. */
        void hideSystemKeyboard();
        /** The drawer is gone; an IME it dismissed on the way in comes back. */
        void restoreSystemKeyboard();

        /**
         * The Android-keyboard search: the drawer's own text field takes focus from the terminal and
         * the system keyboard is shown for it. The built-in keyboard, if enabled, yields without
         * moving — the plane covers it and the accessory stack must not relayout under an open plane.
         */
        void beginTextFieldSearch(@NonNull EditText field);

        /** The search is over: focus returns to the terminal and the keyboard the field showed goes. */
        void endTextFieldSearch(@NonNull EditText field);

        /** The app-drawer settings page, where categorization is run. */
        void openAppDrawerSettings();
    }

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
    /**
     * Closing runs on a stiffer spring: the same 420/41 that makes opening land softly reads as a
     * crawl on the way out — a critically damped spring spends its last hundred milliseconds
     * creeping through the final few percent, and a dismissal should feel decisive.
     */
    /**
     * Closing is not a spring at all: a spring's exponential tail is a crawl through the last few
     * percent however it is tuned, and a dismissal should read as one decisive motion. The plane
     * exits on a fixed-length accelerating curve — it leaves at the speed of the release and speeds
     * up into the dock, ending dead on zero with no tail — scaled by how far it has to travel so a
     * barely-open plane doesn't take the full ride.
     */
    private static final long CLOSE_ANIM_FULL_TRAVEL_MS = 220L;
    private static final long CLOSE_ANIM_MIN_MS = 100L;
    private static final android.view.animation.Interpolator CLOSE_ANIM_CURVE =
        new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f);

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
    /**
     * Where a closing plane is done: ~0.6% of the travel is under a dozen pixels, already inside
     * the dock glass, and cutting there is what makes the close end decisively — the spring tail
     * below this line is pure crawl.
     */
    private static final float CLOSED_EPSILON = 0.006f;
    /** Breathing room between the plane's bottom edge and a system keyboard's top. */
    private static final float IME_GAP_DP = 8f;

    private final Host mHost;
    private final float mDensity;
    private final Spring mProgress = new Spring(0f, STIFFNESS, DAMPING);
    /** True while the plane is exiting on the timed curve instead of the spring. */
    private boolean mCloseTimeAnim;
    private long mCloseAnimStartNanos;
    private float mCloseAnimStartValue;
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
    private final AppDrawerCategoryNudgePolicy mCategoryNudge = new AppDrawerCategoryNudgePolicy();
    /** Read per open: the Android-keyboard search, through the content's own text field. */
    private boolean mTextFieldSearch;
    /** True from the host being asked to focus the field until it is asked to let it go. */
    private boolean mTextFieldSearchBegun;
    /** A field focus owed to the next frame the content is visible on; a hidden view cannot take it. */
    private boolean mTextFieldFocusPending;
    /** The system keyboard's top edge in host coordinates while it is up over the plane; 0 when not. */
    private float mImePinTopPx;
    /** The rectangle the content is currently laid out in; the open rect less any keyboard. */
    @Nullable private Frame mContentRect;
    private boolean mHostClipped;
    private final Rect mHostClipScratch = new Rect();
    @NonNull private AppDrawerLayoutConfig mLayoutConfig = AppDrawerLayoutConfig.defaults();

    private final int[] mHostLocation = new int[2];
    private final int[] mViewLocation = new int[2];
    /** Reused by {@code setClipBounds}, which copies; never handed out. */
    private final Rect mClipScratch = new Rect();

    @Nullable private FrameLayout mHostLayout;
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
    /** The plane's live backdrop blur, held so the settle logic can rest and wake it per frame. */
    @Nullable private com.github.mmin18.widget.RealtimeBlurView mLiveBlur;
    @NonNull private AppDrawerCommitPolicy.Direction mDirection =
        AppDrawerCommitPolicy.Direction.OPENING;
    private float mDownRawY;
    /** Progress the current drag started from; non-zero only when catching a settling plane. */
    private float mGrabProgress;

    private boolean mFrameScheduled;
    private long mLastFrameTimeNanos;

    public AppDrawerController(@NonNull Host host) {
        mHost = host;
        mDensity = host.context().getResources().getDisplayMetrics().density;
        // Wired here rather than with the views: the three intake channels are routed through the
        // activity, which has no idea whether the plane has been built yet, and a search that only
        // answered once a RecyclerView existed would silently let the first keystrokes through to
        // the shell. isSearchActive() reads mOpen, so an unbuilt drawer still claims nothing.
        mSearch.setHost(this);
        TermuxAppSharedPreferences preferences = host.preferences();
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
        mCloseTimeAnim = false;
        mEngaged = true;
        // With the embedded keyboard the IME can never be up here; with the system keyboard it
        // stayed open across the drawer, covering the grid until dismissed by hand. A keyboard the
        // drawer's own text field put up is the search's, and is put away with the search instead.
        if (!closing || !mTextFieldSearchBegun) mHost.hideSystemKeyboard();
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
        if (open) {
            requestSearchKeyboardOnOpenIfEnabled();
            nudgeCategorizationIfPending();
        } else {
            endTextFieldSearchIfBegun();
        }
        retargetReveal();
        mCloseTimeAnim = !open;
        mCloseAnimStartNanos = 0L;
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
        TermuxAppSharedPreferences preferences = mHost.preferences();
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
        boolean moving;
        if (mCloseTimeAnim && !mDragging) {
            if (mCloseAnimStartNanos == 0L) {
                mCloseAnimStartNanos = frameTimeNanos;
                mCloseAnimStartValue = Math.max(0f, mProgress.value);
            }
            long durationMs = Math.max(CLOSE_ANIM_MIN_MS,
                (long) (CLOSE_ANIM_FULL_TRAVEL_MS * mCloseAnimStartValue));
            float t = Math.min(1f, (frameTimeNanos - mCloseAnimStartNanos) / (durationMs * 1e6f));
            if (reducedMotion) t = 1f;
            mProgress.value = mCloseAnimStartValue * (1f - CLOSE_ANIM_CURVE.getInterpolation(t));
            mProgress.vel = 0f;
            moving = t < 1f;
            if (!moving) {
                mCloseTimeAnim = false;
                mProgress.value = 0f;
            }
        } else {
            moving = mProgress.tick(reducedMotion, dt);
        }
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
        payPendingTextFieldFocus();
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
        if (moving || revealMoving || fxMoving || mTextFieldFocusPending) kick();
        // The live blur ghosts the terminal through the glass at the price of a full software
        // re-draw of the decor hierarchy on every frame the window produces — including every
        // frame of a grid scroll. Once the plane has settled fully open, what it blurs is static
        // to the eye, so the blur rests on its last captured frame; any motion of the plane
        // itself (a close, a drag, a re-open) drops the progress out of the settled band and the
        // next frame here wakes it. Scrub and rope frames deliberately do not wake it — they move
        // content above the glass, never what is behind it.
        if (mLiveBlur != null) {
            mLiveBlur.setUpdatesPaused(mOpen && !mDragging
                && mProgress.target >= 1f && mProgress.value >= 0.999f);
        }
    }

    /**
     * The keyboard-on-open preference: a committed open asks for the keyboard itself, so the drawer
     * arrives ready to type instead of waiting for the pill to be tapped.
     *
     * <p>Read here rather than cached with the layout config: the switch takes effect on the next
     * open with no drawer rebuild, and this runs once per open, not per frame.
     */
    private void requestSearchKeyboardOnOpenIfEnabled() {
        AppDrawerContentView content = mContent;
        TermuxAppSharedPreferences preferences = mHost.preferences();
        if (content == null || preferences == null
            || !preferences.isAppLauncherDrawerSearchOnOpenEnabled()) {
            return;
        }
        content.requestSearchKeyboard();
    }

    /**
     * The categories layout's nudge: apps installed since the last categorization run land in
     * "Other" until it is run again, and past a handful of them the drawer says so once, on the
     * open. Silent for every other layout, for a user who has never run it, and until the count
     * changes again.
     */
    private void nudgeCategorizationIfPending() {
        if (mLayoutConfig.viewType != AppDrawerViewType.CATEGORIES) return;
        Context context = mHost.context();
        if (!new LauncherCategorySortState(context).hasRun()) {
            mCategoryNudge.reset();
            return;
        }
        int pending = LauncherCategoryPendingApps.count(context,
            LauncherAppDataProvider.getInstance(context).getAllApps());
        if (!mCategoryNudge.onDrawerOpened(pending)) return;
        AppNotice.shell(context,
            context.getResources().getQuantityString(R.plurals.app_drawer_category_pending_notice,
                pending, pending),
            context.getString(R.string.app_drawer_category_pending_notice_hint), null, false,
            mHost::openAppDrawerSettings);
    }

    /**
     * The content asked for a keyboard — a pill tap, or the keyboard-on-open preference. With the
     * Android-keyboard search on, that is the drawer's own text field and the system keyboard;
     * otherwise it is the terminal-owned request the host has always answered.
     */
    @VisibleForTesting
    void onSearchKeyboardRequested() {
        AppDrawerContentView content = mContent;
        if (mTextFieldSearch && content != null) {
            mTextFieldSearchBegun = true;
            // A committed fling can settle with the content still faded out, and a view that is not
            // shown refuses focus. The frame loop pays the focus the first frame it can.
            if (content.isShown()) {
                mHost.beginTextFieldSearch(content.searchInput());
            } else {
                mTextFieldFocusPending = true;
                kick();
            }
            return;
        }
        mHost.requestSearchKeyboard();
    }

    private void payPendingTextFieldFocus() {
        if (!mTextFieldFocusPending || !mOpen) return;
        AppDrawerContentView content = mContent;
        if (content == null || !content.isShown()) return;
        mTextFieldFocusPending = false;
        mHost.beginTextFieldSearch(content.searchInput());
    }

    private void endTextFieldSearchIfBegun() {
        mTextFieldFocusPending = false;
        if (!mTextFieldSearchBegun) return;
        mTextFieldSearchBegun = false;
        AppDrawerContentView content = mContent;
        if (content != null) mHost.endTextFieldSearch(content.searchInput());
    }

    /**
     * The system keyboard's inset, from the activity's window insets, whenever it changes.
     *
     * <p>The keyboard is a window of its own that the plane cannot pin to a band, so this is how the
     * plane learns where its top edge is: the reveal then shortens the plane to sit above it — and
     * lays the grid out for the space that is left — exactly as it does for the built-in keyboard.
     *
     * @param imeBottomPx the keyboard's height above the window's bottom edge; 0 when hidden
     */
    public void onImeInsetChanged(int imeBottomPx) {
        float pin = resolveImePinTopPx(imeBottomPx);
        if (pin == mImePinTopPx) return;
        mImePinTopPx = pin;
        if (!mEngaged) return;
        retargetReveal();
        applyContentInsets();
        applyFrame(mProgress.value);
    }

    private float resolveImePinTopPx(int imeBottomPx) {
        FrameLayout host = mHostLayout;
        if (host == null || imeBottomPx <= 0 || host.getHeight() <= 0) return 0f;
        View root = host.getRootView();
        int windowHeight = root == null || root.getHeight() <= 0 ? host.getHeight() : root.getHeight();
        host.getLocationInWindow(mViewLocation);
        float pin = windowHeight - imeBottomPx - mViewLocation[1];
        return pin < host.getHeight() ? Math.max(0f, pin) : 0f;
    }

    /**
     * Retargets the search-keyboard reveal. Driven by the content — a first keystroke, a query
     * emptied, a pill tap — and by the system keyboard's inset; never polled.
     */
    private void retargetReveal() {
        AppDrawerContentView content = mContent;
        float target = content != null && mOpen && hasRevealableKeyboard()
            ? AppDrawerTransitionGeometry.clamp01(content.getRevealFraction()) : 0f;
        if (mReveal.target == target) return;
        mReveal.target = target;
        applyContentInsets();
        kick();
    }

    /**
     * Lays the content out for the space the keyboard leaves it. The plane's rectangle shortens with
     * the reveal spring frame by frame, but the grid is re-laid once, at the decision: a grid that
     * kept the full-height layout under a shortened plane would have its last rows under the
     * keyboard, reachable by no amount of scrolling.
     */
    private void applyContentInsets() {
        AppDrawerPlaneView plane = mPlane;
        AppDrawerContentView content = mContent;
        Frame rect = contentRect();
        if (plane == null || content == null || rect == null || rect.equals(mContentRect)) return;
        boolean heightOnly = mContentRect != null && mContentRect.left == rect.left
            && mContentRect.right == rect.right && mContentRect.top == rect.top;
        mContentRect = rect;
        plane.setContentInsets(rect);
        applyContentMetrics(content, rect, heightOnly);
    }

    @Nullable
    private Frame contentRect() {
        Frame openRect = mOpenRect;
        if (openRect == null) return null;
        if (mReveal.target <= 0f || !hasRevealableKeyboard()) return openRect;
        float bottom = pinTopPx() - revealGapPx();
        if (bottom >= openRect.bottom) return openRect;
        return new Frame(openRect.left, openRect.top, openRect.right,
            Math.max(openRect.top, bottom));
    }

    private boolean isReducedMotion() {
        return Settings.Global.getFloat(mHost.context().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    // ------------------------------------------------------------------ views

    private boolean bindViews() {
        if (mPlane != null) return true;
        mHostLayout = mHost.findView(R.id.app_drawer_host);
        mGlass = mHost.findView(R.id.app_drawer_glass);
        if (mHostLayout == null || mGlass == null) return false;
        // The handoff fades a ViewGroup, which by default means an offscreen layer allocated on
        // every frame of the ramp. The children it holds are two translucent surfaces at alpha
        // below 0.1 for a tenth of the transition; the layer buys a blend nobody can see.
        mHostLayout.forceHasOverlappingRendering(false);
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
        AppDrawerPlaneView plane = new AppDrawerPlaneView(mHost.context());
        plane.setCallbacks(this);
        // Added after the glass pane, so it paints over the blur without needing an elevation that
        // would cast a shadow from a full-screen caster.
        mHostLayout.addView(plane, new FrameLayout.LayoutParams(
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
        AppDrawerContentView content = new AppDrawerContentView(mHost.context(),
            mHost.suggestionBar());
        content.setCallbacks(this);
        content.setRevealListener(this::retargetReveal);
        content.setFrameRequestListener(this::requestFrames);
        content.setSearchKeyboardRequestListener(this::onSearchKeyboardRequested);
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
        FrameLayout host = mHostLayout;
        View dock = mHost.findView(R.id.accessory_surface_host);
        if (host == null || dock == null || host.getWidth() <= 0 || host.getHeight() <= 0
            || dock.getWidth() <= 0 || dock.getHeight() <= 0) return false;

        host.getLocationOnScreen(mHostLocation);
        Frame dockRect = frameOf(dock);
        if (dockRect == null) return false;
        mDockRect = dockRect;

        // One dock-geometry snapshot for the whole capture: style, seed radius and (via
        // resolveOpenRect) the outer inset all come off the same value.
        DockLayout dockLayout = mHost.dockLayout();
        mRoundedStyle = dockLayout.capsule;
        mSeedRadiusPx = mRoundedStyle ? dockLayout.capsuleCornerRadiusPx(dock.getHeight()) : 0f;
        mOpenRadiusPx = resolveOpenRadiusPx();

        mOpenRect = resolveOpenRect();

        mTravelPx = AppDrawerTransitionGeometry.resolveOpenTravelPx(host.getHeight(),
            dp(MIN_TRAVEL_DP), dp(MAX_TRAVEL_DP));
        mLiftPx = dp(DOCK_LIFT_DP);
        mSlopPx = ViewConfiguration.get(mHost.context()).getScaledTouchSlop();

        mAccessorySurface = dock;
        mAppsPager = mHost.findView(R.id.apps_bar_viewpager);
        mAzRow = mHost.findView(R.id.apps_bar_az_row);
        mIndicatorBand = mHost.findView(R.id.apps_bar_indicator_band);
        // The A-Z scrub's three effect layers fade on the row's ramp with the row itself. The label
        // overlay is the one that actually matters: it is a match_parent child of
        // activity_termux_root_relative_layout, a sibling of the drawer host that wins in z, so a
        // scrub label left painting would sit on top of the plane rather than behind it.
        mAzFxUnderlay = mHost.findView(R.id.apps_bar_az_fx_underlay);
        mAzFxOverlay = mHost.findView(R.id.apps_bar_az_fx_overlay);
        mAzLabelOverlay = mHost.findView(R.id.apps_bar_az_label_overlay);
        captureBands(dockRect);
        captureStatusBand(dockLayout);
        return true;
    }

    /**
     * The top status bar, measured once — whichever form it is in.
     *
     * <p>Captured like the bottom bands and for the same reason: the pane's height <em>is</em> the
     * terminal's height, so the transition may only transform it. The compact height comes from the
     * dock layout rather than being assumed, because the rounded style's pane is a different size
     * and the collapse channel is the difference between the two.
     *
     * <p>A hidden bar (terminal-only styles, fullscreen) leaves a null band and no writes at all,
     * which is what keeps a pane that is {@code GONE} from being handed a translation it would still
     * be wearing the next time something makes it visible.
     */
    private void captureStatusBand(@NonNull DockLayout dockLayout) {
        mStatusBarView = mHost.findView(R.id.terminal_window_bar_host);
        Frame bar = isBandVisible(mStatusBarView) ? frameOf(mStatusBarView) : null;
        mStatusBand = bar == null ? null
            : new AppDrawerAccessoryChoreography.Band(bar.top, bar.height());
        mStatusCompactHeightPx = dockLayout.compactStatusBarHeightPx;
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
        FrameLayout host = mHostLayout;
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return null;
        host.getLocationOnScreen(mHostLocation);
        // The drawer keeps the dock's outer margin rather than inventing one — same preference,
        // same edge. The horizontal lerp is carried by the plane rect itself, whose seed left/right
        // are the dock's and whose open left/right are this inset.
        int dockInsetPx = mHost.dockLayout().horizontalInsetPx;
        float inset = AppDrawerTransitionGeometry.resolveInsetPx(dockInsetPx, dockInsetPx, 1f);
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
        mExtraKeysView = mHost.findView(R.id.terminal_toolbar_view_pager);
        mKeyboardView = mHost.findView(R.id.inapp_keyboard_container);
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
        if (view == null || mHostLayout == null) return null;
        view.getLocationOnScreen(mViewLocation);
        float left = mViewLocation[0] - mHostLocation[0];
        float top = mViewLocation[1] - mHostLocation[1];
        return new Frame(left, top, left + view.getWidth(), top + view.getHeight());
    }

    /** {@code -1} on the preference means "follow the rounded-surface token", as the dock's does. */
    private float resolveOpenRadiusPx() {
        TermuxAppSharedPreferences preferences = mHost.preferences();
        int configured = preferences == null
            ? -1 : preferences.getAppLauncherDrawerCornerRadius();
        if (configured < 0)
            configured = TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(null, true);
        return dp(configured);
    }

    /** Raises the overlay and picks its backdrop material for this open. */
    private void prepareOverlay() {
        FrameLayout host = mHostLayout;
        FrameLayout glass = mGlass;
        AppDrawerPlaneView plane = mPlane;
        if (host == null || glass == null || plane == null) return;
        TermuxAppSharedPreferences preferences = mHost.preferences();
        // Capped below the user's dock opacity so the drawer glass always stays see-through:
        // the terminal keeps running visibly behind the open drawer.
        float opacity = Math.min(0.45f,
            preferences == null ? 0.5f : preferences.getAppBarOpacity() / 100f);
        int grain = preferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN
            : preferences.getDockGlassGrain();
        plane.applyGlassMaterial(
            InAppKeyboardPaletteFactory.resolveDockGlassBaseColor(mHost.context()),
            MaterialColors.getColor(mHost.context(), com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(mHost.context(), R.color.termux_primary)),
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
        content.setDock(mHost.suggestionBar());
        plane.setContentInsets(openRect);
        mContentRect = openRect;
        content.setSurfaceRadiusPx(mOpenRadiusPx);
        // Which keyboard the search will ask for is decided here, once per open, so a switch flipped
        // in Settings takes effect on the next pull and never mid-search.
        TermuxAppSharedPreferences preferences = mHost.preferences();
        mTextFieldSearch = preferences != null
            && preferences.isAppLauncherDrawerSearchAndroidKeyboardEnabled();
        mSearch.setTextFieldOwnsInput(mTextFieldSearch);
        content.setTextFieldSearch(mTextFieldSearch);
        content.setViewType(mLayoutConfig.viewType);
        applyContentMetrics(content, openRect, false);
        content.bind(LauncherAppDataProvider.getInstance(mHost.context()), mSearch);
        // Visible, but not yet interactive: interactivity is settle()'s to grant, and it grants it
        // from mOpen alone. The plane's own alpha-driven visibility flip on the content host keeps
        // this hidden until the sprout has actually reached it.
        content.setVisibility(View.VISIBLE);
        retargetReveal();
    }

    /** Reconfigures the existing content tree; never enters styling/accessory/activity paths. */
    private void applyLayoutConfig() {
        AppDrawerContentView content = mContent;
        Frame rect = mContentRect != null ? mContentRect : mOpenRect;
        if (content == null || rect == null) return;
        content.cancelTransientFolderState();
        content.setViewType(mLayoutConfig.viewType);
        applyContentMetrics(content, rect, false);
        content.rebindCurrentResults();
    }

    /**
     * Columns, rows and cell sizes for the rectangle the content is laid out in.
     *
     * <p>Every one of these is re-resolved rather than cached: the column count is a function of the
     * plane's width and the icon size of the density, so a rotation that reused the last open's
     * metrics would lay a portrait grid out in landscape. {@code heightOnly} is the keyboard coming
     * or going: the vertical grid's metrics are width-only and skip the rebind, the paged layouts
     * re-chunk their rows for the height that is left.
     */
    private void applyContentMetrics(@NonNull AppDrawerContentView content, @NonNull Frame rect,
                                     boolean heightOnly) {
        float labelHeightPx = resolveCellLabelHeightPx();
        AppDrawerLayoutConfig config = mLayoutConfig;
        switch (config.viewType) {
            case VERTICAL:
                if (heightOnly) return;
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(rect.width()
                    - AppDrawerRopeMetrics.resolveColumnWidthPx(mDensity), mDensity, labelHeightPx,
                    config.verticalColumns, config.iconSizeDp));
                break;
            case HORIZONTAL:
                content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(rect.width(),
                    content.horizontalPagerUsableHeight(rect.height()), mDensity, labelHeightPx,
                    config.horizontalColumns, config.horizontalRows, config.iconSizeDp));
                if (heightOnly) content.rebindCurrentResults();
                break;
            case CATEGORIES:
                SuggestionBarView dock = mHost.suggestionBar();
                int budget = dock == null ? 6 * 1024 * 1024 : dock.getRenderedIconCacheBudgetBytes();
                // Category search temporarily reuses the shipped vertical grid at full width. It
                // gets AUTO geometry here and deliberately reads no vertical grid preference.
                content.setVerticalMetrics(AppDrawerGridMetrics.resolve(rect.width(), mDensity,
                    labelHeightPx, 0, config.iconSizeDp));
                content.setCategoryMetrics(AppDrawerCategoryGridMetrics.resolve(rect.width(),
                    content.horizontalPagerUsableHeight(rect.height()), mDensity,
                    resolveCategoryTileHeadingHeightPx(), labelHeightPx, mOpenRadiusPx, budget,
                    config.categoryColumns, config.iconSizeDp));
                if (heightOnly) content.rebindCurrentResults();
                break;
        }
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
            * mHost.context().getResources().getDisplayMetrics().scaledDensity);
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
            * mHost.context().getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    private void addHostLayoutListener() {
        FrameLayout host = mHostLayout;
        if (host == null || mHostLayoutListener != null) return;
        mHostLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
            -> onHostLayoutChanged();
        host.addOnLayoutChangeListener(mHostLayoutListener);
    }

    private void removeHostLayoutListener() {
        FrameLayout host = mHostLayout;
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
        applyContentInsets();
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
        ImageView frost = mHost.findView(R.id.app_drawer_wallpaper_backdrop);
        View blur = mHost.findView(R.id.app_drawer_blur);
        boolean frosted = frost != null && mHost.applyWallpaperFrost(frost);
        if (blur == null) return;
        mLiveBlur = blur instanceof com.github.mmin18.widget.RealtimeBlurView
            ? (com.github.mmin18.widget.RealtimeBlurView) blur : null;
        // A fresh open always starts live; doFrame rests it again once the plane settles.
        if (mLiveBlur != null) mLiveBlur.setUpdatesPaused(false);
        TermuxAppSharedPreferences preferences = mHost.preferences();
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
        if (plane == null || mHostLayout == null || dockRect == null || openRect == null) return;
        float p = AppDrawerTransitionGeometry.clamp01(progress);

        float lift = -mLiftPx * AppDrawerTransitionGeometry.dockLiftFraction(p);
        Frame frame = AppDrawerTransitionGeometry.resolvePlaneFrame(dockRect, openRect, p, lift);
        // The search keyboard's reveal only ever shortens the plane from the bottom. At k = 0 this
        // is skipped entirely, so an open drawer with nothing typed into it draws the same rectangle
        // it drew in B-1 — down to the allocation, which is why the branch is on k and not on
        // whether a keyboard exists.
        float k = revealFraction();
        if (k > 0f) {
            float pinTop = pinTopPx();
            frame = new Frame(frame.left, frame.top, frame.right,
                AppDrawerTransitionGeometry.resolveSearchPlaneBottom(frame.bottom, pinTop,
                    revealGapPx(), k));
            // The scene dim is the host's background and the host covers the whole screen, keyboard
            // included. Clipped to the edge the reveal is uncovering, so the keyboard comes back at
            // its own brightness rather than under the drawer's shade.
            mHostClipScratch.set(0, 0, mHostLayout.getWidth(), Math.round(
                AppDrawerTransitionGeometry.resolveRevealClipBottom(mHostLayout.getHeight(), pinTop, k)));
            mHostLayout.setClipBounds(mHostClipScratch);
            mHostClipped = true;
        } else if (mHostClipped) {
            mHostLayout.setClipBounds(null);
            mHostClipped = false;
        }
        mCurrentRadiusPx = AppDrawerTransitionGeometry.resolveRadiusPx(mSeedRadiusPx,
            mOpenRadiusPx, p);
        plane.setFrame(frame, mCurrentRadiusPx, p);
        if (mGlass != null) mGlass.invalidateOutline();

        // Inverted standardized dim: the scene behind the drawer (terminal, dock) darkens with
        // the drawer's own spring while the glass keeps its opacity. setBackgroundColor reuses
        // the host's ColorDrawable after the first frame, so this stays allocation-free.
        mHostLayout.setBackgroundColor(com.termux.app.GlassBackdropTint.colorFor(p));

        // Glass handoff: the host carries the fade so the frost/blur pane and the painted slab
        // cross over as one surface.
        float handoff = AppDrawerTransitionGeometry.ramp(p, 0f, GLASS_FADE_END);
        mHostLayout.setAlpha(handoff);
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

        // The built-in keyboard's bands come back with the reveal only when they are what is being
        // revealed; under a system keyboard they stay where the plane pushed them.
        applyAccessoryBands(p, frame.bottom, mImePinTopPx > 0f ? 0f : k);
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
        if (mImePinTopPx > 0f) return true;
        return !mTextFieldSearch && mHasBands && mExtraKeysBand != null && mKeyboardBand != null
            && mKeyboardBand.heightPx > 0f;
    }

    /** The edge the plane shortens to: the system keyboard's top when it is up, else the band's. */
    private float pinTopPx() {
        return mImePinTopPx > 0f ? mImePinTopPx : capturedPinTopPx();
    }

    private float revealGapPx() {
        return mImePinTopPx > 0f ? dp(IME_GAP_DP) : mCapturedGapPx;
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
            endTextFieldSearchIfBegun();
            mImePinTopPx = 0f;
            mContentRect = null;
            if (mHostLayout != null && mHostClipped) {
                mHostLayout.setClipBounds(null);
                mHostClipped = false;
            }
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
            if (mHostLayout != null) {
                mHostLayout.setAlpha(1f);
                mHostLayout.setVisibility(View.INVISIBLE);
            }
            if (mGlass != null) mGlass.setVisibility(View.INVISIBLE);
            ImageView frost = mHost.findView(R.id.app_drawer_wallpaper_backdrop);
            if (frost != null) {
                frost.setImageDrawable(null);
                frost.setVisibility(View.GONE);
            }
            mHasBands = false;
            mStatusBand = null;
        } finally {
            mEngaged = false;
            mHost.flushPendingAccessoryGeometry();
            mHost.restoreSystemKeyboard();
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
        mHost.setInterceptorActive(mOpen);
    }

    private float dp(float value) {
        return value * mDensity;
    }
}
