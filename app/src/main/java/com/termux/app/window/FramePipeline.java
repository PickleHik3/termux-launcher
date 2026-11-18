package com.termux.app.window;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The window's one frame order (ADR 0028 sections 1, 3 and 4). Inputs only record that a step has
 * work and ask for a frame ({@link #requestFrame}); the pipeline runs the {@link FrameStep} table
 * in order, only on frames an input marked dirty, so nothing repaints at rest.
 *
 * <p>A request books the frame's animation phase on the root, which the platform runs before
 * layout, so what a step writes there is laid out in the same frame. A request made after that
 * phase (from an inset dispatch or a layout listener) is run by the one pre-draw hook in the same
 * frame, and the draw goes ahead: nothing cancels a draw except the window's first frame, held
 * while the table still lays it out, at most {@link #FIRST_FRAME_MAX_HELD} frames and
 * {@link #FIRST_FRAME_BACKSTOP_MS}. Without an attached root (before the window, in a test) a
 * plain post on the main looper stands in for the frame.</p>
 *
 * <p>A result needed before the next frame is asked for with {@link #flush}, which runs the whole
 * table at once and says who asked. No step commits on its own.</p>
 */
public final class FramePipeline {

    /** The first frame is cancelled for the table's layout at most this many times. */
    static final int FIRST_FRAME_MAX_HELD = 2;
    /** The first frame is never held longer than this after its first cancel. */
    static final long FIRST_FRAME_BACKSTOP_MS = 160L;

    /** Log event of a flush; the detail is who asked. */
    public static final String LOG_FLUSH = "frame-flush";
    /** Log event of the first frame's release; the detail is signal or backstop. */
    public static final String LOG_FIRST_FRAME = "first-frame-release";

    /** Where the pipeline says who flushed and why the first frame was let go. */
    public interface Log {
        void log(@NonNull String event, @NonNull String detail);
    }

    /** Counter name of each step run late (in the pre-draw pass), built once. */
    private static final String[] LATE_KEYS = lateKeys();

    private final Runnable[] mSteps = new Runnable[FrameStep.TABLE.length];
    private final FrameGuards mGuards = new FrameGuards();
    /** Holds on the first frame (the relaunch reveal); open ones keep that draw back. */
    private final ArrayList<Hold> mFirstFrameHolds = new ArrayList<>(1);
    /** Holds stepped once per frame ({@link #stepHold}), each beside what it asks of a frame. */
    private final ArrayList<Hold> mSteppedHolds = new ArrayList<>(2);
    private final ArrayList<BooleanSupplier> mHoldSteps = new ArrayList<>(2);
    private int mFirstFrameAttempts;
    private int mDirty;
    /** Steps asked for the next frame after the current one ({@link #requestNextFrame}). */
    private int mNextDirty;
    /** Steps run on every drawn frame without asking for one ({@link #arm}). */
    private int mArmed;
    /** The steps that run only in the pre-draw hook ({@link FrameStep#drawStep}). */
    private static final int DRAW_STEPS = drawSteps();
    @NonNull private final LongSupplier mClock;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable private Log mLog;

    @Nullable private View mRoot;
    private boolean mAnimationBooked;
    /**
     * The frame's animation pass has run and its pre-draw hook not yet: a request now runs late.
     * With the motion log on, such a request records its caller for the crossing-laid-out line.
     */
    private boolean mAfterPass;
    @Nullable private String[] mLateBy;
    private boolean mPostBooked;
    private boolean mRunning;
    private boolean mDestroyed;

    private boolean mFirstFrameDrawn = true;
    /** True while the first frame's own pre-draw runs the table. */
    private boolean mInFirstFrame;
    private int mFirstFrameHeld;
    private long mFirstFrameHeldSinceMs;
    private int mFirstFrameBackstops;

    private int mFlushCount;
    @Nullable private String mLastFlushBy;

    private final Runnable mBookedPass = this::runBookedPass;
    private final ViewTreeObserver.OnPreDrawListener mPreDraw = this::onPreDraw;

    public FramePipeline() {
        this(SystemClock::uptimeMillis);
    }

    FramePipeline(@NonNull LongSupplier clock) {
        mClock = clock;
    }

    /** Binds the code that runs at {@code step}; one per step. */
    public void bind(@NonNull FrameStep step, @NonNull Runnable run) {
        mSteps[step.ordinal()] = run;
    }

    public void setLog(@Nullable Log log) {
        mLog = log;
    }

    /** The guards every writer in the table runs while the motion log is on. */
    @NonNull
    public FrameGuards guards() {
        return mGuards;
    }

    /**
     * Keeps the window's first frame from drawing while the returned {@link Hold} is open: the one
     * hold in the pipeline that cancels a draw (ADR 0028 section 4), for the first frame after a
     * relaunch that must show the surface it carried. It ends by its signal or its backstops
     * ({@code backstopMs} and {@code maxFrames} frames declined), so a held window can never stay
     * black. Null when the first frame is already behind (nothing is held then).
     */
    @Nullable
    public Hold holdFirstFrame(@NonNull String name, long backstopMs, int maxFrames) {
        View root = mRoot;
        if (mDestroyed || root == null || mFirstFrameDrawn) return null;
        Hold hold = Hold.take(name, backstopMs, maxFrames, mClock);
        // The time backstop holds even when no frame comes to ask; a release asks for the frame.
        hold.onRelease(() -> {
            View r = mRoot;
            if (r != null) r.invalidate();
        });
        hold.armBackstop(mHandler);
        mFirstFrameHolds.add(hold);
        return hold;
    }

    /**
     * Hooks the window's root: the pipeline's one pre-draw listener goes on it. A root not yet
     * attached has its first frame ahead, which the pipeline may hold; null unhooks.
     */
    public void attach(@Nullable View root) {
        if (mRoot == root) return;
        unhook();
        mRoot = root;
        if (root == null || mDestroyed) {
            mFirstFrameDrawn = true;
            return;
        }
        mFirstFrameDrawn = root.isAttachedToWindow();
        // A detached view hands out its floating observer, which the window adopts on attach.
        root.getViewTreeObserver().addOnPreDrawListener(mPreDraw);
    }

    /** Records that {@code step} has work and asks for a frame. */
    public void requestFrame(@NonNull FrameStep step) {
        if (mDestroyed) return;
        mDirty |= 1 << step.ordinal();
        // The chrome commit is rare (a few per crossing), so its caller is always noted.
        if (Counters.enabled && (mAfterPass || step == FrameStep.CHROME_COMMIT)) {
            recordLateCaller(step.ordinal());
        }
        bookFrame();
    }

    /**
     * Records that {@code step} has work that waits for the next booked pass, which starts after
     * the current frame: never in this frame's pre-draw hook, never in a pass already running.
     * For a step the frame being drawn leaves behind (the landed frame's settle).
     */
    public void requestNextFrame(@NonNull FrameStep step) {
        if (mDestroyed) return;
        mNextDirty |= 1 << step.ordinal();
        bookFrame();
    }

    /** Names the first caller outside the pipeline's plumbing that asked for {@code index} late. */
    private void recordLateCaller(int index) {
        if (mLateBy == null) mLateBy = new String[FrameStep.TABLE.length];
        StringBuilder who = new StringBuilder();
        int kept = 0;
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("com.termux.app.window.") || c.startsWith("com.termux.app.chrome.ChromeRenderer")) continue;
            if (who.length() > 0) who.append(" < ");
            who.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName())
                .append(':').append(e.getLineNumber());
            if (++kept == 4) break;
        }
        View root = mRoot;
        if (root != null && root.isInLayout()) who.append(" (in layout)");
        mLateBy[index] = who.toString();
    }


    /**
     * Arms {@code step} to run in the pre-draw hook of every frame that draws, or disarms it. An
     * armed step never asks for a frame itself, so at rest, with nothing drawing, it costs
     * nothing: for work that must see every drawn frame (the glass commit re-recording what moved
     * beneath a Live surface).
     */
    public void arm(@NonNull FrameStep step, boolean armed) {
        int bit = 1 << step.ordinal();
        mArmed = armed ? mArmed | bit : mArmed & ~bit;
    }

    /** Whether {@code step} is armed. */
    public boolean isArmed(@NonNull FrameStep step) {
        return (mArmed & (1 << step.ordinal())) != 0;
    }

    /**
     * Runs the whole table once, now, in order, for a caller that needs the result before the next
     * frame (a pass already in the frame's animation phase, pause, a test). {@code who} names the
     * caller in the log.
     */
    public void flush(@NonNull String who) {
        if (mDestroyed) return;
        mFlushCount++;
        mLastFlushBy = who;
        Log log = mLog;
        if (log != null) log.log(LOG_FLUSH, who);
        if (mRunning) {
            // A step asked from inside the table: the rest of this run and the next frame cover it.
            mDirty = allSteps();
            bookFrame();
            return;
        }
        mDirty = allSteps();
        runTable(true, false);
    }

    /** Whether the root's first frame is still ahead (or being held). */
    public boolean isBeforeFirstFrame() {
        return mRoot != null && !mFirstFrameDrawn && !mInFirstFrame;
    }

    /** Whether a frame is booked for recorded work; false at rest. */
    public boolean isFrameBooked() {
        return mAnimationBooked || mPostBooked;
    }

    public int flushCount() {
        return mFlushCount;
    }

    @Nullable
    public String lastFlushBy() {
        return mLastFlushBy;
    }

    /** How many first frames were let go by a backstop rather than settled. */
    public int firstFrameBackstops() {
        return mFirstFrameBackstops;
    }

    /**
     * Steps {@code hold} once per frame while it is open, for a wait counted in frames (a surface
     * that needs a frame or two to fill): each frame the hold counts the frame and ends by its time
     * backstop when that has passed, then {@code onFrame} runs; it answers false once its owner is
     * done. A hold released elsewhere is dropped. Frames are asked only while a stepped hold is
     * open and the root has a window: without one, nothing ticks, and the owner's own timer is the
     * backstop. Stepping the same hold again replaces what it asks.
     */
    public void stepHold(@NonNull Hold hold, @NonNull BooleanSupplier onFrame) {
        if (mDestroyed || !hold.isOpen()) return;
        int at = mSteppedHolds.indexOf(hold);
        if (at >= 0) {
            mHoldSteps.set(at, onFrame);
        } else {
            mSteppedHolds.add(hold);
            mHoldSteps.add(onFrame);
        }
        bookHoldFrame();
    }

    /** One booked frame, run now, as the vsync would run it; for tests. */
    void runBookedFrameForTest() {
        runBookedPass();
    }

    /** How many holds are stepped per frame now; for tests. */
    int steppedHolds() {
        return mSteppedHolds.size();
    }

    /** Unhooks the root and drops every booked pass; nothing runs afterwards. */
    public void destroy() {
        mDestroyed = true;
        mDirty = 0;
        mNextDirty = 0;
        for (int i = mSteppedHolds.size() - 1; i >= 0; i--) {
            mSteppedHolds.get(i).release(Hold.LANDING, "destroyed");
        }
        mSteppedHolds.clear();
        mHoldSteps.clear();
        for (int i = mFirstFrameHolds.size() - 1; i >= 0; i--) {
            mFirstFrameHolds.get(i).release(Hold.LANDING, "destroyed");
        }
        mFirstFrameHolds.clear();
        unhook();
        mRoot = null;
        mFirstFrameDrawn = true;
    }

    // ------------------------------------------------------------------ frames

    private void bookFrame() {
        View root = mRoot;
        if (root != null && root.isAttachedToWindow()) {
            if (mAnimationBooked) return;
            mAnimationBooked = true;
            root.postOnAnimation(mBookedPass);
        } else if (!mPostBooked) {
            mPostBooked = true;
            mHandler.post(mBookedPass);
        }
    }

    private void unbook() {
        if (mAnimationBooked && mRoot != null) mRoot.removeCallbacks(mBookedPass);
        if (mPostBooked) mHandler.removeCallbacks(mBookedPass);
        mAnimationBooked = false;
        mPostBooked = false;
    }

    private void unhook() {
        unbook();
        View root = mRoot;
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(mPreDraw);
    }

    private void runBookedPass() {
        mAnimationBooked = false;
        mPostBooked = false;
        if (mDestroyed) return;
        mDirty |= mNextDirty;
        mNextDirty = 0;
        View root = mRoot;
        boolean framed = root != null && root.isAttachedToWindow();
        // Stepped holds tick on real frames only: a post without a window would spin.
        if (framed && !mSteppedHolds.isEmpty()) stepHolds();
        if (mDirty != 0 && !mDestroyed) {
            // Without a window the post stands in for the whole frame, the draw steps too.
            runTable(!framed, false);
            mAfterPass = framed;
            // A draw step waits for the pre-draw hook: make sure this frame draws.
            if (framed && (mDirty & DRAW_STEPS) != 0 && !mDestroyed) root.invalidate();
        }
        if (!mSteppedHolds.isEmpty()) bookHoldFrame();
    }

    /** One frame for every stepped hold; what a step does may step, release or add holds. */
    private void stepHolds() {
        Hold[] holds = mSteppedHolds.toArray(new Hold[0]);
        BooleanSupplier[] steps = mHoldSteps.toArray(new BooleanSupplier[0]);
        for (int i = 0; i < holds.length; i++) {
            Hold hold = holds[i];
            boolean keep = hold.onFrame() && steps[i].getAsBoolean() && hold.isOpen();
            int at = mSteppedHolds.indexOf(hold);
            // Re-stepped by its own step: the newer ask stays.
            if (!keep && at >= 0 && mHoldSteps.get(at) == steps[i]) {
                mSteppedHolds.remove(at);
                mHoldSteps.remove(at);
            }
        }
        for (int i = mSteppedHolds.size() - 1; i >= 0; i--) {
            if (!mSteppedHolds.get(i).isOpen()) {
                mSteppedHolds.remove(i);
                mHoldSteps.remove(i);
            }
        }
    }

    /** The next frame for the stepped holds, through the same booked pass; only with a window. */
    private void bookHoldFrame() {
        View root = mRoot;
        if (mDestroyed || root == null || !root.isAttachedToWindow() || mAnimationBooked) return;
        mAnimationBooked = true;
        root.postOnAnimation(mBookedPass);
    }

    /** The one pre-draw hook. Runs the table only on a frame an input marked dirty. */
    private boolean onPreDraw() {
        View root = mRoot;
        if (mDestroyed || root == null) return true;
        mAfterPass = false;
        if (mFirstFrameDrawn) {
            mDirty |= mArmed;
            // After the animation phase: what runs here draws on this frame's layout.
            if (mDirty != 0) runTable(true, true);
            return true;
        }
        // The window's first frame: the whole table runs, whatever was asked (a held frame past
        // the first few runs only what was asked since, so a long relaunch hold costs no table).
        boolean laidOutBefore = root.isLayoutRequested();
        mDirty |= mNextDirty;
        mNextDirty = 0;
        mInFirstFrame = true;
        try {
            if (mFirstFrameAttempts++ <= FIRST_FRAME_MAX_HELD) mDirty = allSteps();
            mDirty |= mArmed;
            runTable(true, false);
        } finally {
            mInFirstFrame = false;
        }
        boolean relaunchHeld = false;
        boolean relaunchBackstop = false;
        for (int i = mFirstFrameHolds.size() - 1; i >= 0; i--) {
            Hold hold = mFirstFrameHolds.get(i);
            if (hold.isOpen() && hold.onFrame()) {
                relaunchHeld = true;
                continue;
            }
            if (Hold.BACKSTOP.equals(hold.releaseReason())) {
                mFirstFrameBackstops++;
                relaunchBackstop = true;
            }
            mFirstFrameHolds.remove(i);
        }
        // The one draw a hold cancels: the first frame after a relaunch waits for the surface it
        // carried. Its backstops (frames and time) are on the hold itself.
        if (relaunchHeld) return false;
        boolean tableLaidOut = !laidOutBefore && root.isLayoutRequested();
        if (!tableLaidOut) {
            releaseFirstFrame(relaunchBackstop ? "backstop relaunch" : "signal");
            return true;
        }
        long now = mClock.getAsLong();
        if (mFirstFrameHeld == 0) mFirstFrameHeldSinceMs = now;
        if (mFirstFrameHeld >= FIRST_FRAME_MAX_HELD) {
            mFirstFrameBackstops++;
            releaseFirstFrame("backstop frames");
            return true;
        }
        if (now - mFirstFrameHeldSinceMs >= FIRST_FRAME_BACKSTOP_MS) {
            mFirstFrameBackstops++;
            releaseFirstFrame("backstop time");
            return true;
        }
        // The one draw the pipeline cancels: the table moved a view before anything was shown, so
        // the layout runs again first. One frame later beats a first frame wrong.
        mFirstFrameHeld++;
        Trace.beginSection("Frames.firstFrameHeld");
        Trace.endSection();
        return false;
    }

    private void releaseFirstFrame(@NonNull String reason) {
        mFirstFrameDrawn = true;
        if (Counters.enabled) {
            Counters.count(reason.startsWith("backstop") ? "first-frame:backstop" : "first-frame:signal", reason);
        }
        Log log = mLog;
        if (log != null) log.log(LOG_FIRST_FRAME, reason);
    }

    /**
     * Runs the dirty steps in table order. {@code preDraw} when the frame's layout has settled
     * (the pre-draw hook, a flush, a frame with no window), else the draw steps stay dirty for the
     * pre-draw hook of this frame. {@code late} marks a run in the pre-draw pass after the frame's
     * layout: a step that asks for a layout there draws one frame of its new look on the old
     * layout, which the guards count so a device shows whether it happens.
     */
    private void runTable(boolean preDraw, boolean late) {
        if (mRunning) return;
        unbook();
        mRunning = true;
        Trace.beginSection("Frames.table");
        try {
            for (int i = 0; i < mSteps.length; i++) {
                int bit = 1 << i;
                if ((mDirty & bit) == 0) continue;
                if (!preDraw && (DRAW_STEPS & bit) != 0) continue;
                mDirty &= ~bit;
                Runnable step = mSteps[i];
                if (step == null || mDestroyed) continue;
                // Cleared first: a step that asks for itself again names itself.
                if (mLateBy != null) mLateBy[i] = null;
                if (late) runLate(i, step);
                else step.run();
            }
        } finally {
            mRunning = false;
            Trace.endSection();
        }
        // A step that asked again from inside the table gets the next frame; a draw step left for
        // this frame's pre-draw hook does not book another.
        if (((preDraw ? mDirty : mDirty & ~DRAW_STEPS) | mNextDirty) != 0 && !mDestroyed) bookFrame();
    }

    private void runLate(int index, @NonNull Runnable step) {
        View root = mRoot;
        boolean laidOutBefore = root != null && root.isLayoutRequested();
        step.run();
        if (!Counters.enabled) return;
        Counters.count(LATE_KEYS[index], null);
        if (root != null && !laidOutBefore && root.isLayoutRequested()) {
            String by = mLateBy == null ? null : mLateBy[index];
            mGuards.layoutRequested(FrameStep.TABLE[index].name(),
                "late in pre-draw: new look drawn on the old layout" + (by == null ? "" : " asked by " + by));
        }
    }

    private static String[] lateKeys() {
        String[] keys = new String[FrameStep.TABLE.length];
        for (int i = 0; i < keys.length; i++) keys[i] = "pre-draw-step:" + FrameStep.TABLE[i].name();
        return keys;
    }

    private static int allSteps() {
        return (1 << FrameStep.TABLE.length) - 1;
    }

    private static int drawSteps() {
        int bits = 0;
        for (FrameStep step : FrameStep.TABLE) {
            if (step.drawStep()) bits |= 1 << step.ordinal();
        }
        return bits;
    }
}
