package com.termux.app.terminal;

/**
 * Which window chip, if any, is showing its close control right now.
 *
 * <p>The row's chips are small and are dragged as often as they are tapped, so the × is not a
 * permanent part of a chip: tapping the chip that is already selected asks for it, and it goes
 * away again at the first sign the user meant something else — a finger landing anywhere else on
 * the row, a sideways scroll, a downward pull, a selection or window list that moved underneath
 * it, or simply {@link #REVEAL_DURATION_MS} passing.
 *
 * <p>Pure: it holds no views and reads no clock of its own, so the whole rule set is exercised in
 * unit tests and the bar only has to draw the answer.
 */
public final class ChipRevealPolicy {

    /** No chip is showing its ×. */
    public static final int NONE = -1;

    /** How long a revealed × stays up on its own. */
    public static final long REVEAL_DURATION_MS = 4_000L;

    private int mRevealed = NONE;
    private long mHideAt;
    /** Whether this finger's stream has already turned into a drag, which can never be a tap. */
    private boolean mStreamDragged;

    /** The chip showing its ×, or {@link #NONE}. */
    public int revealedIndex() {
        return mRevealed;
    }

    public boolean isRevealed() {
        return mRevealed != NONE;
    }

    /** When the current reveal lapses, on the caller's clock; meaningless while nothing shows. */
    public long hideAt() {
        return mHideAt;
    }

    /**
     * A chip was tapped. The first tap on an unselected chip selects it as it always has; a tap on
     * the chip that is already selected asks for its ×, and a second one puts it away.
     *
     * @param nowMs the caller's clock, which {@link #onTimeout(long)} is later read against
     * @return true when the tap was spent on the ×, so the caller must not also select the chip
     */
    public boolean onChipTap(int index, int selectedIndex, long nowMs) {
        if (index < 0 || mStreamDragged) {
            hide();
            return false;
        }
        if (index == mRevealed) {
            // The way out: the chip that offered its × takes it back.
            hide();
            return true;
        }
        if (index != selectedIndex) {
            hide();
            return false;
        }
        mRevealed = index;
        mHideAt = nowMs + REVEAL_DURATION_MS;
        return true;
    }

    /** A finger landed on the row somewhere other than the chip that is showing its ×. */
    public void onTouchElsewhere() {
        hide();
    }

    /** A new finger: whatever the last one did to the strip is over. */
    public void onTouchDown() {
        mStreamDragged = false;
    }

    /**
     * The stream became a drag rather than a tap — sideways along the chips, or downwards into the
     * status bar's pull. Neither reveals anything, and neither may leave a tap behind when it ends
     * on the chip it started on.
     */
    public void onScrollStarted() {
        mStreamDragged = true;
        hide();
    }

    /** The selected chip moved, so the × is standing over the wrong window. */
    public void onSelectionChanged() {
        hide();
    }

    /** The row was given a different set of windows; the revealed index no longer means anything. */
    public void onItemsChanged() {
        hide();
    }

    /**
     * The caller's timer fired.
     *
     * @return true when the reveal had really lapsed and was put away; false for a stale timer that
     *     a fresh tap has already outlived
     */
    public boolean onTimeout(long nowMs) {
        if (mRevealed == NONE || nowMs < mHideAt) return false;
        hide();
        return true;
    }

    public void hide() {
        mRevealed = NONE;
        mHideAt = 0L;
    }
}
