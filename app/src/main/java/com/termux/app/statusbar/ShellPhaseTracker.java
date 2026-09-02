package com.termux.app.statusbar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The lifecycle of the command in each shell, as the window pills show it — the same three states
 * a terminal workspace manager badges its agents with, read from what the terminal can see rather
 * than from agent hooks:
 *
 * <ul>
 * <li>{@link Phase#WORKING} while the command is busy, and for a short grace after it goes quiet, so
 *     an agent's pause between two lines of output does not flicker the ring.</li>
 * <li>{@link Phase#ATTENTION} when it went quiet with a question on screen — an approval, a choice,
 *     a yes/no — in a window the user is not looking at.</li>
 * <li>{@link Phase#DONE} when it went quiet or handed the terminal back to the shell after a real
 *     stretch of work, again while unseen. Seen windows need no badge: the user is looking at the
 *     result.</li>
 * </ul>
 *
 * <p>Both badges hold until the window is seen. Free of Android imports; time is passed in.
 */
public final class ShellPhaseTracker {

    public enum Phase { NONE, WORKING, ATTENTION, DONE }

    /** Work shorter than this is a directory listing, not a task whose end is worth a badge. */
    public static final long MIN_WORK_MS = 1500L;
    /**
     * How long a command stays quiet before it counts as waiting or finished. Longer than the gaps
     * an agent leaves between rendering steps, shorter than the time it takes to wonder.
     */
    public static final long QUIET_MS = 2500L;

    private static final class Entry {
        long workStartMs = -1L;
        long lastWorkMs = -1L;
        Phase phase = Phase.NONE;
        /** True once the current quiet spell has been judged, so it is judged once. */
        boolean settled;
    }

    private final Map<Integer, Entry> mEntries = new HashMap<>();

    /**
     * One observation of a shell.
     *
     * @param working            whether the command is busy right now (CPU or output).
     * @param foregroundKnown    whether the foreground process could be read at all.
     * @param foregroundIsShell  whether the shell itself has the terminal, i.e. the command is gone.
     * @param questionOnScreen   evaluated only when a quiet spell is judged, so reading the screen is
     *                           paid for once per transition rather than on every refresh.
     * @param seen               whether the user is looking at this shell's window.
     * @return the phase to show.
     */
    public Phase observe(int pid, long nowMs, boolean working, boolean foregroundKnown,
                         boolean foregroundIsShell, BooleanSupplier questionOnScreen,
                         boolean seen) {
        Entry e = mEntries.get(pid);
        if (e == null) {
            e = new Entry();
            mEntries.put(pid, e);
        }
        if (working) {
            if (e.phase != Phase.WORKING || nowMs - e.lastWorkMs > QUIET_MS) e.workStartMs = nowMs;
            e.lastWorkMs = nowMs;
            e.phase = Phase.WORKING;
            e.settled = false;
            return e.phase;
        }
        if (e.phase == Phase.WORKING && !e.settled) {
            boolean handedBack = foregroundKnown && foregroundIsShell;
            boolean quiet = nowMs - e.lastWorkMs >= QUIET_MS;
            if (!handedBack && !quiet) return Phase.WORKING;
            e.settled = true;
            long worked = e.lastWorkMs - e.workStartMs;
            if (seen || worked < MIN_WORK_MS) {
                e.phase = Phase.NONE;
            } else if (!handedBack && questionOnScreen.getAsBoolean()) {
                e.phase = Phase.ATTENTION;
            } else {
                e.phase = Phase.DONE;
            }
            return e.phase;
        }
        if (seen && (e.phase == Phase.ATTENTION || e.phase == Phase.DONE)) e.phase = Phase.NONE;
        return e.phase;
    }

    /** The user is looking at this shell: whatever it wanted them to see, they have seen. */
    public void markSeen(int pid) {
        Entry e = mEntries.get(pid);
        if (e != null && e.phase != Phase.WORKING) e.phase = Phase.NONE;
    }

    public Phase phaseOf(int pid) {
        Entry e = mEntries.get(pid);
        return e == null ? Phase.NONE : e.phase;
    }

    /**
     * When the soonest working-but-quiet shell has to be judged, or -1 when none is waiting. The
     * host schedules one refresh for that moment rather than polling.
     */
    public long nextJudgementMs(long nowMs) {
        long soonest = -1L;
        for (Entry e : mEntries.values()) {
            if (e.phase != Phase.WORKING || e.settled) continue;
            long due = e.lastWorkMs + QUIET_MS;
            if (soonest < 0L || due < soonest) soonest = Math.max(nowMs, due);
        }
        return soonest;
    }

    /** Drops shells that no longer exist. */
    public void retain(Set<Integer> livePids) {
        for (Iterator<Integer> it = mEntries.keySet().iterator(); it.hasNext(); ) {
            if (!livePids.contains(it.next())) it.remove();
        }
    }
}
