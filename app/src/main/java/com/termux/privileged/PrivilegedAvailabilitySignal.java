package com.termux.privileged;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Tells the UI when privileged access comes or goes, so a reading that needs it can hide while it
 * is missing and come back by itself when Shizuku or su/rish arrives, with no polling of its own.
 *
 * <p>Any thread may {@link #report} what it just found; only a real flip is passed on, and it is
 * passed on through {@code main}, never inline, so a report made from inside a measure or a draw
 * cannot re-enter the view that asked. Delivery re-reads the latest report rather than carrying
 * the value it was posted with: a flip and its undo arriving before main gets to run (the
 * {@code INITIALIZING} step of a re-initialization) cancel out and nobody sees a blink.
 *
 * <p>{@link #delivered()} is the value the listeners last heard. A consumer that reads it on main
 * and then listens sees every later change, which the latest report alone cannot promise.
 */
public final class PrivilegedAvailabilitySignal {

    /** Called on the main thread when privileged access has actually become (un)available. */
    public interface Listener {
        void onPrivilegedAvailabilityChanged(boolean available);
    }

    @NonNull private final Executor main;
    private final Object lock = new Object();
    /** The latest report from any thread. Unavailable until something has decided otherwise. */
    private boolean reported;
    /** Main thread only: what the listeners were last told. */
    private boolean delivered;
    /** Main thread only. */
    private final List<Listener> listeners = new ArrayList<>();

    PrivilegedAvailabilitySignal(@NonNull Executor main) {
        this.main = main;
    }

    /** Records what a check just found; posts a delivery only when it differs from the last report. */
    void report(boolean available) {
        synchronized (lock) {
            if (available == reported) return;
            reported = available;
        }
        main.execute(this::deliver);
    }

    private void deliver() {
        boolean now;
        synchronized (lock) {
            now = reported;
        }
        if (now == delivered) return;
        delivered = now;
        // A copy: a listener may rebuild itself, which unregisters and registers it again.
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onPrivilegedAvailabilityChanged(now);
        }
    }

    /** Main thread only: the availability the listeners were last told about. */
    boolean delivered() {
        return delivered;
    }

    /** Main thread only. Not called back with the current value: read {@link #delivered()}. */
    void add(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    /** Main thread only. */
    void remove(@NonNull Listener listener) {
        listeners.remove(listener);
    }
}
