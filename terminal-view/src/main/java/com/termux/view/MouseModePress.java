package com.termux.view;

/**
 * Mouse mode's left button, and when it is allowed to go down. Normally the finger landing is the
 * press — that is what makes mouse mode feel like a mouse.
 *
 * <p>A finger that landed in a pane corner is the exception. The corner lets the touch through and
 * may still claim it as a hold, at which point the view is sent a cancel; a button already pressed
 * would be released by that cancel and the program would read the pair as a full click on a corner
 * the user was only reaching for. So that press waits: it goes out with the first real movement, or
 * as a whole click with the lift, and a corner that takes the finger sends neither.
 *
 * <p>Pure by design — it is told what the touch stream did and answers with what the program is
 * owed, so the rule is a unit test rather than a thumb on a phone.
 */
final class MouseModePress {

    /** What one touch event owes the program. */
    enum Step {
        /** Nothing. */
        NOTHING,
        /** The button goes down here. */
        PRESS,
        /** The button goes down and straight back up here: one whole click. */
        CLICK,
        /** The button, which is down, comes up here. */
        RELEASE
    }

    /** Whether the program has been told the button is down. */
    private boolean mPressed;

    /** Whether a press is owed but has not gone out, because a corner may still take this finger. */
    private boolean mOwed;

    /**
     * A finger landed.
     *
     * @param tracking whether the program is reading the mouse at all.
     * @param holdExempt whether it landed in a pane corner, which may still claim it as a hold.
     */
    Step down(boolean tracking, boolean holdExempt) {
        mPressed = false;
        mOwed = false;
        if (!tracking)
            return Step.NOTHING;
        if (holdExempt) {
            mOwed = true;
            return Step.NOTHING;
        }
        mPressed = true;
        return Step.PRESS;
    }

    /** The finger moved. A deferred press is owed as soon as the movement is real. */
    Step move(boolean pastSlop) {
        if (!mOwed || !pastSlop)
            return Step.NOTHING;
        mOwed = false;
        mPressed = true;
        return Step.PRESS;
    }

    /** The finger lifted. A press that never went out still owes the whole click. */
    Step up() {
        if (mOwed) {
            mOwed = false;
            return Step.CLICK;
        }
        return letUp();
    }

    /** The gesture was taken away — by the corner that claimed it, or by the window. */
    Step cancel() {
        mOwed = false;
        return letUp();
    }

    /** A second finger: the gesture is the wheel, and was never a click. */
    Step pointerDown() {
        mOwed = false;
        return letUp();
    }

    /** Let the button up from outside the touch stream, which is mouse mode being switched off. */
    Step release() {
        mOwed = false;
        return letUp();
    }

    /** Whether the button is down right now, which is what a move has to report against. */
    boolean isPressed() {
        return mPressed;
    }

    private Step letUp() {
        if (!mPressed)
            return Step.NOTHING;
        mPressed = false;
        return Step.RELEASE;
    }
}
