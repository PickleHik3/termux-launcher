package com.termux.launcherctl;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Folds a burst of requests into one run. The first request schedules the work after a short
 * delay and every request until the work starts rides along with it; a request made once the work
 * has started schedules one more run, so nothing asked for after a run began is missed.
 *
 * <p>Requests may come from any thread; the work runs wherever the scheduler puts it.
 */
public final class CoalescingTask {

    public interface Scheduler {
        /** Runs {@code task} after {@code delayMs}; false when it could not be scheduled. */
        boolean schedule(@NonNull Runnable task, long delayMs);
    }

    private final AtomicBoolean mPending = new AtomicBoolean();
    private final long mDelayMs;
    @NonNull private final Scheduler mScheduler;
    @NonNull private final Runnable mWork;
    @NonNull private final Runnable mRun = this::run;

    public CoalescingTask(long delayMs, @NonNull Scheduler scheduler, @NonNull Runnable work) {
        mDelayMs = Math.max(0L, delayMs);
        mScheduler = scheduler;
        mWork = work;
    }

    /** Asks for a run; a no-op while one is already scheduled and has not started. */
    public void request() {
        if (!mPending.compareAndSet(false, true)) return;
        if (!mScheduler.schedule(mRun, mDelayMs)) mPending.set(false);
    }

    public boolean isPending() {
        return mPending.get();
    }

    private void run() {
        // Cleared before the work, so a request arriving mid-run schedules a fresh pass.
        mPending.set(false);
        mWork.run();
    }
}
