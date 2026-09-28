package com.termux.app.wall;

import android.content.Context;
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
import com.termux.app.terminal.Motion;
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
 * vertical swipe without the hold, which opens and closes the keyboard. The window strip's
 * overswipe drives the same drag from outside.
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
     * A slide is the terminal's own window pan: the same settle curve over the same time for a
     * full width, shortened in proportion when the wall has less far to go, so a release near
     * rest lands quickly and a tap from one place to the next travels like a window switch. The
     * same time whatever the destination, too: the slide keeps its own clock
     * ({@link WallSlideClock}), so the frames an arrival costs stretch it rather than skip it.
     */
    private static final long SLIDE_FULL_MS = 560L;
    private static final long SLIDE_MIN_MS = 180L;
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
         * Whether the page a border drag pulls tips like a plank ({@link PlankTilt}) — Fancier
         * Glass, with the phone animating. Asked as the drag claims the finger, for the page the
         * wall rests on.
         */
        default boolean isPlankTiltEnabled(@NonNull PaneWallPage page) { return false; }
        /**
         * Whether a vertical swipe off the current page's bottom border is the keyboard's
         * ({@link BorderDrag#KEYBOARD_REACH_DP}); asked as each finger lands.
         */
        default boolean isBorderKeyboardSwipeEnabled() { return false; }
        /** A swipe off the bottom border asked for the keyboard: up to open, down to close. */
        default void onBorderKeyboardSwipe(boolean open) { }
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
     * The page tipping under the drag ({@link PlankTilt}), from the drag's start to the wall's
     * settle; null while no plank is engaged. Its angle is written beside its translation in
     * {@link #applyPagePositions}, so the tilt and the slide are one motion.
     */
    @Nullable private View mTiltPage;

    public PaneWallLayout(@NonNull Context context) {
        this(context, null);
    }

    public PaneWallLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
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
        if (previous != null && previous == mTiltPage && previous != view) releasePlank();
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
        mGesturesEnabled = enabled;
        // A keyboard swipe under way asks for nothing once another surface owns the gesture.
        if (!enabled && mBorderDrag.isKeyboardSwipe()) mBorderDrag.abandon();
        if (enabled || !mDragging) return;
        // The claimant is told to let go, and the wall goes back to rest on its own — nothing
        // else is going to release this drag now.
        interruptDrag();
        if (mReducedMotion) settleImmediately();
        else startSlide();
    }

    public boolean areGesturesEnabled() {
        return mGesturesEnabled;
    }

    // ---- Navigation ------------------------------------------------------------------------

    /** Go to {@code page}, sliding unless {@code animate} is false or motion is reduced. */
    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        if (!mPages.contains(page)) return false;
        interruptDrag();
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
            settleImmediately();
        } else {
            startSlide();
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
        beginDrag(false);
    }

    /**
     * @param plank whether the page under the finger tips ({@link PlankTilt}), which is the
     *              border drag's own motion: a finger on the pane's edge pressing it sideways
     */
    private void beginDrag(boolean plank) {
        if (!mGesturesEnabled) return;
        Trace.beginSection("Wall.beginDrag");
        try {
            mDragging = true;
            stopSlide();
            stopNudge();
            if (plank) engagePlank();
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
        int steps = PaneWallPolicy.settle(mOffsetPx, velocityPxPerSec, getWidth(),
            getResources().getDisplayMetrics().density,
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, -1),
            PaneWallPolicy.hasNeighbour(mPages, mCurrent, 1));
        if (steps == 0) {
            if (mReducedMotion) settleImmediately();
            else startSlide();
            return;
        }
        goBy(steps, true);
    }

    /** The claimant let go without a release (its stream was cancelled). */
    public void cancelDrag() {
        if (!mDragging) return;
        mDragging = false;
        if (mReducedMotion) settleImmediately();
        else startSlide();
    }

    /**
     * The wall is taking over from a live drag. Unlike {@link #cancelDrag}, the claimant did not
     * ask for this, so it is told: the rest of that finger's motion is not the wall's to answer.
     */
    private void interruptDrag() {
        if (!mDragging) return;
        mDragging = false;
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
     * ({@link BorderDrag#move}), and its lift asks the listener to open or close it.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mCancellingChild) return super.dispatchTouchEvent(event);
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
                    // Cancelled from above: the keyboard stays as it was.
                    releaseBorderDrag();
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
        mBorderVelocity = VelocityTracker.obtain();
        beginDrag(true);
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
     * touch is over, exactly as for the border drag, and the rest of the stream is read here for
     * its release. Nothing moves under it; the keyboard animates on its own once asked.
     */
    private void claimKeyboardSwipe(@NonNull MotionEvent event) {
        mHoldHandler.removeCallbacks(mHoldElapsed);
        cancelChildGesture();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        mBorderVelocity = VelocityTracker.obtain();
        mBorderVelocity.addMovement(event);
    }

    /** The keyboard swipe's finger lifted: up opens the keyboard, down closes it, short is nothing. */
    private void releaseKeyboardSwipe(@NonNull MotionEvent event) {
        float velocity = 0f;
        if (mBorderVelocity != null) {
            mBorderVelocity.addMovement(event);
            mBorderVelocity.computeCurrentVelocity(1000);
            velocity = mBorderVelocity.getYVelocity();
        }
        float density = getResources().getDisplayMetrics().density;
        BorderDrag.KeyboardSwipe swipe = mBorderDrag.keyboardRelease(event.getY(), velocity,
            BorderDrag.KEYBOARD_COMMIT_DP * density,
            BorderDrag.KEYBOARD_FLING_DP_PER_SEC * density);
        releaseBorderDrag();
        if (swipe != BorderDrag.KeyboardSwipe.NONE && mListener != null) {
            mListener.onBorderKeyboardSwipe(swipe == BorderDrag.KeyboardSwipe.OPEN);
        }
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
     * A border drag claimed the finger: the page the wall rests on becomes the plank, if the
     * listener lets it, until the settle lays it flat. A hardware layer for the length of the
     * motion, so the page — a screen of glyphs and glass — is drawn flat once per change and the
     * tilt is a textured quad per frame rather than every glyph re-rendered under a perspective
     * matrix.
     */
    private void engagePlank() {
        if (mTiltPage != null || mReducedMotion || getWidth() <= 0) return;
        if (mListener == null || !mListener.isPlankTiltEnabled(mCurrent)) return;
        View page = mPageViews.get(mCurrent);
        if (page == null || page.getWidth() <= 0 || page.getHeight() <= 0) return;
        mTiltPage = page;
        page.setPivotX(page.getWidth() / 2f);
        page.setPivotY(page.getHeight() / 2f);
        page.setCameraDistance(PlankTilt.CAMERA_DISTANCE_DP
            * getResources().getDisplayMetrics().density);
        page.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    /** The wall is at rest, or the plank's page is going away: flat, and the layer dropped. */
    private void releasePlank() {
        View page = mTiltPage;
        if (page == null) return;
        mTiltPage = null;
        page.setRotationY(0f);
        page.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    /** The page tipping under a drag, for tests; null while none is. */
    @Nullable
    View tiltPage() {
        return mTiltPage;
    }

    // ---- Motion ----------------------------------------------------------------------------

    private void startSlide() {
        if (mOffsetPx == 0f) {
            settleImmediately();
            return;
        }
        stopSlide();
        int width = Math.max(1, getWidth());
        float fraction = Math.min(1f, Math.abs(mOffsetPx) / width);
        final long duration = Math.max(SLIDE_MIN_MS, Math.round(SLIDE_FULL_MS * fraction));
        final float from = mOffsetPx;
        final android.view.animation.Interpolator curve = Motion.settle();
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
                float t = curve.getInterpolation(WallSlideClock.fraction(mElapsedMs, duration));
                mOffsetPx = from * (1f - t);
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
            releasePlank();
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
            // The plank's angle is a function of where the page stands, so the drag, the spring
            // back and the carry-out all read the same number and it is flat wherever it rests.
            if (view == mTiltPage) view.setRotationY(PlankTilt.angleDeg(x, width));
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
        releaseBorderDrag();
        releasePlank();
    }
}
