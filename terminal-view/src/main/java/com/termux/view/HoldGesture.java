package com.termux.view;

/**
 * The terminal's hold, and everything the finger may do after it. One decision point: a finger
 * that is still at {@link HoldTiming#holdTimeoutMs()} has held, and from there the finger itself
 * chooses between clicking, dragging and selecting text — nothing else is decided by time again.
 *
 * <p>On a terminal that is tracking the mouse the hold hands the finger the mouse: the lift clicks
 * the cell under it, and a drag is a button-held mouse drag when the program asked for motion.
 * Keeping still for longer again takes the hold to its second stage and selects text. A second
 * finger hands both fingers back to the wheel or the pinch, before or after the hold. A plain shell
 * has no mouse to offer, so its hold is text selection at the first stage, as Termux always has.
 *
 * <p>Pure by design — it is fed a touch stream and answers with what the view owes the user, so
 * every row of that grammar is a unit test rather than a thumb on a phone.
 */
final class HoldGesture {

    enum Phase {
        /** No finger, or one whose gesture the hold has finished with. */
        IDLE,
        /** A finger is down and will have held if it stays still long enough. */
        PENDING,
        /** The hold is recognised and the finger has not yet said what it wants. */
        HELD,
        /** The finger committed to a drag the program is being told about. */
        DRAGGING,
        /** This gesture is over as far as the hold is concerned, whoever ended up with it. */
        DONE
    }

    /** What one touch event means. The view turns these into haptics, mouse events and selection. */
    enum Outcome {
        /** Nothing is owed. */
        NOTHING,
        /** The hold is recognised on a mouse-tracking terminal: buzz, and the finger is the mouse. */
        HOLD_MOUSE,
        /**
         * The hold is text selection: on a plain shell that is the first stage, and on a
         * mouse-tracking one it is a finger that kept holding into the second.
         */
        HOLD_SELECTED,
        /** The drag is committed: tick, and hold the button down from the cell held. */
        DRAG_STARTED,
        /** The drag moved on: report wherever it is now. */
        DRAG_MOVED,
        /** The drag is over: let the button up. */
        DRAG_ENDED,
        /** The lift clicks the cell the finger ended on. */
        CLICK,
        /** The hold is given up; the fingers belong to the wheel, the pinch or the scroll again. */
        ABANDONED
    }

    private Phase mPhase = Phase.IDLE;

    /** Where the finger landed, which is where it held: travel before the hold cancels it. */
    private float mHoldX, mHoldY;

    /** Where the finger is now. */
    private float mX, mY;

    private float mSlop;

    /** Whether the program asked to be told about motion while a button is held. */
    private boolean mMotionReported;

    /** Whether the finger has travelled since the hold, which is what the hint waits for. */
    private boolean mTravelled;

    /** A finger landed. Only an {@code available} one can hold: see the view's own guard. */
    void down(float x, float y, float slopPixels, boolean available) {
        mPhase = available ? Phase.PENDING : Phase.IDLE;
        mHoldX = mX = x;
        mHoldY = mY = y;
        mSlop = slopPixels;
        mMotionReported = false;
        mTravelled = false;
    }

    /**
     * The hold time elapsed. A finger that travelled has already given the hold up, so a still
     * pending one is a still one.
     *
     * @param mouseTracking whether the program is reading the mouse, which is what there is to hand over.
     * @param motionReported whether it also asked for motion while a button is held.
     */
    Outcome holdElapsed(boolean mouseTracking, boolean motionReported) {
        if (mPhase != Phase.PENDING)
            return Outcome.NOTHING;
        if (!mouseTracking) {
            // No mouse to hand over: the hold is the selection, and the gesture belongs to it now.
            mPhase = Phase.DONE;
            return Outcome.HOLD_SELECTED;
        }
        mMotionReported = motionReported;
        mPhase = Phase.HELD;
        return Outcome.HOLD_MOUSE;
    }

