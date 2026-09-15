package com.termux.app.chrome;

/**
 * The hold that takes a pane corner. A corner square is not a button: the program drawing in it —
 * tmux's clock, vim's ruler, a status line's last column — keeps every touch that does not rest
 * there, so a finger down in the square goes to the terminal first and the corner only claims it
 * once it has been still long enough to mean a hold.
 *
 * <p>Three ways out of that wait, and two of them give the gesture up for good: the finger travels
 * past the slop, a second finger lands, or it lifts. The chrome keeps handing the terminal every
 * event either way — it consumed the touch stream to be able to forward it — and simply claims
 * nothing.
 *
 * <p>Pure by design, like {@link com.termux.view.AimState} on the other side of the same square:
 * the overlay feeds it a touch stream and reads back who owns the gesture and what the lift meant.
 */
public final class CornerHold {

    public enum Phase {
        /** No finger, or one whose gesture is over. */
        IDLE,
        /** A finger is down in the square and the terminal has a copy of every event so far. */
        PENDING,
        /** The hold fired on a still finger: the corner owns the gesture now. */
        HELD,
        /** Held at a corner a seam ends at, then dragged: the split is being resized. */
        RESIZING,
        /** Held at a corner no seam ends at, then dragged: the tab is being carried out. */
        CARRYING,
        /** The terminal kept the gesture. Events still go to it; nothing here will claim them. */
        ABANDONED
    }

    /** What one event did to the gesture, for the overlay that has to answer for it. */
    public enum Move {
        /** Nothing to do. */
        NONE,
        /** Still waiting on the hold: the terminal has this event too. */
        FORWARD,
        /** Past the slop before the hold fired — the terminal keeps the gesture. */
        ABANDONED,
        /** The first move past the slop after the hold: the drag starts here. */
        COMMITTED,
        /** A later move of a drag already under way. */
        DRAGGING
    }

    /** What the lift meant. */
    public enum Lift {
        /** Not ours. The terminal had this gesture all along and finishes it. */
        NOTHING,
        /** Open the tab on the corner the finger asked at, whether or not a drag carried it. */
        OPEN_TAB,
        /** Settle the split the drag resized. */
        COMMIT_RESIZE
    }

    private Phase mPhase = Phase.IDLE;

    private float mDownX, mDownY;

    /** How far a finger may travel and still be holding still. */
    private float mHoldSlop;

    /** How far a finger must travel after the hold before it is a drag rather than a lift. */
    private float mDragSlop;

    /** Whether this corner is the end of a seam, which is what a drag after the hold resizes. */
    private boolean mAtSeam;

    /** A finger landed in the square. The terminal keeps it unless it holds still. */
    public void down(float x, float y, float holdSlopPx, float dragSlopPx, boolean atSeam) {
        mPhase = Phase.PENDING;
        mDownX = x;
        mDownY = y;
        mHoldSlop = holdSlopPx;
        mDragSlop = dragSlopPx;
        mAtSeam = atSeam;
    }

    /**
     * The hold time elapsed. A finger that travelled or was joined by another has given the
     * gesture up through {@link #move} or {@link #secondFinger} by now, so a still pending hold is
     * a still one.
     *
     * @return true when the corner just claimed the gesture, and the terminal is owed a cancel.
     */
    public boolean holdElapsed() {
        if (mPhase != Phase.PENDING)
            return false;
        mPhase = Phase.HELD;
        return true;
    }

    /** The finger moved. Before the hold that is the program's gesture; after it, a drag. */
    public Move move(float x, float y) {
        switch (mPhase) {
            case PENDING:
                if (travelled(x, y, mHoldSlop)) {
                    mPhase = Phase.ABANDONED;
                    return Move.ABANDONED;
                }
                return Move.FORWARD;
            case ABANDONED:
                return Move.FORWARD;
            case HELD:
                if (!travelled(x, y, mDragSlop))
                    return Move.NONE;
                mPhase = mAtSeam ? Phase.RESIZING : Phase.CARRYING;
                return Move.COMMITTED;
            case RESIZING:
            case CARRYING:
                return Move.DRAGGING;
            default:
                return Move.NONE;
        }
    }

    /**
     * A second finger landed. Two fingers on a terminal are a scroll or a pinch, never a hold, so
     * the corner lets go and both of them belong to the program.
     *
     * @return true when this is what gave the gesture up.
     */
    public boolean secondFinger() {
        if (mPhase != Phase.PENDING)
            return false;
        mPhase = Phase.ABANDONED;
        return true;
    }

    /** The finger lifted, ending the gesture either way. */
    public Lift lift() {
        Phase phase = mPhase;
        mPhase = Phase.IDLE;
        if (phase == Phase.HELD || phase == Phase.CARRYING) return Lift.OPEN_TAB;
        if (phase == Phase.RESIZING) return Lift.COMMIT_RESIZE;
        return Lift.NOTHING;
    }

    /** Forget the gesture entirely — the window went away, or an ancestor took the stream. */
    public void reset() {
        mPhase = Phase.IDLE;
    }

    /** Whether the terminal is still owed copies of the events arriving now. */
    public boolean forwardsToTerminal() {
        return mPhase == Phase.PENDING || mPhase == Phase.ABANDONED;
    }

    /** Whether the corner owns the gesture and the terminal has been told to forget it. */
    public boolean isClaimed() {
        return mPhase == Phase.HELD || mPhase == Phase.RESIZING || mPhase == Phase.CARRYING;
    }

    /** Whether a finger is down in the square at all, claimed or not. */
    public boolean isTracking() {
        return mPhase != Phase.IDLE;
    }

    public Phase phase() {
        return mPhase;
    }

    private boolean travelled(float x, float y, float slop) {
        float dx = x - mDownX;
        float dy = y - mDownY;
        return dx * dx + dy * dy > slop * slop;
    }
}
