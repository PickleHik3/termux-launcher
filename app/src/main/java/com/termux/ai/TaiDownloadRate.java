package com.termux.ai;

/**
 * A smoothed transfer rate for one download, fed from the byte counter as it moves.
 *
 * <p>The raw rate between two progress samples jumps around (one sample lands mid-TCP-burst, the
 * next after a stall), so the number shown next to a progress bar would flicker and the ETA with
 * it. This keeps an exponential moving average with a time constant of a few seconds: fast enough
 * to follow a real change in speed, slow enough that the digits hold still while you read them.
 * Samples closer together than {@link #MIN_SAMPLE_MS} are folded into the next one, because a
 * rate computed over a handful of milliseconds is noise.
 */
public final class TaiDownloadRate {
    /** How fast the average follows a change: after this long at a new speed, ~63% of the way there. */
    static final long TIME_CONSTANT_MS = 3_000L;
    /** Two samples closer than this are treated as one; a rate over less time is not a rate. */
    static final long MIN_SAMPLE_MS = 250L;

    private long lastBytes = -1L;
    private long lastAtMs;
    private double bytesPerSecond;
    private boolean primed;

    /** Feeds the current byte count at {@code nowMs}; returns the smoothed rate after this sample. */
    public synchronized double update(long bytes, long nowMs) {
        if (lastBytes < 0L) {
            lastBytes = bytes;
            lastAtMs = nowMs;
            return bytesPerSecond;
        }
        long elapsed = nowMs - lastAtMs;
        if (elapsed < MIN_SAMPLE_MS) return bytesPerSecond;
        long delta = bytes - lastBytes;
        if (delta < 0L) {
            // The counter went backwards (a restart from a different offset): start over rather
            // than average a negative burst into the estimate.
            reset();
            lastBytes = bytes;
            lastAtMs = nowMs;
            return bytesPerSecond;
        }
        double instant = delta * 1000.0 / elapsed;
        if (!primed) {
            bytesPerSecond = instant;
            primed = true;
        } else {
            double alpha = 1.0 - Math.exp(-(double) elapsed / TIME_CONSTANT_MS);
            bytesPerSecond += alpha * (instant - bytesPerSecond);
        }
        lastBytes = bytes;
        lastAtMs = nowMs;
        return bytesPerSecond;
    }

    public synchronized double bytesPerSecond() {
        return bytesPerSecond;
    }

    /** Seconds until {@code remainingBytes} arrive at the current rate, or -1 when there is no rate yet. */
    public synchronized long etaSeconds(long remainingBytes) {
        if (remainingBytes <= 0L) return 0L;
        if (!primed || bytesPerSecond < 1.0) return -1L;
        return (long) Math.ceil(remainingBytes / bytesPerSecond);
    }

    /** Forgets the history: the next sample primes the average again. Used when a transfer resumes
     *  after a pause, so the idle stretch does not read as a stall. */
    public synchronized void reset() {
        lastBytes = -1L;
        lastAtMs = 0L;
        bytesPerSecond = 0.0;
        primed = false;
    }
}
