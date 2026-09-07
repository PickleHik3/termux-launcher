package com.termux.app.statusbar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Which shells have produced output recently, and which of them have produced enough of it for long
 * enough that it reads as a command working rather than as a keystroke being echoed back.
 *
 * <p>A single screen update is not work: every character typed at a prompt produces one. So a burst
 * is tracked — when it started, when it was last extended, and how many updates it holds — and only a
 * burst that is both long enough and busy enough counts. A gap longer than {@link #DECAY_MS} ends the
 * burst, so each command starts its own instead of inheriting the previous one's credit.
 *
 * <p>Typing is the other thing that is not work, and a remote program echoing every keystroke — a
 * shell or an agent's prompt over ssh — redraws in enough pieces for the burst rule alone to read a
 * sentence as a command running. So output that lands within {@link #INPUT_ECHO_MS} of the user's
 * last keystroke is taken as echo: it never starts a burst and it ends the one in progress, so the
 * indication cannot come up in the pause after the typing stops, when the last echoes are still
 * inside the decay window but the keystroke that caused them is no longer "just now".
 *
 * <p>Free of Android imports so it can be unit-tested. Time is passed in rather than read, so the
 * decay is testable without sleeping and the host can drive it from one clock.
 */
public final class ShellActivityTracker {

    /**
     * How long after its last output a shell still counts as producing output. Long enough that the
     * gaps between a compiler's lines do not read as stopping, short enough that the indication dies
     * within about a second of the command finishing.
     */
    public static final long DECAY_MS = 1200L;

    /** How long a burst has to run before it can read as work. */
    public static final long SUSTAIN_MS = 450L;

    /** And in at least this many separate screen updates, so one long paste is not "working". */
    public static final int SUSTAIN_UPDATES = 4;

    /**
     * How long after a keystroke the output that follows is its echo rather than a command's work.
     * Long enough for a round trip to a remote shell, short enough that a command started by the
     * Enter key is credited with its first second of output.
     */
    public static final long INPUT_ECHO_MS = 700L;

    private static final class Burst {
        long firstMs;
        long lastMs;
        int updates;

        Burst(long nowMs) {
            firstMs = nowMs;
            lastMs = nowMs;
            updates = 1;
        }
    }

    private final Map<Integer, Burst> mBursts = new HashMap<>();

    /** Records output from {@code pid}. Cheap enough to call on every screen update. */
    public void noteActivity(int pid, long nowMs) {
        noteActivity(pid, nowMs, -1L);
    }

    /**
     * Records output from {@code pid}, knowing when the user last typed into it ({@code -1} or
     * {@code 0} when never). Output inside {@link #INPUT_ECHO_MS} of that keystroke is echo: it is
     * not recorded, and any burst under way is dropped rather than left to be judged later.
     */
    public void noteActivity(int pid, long nowMs, long lastInputMs) {
        if (pid <= 0) return;
        if (lastInputMs > 0L && nowMs - lastInputMs < INPUT_ECHO_MS) {
            mBursts.remove(pid);
            return;
        }
        Burst burst = mBursts.get(pid);
        if (burst == null || nowMs - burst.lastMs >= DECAY_MS) {
            mBursts.put(pid, new Burst(nowMs));
            return;
        }
        burst.lastMs = nowMs;
        burst.updates++;
    }

    /** Whether {@code pid} produced any output inside the decay window. */
    public boolean isActive(int pid, long nowMs) {
        Burst burst = mBursts.get(pid);
        return burst != null && nowMs - burst.lastMs < DECAY_MS;
    }

    /**
     * Whether {@code pid}'s current burst is sustained enough to read as work: still live, at least
     * {@link #SUSTAIN_UPDATES} updates, and spanning at least {@link #SUSTAIN_MS}.
     */
    public boolean isWorking(int pid, long nowMs) {
        Burst burst = mBursts.get(pid);
        return burst != null && nowMs - burst.lastMs < DECAY_MS
            && burst.updates >= SUSTAIN_UPDATES
            && burst.lastMs - burst.firstMs >= SUSTAIN_MS;
    }

    /** Drops shells whose last output is older than {@code cutoffMs}, so dead pids cannot pile up. */
    public void pruneBefore(long cutoffMs) {
        for (Iterator<Map.Entry<Integer, Burst>> it = mBursts.entrySet().iterator();
                it.hasNext(); ) {
            if (it.next().getValue().lastMs < cutoffMs) it.remove();
        }
    }

    public void forget(int pid) {
        mBursts.remove(pid);
    }

    public void clear() {
        mBursts.clear();
    }

    /**
     * When the soonest still-active shell stops counting as working, or -1 when none is. Lets the
     * host schedule exactly one refresh at the moment the indication has to come down, instead of
     * polling.
     */
    public long nextExpiryMs(long nowMs) {
        long soonest = -1L;
        for (Burst burst : mBursts.values()) {
            long expiry = burst.lastMs + DECAY_MS;
            if (expiry <= nowMs) continue;
            if (soonest < 0L || expiry < soonest) soonest = expiry;
        }
        return soonest;
    }
}
