package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One chrome geometry pass per transition, and one terminal resize.
 *
 * <p>A keyboard coming up used to be a run of passes: the show's own, one posted after the
 * keyboard's layout, one for the pane host's new size, one for every pane whose grid then
 * reflowed — each building a {@code ChromeSpec} and a {@code DockLayout} and booking a layout of
 * its own, with the panes' grid resized after whichever pass happened to pair a pause with a
 * resume. This module is the one place those triggers meet. Callers say <em>why</em> geometry may
 * have moved ({@link Reason}) and when the panes may hear about it ({@link ResizePolicy}); it
 * decides when the pass runs and owns the pause that makes the panes hear one size.
 *
 * <ul>
 *   <li><b>Coalescing.</b> {@link #request} books the pass for the end of the current dispatch;
 *   every request before then joins it. {@link #requestNow} runs it before returning, for the
 *   callers whose next traversal must already see the new geometry (a keyboard's reveal gate
 *   applies against it), unless a {@linkplain #beginBatch batch} is open: then the batch's end
 *   runs the one pass.</li>
 *   <li><b>Echoes.</b> A pass changes layout, and layout calls back ({@link Reason#LAYOUT}, and
 *   {@link Reason#METRICS} once the grid it paused reflows). The pass is a function of the state
 *   its trigger changed and of a few layout facts, which the host digests
 *   ({@link Host#layoutInputsKey}); an echo whose facts are the ones the last pass saw would
 *   compute that pass's answer again, so it runs nothing.</li>
 *   <li><b>The grid.</b> A request with {@link ResizePolicy#NOW} pauses every pane's sizing from
 *   the moment it is made — so a layout that lands before the pass (the system keyboard resizing
 *   the window) reflows nothing — and resumes it once, after the layout of the last pass the
 *   transition booked. A {@link Transition} holds it until {@link #settle}, the way a slide, a
 *   divider drag or the status bar's fold do. Nobody pairs a pause with a resume by hand.</li>
 * </ul>
 *
 * <p>Pure: everything with a window behind it goes through {@link Host}, so the counting is
 * provable on a fake ({@code GeometrySchedulerTest}).
 */
public final class GeometryScheduler {

    /** Why geometry may have moved. Only the echoes are ever dropped. */
    public enum Reason {
        /** The in-app keyboard was shown, hidden, re-measured or previewed. */
        KEYBOARD,
        /** Window insets moved: the system keyboard, the system bars, the root's IME margin. */
        INSETS,
        /** A watched view changed size: usually the layout a pass itself booked. */
        LAYOUT,
        /** The terminal's rows or font changed: usually the reflow a pass itself released. */
        METRICS,
        /** The wall is sliding between places and gives the content room ahead of settle. */
        PLACE_TRAVEL,
        /** The wall, or a keyboard swipe, came to rest. */
        PLACE_SETTLE,
        /** A split's weights are being moved: a divider drag, a resize key, focus growth. */
        DIVIDER,
        /** The status bar is folding or unfolding. */
        STATUS_FOLD,
        /** The app drawer let go of the stack it froze. */
        DRAWER,
        /** Styling or preferences were reloaded. */
        STYLING,
        /** HOME arrived while already home. */
        HOME;

        /** A request that is usually the layout of an earlier pass coming back. */
        public boolean isEcho() {
            return this == LAYOUT || this == METRICS;
        }
    }

    /** When the panes' grid may follow the geometry a request moves. */
    public enum ResizePolicy {
        /** The request does not concern the grid; a pass it causes runs under any hold open. */
        NONE,
        /** Pause the grid now and resize it once, after the pass's layout. */
        NOW,
        /**
         * The grid belongs to the open transition and resizes when it settles. With nothing open
         * it is {@link #NOW}.
         */
        AT_SETTLE
    }

    /** The window-side half: posting, the pass itself, and the panes' pause. */
    public interface Host {

        /** Runs {@code frame} once the current dispatch is over, ahead of the next traversal. */
        void postFrame(@NonNull Runnable frame);

        /** Runs {@code afterLayout} after the traversal the current changes have booked. */
        void postAfterLayout(@NonNull Runnable afterLayout);

        void postDelayed(@NonNull Runnable runnable, long delayMs);

        void removeCallbacks(@NonNull Runnable runnable);

        /** One chrome geometry pass: one spec, one dock layout, one set of layout writes. */
        void runPass(@NonNull Reason reason);

        /** Pauses every pane's grid sizing; sizes arriving meanwhile are kept as pending. */
        void beginGridHold();

        /** Resumes it: each pane sends its one size, anchored at the bottom edge. */
        void finishGridHold();

        /** Holds the Linux display's screen size the same way. */
        void beginDisplayHold();

        void finishDisplayHold();

        /**
         * A digest of every layout fact the pass reads — the content root's size as the pass's
         * own margin will leave it, the terminal's top, the font's line metrics, how many panes
         * share the host. Equal digests mean the pass would compute the same answer.
         */
        long layoutInputsKey();

        /** False once the window is going away; nothing posted may run then. */
        boolean isAlive();
    }

    /**
     * Something that owns the grid from {@link #begin} to {@link #settle}: a slide, a drag, the
     * fold. Settling twice is harmless.
     */
    public static final class Transition {
        @NonNull final Reason reason;
        final boolean defersEchoes;
        final boolean holdsDisplay;
        boolean settled;

        Transition(@NonNull Reason reason, boolean defersEchoes, boolean holdsDisplay) {
            this.reason = reason;
            this.defersEchoes = defersEchoes;
            this.holdsDisplay = holdsDisplay;
        }
    }

    /**
     * Fail-safe for a {@link ResizePolicy#NOW} hold whose pass never got its layout (a window
     * that stopped drawing): the grid is resumed rather than left at a size it never reported.
     * Transitions are their owners' to end and are not timed out.
     */
    static final long HOLD_BACKSTOP_MS = 500L;

    @NonNull private final Host mHost;

    private boolean mPassPending;
    @Nullable private Reason mPendingReason;
    private boolean mFramePosted;
    private boolean mInPass;
    private int mBatchDepth;

    private boolean mHoldOpen;
    private boolean mDisplayHoldOpen;
    private int mOpenTransitions;
    private int mDeferringTransitions;
    private boolean mEchoDeferred;
    private boolean mSettleCheckPosted;

    private boolean mHasPassKey;
    private long mLastPassKey;

    private boolean mSettlePassPosted;

    private final Runnable mFrame = this::onFrame;
    private final Runnable mSettlePass = this::onSettlePass;
    private final Runnable mSettleCheck = this::onSettleCheck;
    private final Runnable mBackstop = this::onBackstop;

    public GeometryScheduler(@NonNull Host host) {
        mHost = host;
    }

    // ------------------------------------------------------------------ requests

    /** Books a pass for the end of the current dispatch; requests until then share it. */
    public void request(@NonNull Reason reason, @NonNull ResizePolicy policy) {
        if (!accept(reason, policy)) return;
        if (mInPass || mBatchDepth > 0) return;
        postFrame();
    }

    /**
     * Runs the pass before returning — with everything already booked folded into it — unless a
     * batch is open, when the batch's end runs it, or a pass is running, when it follows that one.
     */
    public void requestNow(@NonNull Reason reason, @NonNull ResizePolicy policy) {
        if (!accept(reason, policy)) return;
        if (mInPass) {
            postFrame();
            return;
        }
        if (mBatchDepth > 0) return;
        runPendingPass();
    }

    /** Requests made until the matching {@link #endBatch} share one pass, run at the end. */
    public void beginBatch() {
        mBatchDepth++;
    }

    public void endBatch() {
        if (mBatchDepth == 0) return;
        if (--mBatchDepth > 0) return;
        if (!mInPass) runPendingPass();
        if (mHoldOpen && !mPassPending) postSettleCheck();
    }

    /**
     * Notes that a pass ran outside the scheduler — a direct relayout of the stack — so the echo
     * of its layout is measured against the facts it saw.
     */
    public void notePassInputs() {
        mLastPassKey = mHost.layoutInputsKey();
        mHasPassKey = true;
    }

    /**
     * A pass that found the panes' room changed: the grid is paused, if it is not already, and
     * resumed once after the layout this pass booked — or at the settle of a transition open.
     */
    public void resizeGridAfterLayout() {
        openHold();
        // Behind the traversal the caller's layout writes have just booked.
        postSettleCheck();
    }

    /** False when the request runs nothing: an echo of facts the last pass already saw. */
    private boolean accept(@NonNull Reason reason, @NonNull ResizePolicy policy) {
        if (reason.isEcho()) {
            // The running pass is the one being echoed.
            if (mInPass) return false;
            // A fold reshapes the content every frame; its settle runs the one pass for all of it.
            if (mDeferringTransitions > 0) {
                mEchoDeferred = true;
                return false;
            }
            if (!mPassPending && mHasPassKey && mHost.layoutInputsKey() == mLastPassKey)
                return false;
        }
        if (policy != ResizePolicy.NONE) openHold();
        if (!mPassPending || (mPendingReason != null && mPendingReason.isEcho()))
            mPendingReason = reason;
        mPassPending = true;
        return true;
    }

    private void postFrame() {
        if (mFramePosted) return;
        mFramePosted = true;
        mHost.postFrame(mFrame);
    }

    /**
     * Books the pending pass behind the traversal already booked, so it reads the layout a
     * transition ended on — the bar's last height — rather than the one before it.
     */
    private void postSettlePass() {
        if (mSettlePassPosted) return;
        mSettlePassPosted = true;
        mHost.postAfterLayout(mSettlePass);
    }

    private void onSettlePass() {
        mSettlePassPosted = false;
        runBookedPass();
    }

    private void onFrame() {
        mFramePosted = false;
        runBookedPass();
    }

    private void runBookedPass() {
        if (!mHost.isAlive()) {
            mPassPending = false;
            mPendingReason = null;
            releaseHolds();
            return;
        }
        if (mBatchDepth > 0 || mInPass) return;
        runPendingPass();
    }

    private void runPendingPass() {
        if (!mPassPending) return;
        Reason reason = mPendingReason != null ? mPendingReason : Reason.LAYOUT;
        mPassPending = false;
        mPendingReason = null;
        mInPass = true;
        try {
            mHost.runPass(reason);
        } finally {
            mInPass = false;
            notePassInputs();
            if (mPassPending) postFrame();
            // A check posted before this pass would run ahead of the layout it just booked.
            if (mHoldOpen) postSettleCheck();
        }
    }

    // ------------------------------------------------------------------ transitions

    /**
     * Opens a transition: the grid is paused from now until {@link #settle}. With
     * {@code defersEchoes} the layout it changes every frame books no pass until then; with
     * {@code holdsDisplay} the Linux display keeps its screen size too.
     */
    @NonNull
    public Transition begin(@NonNull Reason reason, boolean defersEchoes, boolean holdsDisplay) {
        Transition transition = new Transition(reason, defersEchoes, holdsDisplay);
        mOpenTransitions++;
        if (defersEchoes) mDeferringTransitions++;
        openHold();
        if (holdsDisplay && !mDisplayHoldOpen) {
            mDisplayHoldOpen = true;
            mHost.beginDisplayHold();
        }
        return transition;
    }

    /**
     * Ends a transition. With {@code runPass}, or when it deferred echoes, one pass is booked
     * behind the layout the transition ended on, so it reads that layout; the grid resumes after
     * the layout of the last pass booked, once nothing else holds it.
     */
    public void settle(@Nullable Transition transition, boolean runPass) {
        if (transition == null || transition.settled) return;
        transition.settled = true;
        mOpenTransitions = Math.max(0, mOpenTransitions - 1);
        boolean echoed = false;
        if (transition.defersEchoes) {
            mDeferringTransitions = Math.max(0, mDeferringTransitions - 1);
            if (mDeferringTransitions == 0) {
                echoed = mEchoDeferred;
                mEchoDeferred = false;
            }
        }
        if ((runPass || echoed) && accept(transition.reason, ResizePolicy.AT_SETTLE)) {
            // The pass posts the check behind its own layout.
            if (!mInPass && mBatchDepth == 0) postSettlePass();
            return;
        }
        postSettleCheck();
    }

    // ------------------------------------------------------------------ the hold

    private void openHold() {
        if (mHoldOpen) return;
        mHoldOpen = true;
        mHost.beginGridHold();
        mHost.removeCallbacks(mBackstop);
        mHost.postDelayed(mBackstop, HOLD_BACKSTOP_MS);
    }

    /**
     * Posts the check behind whatever is queued now — moving one posted earlier, which could
     * otherwise run ahead of a traversal booked since.
     */
    private void postSettleCheck() {
        if (mSettleCheckPosted) mHost.removeCallbacks(mSettleCheck);
        mSettleCheckPosted = true;
        mHost.postAfterLayout(mSettleCheck);
    }

    /** After a pass's layout: resume the grid, unless more geometry is on its way. */
    private void onSettleCheck() {
        mSettleCheckPosted = false;
        if (mOpenTransitions > 0 || mPassPending || mInPass || mBatchDepth > 0) return;
        releaseHolds();
    }

    private void onBackstop() {
        if (!mHoldOpen || mOpenTransitions > 0 || mInPass || mBatchDepth > 0) return;
        if (mPassPending && mHost.isAlive()) runPendingPass();
        releaseHolds();
    }

    private void releaseHolds() {
        if (mHoldOpen) {
            mHoldOpen = false;
            mHost.removeCallbacks(mBackstop);
            mHost.finishGridHold();
        }
        if (mDisplayHoldOpen && mOpenTransitions == 0) {
            mDisplayHoldOpen = false;
            mHost.finishDisplayHold();
        }
    }

    /** Whether the grid is paused by this scheduler right now. */
    public boolean isGridHeld() {
        return mHoldOpen;
    }
}
