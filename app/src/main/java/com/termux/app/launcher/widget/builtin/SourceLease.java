package com.termux.app.launcher.widget.builtin;

import android.os.Handler;

import androidx.annotation.NonNull;

/**
 * One registration a shared widget source holds with the system (a broadcast receiver, a content
 * observer) while any widget wants it. It opens with the first holder and closes after the last
 * one leaves, on the next turn of the main loop rather than at once: a widget that rebuilds stops
 * and starts again in the same turn, and with the registration kept across that gap nothing new
 * reaches the system server. Every receiver or observer registered in the system server is a
 * Binder object held there until the app's side is collected, so registering one per rebuild was
 * what filled the app's Binder quota.
 *
 * <p>Opened at most once between two closes; every open is matched by exactly one close. Main
 * thread only.</p>
 */
final class SourceLease {

    /** Where the deferred close runs: the main handler in the app, a hand-run queue in tests. */
    interface Scheduler {
        void post(@NonNull Runnable task);
        void cancel(@NonNull Runnable task);
    }

    /** The registration itself. */
    interface Registration {
        /** Registers; whether it took (a source with no provider may refuse). */
        boolean open();
        void close();
    }

    @NonNull static Scheduler on(@NonNull Handler handler) {
        return new Scheduler() {
            @Override public void post(@NonNull Runnable task) { handler.post(task); }
            @Override public void cancel(@NonNull Runnable task) { handler.removeCallbacks(task); }
        };
    }

    @NonNull private final Scheduler scheduler;
    @NonNull private final Registration registration;
    private boolean open;
    private boolean closePending;
    private final Runnable closeTask = this::closeNow;

    SourceLease(@NonNull Scheduler scheduler, @NonNull Registration registration) {
        this.scheduler = scheduler;
        this.registration = registration;
    }

    /**
     * A holder wants the registration: a pending close is called off, and it is opened when it
     * is not open. Whether this call opened it (false when it was kept, or refused).
     */
    boolean ensureOpen() {
        if (closePending) {
            closePending = false;
            scheduler.cancel(closeTask);
        }
        if (open) return false;
        open = registration.open();
        return open;
    }

    /** The last holder left: closed on the next turn unless a holder comes back first. */
    void closeSoon() {
        if (!open || closePending) return;
        closePending = true;
        scheduler.post(closeTask);
    }

    /** Closed now, pending or not: the source is torn down, or the registration is unwanted. */
    void closeNow() {
        if (closePending) {
            closePending = false;
            scheduler.cancel(closeTask);
        }
        if (!open) return;
        open = false;
        registration.close();
    }

    boolean isOpen() { return open; }

    boolean isClosePending() { return closePending; }
}
