package com.termux.app.statusbar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Which shells have produced output recently — tmux's {@code monitor-activity}, in the smallest form
 * that answers "is this window working?".
 *
 * <p>Free of Android imports so it can be unit-tested. Time is passed in rather than read, so the
 * decay is testable without sleeping and the host can drive it from one clock.
 */
public final class ShellActivityTracker {

    /**
     * How long after its last output a shell still counts as working. Long enough that the gaps
     * between a compiler's lines do not read as stopping, short enough that the indication dies
     * within about a second of the command finishing.
     */
    public static final long DECAY_MS = 1200L;

    private final Map<Integer, Long> mLastActivityMs = new HashMap<>();

    /** Records output from {@code pid}. Cheap enough to call on every screen update. */
    public void noteActivity(int pid, long nowMs) {
        if (pid <= 0) return;
        mLastActivityMs.put(pid, nowMs);
    }

    public boolean isActive(int pid, long nowMs) {
        Long last = mLastActivityMs.get(pid);
        return last != null && nowMs - last < DECAY_MS;
    }

    /** Drops shells whose last output is older than {@code cutoffMs}, so dead pids cannot pile up. */
    public void pruneBefore(long cutoffMs) {
        for (Iterator<Map.Entry<Integer, Long>> it = mLastActivityMs.entrySet().iterator();
                it.hasNext(); ) {
            if (it.next().getValue() < cutoffMs) it.remove();
        }
    }

    public void forget(int pid) {
        mLastActivityMs.remove(pid);
    }

    public void clear() {
        mLastActivityMs.clear();
    }

    /**
     * When the soonest still-active shell stops counting as working, or -1 when none is. Lets the
     * host schedule exactly one refresh at the moment the indication has to come down, instead of
     * polling.
     */
    public long nextExpiryMs(long nowMs) {
        long soonest = -1L;
        for (Long last : mLastActivityMs.values()) {
            long expiry = last + DECAY_MS;
            if (expiry <= nowMs) continue;
            if (soonest < 0L || expiry < soonest) soonest = expiry;
        }
        return soonest;
    }
}
