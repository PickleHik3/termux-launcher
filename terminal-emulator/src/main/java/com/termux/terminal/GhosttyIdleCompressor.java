package com.termux.terminal;

/**
 * Compresses a Ghostty pane's history once its output has been idle for {@link #IDLE_MILLIS}.
 *
 * <p>Each output turn moves the one pending callback to {@link #IDLE_MILLIS} after it, so a burst
 * leaves exactly one callback behind it (one per idle edge) and nothing polls. When it runs it
 * takes one bounded, incremental compression step; while the library says more is pending, the
 * next step is posted straight after, at most {@link #MAX_STEPS} per edge. New output cancels the
 * chain. Once the last step has run nothing stays scheduled, so a pane at rest wakes nothing.
 *
 * <p>Main thread only.
 */
final class GhosttyIdleCompressor {

    /** Output idle time before compressing: past a typing burst, well before anyone looks away. */
    static final long IDLE_MILLIS = 250;
    /**
     * Steps one idle edge may take. Each step is bounded by the library; this only guarantees the
     * chain ends even if a step kept reporting more work.
     */
    static final int MAX_STEPS = 64;

    /** Where the callbacks go: the session's main-thread handler, or a fake in tests. */
    interface Scheduler {
        void postDelayed(Runnable runnable, long delayMillis);

        void cancel(Runnable runnable);
    }

    /** One incremental step on the pane's core: a {@code GhosttyNative.COMPRESS_*} result, or -1. */
    interface Step {
        int compressStep();
    }

    private final Scheduler mScheduler;
    private final Step mStep;
    private boolean mScheduled;
    private int mSteps;
    private final Runnable mRun = this::run;

    GhosttyIdleCompressor(Scheduler scheduler, Step step) {
        mScheduler = scheduler;
        mStep = step;
    }

    /** A turn of output reached the core: compress once it has been quiet for a while. */
    void onOutput() {
        if (mScheduled) mScheduler.cancel(mRun);
        mScheduled = true;
        mSteps = 0;
        mScheduler.postDelayed(mRun, IDLE_MILLIS);
    }

    /** The pane closed or left the Ghostty core: drop whatever is scheduled. */
    void cancel() {
        if (!mScheduled) return;
        mScheduled = false;
        mScheduler.cancel(mRun);
    }

    /** Whether a callback is scheduled; false at rest. */
    boolean isScheduled() {
        return mScheduled;
    }

    private void run() {
        mScheduled = false;
        int result = mStep.compressStep();
        if (result == GhosttyNative.COMPRESS_PENDING && ++mSteps < MAX_STEPS) {
            mScheduled = true;
            mScheduler.postDelayed(mRun, 0);
        }
    }
}
