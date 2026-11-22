package com.termux.app.terminal;

/**
 * Who may take the frost under a turning window switch's gap away again (#316). Each switch that
 * shows it takes a ticket; only the latest switch's end (or cancel) hides it, so an earlier switch
 * landing under a newer one leaves the newer frost up. A backstop hides it past the travel, the
 * first-draw gate's wait and a margin, whatever happened to the animator: the frost is never left
 * on at rest. Pure; the caller owns the view and the clock.
 */
public final class SwitchFrostHold {

    /** Past the travel and the gate's wait, how long the backstop waits before it hides the frost. */
    public static final long BACKSTOP_MARGIN_MS = 250L;

    private int mTicket;
    private boolean mShown;
    private long mDeadlineMs;

    /**
     * A switch shows the frost: its ticket, and the frost now stands until that ticket ends or the
     * backstop passes.
     *
     * @param travelMs the switch's travel
     * @param gateWaitMs the longest the travel may wait for its first-draw gate
     */
    public int begin(long nowMs, long travelMs, long gateWaitMs) {
        mTicket++;
        mShown = true;
        mDeadlineMs = nowMs + backstopDelayMs(travelMs, gateWaitMs);
        return mTicket;
    }

    /** How long after {@link #begin} the backstop runs. */
    public static long backstopDelayMs(long travelMs, long gateWaitMs) {
        return Math.max(0L, travelMs) + Math.max(0L, gateWaitMs) + BACKSTOP_MARGIN_MS;
    }

    /**
     * The switch holding {@code ticket} ended or was cancelled: whether the frost goes now. False
     * for a ticket a newer switch has replaced, or when the frost is already gone.
     */
    public boolean end(int ticket) {
        if (!mShown || ticket != mTicket) return false;
        mShown = false;
        return true;
    }

    /** The backstop: whether the frost goes now because its switch has run past its time. */
    public boolean expire(long nowMs) {
        if (!mShown || nowMs < mDeadlineMs) return false;
        mShown = false;
        return true;
    }

    /** A switch that shows no frost, or the window going away: whether a frost still up goes now. */
    public boolean drop() {
        if (!mShown) return false;
        mShown = false;
        return true;
    }

    public boolean shown() {
        return mShown;
    }
}
