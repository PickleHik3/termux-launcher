package com.termux.app.x11;

import androidx.annotation.NonNull;

/**
 * Stopping the display in two steps instead of one: ask every app on it to close itself, and only
 * kill the server once they have gone.
 *
 * <p>Killing the server outright takes every X client's connection away mid-session, which is why
 * an app reopened afterwards offers to restore a crashed one. A window told to close by the window
 * manager's own route saves what it has and exits, and the window list drains as it does. So the
 * sequence is: close them all, wait for the list to empty, then the kill that was always there.
 *
 * <p>All policy, no views and no sockets. It knows three things — how many windows are listed, that
 * the list changed, and that the wait ran out — and it answers with the two actions. The wait has a
 * ceiling of {@value #DRAIN_TIMEOUT_MS} ms: an app that puts up its own "save before closing?"
 * dialog, or one that simply ignores the request, must not hold the Stop the user asked for.
 */
public final class DisplayStopSequence {

    /** How long the drain is given before the server is killed anyway. */
    public static final long DRAIN_TIMEOUT_MS = 3000L;

    /** The two things a stop does, in the order this class puts them in. */
    public interface Actions {
        /** Ask every listed window to close, the way its own close button would. */
        void closeAllWindows();

        /** Stop the display server itself. Called exactly once per stop. */
        void killDisplayServer();
    }

    /** The drain's ceiling, handed in so a test can let it run out by hand. */
    public interface Scheduler {
        /** Run {@code action} in {@code delayMs}; only ever one is pending. */
        void schedule(long delayMs, @NonNull Runnable action);

        /** Drop a pending action, if there is one. */
        void cancel();
    }

    @NonNull private final Actions actions;
    @NonNull private final Scheduler scheduler;

    /** True between the close-all and the kill. */
    private boolean waiting;

    private final Runnable expire = this::onDrainTimeout;

    public DisplayStopSequence(@NonNull Actions actions, @NonNull Scheduler scheduler) {
        this.actions = actions;
        this.scheduler = scheduler;
    }

    /** True while the apps are being given their chance to close. */
    public boolean isWaitingForDrain() {
        return waiting;
    }

    /**
     * The user asked for the display to stop, with {@code windowCount} apps listed on it. With
     * nothing listed there is nobody to ask, so the kill happens straight away. A second ask while
     * the first is still draining changes nothing — the windows have already been told.
     */
    public void start(int windowCount) {
        if (waiting) return;
        if (windowCount <= 0) {
            kill();
            return;
        }
        waiting = true;
        actions.closeAllWindows();
        scheduler.cancel();
        scheduler.schedule(DRAIN_TIMEOUT_MS, expire);
    }

    /**
     * The window list changed while a stop was waiting. An empty list means every app has gone and
     * the server can be stopped now rather than at the ceiling.
     */
    public void onWindowsChanged(int windowCount) {
        if (!waiting || windowCount > 0) return;
        kill();
    }

    /**
     * The activity is going away, or the display stopped by some other route. Nothing further is
     * killed on this sequence's behalf.
     */
    public void abandon() {
        waiting = false;
        scheduler.cancel();
    }

    private void onDrainTimeout() {
        if (!waiting) return;
        kill();
    }

    private void kill() {
        waiting = false;
        scheduler.cancel();
        actions.killDisplayServer();
    }
}
