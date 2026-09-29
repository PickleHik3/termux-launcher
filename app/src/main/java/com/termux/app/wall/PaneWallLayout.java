package com.termux.app.wall;

import android.content.Context;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import com.termux.app.chrome.CornerZones;
import com.termux.view.HoldTiming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The pane wall: fixed, full-size places side by side — Widgets, Terminal, Display — of which
 * exactly one is on screen at rest. It wraps the terminal's pane host as its middle page, so the
 * terminal never resizes for the wall and {@code TerminalPaneController} never learns the wall
 * exists.
 *
 * <p>Not a {@code ViewPager2}: its pages must never be recycled (a recreated terminal or X
 * surface is a lost session), and its centre page runs its own
 * {@code requestDisallowInterceptTouchEvent} traffic. Three children, one offset and one spring
 * is the whole mechanism. The one touch the wall reads for itself is the border drag
 * ({@link BorderDrag}): a press held on the current page's border, then dragged sideways, which
 * is how a finger pages the wall on every place and in every mode — and, on the bottom border, a
 * vertical swipe without the hold, which carries the keyboard up or down under the finger
 * ({@link KeyboardReveal}) and is marked by a small grabber ({@link KeyboardGrabber}). The window
 * strip's overswipe drives the same drag from outside.
 *
 * <p>Every page is laid out at the host's size and moved with {@code translationX}, so a page
 * change and a whole drag cost no layout work. Only the pages on screen are laid out at all: a
 * page parked off screen keeps its last layout — the widget grid is not re-cut, the display's
 * surface not resized, for a change to the frame it cannot be seen in — and is laid out again in
 * the frame that brings it back (see {@link #applyPagePositions}).
 *
 * <p>The terminal page's margins are every page's margins. The activity lays the pane host out
 * inside the frame insets the surface editor decides — the dock's side gap, the border's air —
 * by setting margins on it, and the places beside it have to sit inside the same frame or the
 * wall reads as three differently sized sheets. So the wall reads the terminal page's
 * {@link MarginLayoutParams} and applies them to all of its pages; margins on the other pages are
 * ignored.
 */
public final class PaneWallLayout extends ViewGroup {

    /**
     * A slide is a spring ({@link SettleSpring}) that starts where the finger let go and at the
     * finger's own speed, so the release has no seam, and lands without passing its rest. From
     * still — a tile, a key, {@code wall.go} — a whole width takes about the 560 ms the terminal's
     * window pan takes; a flick is carried on by its own speed, and one thrown hard near its rest
     * stiffens the spring rather than overshooting. The same time whatever the destination, too:
     * the slide keeps its own clock ({@link WallSlideClock}), so the frames an arrival costs
     * stretch it rather than skip it.
     */
    static final float SLIDE_OMEGA = 18f;
    static final float SLIDE_MAX_OMEGA = 60f;
    /** How close to rest, in px, the slide counts as landed. */
    private static final float SLIDE_REST_PX = 0.5f;
    /**
     * The fastest release, in px per second, the slide carries on from: a stray tracker reading
     * is not a throw.
     */
    private static final float SLIDE_MAX_VELOCITY_PX_PER_SEC = 12000f;
    /**
     * How much longer than its way the slide's ticker is allowed to run before the slide is cut
     * short and settles where it stands: the ceiling under which a run of long frames can stretch
     * it.
     */
    private static final int SLIDE_STRETCH_CEILING = 2;

    public interface Listener {
        /** The wall has committed to a different page; the slide may still be running. */
        default void onWallPageChanged(@NonNull PaneWallPage page) { }
        /** The slide has stopped, with {@code page} at rest on screen. */
        default void onWallPageSettled(@NonNull PaneWallPage page) { }
        /** The wall moved: {@code offsetPx} is signed distance from the current page's rest. */
        default void onWallOffsetChanged(float offsetPx) { }
        /**
         * The Terminal page just went fully off screen, or just came back. Unlike the other
         * places it is never {@code INVISIBLE} (see {@link #applyPagePositions}), so nothing in
         * the view hierarchy notices it leaving on its own any more; this is the signal that
         * stands in for that, for the things a hidden terminal has to pause — kitty animations,
         * the cursor blinker, focus, accessibility — across every pane the terminal page holds.
         */
        default void onTerminalOffScreenChanged(boolean offScreen) { }
        /**
         * A drag was under way and something else moved the wall — {@link #goTo}, or the
         * gestures being switched off. Whoever was driving the drag has to let go of the finger:
         * the wall will ignore it from here on, and a claimant that keeps streaming to it is
         * holding a gesture nobody answers.
         */
        default void onWallDragInterrupted() { }
        /**
         * Whether {@code page} tips like a plank ({@link PlankTilt}) under a border drag — Fancier
         * Glass, with the phone animating. Asked as the drag claims the finger, for the page the
         * wall rests on and each page beside it, since the page arriving tips too.
         */
        default boolean isPlankTiltEnabled(@NonNull PaneWallPage page) { return false; }
        /**
         * Whether a vertical swipe off the current page's bottom border is the keyboard's
         * ({@link BorderDrag#KEYBOARD_REACH_DP}); asked as each finger lands, and by the grabber
         * that marks the border ({@link KeyboardGrabber}), which is drawn only while it is.
         */
        default boolean isBorderKeyboardSwipeEnabled() { return false; }
        /**
         * A swipe off the bottom border asked for the keyboard, up to open and down to close:
         * the release-only path, for a swipe the keyboard could not follow
         * ({@link #onKeyboardRevealBegin} answered 0).
         */
        default void onBorderKeyboardSwipe(boolean open) { }
        /**
         * A keyboard swipe was just claimed, going up ({@code opening}) or down: the keyboard
         * is to follow the finger from here ({@link KeyboardReveal}). Answers the height in px
         * the finger drives it over — the keyboard's own — or 0 where it cannot follow (a
         * floating keyboard, the phone's own, one switched off, one already where the swipe
         * points), and then the release asks through {@link #onBorderKeyboardSwipe} as it always
         * has. Answering more than 0 takes the keyboard until {@link #onKeyboardRevealEnd}.
         */
        default int onKeyboardRevealBegin(boolean opening) { return 0; }
        /** How much of the keyboard shows for this frame of the swipe or its settle, 0 to 1. */
        default void onKeyboardRevealProgress(float reveal) { }
        /**
         * The keyboard the swipe drove came to rest, up ({@code open}) or down: where it began
         * for a swipe let go short or cancelled, the other state for one that went through.
         */
        default void onKeyboardRevealEnd(boolean open) { }
        /**
         * The page a held border sank ({@link PageSink}) is drawn at {@code scale} about its
         * centre, 1 once it is back at rest: for chrome drawn outside the page that frames it.
         */
        default void onPageSinkChanged(@NonNull PaneWallPage page, float scale) { }
    }

    private final Map<PaneWallPage, View> mPageViews = new EnumMap<>(PaneWallPage.class);
    private List<PaneWallPage> mPages = Collections.singletonList(PaneWallPage.TERMINAL);
    @NonNull private PaneWallPage mCurrent = PaneWallPage.TERMINAL;
    @Nullable private Listener mListener;

    /** Signed distance from the current page's rest position, in px. */
    private float mOffsetPx;
    @Nullable private ValueAnimator mSlide;
    private boolean mSliding;
    private boolean mDragging;
    /** One page slid in from beside its place on top of wherever the wall puts it. */
    @Nullable private PaneWallPage mNudgePage;
    private float mNudgePx;
    @Nullable private ValueAnimator mNudge;
    private boolean mReducedMotion;
    private boolean mGesturesEnabled = true;
    /**
     * Whether the last {@link #applyPagePositions} call reported the Terminal page off screen, or
     * {@code null} before the first call, so that first call always tells the listener where it
     * stands rather than only on a change from some assumed starting state.
     */
    @Nullable private Boolean mTerminalOffScreen;
    /** The border drag under way, if any; one instance, so a gesture allocates nothing. */
    private final BorderDrag mBorderDrag = new BorderDrag();
    /** Where the armed finger landed, in the wall's coordinates: what the child's cancel says. */
    private float mBorderDownX;
    private float mBorderDownY;
    /** Held from the claim to the lift, for the release velocity the settle reads. */
    @Nullable private VelocityTracker mBorderVelocity;
    /**
     * Its own handler rather than {@link View#postDelayed}: a detached view queues those until
     * it is attached, and the hold has to fire or be cancelled on its own clock.
     */
    private final Handler mHoldHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onBorderHoldElapsed;
    /** True only inside {@link #cancelChildGesture()}: that cancel is the child's, not the stream's. */
    private boolean mCancellingChild;
    /**
     * The places whose pages tip under this drag ({@link PlankTilt}): the one the wall rests on
     * and the ones beside it that the listener lets tip, from the drag's claim to the wall's
     * settle; empty while no plank is engaged.
     */
    private final java.util.EnumSet<PaneWallPage> mPlankPlaces =
        java.util.EnumSet.noneOf(PaneWallPage.class);
    /**
     * Those places' pages that are tipping now, each on a hardware layer. A page joins once it
     * has been laid out, since its pivot is its own centre. Their angles are written beside their
     * translations in {@link #applyPagePositions}, so the tilt and the slide are one motion.
     */
    private final List<View> mTiltPages = new ArrayList<>(3);
    /**
     * The page the finger held and how far from its centre line it pressed, in px: the weight
     * every plank leans toward ({@link PlankTilt#lean}). It stays where it pressed on that page,
     * so after the lift it travels with the page and the planks keep leaning the same way while
     * the settle lays them flat.
     */
    @Nullable private View mWeightPage;
    private float mWeightOffsetPx;
    /** The keyboard swipe's grabber on the current page's bottom border. */
    @NonNull private final KeyboardGrabber mGrabber;
    /** The wall offset and sink the grabber was last drawn for, so a still wall redraws nothing. */
    private float mGrabberDrawnOffsetPx = Float.NaN;
    private float mGrabberDrawnSink = Float.NaN;
    /**
     * The keyboard's height the finger drives the reveal over ({@link KeyboardReveal}), from a
     * keyboard swipe the listener took ({@link Listener#onKeyboardRevealBegin}) until the settle
     * lands; 0 while no reveal is engaged.
     */
    private int mRevealTravelPx;
    /** Whether the keyboard was up when the engaged reveal began. */
    private boolean mRevealFromOpen;
    /** How much of the keyboard shows: 0 down, 1 up. */
    private float mReveal;
    /** The reveal, and the finger's y, the finger is measured from. */
    private float mRevealStart;
    private float mRevealAnchorY;
    /** Where the settle running is taking the keyboard. */
    private boolean mRevealTarget;
    @Nullable private ValueAnimator mRevealSpring;
    /**
     * The page a held border sank ({@link PageSink}), from the hold's claim until it has sprung
     * back to rest after the lift, and its place; null while none is.
     */
    @Nullable private View mSinkPage;
    @Nullable private PaneWallPage mSinkPlace;
    /** How far {@link #mSinkPage} is down: 0 at rest, 1 fully sunk, a little past either mid-spring. */
    private float mSink;
    /** The held side border for the plank's press: -1 left, +1 right, 0 top or bottom. */
    private int mSinkSide;
    /**
     * Whether the sunk page is dimmed, on a hardware layer. Not a Display page showing its live
     * surface: a SurfaceView follows a scale on its frame but would be stranded by a layer. Its
     * stand-in ({@link SurfacePage}) takes the layer, from the moment it is up.
     */
    private boolean mSinkLayered;
    @Nullable private ValueAnimator mSinkSpring;
    /** The sunk page's layer paint; its colour filter is the dim, one cached filter per level. */
    private final Paint mSinkPaint = new Paint();
    private static final int SINK_DIM_STEPS = 16;
    @Nullable private LightingColorFilter[] mSinkDimFilters;
    private int mSinkDimLevel = -1;
    /**
     * The places whose surface pages ({@link SurfacePage}) were asked to hold still for the motion
     * under way, from its start until the wall rests with every sink risen.
     */
    private final java.util.EnumSet<PaneWallPage> mStillPlaces =
        java.util.EnumSet.noneOf(PaneWallPage.class);

    public PaneWallLayout(@NonNull Context context) {
        this(context, null);
    }

    public PaneWallLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        mGrabber = new KeyboardGrabber(this);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    /**
     * Register the view that is one of the wall's places. Pass null to take a place away; the
     * view itself must already be a child of this layout (the terminal page comes from the
     * layout file, the others are added by their controllers).
     */
    public void setPageView(@NonNull PaneWallPage page, @Nullable View view) {
        View previous = mPageViews.get(page);
        if (previous != null && previous == mSinkPage && previous != view) finishSink();
        if (previous != null && previous != view && mTiltPages.contains(previous)) {
            releasePlankPage(previous);
        }
        if (previous != null && previous != view && previous == mWeightPage) mWeightPage = null;
        // A surface page leaving the wall lets go of its stand-in and the copy it kept.
        if (previous instanceof SurfacePage && previous != view) {
            mStillPlaces.remove(page);
            ((SurfacePage) previous).dropStill();
        }
        if (view == null) mPageViews.remove(page);
        else mPageViews.put(page, view);
        applyPagePositions();
    }

    @Nullable
    public View pageView(@NonNull PaneWallPage page) {
        return mPageViews.get(page);
    }

    /**
     * The places this install has, in spatial order (see {@link PaneWallPolicy#availablePages}).
     * A page that goes away while it is showing hands the wall back to the terminal.
     */
    public void setPages(@NonNull List<PaneWallPage> pages) {
        List<PaneWallPage> resolved = new ArrayList<>(pages);
        if (!resolved.contains(PaneWallPage.TERMINAL)) resolved.add(PaneWallPage.TERMINAL);
        if (resolved.equals(mPages)) return;
        mPages = Collections.unmodifiableList(resolved);
        if (!mPages.contains(mCurrent)) {
            mCurrent = PaneWallPolicy.homePage();
            notifyPageChanged();
        }
        applyPagePositions();
    }

    @NonNull
    public List<PaneWallPage> pages() {
        return mPages;
    }

    @NonNull
    public PaneWallPage currentPage() {
        return mCurrent;
    }

    /** True while the wall is anywhere but at rest on its current page. */
    public boolean isMoving() {
        return mDragging || mSliding;
    }

    /** True while a finger is driving the wall. */
    public boolean isDragging() {
        return mDragging;
    }

    /**
     * Signed distance of the wall from the current page's rest, in px: positive when the pages
     * sit to the right of where they will land, so the current page is arriving from the right.
     */
    public float offsetPx() {
        return mOffsetPx;
    }

    /**
     * Whether {@code page}'s pixels are on screen, read off the page view itself rather than off
     * the record of which page is current. The two agree whenever the wall rests where it says it
     * does; a slide that was cut short leaves them apart, and then the pixels are the truth.
     * Before the first layout there are no pixels to ask, so the record answers.
     *
     * <p>The Terminal page stays {@code VISIBLE} even off screen (see {@link #applyPagePositions}),
     * so this reads its translation alone for it; the two-part check below still answers correctly
     * for it too, since an off-screen Terminal page's translation is never inside the width either.
     */
    public boolean isPageOnScreen(@NonNull PaneWallPage page) {
        View view = mPageViews.get(page);
        if (view == null || !mPages.contains(page)) return false;
        int width = getWidth();
        if (width <= 0) return page == mCurrent;
        return view.getVisibility() == VISIBLE && Math.abs(view.getTranslationX()) < width;
    }

    /** True while the wall is at rest but not where its record says: a slide was cut short. */
    public boolean isRestingOffPage() {
        return !isMoving() && mOffsetPx != 0f;
    }

    /**
     * Slide {@code page} in from {@code fromPx} beside its place, on top of wherever the wall puts
     * it. The window bar's arrival used to animate the terminal page's own translation, which the
     * wall writes too; every movement of a page goes through the wall now so the position has one
     * owner. Only the page the wall rests on is moved; a wall that starts moving drops the nudge;
     * {@code onEnd} runs however it ends.
     */
    public void nudgePage(@NonNull PaneWallPage page, float fromPx, long durationMs,
                          @Nullable android.view.animation.Interpolator interpolator,
                          @Nullable Runnable onEnd) {
        stopNudge();
        // Only the page the wall rests on is nudged: a page the wall has put away would otherwise
        // slide across the place the user is looking at.
        if (mPageViews.get(page) == null || page != mCurrent || isMoving() || mOffsetPx != 0f
                || fromPx == 0f) {
            if (onEnd != null) onEnd.run();
            return;
        }
        mNudgePage = page;
        mNudgePx = fromPx;
        applyPagePositions();
        ValueAnimator nudge = ValueAnimator.ofFloat(fromPx, 0f);
        nudge.setDuration(durationMs);
        if (interpolator != null) nudge.setInterpolator(interpolator);
        nudge.addUpdateListener(animation -> {
            mNudgePx = (Float) animation.getAnimatedValue();
            applyPagePositions();
        });
        nudge.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mNudge == animation) mNudge = null;
                mNudgePage = null;
                mNudgePx = 0f;
                applyPagePositions();
                if (onEnd != null) onEnd.run();
            }
        });
        mNudge = nudge;
        nudge.start();
    }

    private void stopNudge() {
        ValueAnimator nudge = mNudge;
        mNudge = null;
        mNudgePage = null;
        mNudgePx = 0f;
        // Cancelling runs the end listener, which repositions the page and runs onEnd.
        if (nudge != null) nudge.cancel();
    }

    /** Off while another surface owns the gesture (the surface editor, for one). */
    public void setGesturesEnabled(boolean enabled) {
        boolean changed = enabled != mGesturesEnabled;
        mGesturesEnabled = enabled;
        // The grabber marks a gesture that is on: it goes and comes back with it.
        if (changed) invalidate();
        // A keyboard swipe under way asks for nothing once another surface owns the gesture, and
        // a keyboard it was carrying lands at once where it was going.
        if (!enabled && mBorderDrag.isKeyboardSwipe()) mBorderDrag.abandon();
        if (!enabled) settleKeyboardRevealNow();
        if (enabled || !mDragging) return;
        // The claimant is told to let go, and the wall goes back to rest on its own — nothing
        // else is going to release this drag now.
        interruptDrag();
        if (mReducedMotion) settleImmediately();
        else startSlide(0f);
    }

    public boolean areGesturesEnabled() {
        return mGesturesEnabled;
    }

    // ---- Navigation ------------------------------------------------------------------------

    /** Go to {@code page}, sliding unless {@code animate} is false or motion is reduced. */
    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        return goTo(page, animate, 0f);
    }

    /**
     * The same, with the slide starting at {@code velocityPxPerSec}: a released drag's own speed,
     * so its settle carries on from the finger rather than from still.
     */
    private boolean goTo(@NonNull PaneWallPage page, boolean animate, float velocityPxPerSec) {
        if (!mPages.contains(page)) return false;
        interruptDrag();
        // The wall is about to move: a keyboard still settling from a swipe lands first.
        settleKeyboardRevealNow();
        if (page == mCurrent && mOffsetPx == 0f) return true;
        stopNudge();
        // Carry the current visual position across the page change: the new page's rest is one
        // width away per step, so the wall keeps drawing where it already was and springs from
        // there instead of jumping. On a ring the step is the shorter way round, which is also
        // the side the page's tile sits on.
        mOffsetPx += PaneWallPolicy.relativePosition(mPages, mCurrent, page) * (float) getWidth();
        mCurrent = page;
        notifyPageChanged();
        if (!animate || mReducedMotion || getWidth() <= 0) {
            // A jump lands everything at rest at once, a sunk page included.
            finishSink();
            settleImmediately();
        } else {
            startSlide(velocityPxPerSec);
        }
        return true;
    }

    /** Go one place left ({@code -1}) or right ({@code +1}). */
    public boolean goBy(int steps, boolean animate) {
        return goTo(PaneWallPolicy.neighbour(mPages, mCurrent, steps), animate);
    }

    // ---- Dragging (the border drag, and the window strip's overswipe from outside) ----------

    /** Take a drag from outside — the window strip's overswipe. The page slides flat. */
    public void beginDrag() {
        beginDrag(false, 0f);
    }

    /**
     * @param plank whether the pages under the finger tip ({@link PlankTilt}), which is the
     *              border drag's own motion: a finger on the pane's edge pressing its weight in
     * @param fingerX where that finger pressed, in the wall's coordinates
     */
    private void beginDrag(boolean plank, float fingerX) {
        if (!mGesturesEnabled) return;
        Trace.beginSection("Wall.beginDrag");
        try {
            // A keyboard still settling from a swipe lands before the wall moves.
            settleKeyboardRevealNow();
            mDragging = true;
            stopSlide();
            stopNudge();
            // Before the plank and the sink look: a surface page with a copy to hand is still at
            // once, and takes them from the first frame.
            holdStillAround();
            if (plank) engagePlank(fingerX);
        } finally {
            Trace.endSection();
        }
    }

    /** Move the wall for a finger that has travelled {@code dxPx} since it went down. */
    public void dragTo(float dxPx) {
        if (!mDragging) return;
        Trace.beginSection("Wall.dragTo");
        try {
            int width = getWidth();
            mOffsetPx = PaneWallPolicy.offsetForDrag(dxPx, width,
                PaneWallPolicy.hasNeighbour(mPages, mCurrent, -1),
                PaneWallPolicy.hasNeighbour(mPages, mCurrent, 1));
            applyPagePositions();
        } finally {
            Trace.endSection();
        }
    }

    /** Release the drag at {@code velocityPxPerSec} (positive to the right). */
    public void endDrag(float velocityPxPerSec) {
        if (!mDragging) return;
        mDragging = false;
        // The finger let go: a sunk page springs back up as the wall carries it.
        riseSink();
        boolean previousExists = PaneWallPolicy.hasNeighbour(mPages, mCurrent, -1);
        boolean nextExists = PaneWallPolicy.hasNeighbour(mPages, mCurrent, 1);
        int steps = PaneWallPolicy.settle(mOffsetPx, velocityPxPerSec, getWidth(),
            getResources().getDisplayMetrics().density, previousExists, nextExists);
        // The wall's own speed at the release, which the settle carries on from.
        float velocity = PaneWallPolicy.wallVelocity(mOffsetPx, velocityPxPerSec, previousExists,
            nextExists);
        if (steps == 0) {
            if (mReducedMotion) settleImmediately();
            else startSlide(velocity);
            return;
        }
        goTo(PaneWallPolicy.neighbour(mPages, mCurrent, steps), true, velocity);
    }

    /** The claimant let go without a release (its stream was cancelled). */
    public void cancelDrag() {
        if (!mDragging) return;
        mDragging = false;
        riseSink();
        if (mReducedMotion) settleImmediately();
        else startSlide(0f);
    }

    /**
     * The wall is taking over from a live drag. Unlike {@link #cancelDrag}, the claimant did not
     * ask for this, so it is told: the rest of that finger's motion is not the wall's to answer.
     */
    private void interruptDrag() {
        if (!mDragging) return;
        mDragging = false;
        riseSink();
        // The wall's own border drag is a claimant like the others: the rest of its finger's
        // travel is swallowed and moves nothing.
        mBorderDrag.abandon();
        if (mListener != null) mListener.onWallDragInterrupted();
    }

    // ---- The border drag ---------------------------------------------------------------------

    /**
     * Every touch on the wall passes through here, whoever ends up with it. A finger down on the
     * current page's border arms the drag ({@link BorderDrag}) and starts the hold's timer; the
     * child under it gets the DOWN and everything after, exactly as if the border were not there,
     * until the hold fires on a finger that has held still. The claim is made by the timer, not
     * by a touch, and it is made here rather than in {@code onInterceptTouchEvent} because the
     * pane's own overlay forbids interception for the whole of a gesture it starts, and a border
     * drag has to be able to begin over it.
     *
     * <p>Once claimed, the stream is the wall's: the child was told to forget it
     * ({@link #cancelChildGesture}), so the terminal has released whatever mouse button or hold
     * it had begun, and every later event is read here and goes no further.
     *
     * <p>The keyboard swipe is claimed the same way, by the move rather than by the timer: a
     * finger that sets off up or down from the bottom border before the hold is the keyboard's
     * ({@link BorderDrag#move}). Where the listener can move the keyboard with it, the keyboard
     * follows the finger and the lift settles it ({@link KeyboardReveal}); elsewhere the lift
     * asks the listener to open or close it.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mCancellingChild) return super.dispatchTouchEvent(event);
        boolean handled = dispatchBorderTouch(event);
        // The grabber answers whatever the event made of the finger on the band.
        syncGrabberPress(event);
        return handled;
    }

    private boolean dispatchBorderTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                armBorderDrag(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mBorderDrag.isPaging()) {
                    if (mBorderVelocity != null) mBorderVelocity.addMovement(event);
                    dragTo(mBorderDrag.travel(event.getX()));
                    return true;
                }
                if (mBorderDrag.isKeyboardSwipe()) {
                    if (mBorderVelocity != null) mBorderVelocity.addMovement(event);
                    trackKeyboardReveal(event.getY());
                    return true;
                }
                BorderDrag.Claim claim = mBorderDrag.move(event.getX(), event.getY());
                if (claim == BorderDrag.Claim.KEYBOARD) {
                    claimKeyboardSwipe(event);
                    return true;
                }
                if (claim == BorderDrag.Claim.ABANDONED) {
                    mHoldHandler.removeCallbacks(mHoldElapsed);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mBorderDrag.isPaging() || mBorderDrag.isKeyboardSwipe()) return true;
                if (mBorderDrag.secondPointer() == BorderDrag.Claim.ABANDONED) {
                    mHoldHandler.removeCallbacks(mHoldElapsed);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mBorderDrag.isPaging() || mBorderDrag.isKeyboardSwipe()) return true;
                break;
            case MotionEvent.ACTION_UP:
                if (mBorderDrag.isKeyboardSwipe()) {
                    releaseKeyboardSwipe(event);
                    return true;
                }
                if (mBorderDrag.isPaging()) {
                    float velocity = 0f;
                    if (mBorderVelocity != null) {
                        mBorderVelocity.addMovement(event);
                        mBorderVelocity.computeCurrentVelocity(1000);
                        velocity = mBorderVelocity.getXVelocity();
                    }
                    releaseBorderDrag();
                    endDrag(velocity);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mBorderDrag.isPaging()) {
                    releaseBorderDrag();
                    cancelDrag();
                    return true;
                }
                if (mBorderDrag.isKeyboardSwipe()) {
                    // Cancelled from above: the keyboard goes back to how it was.
                    releaseBorderDrag();
                    if (mRevealTravelPx > 0) settleKeyboardReveal(mRevealFromOpen, 0f);
                    return true;
                }
                break;
            default:
                break;
        }
        boolean handled = super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            releaseBorderDrag();
        }
        return handled;
    }

    /**
     * A DOWN nothing under the finger took — the air around the pages, which is the wall's own —
     * is kept only while the drag is armed, so a press on a border from just outside the page
     * can still hold. Anything else that lands here belongs to an armed finger whose child has
     * already been cancelled, and is swallowed.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCancellingChild) return false;
        return mBorderDrag.isArmed();
    }

    /**
     * A finger landed. It arms the border drag when the wall has another place to go, and the
     * keyboard swipe when the listener wants it — on a wall of one place too, since the keyboard
     * has to be reachable from every place in every mode.
     */
    private void armBorderDrag(@NonNull MotionEvent event) {
        releaseBorderDrag();
        if (!mGesturesEnabled) return;
        boolean canPage = mPages.size() > 1;
        float density = getResources().getDisplayMetrics().density;
        float keyboardReach = mListener != null && mListener.isBorderKeyboardSwipeEnabled()
            ? BorderDrag.KEYBOARD_REACH_DP * density : 0f;
        if (!canPage && keyboardReach <= 0f) return;
        View page = mPageViews.get(mCurrent);
        if (page == null || page.getWidth() <= 0 || page.getHeight() <= 0) return;
        float left = page.getLeft() + page.getTranslationX();
        float top = page.getTop();
        boolean armed = mBorderDrag.down(event.getX(), event.getY(),
            left, top, left + page.getWidth(), top + page.getHeight(),
            BorderDrag.BAND_DP * density,
            CornerZones.clampSize(CornerZones.paneSizePx(density), page.getWidth(), page.getHeight()),
            ViewConfiguration.get(getContext()).getScaledTouchSlop(), canPage, keyboardReach);
        if (!armed) return;
        mBorderDownX = event.getX();
        mBorderDownY = event.getY();
        mHoldHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
    }

    /**
     * The hold time passed with the finger still on the border. The wall takes the gesture from
     * here: the child is told its touch is over, the hand is told the hold was heard, and the
     * drag begins where the finger stands, so the first move after the hold is the first pixel
     * of travel. The velocity tracker starts here too: the hold's stillness is not the flick's.
     * And the page sinks under the finger ({@link PageSink}), pushed in on the held side where
     * the plank is on: the weight is what says the hold was taken.
     */
    private void onBorderHoldElapsed() {
        if (!mBorderDrag.holdElapsed()) return;
        if (!mGesturesEnabled || mPages.size() <= 1) {
            mBorderDrag.abandon();
            return;
        }
        cancelChildGesture();
        performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        // The hold made the finger the wall's, not the keyboard's: the grabber goes back to rest.
        mGrabber.setPressed(false, 0f, mReducedMotion);
        mBorderVelocity = VelocityTracker.obtain();
        beginDrag(true, mBorderDownX);
        engageSink(mBorderDrag.border());
        dragTo(0f);
    }

    /**
     * Tell whichever child holds the stream that its touch is over, the moment the border claims
     * it. A cancel is the one ending that leaves nothing behind — no tap, no selection, no mouse
     * button left down — which is what a finger that turned out to be a border hold owes the
     * content. Waiting for the next event is not the same thing: a finger holding still sends
     * none, and the terminal's own hold fired into the gap. Down the wall's own dispatch, so the
     * wall stops being a touch target for it in the bargain and the rest of the stream lands
     * here.
     */
    private void cancelChildGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL,
            mBorderDownX, mBorderDownY, 0);
        mCancellingChild = true;
        try {
            super.dispatchTouchEvent(cancel);
        } finally {
            mCancellingChild = false;
            cancel.recycle();
        }
    }

    /**
     * A finger set off up or down from the bottom border before the hold: the content is told its
     * touch is over, exactly as for the border drag, and the rest of the stream is read here.
     * Where the listener can move the keyboard with the finger, it does from this move on
     * ({@link #engageKeyboardReveal}); where it cannot, nothing moves until the release asks.
     */
    private void claimKeyboardSwipe(@NonNull MotionEvent event) {
        mHoldHandler.removeCallbacks(mHoldElapsed);
        cancelChildGesture();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        mBorderVelocity = VelocityTracker.obtain();
        mBorderVelocity.addMovement(event);
        engageKeyboardReveal(event.getY());
    }

    /**
     * The keyboard swipe's finger lifted. A keyboard following the finger settles on the spring
     * from where the finger left it, open or closed as {@link KeyboardReveal#settlesOpen} says;
     * otherwise up opens the keyboard, down closes it, and short is nothing.
     */
    private void releaseKeyboardSwipe(@NonNull MotionEvent event) {
        float velocity = 0f;
        if (mBorderVelocity != null) {
            mBorderVelocity.addMovement(event);
            mBorderVelocity.computeCurrentVelocity(1000);
            velocity = mBorderVelocity.getYVelocity();
        }
        float density = getResources().getDisplayMetrics().density;
        if (mRevealTravelPx > 0) {
            trackKeyboardReveal(event.getY());
            float revealVelocity = KeyboardReveal.revealVelocity(velocity, mRevealTravelPx);
            boolean open = KeyboardReveal.settlesOpen(mRevealFromOpen, mReveal, revealVelocity,
                KeyboardReveal.FLING_DP_PER_SEC * density / mRevealTravelPx);
            releaseBorderDrag();
            settleKeyboardReveal(open, revealVelocity);
            return;
        }
        BorderDrag.KeyboardSwipe swipe = mBorderDrag.keyboardRelease(event.getY(), velocity,
            BorderDrag.KEYBOARD_COMMIT_DP * density,
            BorderDrag.KEYBOARD_FLING_DP_PER_SEC * density);
        releaseBorderDrag();
        if (swipe != BorderDrag.KeyboardSwipe.NONE && mListener != null) {
            mListener.onBorderKeyboardSwipe(swipe == BorderDrag.KeyboardSwipe.OPEN);
        }
    }

    // ---- The keyboard under the finger -------------------------------------------------------

    /**
     * The keyboard swipe was claimed with the finger at {@code y}. A settle still running from the
     * last swipe is taken back by this finger where it stands; otherwise the listener is asked
     * whether the keyboard can follow ({@link Listener#onKeyboardRevealBegin}) — up opens, down
     * closes — and from here the reveal is the finger's. Reduced motion follows nothing: the
     * release asks, and the keyboard jumps.
     */
    private void engageKeyboardReveal(float y) {
        if (mRevealTravelPx > 0) {
            stopRevealSpring();
            mRevealStart = mReveal;
            mRevealAnchorY = y;
            return;
        }
        if (mReducedMotion || mListener == null) return;
        boolean opening = y < mBorderDownY;
        int travel = mListener.onKeyboardRevealBegin(opening);
        if (travel <= 0) return;
        mRevealTravelPx = travel;
        mRevealFromOpen = !opening;
        mRevealTarget = mRevealFromOpen;
        mReveal = opening ? 0f : 1f;
        mRevealStart = mReveal;
        // From where the finger landed, so the keyboard is already as far along as the finger.
        mRevealAnchorY = mBorderDownY;
        trackKeyboardReveal(y);
    }

    /**
     * One move of the keyboard swipe's finger: the reveal is where the finger says, and crossing
     * the point past which the release would go on (or back) is felt as one light tick.
     */
    private void trackKeyboardReveal(float y) {
        if (mRevealTravelPx <= 0) return;
        float before = mReveal;
        float reveal = KeyboardReveal.reveal(mRevealStart, y - mRevealAnchorY, mRevealTravelPx);
        if (KeyboardReveal.crossesCommit(mRevealFromOpen, before, reveal)) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        setReveal(reveal);
    }

    private void setReveal(float reveal) {
        if (reveal == mReveal) return;
        mReveal = reveal;
        if (mListener != null) mListener.onKeyboardRevealProgress(reveal);
    }

    /**
     * Settle the keyboard up ({@code open}) or down on a spring that starts where the reveal
     * stands and at {@code velocityPerSec}, the finger's own speed in the reveal's units. The
     * animator is only the ticker; the spring says where the reveal is at each frame, on the
     * slide's own bounded clock ({@link WallSlideClock}), and landing hands the keyboard back.
     */
    private void settleKeyboardReveal(final boolean open, float velocityPerSec) {
        stopRevealSpring();
        if (mRevealTravelPx <= 0) return;
        mRevealTarget = open;
        final float target = open ? 1f : 0f;
        if (mReducedMotion || (mReveal == target && velocityPerSec == 0f)) {
            endKeyboardReveal();
            return;
        }
        final SettleSpring spring = SettleSpring.of(mReveal - target, velocityPerSec,
            KeyboardReveal.SETTLE_OMEGA, KeyboardReveal.SETTLE_MAX_OMEGA);
        final long duration = spring.durationMs(KeyboardReveal.SETTLE_REST_PX / mRevealTravelPx);
        ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(duration * SLIDE_STRETCH_CEILING);
        ticker.setInterpolator(null);
        ticker.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private long mLastPlayTimeMs;
            private long mElapsedMs;
            private boolean mEnding;

            @Override public void onAnimationUpdate(ValueAnimator animation) {
                if (mEnding) return;
                long playTime = animation.getCurrentPlayTime();
                mElapsedMs = WallSlideClock.advance(mElapsedMs, playTime - mLastPlayTimeMs);
                mLastPlayTimeMs = playTime;
                float reveal = target + spring.displacementAt(Math.min(duration, mElapsedMs)
                    / 1000f);
                setReveal(Math.max(0f, Math.min(1f, reveal)));
                if (mElapsedMs >= duration && mRevealSpring == animation) {
                    mEnding = true;
                    animation.end();
                }
            }
        });
        ticker.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(Animator animation) { mCancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (mRevealSpring == animation) mRevealSpring = null;
                if (!mCancelled) endKeyboardReveal();
            }
        });
        mRevealSpring = ticker;
        ticker.start();
    }

    private void stopRevealSpring() {
        ValueAnimator spring = mRevealSpring;
        mRevealSpring = null;
        if (spring != null) spring.cancel();
    }

    /** The reveal is where it was going: the keyboard is the listener's again. */
    private void endKeyboardReveal() {
        stopRevealSpring();
        if (mRevealTravelPx <= 0) return;
        boolean open = mRevealTarget;
        mRevealTravelPx = 0;
        setReveal(open ? 1f : 0f);
        if (mListener != null) mListener.onKeyboardRevealEnd(open);
    }

    /**
     * Land a keyboard the finger is carrying, or a settle still running, at once: the settle's
     * own destination, or where the swipe began for a finger still down, whose rest is then
     * swallowed. For whatever is about to move the chrome another way — the wall, another surface
     * taking the gestures, the place's own settle.
     */
    public void settleKeyboardRevealNow() {
        if (mRevealTravelPx <= 0) return;
        if (mBorderDrag.isKeyboardSwipe()) {
            mBorderDrag.abandon();
            mRevealTarget = mRevealFromOpen;
        } else if (mRevealSpring == null) {
            mRevealTarget = mRevealFromOpen;
        }
        endKeyboardReveal();
    }

    /** True from a keyboard swipe the listener let the keyboard follow until it has landed. */
    public boolean isKeyboardRevealEngaged() {
        return mRevealTravelPx > 0;
    }

    /** How much of the keyboard the engaged reveal shows, for tests. */
    float keyboardReveal() {
        return mReveal;
    }

    // ---- The grabber -------------------------------------------------------------------------

    /**
     * Whether the grabber is drawn: while the keyboard swipe is on and the wall's gestures are
     * its own.
     */
    boolean isGrabberShown() {
        return mGesturesEnabled && mListener != null && mListener.isBorderKeyboardSwipeEnabled();
    }

    /**
     * A finger on the keyboard swipe's band — pending there or holding the swipe — lights the
     * grabber and draws it a little way along; anything else puts it back at rest.
     */
    private void syncGrabberPress(@NonNull MotionEvent event) {
        boolean pressed = mBorderDrag.isKeyboardSwipe()
            || (mBorderDrag.claim() == BorderDrag.Claim.PENDING && mBorderDrag.isKeyboardEligible());
        float dy = pressed && mBorderDrag.isKeyboardSwipe() ? event.getY() - mBorderDownY : 0f;
        mGrabber.setPressed(pressed && isGrabberShown(), dy, mReducedMotion);
    }

    /** Re-read the grabber's accent, after a theme or scheme change. */
    public void refreshGrabberColor() {
        mGrabber.refreshColor();
    }

    /** The grabber, for tests. */
    @NonNull
    KeyboardGrabber grabber() {
        return mGrabber;
    }

    /**
     * The pages first, then the grabber over the bottom border of each page on screen: faded with
     * the page's own outline as it leaves its rest, so a slide shows one, and scaled with a sunk
     * page about its centre, so it stays on the edge it marks.
     */
    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        mGrabberDrawnOffsetPx = mOffsetPx;
        mGrabberDrawnSink = mSink;
        if (!isGrabberShown()) return;
        int width = getWidth();
        for (PaneWallPage place : mPages) {
            View page = mPageViews.get(place);
            if (page == null || page.getVisibility() != VISIBLE || page.getWidth() <= 0) continue;
            float visibility = PaneWallPolicy.pageOutlineAlpha(page.getTranslationX(), width);
            if (!(visibility > 0f)) continue;
            float centreX = page.getLeft() + page.getTranslationX() + page.getPivotX()
                + (page.getWidth() / 2f - page.getPivotX()) * page.getScaleX();
            float bottomY = page.getTop() + page.getTranslationY() + page.getPivotY()
                + (page.getHeight() - page.getPivotY()) * page.getScaleY();
            mGrabber.draw(canvas, centreX, bottomY, page.getScaleY(), visibility);
        }
    }

    /** The grabber follows its page: a frame that moved the wall or sank the page redraws it. */
    private void invalidateGrabberIfMoved() {
        if (!isGrabberShown()) return;
        if (mOffsetPx == mGrabberDrawnOffsetPx && mSink == mGrabberDrawnSink) return;
        invalidate();
    }

    private void releaseBorderDrag() {
        mHoldHandler.removeCallbacks(mHoldElapsed);
        mBorderDrag.reset();
        if (mBorderVelocity != null) {
            mBorderVelocity.recycle();
            mBorderVelocity = null;
        }
    }

    /** The border drag's claim, for tests. */
    @NonNull
    BorderDrag.Claim borderDragClaim() {
        return mBorderDrag.claim();
    }

    // ---- The plank ---------------------------------------------------------------------------

    /**
     * A border drag claimed the finger at {@code fingerX}: the page the wall rests on and the
     * pages beside it become planks, each one the listener lets tip, until the settle lays them
     * flat — the page arriving tips with the page leaving, toward the same finger. A surface page
     * (the Display place) tips only while its stand-in is up ({@link SurfacePage}), from whichever
     * frame that is; a Display page without one never does. Each tipping page is on
     * a hardware layer for the length of the motion, so a screen of glyphs and glass is drawn flat
     * once per change and the tilt is a textured quad per frame rather than every glyph
     * re-rendered under a perspective matrix.
     */
    private void engagePlank(float fingerX) {
        if (mReducedMotion || getWidth() <= 0 || mListener == null) return;
        View held = mPageViews.get(mCurrent);
        if (held == null || held.getWidth() <= 0 || held.getHeight() <= 0) return;
        // Planks still lying down from the last drag are laid flat first.
        releasePlank();
        for (int steps = -1; steps <= 1; steps++) {
            PaneWallPage place = PaneWallPolicy.neighbour(mPages, mCurrent, steps);
            if (steps != 0 && place == mCurrent) continue;
            if (mListener.isPlankTiltEnabled(place)) mPlankPlaces.add(place);
        }
        if (mPlankPlaces.isEmpty()) return;
        // The weight stays where it pressed on the page it held.
        mWeightPage = held;
        mWeightOffsetPx = fingerX - (held.getLeft() + held.getTranslationX() + held.getWidth() / 2f);
        applyPlanks();
    }

    /**
     * Every engaged place's page at the angle its position and the finger's weight say
     * ({@link PlankTilt}), plus the held side's press on a sunk page. A page joins the planks the
     * first time it has a size, since it tips about its own centre.
     */
    private void applyPlanks() {
        if (mPlankPlaces.isEmpty()) return;
        int width = getWidth();
        View weightPage = mWeightPage;
        float weightPx = (weightPage == null ? 0f : weightPage.getTranslationX()) + mWeightOffsetPx;
        for (PaneWallPage place : mPlankPlaces) {
            View page = mPageViews.get(place);
            if (page == null || !mPages.contains(place)) continue;
            if (!mTiltPages.contains(page)) {
                if (page.getWidth() <= 0 || page.getHeight() <= 0) continue;
                // A live surface a rotation leaves flat and a layer strands: it joins once its
                // stand-in is up, at whatever angle the motion has reached by then.
                if (!canTransform(place, page)) continue;
                mTiltPages.add(page);
                page.setPivotX(page.getWidth() / 2f);
                page.setPivotY(page.getHeight() / 2f);
                page.setCameraDistance(PlankTilt.CAMERA_DISTANCE_DP
                    * getResources().getDisplayMetrics().density);
                syncPageLayer(page);
            }
            float x = page.getTranslationX();
            boolean sunk = page == mSinkPage;
            page.setRotationY(PlankTilt.angleDeg(x, width, PlankTilt.lean(x, weightPx, width),
                sunk ? mSinkSide : 0, sunk ? mSink : 0f));
        }
    }

    /**
     * The wall is at rest and any sunk page is back up: every plank flat, and each layer dropped
     * unless the sink still dims that page.
     */
    private void releasePlank() {
        mPlankPlaces.clear();
        mWeightPage = null;
        mWeightOffsetPx = 0f;
        while (!mTiltPages.isEmpty()) releasePlankPage(mTiltPages.get(mTiltPages.size() - 1));
    }

    /** One plank's page is going away, or the planks are laid down: flat, and its layer settled. */
    private void releasePlankPage(@NonNull View page) {
        mTiltPages.remove(page);
        page.setRotationY(0f);
        syncPageLayer(page);
    }

    /**
     * A hardware layer while the page tips or is dimmed, carrying the dim as its paint; none
     * otherwise. Only ever called for a page the plank or the sink has touched.
     */
    private void syncPageLayer(@NonNull View page) {
        boolean dimmed = page == mSinkPage && mSinkLayered;
        if (mTiltPages.contains(page) || dimmed) {
            page.setLayerType(View.LAYER_TYPE_HARDWARE, dimmed ? mSinkPaint : null);
        } else {
            page.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    /** The pages tipping under a drag, for tests; empty while none is. */
    @NonNull
    List<View> tiltPages() {
        return Collections.unmodifiableList(new ArrayList<>(mTiltPages));
    }

    // ---- The sink ----------------------------------------------------------------------------

    /**
     * The hold claimed the finger on {@code border}: the page the wall rests on sinks
     * ({@link PageSink}) — smaller about its centre, dimmer where it can be — and, where the plank
     * is on, is pushed in on a held side border. Every mode has it; reduced motion has none.
     */
    private void engageSink(@NonNull BorderDrag.Border border) {
        // Only under a drag the finger holds: its end is what brings the page back up.
        if (!mDragging || mReducedMotion || getWidth() <= 0) return;
        View page = mPageViews.get(mCurrent);
        if (page == null || page.getWidth() <= 0 || page.getHeight() <= 0) return;
        // The page the last drag left, still rising, lands at rest before this one goes down.
        if (mSinkPage != null && mSinkPage != page) finishSink();
        if (mSinkPage == null) {
            mSinkPage = page;
            mSinkPlace = mCurrent;
            mSinkLayered = canTransform(mCurrent, page);
            mSinkDimLevel = -1;
            page.setPivotX(page.getWidth() / 2f);
            page.setPivotY(page.getHeight() / 2f);
            if (mSinkLayered) syncPageLayer(page);
        }
        mSinkSide = border == BorderDrag.Border.LEFT ? -1
            : border == BorderDrag.Border.RIGHT ? 1 : 0;
        springSinkTo(1f, PageSink.SINK_STIFFNESS, PageSink.SINK_DAMPING);
    }

    /** The finger let go, or the wall was taken from under it: the sunk page springs back up. */
    private void riseSink() {
        if (mSinkPage == null) return;
        springSinkTo(0f, PageSink.RISE_STIFFNESS, PageSink.RISE_DAMPING);
    }

    /**
     * Run the sink from where it stands to {@code target} on a spring. The animator is only the
     * ticker; the spring's own curve says where the sink is at each frame, and reaching rest
     * hands the page back ({@link #finishSink}).
     */
    private void springSinkTo(final float target, final float stiffness, final float damping) {
        stopSinkSpring();
        final float from = mSink;
        if (from == target) {
            if (target == 0f) finishSink();
            return;
        }
        final long duration = Math.max(1L, PageSink.durationMs(stiffness, damping));
        ValueAnimator spring = ValueAnimator.ofFloat(0f, 1f);
        spring.setDuration(duration);
        spring.setInterpolator(null);
        spring.addUpdateListener(animation -> setSink(PageSink.value(from, target,
            Math.min(duration, animation.getCurrentPlayTime()), stiffness, damping)));
        spring.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(Animator animation) { mCancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (mSinkSpring == animation) mSinkSpring = null;
                if (mCancelled) return;
                if (target == 0f) finishSink();
                else setSink(target);
            }
        });
        mSinkSpring = spring;
        spring.start();
    }

    private void stopSinkSpring() {
        ValueAnimator spring = mSinkSpring;
        mSinkSpring = null;
        if (spring != null) spring.cancel();
    }

    /** Draw the sunk page at {@code sink}: its scale, its dim, and the plank's press on top. */
    private void setSink(float sink) {
        View page = mSinkPage;
        if (page == null) return;
        mSink = sink;
        float scale = PageSink.scale(sink);
        page.setScaleX(scale);
        page.setScaleY(scale);
        if (mSinkLayered) applySinkDim(page, sink);
        if (mTiltPages.contains(page)) applyPlanks();
        invalidateGrabberIfMoved();
        if (mListener != null && mSinkPlace != null) mListener.onPageSinkChanged(mSinkPlace, scale);
    }

    /**
     * The dim is the layer's colour filter, so a frame of it is a re-composite, never a redraw of
     * the page. Quantised, so a spring allocates at most one filter per level, once.
     */
    private void applySinkDim(@NonNull View page, float sink) {
        int level = PageSink.dimLevel(sink, SINK_DIM_STEPS);
        if (level == mSinkDimLevel) return;
        mSinkDimLevel = level;
        if (level == 0) {
            mSinkPaint.setColorFilter(null);
        } else {
            if (mSinkDimFilters == null) mSinkDimFilters = new LightingColorFilter[SINK_DIM_STEPS];
            LightingColorFilter filter = mSinkDimFilters[level];
            if (filter == null) {
                filter = new LightingColorFilter(PageSink.dimMultiplier(level, SINK_DIM_STEPS), 0);
                mSinkDimFilters[level] = filter;
            }
            mSinkPaint.setColorFilter(filter);
        }
        page.setLayerPaint(mSinkPaint);
    }

    /**
     * The sunk page is back at rest, or has to be at once (a jump, the page going away, the wall
     * leaving the window): full size, undimmed, its layer given back, and the plank laid flat if
     * the wall has already settled.
     */
    private void finishSink() {
        stopSinkSpring();
        View page = mSinkPage;
        if (page == null) return;
        PaneWallPage place = mSinkPlace;
        boolean layered = mSinkLayered;
        mSinkPage = null;
        mSinkPlace = null;
        mSinkLayered = false;
        mSink = 0f;
        mSinkSide = 0;
        mSinkDimLevel = -1;
        mSinkPaint.setColorFilter(null);
        page.setScaleX(1f);
        page.setScaleY(1f);
        boolean tipping = mTiltPages.contains(page);
        // The press lies down with the sink; the travel's own tip stays where the wall is.
        if (tipping) applyPlanks();
        if (layered || tipping) syncPageLayer(page);
        invalidateGrabberIfMoved();
        if (mListener != null && place != null) mListener.onPageSinkChanged(place, 1f);
        if (!isMoving()) {
            releasePlank();
            releaseStills();
        }
    }

    /** The page sunk under a hold, for tests; null while none is. */
    @Nullable
    View sinkPage() {
        return mSinkPage;
    }

    /** How far the sunk page is down, for tests. */
    float sink() {
        return mSink;
    }

    // ---- Surface pages ---------------------------------------------------------------------

    /**
     * Whether {@code page} can take a hardware layer and a rotation now. Every page can but one
     * whose picture is a live surface: a surface page while its stand-in is not up, and a Display
     * page that has no stand-in at all, which keeps to what a surface allows — a scale.
     */
    private static boolean canTransform(@NonNull PaneWallPage place, @NonNull View page) {
        if (page instanceof SurfacePage) return ((SurfacePage) page).isStill();
        return place != PaneWallPage.DISPLAY;
    }

    /** A drag begins: the page it holds and the pages that can arrive beside it hold still. */
    private void holdStillAround() {
        if (mReducedMotion) return;
        for (int steps = -1; steps <= 1; steps++) {
            holdStill(PaneWallPolicy.neighbour(mPages, mCurrent, steps));
        }
    }

    /**
     * A slide begins: a surface page it carries on or off screen holds still — the page it lands
     * on, and any it moves that is on screen now. A page parked through the whole slide is not
     * asked.
     */
    private void holdStillForSlide() {
        if (mReducedMotion) return;
        int width = getWidth();
        for (Map.Entry<PaneWallPage, View> entry : mPageViews.entrySet()) {
            View page = entry.getValue();
            if (!(page instanceof SurfacePage)) continue;
            boolean onScreen = page.getVisibility() == VISIBLE
                && Math.abs(page.getTranslationX()) < width;
            if (entry.getKey() == mCurrent || onScreen) holdStill(entry.getKey());
        }
    }

    private void holdStill(@NonNull PaneWallPage place) {
        View page = mPageViews.get(place);
        if (!(page instanceof SurfacePage) || !mPages.contains(place)) return;
        mStillPlaces.add(place);
        ((SurfacePage) page).holdStill(() -> onStillReady(place));
    }

    /**
     * A surface page's stand-in is up, now or mid-motion: from this frame it takes what the
     * motion gives the other pages at the point it has reached — the sink's dim on its layer, the
     * plank's angle — so it snaps in where it would have been.
     */
    private void onStillReady(@NonNull PaneWallPage place) {
        if (!mStillPlaces.contains(place)) return;
        View page = mPageViews.get(place);
        if (page == null || !canTransform(place, page)) return;
        if (page == mSinkPage && !mSinkLayered) {
            mSinkLayered = true;
            mSinkDimLevel = -1;
            syncPageLayer(page);
            applySinkDim(page, mSink);
        }
        applyPlanks();
    }

    /**
     * The wall is at rest with every sink risen, and the planks and the layers are already gone:
     * each surface page it asked brings its live surface back.
     */
    private void releaseStills() {
        if (mStillPlaces.isEmpty()) return;
        List<PaneWallPage> places = new ArrayList<>(mStillPlaces);
        mStillPlaces.clear();
        for (PaneWallPage place : places) {
            View page = mPageViews.get(place);
            if (page instanceof SurfacePage) ((SurfacePage) page).releaseStill();
        }
    }

    /** The places asked to hold still for the motion under way, for tests. */
    @NonNull
    java.util.Set<PaneWallPage> stillPlaces() {
        return Collections.unmodifiableSet(java.util.EnumSet.copyOf(mStillPlaces));
    }

    // ---- Motion ----------------------------------------------------------------------------

    /**
     * Slide the wall from where it stands to its rest on the settle spring ({@link SettleSpring}),
     * starting at {@code velocityPxPerSec}: a released finger's own speed, or 0 for a slide with
     * no finger on it. Any planks ride the same motion, since their angle is the pages' position.
     */
    private void startSlide(float velocityPxPerSec) {
        if (mOffsetPx == 0f) {
            settleImmediately();
            return;
        }
        stopSlide();
        holdStillForSlide();
        float velocity = Math.max(-SLIDE_MAX_VELOCITY_PX_PER_SEC,
            Math.min(SLIDE_MAX_VELOCITY_PX_PER_SEC, velocityPxPerSec));
        final SettleSpring spring = SettleSpring.of(mOffsetPx, velocity, SLIDE_OMEGA,
            SLIDE_MAX_OMEGA);
        final long duration = spring.durationMs(SLIDE_REST_PX);
        // The animator is the ticker only. The slide reads its own clock off the ticker's play
        // time, a bounded step per frame (WallSlideClock), so the first frame — which pre-rolls
        // the arriving place's chrome and lays its page out — cannot swallow the take-off. The
        // ticker is given more than the way as a ceiling and is ended from here once the clock
        // is up, which runs the same end listener as a ticker that ran out.
        ValueAnimator slide = ValueAnimator.ofFloat(0f, 1f);
        slide.setDuration(duration * SLIDE_STRETCH_CEILING);
        slide.setInterpolator(null);
        slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private long mLastPlayTimeMs;
            private long mElapsedMs;
            private boolean mEnding;

            @Override public void onAnimationUpdate(ValueAnimator animation) {
                if (mEnding) return;
                long playTime = animation.getCurrentPlayTime();
                mElapsedMs = WallSlideClock.advance(mElapsedMs, playTime - mLastPlayTimeMs);
                mLastPlayTimeMs = playTime;
                mOffsetPx = mElapsedMs >= duration ? 0f
                    : spring.displacementAt(mElapsedMs / 1000f);
                applyPagePositions();
                if (mElapsedMs >= duration && mSlide == animation) {
                    mEnding = true;
                    animation.end();
                }
            }
        });
        slide.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;
            @Override public void onAnimationCancel(Animator animation) { mCancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (mSlide == animation) mSlide = null;
                if (!mCancelled) settleImmediately();
            }
        });
        mSlide = slide;
        mSliding = true;
        slide.start();
    }

    private void stopSlide() {
        mSliding = false;
        ValueAnimator slide = mSlide;
        mSlide = null;
        if (slide != null) slide.cancel();
    }

    private void settleImmediately() {
        Trace.beginSection("Wall.settle");
        try {
            stopSlide();
            mOffsetPx = 0f;
            applyPagePositions();
            // A page still springing up keeps its plank until it lands (finishSink), so its
            // press lies down with the spring rather than snapping flat here.
            if (mSinkPage == null) {
                releasePlank();
                releaseStills();
            }
            if (mListener != null) mListener.onWallPageSettled(mCurrent);
        } finally {
            Trace.endSection();
        }
    }

    private void notifyPageChanged() {
        if (mListener != null) mListener.onWallPageChanged(mCurrent);
    }

    /**
     * Put every page where the current page and the offset say it goes. Pages are only moved,
     * never re-laid-out, and a page that is completely off screen stops drawing — mid-slide too:
     * the ring's third page, which a slide between the other two never shows, is not drawn along
     * with them.
     *
     * <p>The Terminal page is the one exception, and only since 707920f7 turned out to cost a
     * frame. That commit tightened {@code onScreen || moving ? VISIBLE : INVISIBLE} down to plain
     * {@code onScreen ? VISIBLE : INVISIBLE} so a slide no longer drew the ring's third page for
     * the whole motion — but it also means an off-screen page now turns INVISIBLE the very frame
     * it leaves, which is the frame the framework drops its display lists on, so the frame it
     * comes back has to re-record everything (the pane's rows, its glass, its padding band) from
     * nothing and misses vsync. The Terminal page is instead kept {@code VISIBLE} always and faded
     * with {@code alpha} to 0 while off screen: a zero-alpha node is skipped by the renderer but
     * keeps its display lists, so coming back repaints instead of re-recording. The other places
     * keep 707920f7's INVISIBLE behaviour unchanged — the Display page in particular owns its own
     * surface, which INVISIBLE is exactly right for. Because the Terminal page no longer goes
     * INVISIBLE, nothing in the view hierarchy notices it leaving on its own any more, which is
     * what {@link Listener#onTerminalOffScreenChanged} is for.
     */
    private void applyPagePositions() {
        int width = getWidth();
        for (Map.Entry<PaneWallPage, View> entry : mPageViews.entrySet()) {
            View view = entry.getValue();
            if (!mPages.contains(entry.getKey())) {
                view.setVisibility(GONE);
                continue;
            }
            // On a ring each page is placed the shorter way round from the current one, so the
            // page past the outer edge is already waiting on the other side when a drag reaches
            // for it.
            float x = PaneWallPolicy.relativePosition(mPages, mCurrent, entry.getKey())
                * (float) width + mOffsetPx
                + (entry.getKey() == mNudgePage ? mNudgePx : 0f);
            view.setTranslationX(x);
            boolean onScreen = width <= 0 || Math.abs(x) < width;
            if (entry.getKey() == PaneWallPage.TERMINAL) {
                view.setVisibility(VISIBLE);
                view.setAlpha(onScreen ? 1f : 0f);
                boolean offScreen = !onScreen;
                if (mListener != null
                        && (mTerminalOffScreen == null || mTerminalOffScreen != offScreen)) {
                    mTerminalOffScreen = offScreen;
                    mListener.onTerminalOffScreenChanged(offScreen);
                }
            } else {
                boolean wasOnScreen = view.getVisibility() == VISIBLE;
                view.setVisibility(onScreen ? VISIBLE : INVISIBLE);
                // A page coming back was skipped by every layout pass while it was parked, so
                // the frame may have moved under it: it is laid out again before it is drawn.
                // Inside a layout pass, onLayout lays it out itself once the positions are set.
                if (onScreen && !wasOnScreen && !isInLayout()) view.requestLayout();
            }
        }
        // The planks' angles are a function of where the pages stand, so the drag, the spring
        // back and the carry-out all read the same number and they are flat wherever they rest —
        // plus the held side's press, while the page is sunk. After every page has moved, since
        // each leans toward the weight on the page the finger held.
        applyPlanks();
        invalidateGrabberIfMoved();
        if (mListener != null) mListener.onWallOffsetChanged(mOffsetPx);
    }

    // ---- Layout ----------------------------------------------------------------------------

    /**
     * The frame every page sits inside: the terminal page's margins, which the activity sets from
     * the surface editor's insets. A wall with no terminal page registered yet has no frame.
     */
    @NonNull
    private MarginLayoutParams pageMargins() {
        View terminal = mPageViews.get(PaneWallPage.TERMINAL);
        ViewGroup.LayoutParams params = terminal == null ? null : terminal.getLayoutParams();
        if (params instanceof MarginLayoutParams) return (MarginLayoutParams) params;
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        MarginLayoutParams margins = pageMargins();
        int childWidth = MeasureSpec.makeMeasureSpec(Math.max(0, width - getPaddingLeft()
            - getPaddingRight() - margins.leftMargin - margins.rightMargin), MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(Math.max(0, height - getPaddingTop()
            - getPaddingBottom() - margins.topMargin - margins.bottomMargin), MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            // A page parked off screen (INVISIBLE, see applyPagePositions) keeps its layout.
            if (child.getVisibility() != VISIBLE) continue;
            child.measure(childWidth, childHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Positions first, so the pages this pass lays out are the ones on screen once the wall
        // has put them where the offset says. A slide cut short (the wall left the window
        // mid-way) leaves the pages displaced and the record ahead of them; the layout puts both
        // right and says so.
        if (isRestingOffPage()) settleImmediately();
        else applyPagePositions();
        MarginLayoutParams margins = pageMargins();
        int left = getPaddingLeft() + margins.leftMargin;
        int top = getPaddingTop() + margins.topMargin;
        int right = Math.max(left, r - l - getPaddingRight() - margins.rightMargin);
        int bottom = Math.max(top, b - t - getPaddingBottom() - margins.bottomMargin);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            // A page that came on screen during this pass was not measured by it.
            if (child.getMeasuredWidth() != right - left || child.getMeasuredHeight() != bottom - top) {
                child.measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(bottom - top, MeasureSpec.EXACTLY));
            }
            child.layout(left, top, right, bottom);
        }
    }

    // The activity sets the terminal page's frame by writing margins into its layout params and
    // checks they *are* margin params first, so the wall has to hand out that kind — a plain
    // ViewGroup does not, and the pane host silently lost its side gap when it moved in here.

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams ? new MarginLayoutParams((MarginLayoutParams) params)
            : new MarginLayoutParams(params);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams params) {
        return params instanceof MarginLayoutParams;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isRestingOffPage()) settleImmediately();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSliding = false;
        stopSlide();
        stopNudge();
        // A keyboard the finger or its settle was carrying lands where it was going.
        settleKeyboardRevealNow();
        releaseBorderDrag();
        finishSink();
        releasePlank();
        releaseStills();
        mGrabber.reset();
    }
}