    /** The finger moved. Before the hold that is a scroll; after it, a drag of one kind or another. */
    Outcome move(float x, float y) {
        boolean travelled = travelledFrom(mHoldX, mHoldY, x, y);
        mX = x;
        mY = y;
        switch (mPhase) {
            case PENDING:
                if (travelled)
                    mPhase = Phase.DONE;
                return Outcome.NOTHING;
            case HELD:
                if (!travelled)
                    return Outcome.NOTHING;
                // The finger has said what it wanted, so the second stage is off. A program that
                // asked for no motion is told nothing until the lift, which still clicks.
                mTravelled = true;
                if (!mMotionReported)
                    return Outcome.NOTHING;
                mPhase = Phase.DRAGGING;
                return Outcome.DRAG_STARTED;
            case DRAGGING:
                return Outcome.DRAG_MOVED;
            default:
                return Outcome.NOTHING;
        }
    }

    /**
     * The select time elapsed. Only a finger that has held and then stayed exactly where it was
     * gets the second stage: once it is dragging it has already said what it wanted.
     */
    Outcome selectElapsed() {
        if (!heldAndStill())
            return Outcome.NOTHING;
        // The selection owns the gesture from here, as it does on a plain shell.
        mPhase = Phase.DONE;
        return Outcome.HOLD_SELECTED;
    }

    /** A second finger landed: the wheel or the pinch, whether or not the hold was recognised. */
    Outcome pointerDown() {
        switch (mPhase) {
            case PENDING:
            case HELD:
                mPhase = Phase.DONE;
                return Outcome.ABANDONED;
            case DRAGGING:
                mPhase = Phase.DONE;
                return Outcome.DRAG_ENDED;
            default:
                return Outcome.NOTHING;
        }
    }

    /** The last finger lifted at {@code x}, {@code y}, ending the gesture whatever it turned out to be. */
    Outcome up(float x, float y) {
        mX = x;
        mY = y;
        Phase phase = mPhase;
        mPhase = Phase.DONE;
        switch (phase) {
            case HELD:
                return Outcome.CLICK;
            case DRAGGING:
                return Outcome.DRAG_ENDED;
            default:
                return Outcome.NOTHING;
        }
    }

    /** Something else took the gesture, or the window did. A committed drag still owes its release. */
    Outcome cancel() {
        Phase phase = mPhase;
        mPhase = Phase.DONE;
        return phase == Phase.DRAGGING ? Outcome.DRAG_ENDED : Outcome.NOTHING;
    }

    /** Forget the last gesture entirely, which is what the next finger down does. */
    void reset() {
        mPhase = Phase.IDLE;
        mTravelled = false;
        mMotionReported = false;
    }

    /** Whether the hold is still waiting on its timer, and the view still owes it one. */
    boolean isPending() {
        return mPhase == Phase.PENDING;
    }

    /** Whether the hold was recognised and this gesture is the view's rather than the scroll's. */
    boolean isHeld() {
        return mPhase == Phase.HELD || mPhase == Phase.DRAGGING;
    }

    /** Whether the hold is recognised and the finger has not moved since. */
    boolean heldAndStill() {
        return mPhase == Phase.HELD && !mTravelled;
    }

    /**
     * Whether the second stage is still reachable: the finger is on its way to the hold, or has
     * held and stayed put. Anything else - a drag, a lift, a second finger - has answered already.
     */
    boolean reachesSelect() {
        return mPhase == Phase.PENDING || heldAndStill();
    }

    Phase phase() {
        return mPhase;
    }

    float holdX() {
        return mHoldX;
    }

    float holdY() {
        return mHoldY;
    }

    float x() {
        return mX;
    }

    float y() {
        return mY;
    }

    private boolean travelledFrom(float fromX, float fromY, float x, float y) {
        float dx = x - fromX;
        float dy = y - fromY;
        return dx * dx + dy * dy > mSlop * mSlop;
    }
}
