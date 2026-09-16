package com.termux.app.chrome;

/**
 * Who owns a finger that lands on a pane: the corner square it landed in, or the content under it.
 * Two things on the same pane want a press that rests — the corner, which drops the pane's tab at
 * {@link com.termux.view.HoldTiming#holdTimeoutMs()}, and the content, which has a long press of
 * its own (the widget grid's add/edit menu, a program's own hold) — and a finger can only mean one
 * of them. This decides which, once, when the finger lands.
 *
 * <p>The answer is geometry, never a race. A press that lands in a corner square is the corner's
 * to claim from that moment: the content still sees every event, so a tap, a scroll and a drag
 * reach it as if the square were not there, but its long press is held back until the wait ends.
 * Leaving the square, a second finger or a lift ends the wait without a claim and hands the long
 * press straight back; the hold firing ends it the other way, and the content is owed a cancel.
 *
 * <p>Two timers on one finger was the bug this replaces: the corner's fired at three quarters of
 * the system long press and the grid's at the whole of it, so a hold in a corner of the Widgets
 * page opened the corner tab <em>and</em> the grid's menu, one on top of the other.
 *
 * <p>Pure, like {@link CornerHold} which it drives and {@link CornerZones} which it asks: it is
 * fed a touch stream and read back, and nothing here knows what a view is.
 */
public final class CornerHoldArbiter {

    /** Who a press belongs to, from the moment it lands until it ends. */
    public enum Owner {
        /** No finger down. */
        NONE,
        /** The content's, whole: no corner is waiting on it, so its own long press may run. */
        CONTENT,
        /**
         * A corner armed on it and is waiting for the hold. Every event still reaches the content
         * — this is a hold-through, not an interception — but the long press is the corner's.
         */
        PENDING,
        /** The hold fired: the corner owns the gesture, and the content has been cancelled. */
        CORNER
    }

    private final CornerHold mHold = new CornerHold();
    private boolean mDown;
    private int mCorner = CornerZones.NONE;

    /**
     * Which corner of a frame this size may claim a press at this point, or
     * {@link CornerZones#NONE} when the content keeps it whole.
     *
     * @param contentOwnsPoint whether the content has something of its own here that a corner may
     *     never take — a widget's remove chip and resize handles sit inside the page's squares.
     */
    public static int cornerFor(float x, float y, float width, float height, float density,
                                boolean contentOwnsPoint) {
        if (contentOwnsPoint) return CornerZones.NONE;
        return CornerZones.cornerAt(x, y, width, height, CornerZones.paneSizePx(density));
    }

    /**
     * A finger landed on a frame this size. The corner it may belong to is decided here and never
     * revisited: where a press lands is what it meant.
     *
     * @param holdSlopPx how far it may travel and still be holding still.
     * @return the corner that armed, or {@link CornerZones#NONE} when this press is the content's.
     */
    public int down(float x, float y, float width, float height, float density,
                    boolean contentOwnsPoint, float holdSlopPx) {
        mHold.reset();
        mDown = true;
        mCorner = cornerFor(x, y, width, height, density, contentOwnsPoint);
        if (mCorner == CornerZones.NONE) return CornerZones.NONE;
        mHold.down(x, y, holdSlopPx, holdSlopPx, false);
        return mCorner;
    }

    /**
     * The hold time elapsed on a finger that never travelled.
     *
     * @return true when the corner just claimed the gesture, and the content is owed a cancel.
     */
    public boolean holdElapsed() {
        return mHold.holdElapsed();
    }

    /** The finger moved: past the slop before the hold fires, the gesture goes back for good. */
    public CornerHold.Move move(float x, float y) {
        return mHold.move(x, y);
    }

    /** A second finger landed. Two fingers are a scroll or a pinch, never a hold. */
    public boolean secondFinger() {
        return mHold.secondFinger();
    }

    /** The finger lifted, ending the gesture either way. */
    public CornerHold.Lift lift() {
        mDown = false;
        return mHold.lift();
    }

    /** Forget the press entirely — the window went away, or an ancestor took the stream. */
    public void reset() {
        mDown = false;
        mCorner = CornerZones.NONE;
        mHold.reset();
    }

    /** Who owns the press that is down now. */
    public Owner owner() {
        if (!mDown) return Owner.NONE;
        if (mHold.isClaimed()) return Owner.CORNER;
        if (mHold.phase() == CornerHold.Phase.PENDING) return Owner.PENDING;
        return Owner.CONTENT;
    }

    /**
     * Whether the content may run a long press of its own on the finger that is down. False for
     * as long as a corner is waiting on it, and true again the moment the corner gives up.
     */
    public boolean contentMayLongPress() {
        Owner owner = owner();
        return owner == Owner.NONE || owner == Owner.CONTENT;
    }

    /** Whether the content is still being handed every event. */
    public boolean contentKeepsEvents() {
        return !mHold.isClaimed();
    }

    /** Whether the corner owns the gesture and the content has been told to forget it. */
    public boolean isClaimed() {
        return mHold.isClaimed();
    }

    /** Whether a corner armed on the finger that is down, claimed or not. */
    public boolean isTracking() {
        return mHold.isTracking();
    }

    /** The corner that armed on this press, or {@link CornerZones#NONE}. */
    public int corner() {
        return mCorner;
    }
}
