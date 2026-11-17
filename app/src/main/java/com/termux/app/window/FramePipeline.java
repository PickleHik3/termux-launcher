package com.termux.app.window;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private final Runnable[] mSteps = new Runnable[FrameStep.TABLE.length];
    private int mDirty;
    @NonNull private final LongSupplier mClock;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable private Log mLog;

    @Nullable private View mRoot;
    private boolean mAnimationBooked;
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
        bookFrame();
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
        runTable();
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

    /** Unhooks the root and drops every booked pass; nothing runs afterwards. */
    public void destroy() {
        mDestroyed = true;
        mDirty = 0;
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
        if (mDestroyed || mDirty == 0) return;
        runTable();
    }

    /** The one pre-draw hook. Runs the table only on a frame an input marked dirty. */
    private boolean onPreDraw() {
        View root = mRoot;
        if (mDestroyed || root == null) return true;
        if (mFirstFrameDrawn) {
            if (mDirty != 0) runTable();
            return true;
        }
        // The window's first frame: the whole table runs, whatever was asked.
        boolean laidOutBefore = root.isLayoutRequested();
        mInFirstFrame = true;
        try {
            mDirty = allSteps();
            runTable();
        } finally {
            mInFirstFrame = false;
        }
        boolean tableLaidOut = !laidOutBefore && root.isLayoutRequested();
        if (!tableLaidOut) {
            releaseFirstFrame("signal");
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
        Log log = mLog;
        if (log != null) log.log(LOG_FIRST_FRAME, reason);
    }

    private void runTable() {
        if (mRunning) return;
        unbook();
        mRunning = true;
        Trace.beginSection("Frames.table");
        try {
            for (int i = 0; i < mSteps.length; i++) {
                int bit = 1 << i;
                if ((mDirty & bit) == 0) continue;
                mDirty &= ~bit;
                Runnable step = mSteps[i];
                if (step != null && !mDestroyed) step.run();
            }
        } finally {
            mRunning = false;
            Trace.endSection();
        }
        // A step that asked again from inside the table gets the next frame.
        if (mDirty != 0 && !mDestroyed) bookFrame();
    }

    private static int allSteps() {
        return (1 << FrameStep.TABLE.length) - 1;
    }
}
