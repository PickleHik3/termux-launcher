package com.termux.terminal;

/**
 * Decides whether shell output that has just arrived is the echo of a keystroke, and so is drawn
 * at once instead of through a post. Pure arithmetic on the {@code SystemClock.uptimeMillis()}
 * clock: the caller owns the state and the switch.
 *
 * <p>A flood of output stays on the coalescing path: after one inline draw the next must wait
 * {@link #MIN_GAP_MS}, so a {@code cat} started by Enter paints at most once per gap, not once per
 * slice parsed.
 */
public final class EchoInlineGate {

    /** How soon after a keystroke write output still counts as its echo. */
    public static final long WINDOW_MS = 50L;
    /** The least time between two inline draws of one session. */
    public static final long MIN_GAP_MS = 8L;

    private EchoInlineGate() {}

    /**
     * @param enabled        the runtime switch
     * @param nowMs          when the output arrived
     * @param lastWriteMs    when the session last had a keystroke written, 0 for never
     * @param lastInlineMs   when this gate last said yes for the session, 0 for never
     */
    public static boolean shouldRedrawInline(boolean enabled, long nowMs, long lastWriteMs,
                                             long lastInlineMs) {
        if (!enabled || lastWriteMs <= 0L) return false;
        long sinceWrite = nowMs - lastWriteMs;
        if (sinceWrite < 0L || sinceWrite > WINDOW_MS) return false;
        return lastInlineMs <= 0L || nowMs - lastInlineMs >= MIN_GAP_MS;
    }
}
