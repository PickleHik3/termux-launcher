package com.termux.app.sound;

/**
 * Decision 3: a process the launcher keeps running is restarted the moment it exits, until it
 * has exited {@link #MAX_EXITS} times within {@link #WINDOW_MS}; then it is left down until
 * {@link #reset} (the next need). Pure: the caller hands in the clock.
 */
final class RestartCap {

    static final int MAX_EXITS = 3;
    static final long WINDOW_MS = 60_000L;

    /** The last {@link #MAX_EXITS} exits, oldest first; {@link Long#MIN_VALUE} for none. */
    private final long[] exits = new long[MAX_EXITS];

    RestartCap() {
        reset();
    }

    /**
     * Records an exit at {@code nowMs}; true when the process may be started again, false when
     * this exit is the one that reaches the cap.
     */
    synchronized boolean onExit(long nowMs) {
        System.arraycopy(exits, 1, exits, 0, MAX_EXITS - 1);
        exits[MAX_EXITS - 1] = nowMs;
        long oldest = exits[0];
        return oldest == Long.MIN_VALUE || nowMs - oldest >= WINDOW_MS;
    }

    /** Forget every exit: the next need starts from a clean slate. */
    synchronized void reset() {
        java.util.Arrays.fill(exits, Long.MIN_VALUE);
    }
}
