package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
     * The insets' word on the system bars: while maximised, the swipe from the top that shows
     * them brings the status row back until they go, or for {@link MaximisePolicy#REVEAL_MS}.
     */
    public void noteBars(boolean shown) {
        boolean appeared = shown && !mLastBarsShown;
        mLastBarsShown = shown;
        if (mKey == null) return;
        if (appeared && !mRevealed) {
            mRevealed = true;
            mHost.post(mApply);
            mHost.removeCallbacks(mRevealEnds);
            mHost.postDelayed(mRevealEnds, MaximisePolicy.REVEAL_MS);
        } else if (!shown && mRevealed) {
            mHost.removeCallbacks(mRevealEnds);
            mHost.post(mRevealEnds);
        }
    }

    /** Whether the system bars hide: the Full screen setting, or Maximise when the setting is off. */
    public boolean hidesSystemBars(boolean fullScreenSetting) {
        return fullScreenSetting || MaximisePolicy.hidesSystemBars(mKey != null, fullScreenSetting);
    }

    /** {@code a} as the page stands it now: itself, or maximised; made once per change, never per read. */
    @NonNull
    public ResolvedArrangement arrangement(@NonNull ResolvedArrangement a) {
        if (mKey == null) return a;
        boolean keyboard = mHost.keyboardShown();
        if (a == mFrom && mArranged != null && mRevealedAt == mRevealed
            && mKeyboardAt == keyboard) {
            return mArranged;
        }
        mFrom = a;
        mRevealedAt = mRevealed;
        mKeyboardAt = keyboard;
        mArranged = a.standing(MaximisePolicy.layoutFor(a.layout(), mRevealed, keyboard));
        return mArranged;
    }

    private void apply() {
        mHost.applyMaximise();
    }

    private static void log(@NonNull String line) {
        android.util.Log.i(TAG, line);
    }
}
