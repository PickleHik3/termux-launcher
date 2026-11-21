package com.termux.app.chrome;

/**
 * When a surface's frozen pages layer is taken again at rest (ADR 0029 section 3): only once something asked for it (the wall's content changed, or the layer no
 * longer matches the pages), at most one capture per {@link #REFRESH_INTERVAL_MS}, and a request
 * that arrived inside the interval is taken as soon as it ends, so the last change is always
 * seen. Pure: the caller passes the clock. One per surface.
 */
final class PageLayerRefresh {

    /** The shortest time between two captures of one surface's pages layer. */
    static final long REFRESH_INTERVAL_MS = 250L;

    private boolean mRequested;
    private boolean mTaken;
    private long mLastTakenMs;

    /** Something beneath changed, or the layer stopped matching: a capture is wanted. */
    void request() {
        mRequested = true;
    }

    /** Whether a capture is wanted, due or not. */
    boolean requested() {
        return mRequested;
    }

    /** Whether the capture wanted may start at {@code nowMs}. */
    boolean due(long nowMs) {
        return mRequested && (!mTaken || nowMs - mLastTakenMs >= REFRESH_INTERVAL_MS);
    }

    /** A capture started at {@code nowMs}: nothing is wanted until the next request. */
    void taken(long nowMs) {
        mRequested = false;
        mTaken = true;
        mLastTakenMs = nowMs;
    }

    /**
     * How long until the wanted capture is due, 0 when it is due now, -1 when none is wanted: the
     * delay to wake a trailing capture after.
     */
    long waitMs(long nowMs) {
        if (!mRequested) return -1L;
        if (!mTaken) return 0L;
        return Math.max(0L, mLastTakenMs + REFRESH_INTERVAL_MS - nowMs);
    }

    /** Forgets everything: the layer was let go and starts over. */
    void reset() {
        mRequested = false;
        mTaken = false;
        mLastTakenMs = 0L;
    }
}
