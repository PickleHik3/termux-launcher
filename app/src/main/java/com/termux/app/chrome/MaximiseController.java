package com.termux.app.chrome;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.ResolvedArrangement;
import com.termux.app.wall.pageswitch.PageBarModel;

/**
 * A window's Maximise (its mark's control): which window fills the screen, the swipe from the
 * top that brings the status row back for a while, and the arrangement the chrome stands while it
 * does. What is hidden is {@link MaximisePolicy}'s; the activity applies it through {@link Host}.
 *
 * <p>Maximised is per window, named by its page bar target ({@link #keyOf}): it ends on Maximise
 * again, when the page in front is no longer that window's ({@link #checkLater}) and when a window
 * closes ({@link #windowClosed}).
 *
 * <p>The maximised arrangement is the Terminal side's one arrangement while it lasts: what the
 * views show and the room the terminal is given are both read from it ({@link #arrangement}, and
 * {@link #layoutAt} for a rest with the keyboard the other way), so a hidden row keeps no room.
 */
public final class MaximiseController {

    /** What the controller asks of the activity. */
    public interface Host {
        /** The system bars and the place layout follow {@link #isMaximised} and {@link #revealed}. */
        void applyMaximise();

        /** Goes to {@code target}'s window or page. */
        void navigateTo(@NonNull PageBarModel.Target target);

        /** The key ({@link #keyOf}) of the page bar entry in front; "" for none. Read on events only. */
        @NonNull String frontKey();

        /** Whether the in-app keyboard is up: the extra keys stay while it is. */
        boolean keyboardShown();

        void post(@NonNull Runnable r);

        void postDelayed(@NonNull Runnable r, long delayMs);

        void removeCallbacks(@NonNull Runnable r);
    }

    private static final String TAG = "MARKS";

    @NonNull private final Host mHost;
    @Nullable private String mKey;
    private boolean mRevealed;
    private boolean mLastBarsShown = true;
    @Nullable private ResolvedArrangement mFrom;
    @Nullable private ResolvedArrangement mArranged;
    private boolean mRevealedAt;
    private boolean mKeyboardAt;
    /** {@link #layoutAt}'s two answers, for {@link #mLayoutsFrom} at {@link #mLayoutsRevealed}. */
    @Nullable private ResolvedArrangement mLayoutsFrom;
    private boolean mLayoutsRevealed;
    @Nullable private PlaceLayout mLayoutUp;
    @Nullable private PlaceLayout mLayoutDown;
    /** The screen edges' system gesture bands, from the last insets; 0 before any. */
    private int mTopBandPx;
    private int mBottomBandPx;
    /** True from a down on a screen edge while maximised to its lift: the stream is the system's. */
    private boolean mEdgeStream;
    @NonNull private final Runnable mRevealEnds = () -> {
        if (!mRevealed) return;
        mRevealed = false;
        apply();
    };
    @NonNull private final Runnable mCheck = this::check;
    @NonNull private final Runnable mApply = this::apply;

    public MaximiseController(@NonNull Host host) {
        mHost = host;
    }

    /** A window's name for Maximise: its page and its place in the row ("pageId:place"). */
    @NonNull
    public static String keyOf(@NonNull PageBarModel.Target target) {
        return target.pageId + ":" + target.index;
    }

    /** The key of {@code model}'s entry in front; "" when it has none. */
    @NonNull
    public static String frontKeyOf(@NonNull PageBarModel model) {
        int current = model.currentIndex();
        return current < 0 ? "" : keyOf(model.entries().get(current).target);
    }

    public boolean isMaximised() {
        return mKey != null;
    }

    /** Whether a swipe from the top has the status row back for now. */
    public boolean revealed() {
        return mRevealed;
    }

    /** The maximised window's key; null while none is. */
    @Nullable
    public String key() {
        return mKey;
    }

    /** Maximise from a window's controls: go to it and fill the screen; again restores. */
    public void toggle(@NonNull PageBarModel.Target target) {
        String key = keyOf(target);
        if (key.equals(mKey)) {
            end();
            return;
        }
        mHost.navigateTo(target);
        mKey = key;
        mRevealed = false;
        log("maximise " + key);
        apply();
    }

    /** Back to the page as it stands; nothing when not maximised. */
    public void end() {
        if (mKey == null) return;
        log("maximise ends " + mKey);
        mKey = null;
        mRevealed = false;
        mArranged = null;
        mHost.removeCallbacks(mRevealEnds);
        apply();
    }

    /** A window closed: the window Maximise filled the screen for may be gone, so it ends. */
    public void windowClosed() {
        end();
    }

    /** Ends Maximise when the page in front is no longer the maximised window's. */
    public void check() {
        String key = mKey;
        if (key != null && MaximisePolicy.ends(key, mHost.frontKey(), false)) end();
    }

    /** {@link #check} once the page or window change under way has settled. */
    public void checkLater() {
        if (mKey != null) mHost.post(mCheck);
    }

