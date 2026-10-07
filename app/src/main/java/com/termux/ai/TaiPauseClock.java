package com.termux.ai;

/**
 * How long something has spent paused, so a deadline can leave the pauses out. Speech the user
 * paused (Read aloud's Pause, or a notification taking the audio for a moment) is still a request
 * in flight, and the waits on it — {@link TaiTtsPlayer#awaitDone} in the runtime, the IPC wait on
 * {@link TaiManager#speak} in the app — would otherwise fail it after a long enough pause.
 *
 * <p>A waiter notes {@link #totalPausedMs} when it starts and moves its deadline out by however
 * much the total has grown since; the total includes a pause still under way, so a deadline keeps
 * moving for as long as the pause lasts. Times are whatever monotonic clock the caller passes
 * ({@code SystemClock.elapsedRealtime} in the app), which keeps this free of Android for tests.
 * Thread-safe; pause and resume are idempotent.
 */
final class TaiPauseClock {
    private long pausedAt = -1L;
    private long closedTotal;

    /** Starts a pause at {@code now}; no-op while one is under way. */
    synchronized void pause(long now) {
        if (pausedAt < 0L) pausedAt = now;
    }

    /** Ends the pause under way at {@code now}; no-op when there is none. */
    synchronized void resume(long now) {
        if (pausedAt < 0L) return;
        closedTotal += Math.max(0L, now - pausedAt);
        pausedAt = -1L;
    }

    synchronized boolean isPaused() {
        return pausedAt >= 0L;
    }

    /** Every pause so far, the one under way counted up to {@code now}. Never decreases. */
    synchronized long totalPausedMs(long now) {
        return pausedAt < 0L ? closedTotal : closedTotal + Math.max(0L, now - pausedAt);
    }

    /**
     * What is left of a wait that began at {@code startedAt} with {@code timeoutMs} to run, when
     * {@code pausedAtStart} was {@link #totalPausedMs} then: the pauses since move the end out.
     */
    synchronized long remainingMs(long startedAt, long timeoutMs, long pausedAtStart, long now) {
        return startedAt + timeoutMs + (totalPausedMs(now) - pausedAtStart) - now;
    }
}