    /**
     * The insets' word on the system bars: while maximised, bars that come back bring the status
     * row back until they go, or for {@link MaximisePolicy#REVEAL_MS}. Android's transient bars
     * (a swipe while they are hidden) move no insets; {@link #onWindowTouch} sees that swipe.
     */
    public void noteBars(boolean shown) {
        boolean appeared = shown && !mLastBarsShown;
        boolean went = !shown && mLastBarsShown;
        mLastBarsShown = shown;
        if (mKey == null) return;
        if (appeared && !mRevealed) {
            reveal();
        } else if (went && mRevealed) {
            mHost.removeCallbacks(mRevealEnds);
            mHost.post(mRevealEnds);
        }
    }

    /**
     * The system gesture bands along the top and the bottom edge, from the insets; an edge that
     * reports none is {@code fallbackPx} deep.
     */
    public void noteGestureBands(int topPx, int bottomPx, int fallbackPx) {
        mTopBandPx = topPx > 0 ? topPx : fallbackPx;
        mBottomBandPx = bottomPx > 0 ? bottomPx : fallbackPx;
    }

    /**
     * Every window touch, before any view: while maximised, a finger that lands on the top edge
     * is the swipe that shows Android's bars, and brings the status row back with them for
     * {@link MaximisePolicy#REVEAL_MS}; one that lands on the bottom edge while the keyboard is
     * down is the Home gesture. Neither stream reaches the views, so the window under the edge
     * takes no tap (a tap on a terminal raises its keyboard). Once the row is back, a finger on
     * the top edge is the row's; with the keyboard up, the bottom edge is the keyboard's.
     *
     * @param windowHeightPx the window's height: where its bottom edge is
     * @return whether the stream is the edge's and the views are to see none of it
     */
    public boolean onWindowTouch(int action, float y, int windowHeightPx) {
        if (action == MotionEvent.ACTION_DOWN) {
            mEdgeStream = false;
            if (mKey == null) return false;
            if (y < mTopBandPx && !mRevealed) {
                log("maximise edge top");
                reveal();
                mEdgeStream = true;
            } else if (windowHeightPx > 0 && y >= windowHeightPx - mBottomBandPx
                && !mHost.keyboardShown()) {
                log("maximise edge bottom");
                mEdgeStream = true;
            }
            return mEdgeStream;
        }
        boolean owned = mEdgeStream;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            mEdgeStream = false;
        return owned;
    }

    private void reveal() {
        mRevealed = true;
        mHost.post(mApply);
        mHost.removeCallbacks(mRevealEnds);
        mHost.postDelayed(mRevealEnds, MaximisePolicy.REVEAL_MS);
    }

    /** Whether the system bars hide: the Full screen setting, or Maximise when the setting is off. */
    public boolean hidesSystemBars(boolean fullScreenSetting) {
        return fullScreenSetting || MaximisePolicy.hidesSystemBars(mKey != null, fullScreenSetting);
    }

    /**
     * {@code a} as the page stands it now, with the keyboard as it is: itself, or maximised; made
     * once per change, never per read. Every reader of the Terminal side takes this one.
     *
     * <p>The first read after the in-app keyboard went up or down (every show and hide asks the
     * geometry pass, which reads it, focus ones included) stands the extra keys again or puts them
     * away, and has the place layout applied for it once.
     */
    @NonNull
    public ResolvedArrangement arrangement(@NonNull ResolvedArrangement a) {
        if (mKey == null) return a;
        boolean keyboard = mHost.keyboardShown();
        if (a == mFrom && mArranged != null && mRevealedAt == mRevealed
            && mKeyboardAt == keyboard) {
            return mArranged;
        }
        if (mArranged != null && mKeyboardAt != keyboard) {
            log("maximise keyboard " + (keyboard ? "up" : "down"));
            mHost.post(mApply);
        }
        mFrom = a;
        mRevealedAt = mRevealed;
        mKeyboardAt = keyboard;
        mArranged = a.standing(layoutAt(a, keyboard));
        return mArranged;
    }

    /**
     * {@code a}'s layout as a maximised page stands it with the keyboard up or down: the rest a
     * keyboard's reveal or put-away predicts is read here and the settled pass from
     * {@link #arrangement}, both from {@link MaximisePolicy#layoutFor}. {@code a}'s own layout
     * when not maximised; each answer is kept, so reading it again builds nothing.
     */
    @NonNull
    public PlaceLayout layoutAt(@NonNull ResolvedArrangement a, boolean keyboardUp) {
        if (mKey == null) return a.layout();
        if (a != mLayoutsFrom || mLayoutsRevealed != mRevealed) {
            mLayoutsFrom = a;
            mLayoutsRevealed = mRevealed;
            mLayoutUp = null;
            mLayoutDown = null;
        }
        if (keyboardUp) {
            if (mLayoutUp == null) mLayoutUp = MaximisePolicy.layoutFor(a.layout(), mRevealed, true);
            return mLayoutUp;
        }
        if (mLayoutDown == null) {
            mLayoutDown = MaximisePolicy.layoutFor(a.layout(), mRevealed, false);
        }
        return mLayoutDown;
    }

    private void apply() {
        mHost.applyMaximise();
    }

    private static void log(@NonNull String line) {
        android.util.Log.i(TAG, line);
    }
}
