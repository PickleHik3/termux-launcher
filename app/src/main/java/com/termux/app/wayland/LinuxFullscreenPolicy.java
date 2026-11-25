package com.termux.app.wayland;

/**
 * When a Linux window is full screen (spec "Compositor window policy", section 4), as pure state
 * the controller asks: a window that asks while it is not in front waits until it comes in front;
 * the window full screen leaves when another page comes in front, on Back, when it asks to leave,
 * or when it goes. Also the bottom-edge swipe that shows the extra-keys row for a moment, and when
 * that row hides again.
 */
final class LinuxFullscreenPolicy {

    /** The extra-keys row hides this long after it came out or after the last key press. */
    static final long REVEAL_MS = 3000L;
    /** A swipe that shows the row starts this close to the bottom edge... */
    static final float EDGE_DP = 32f;
    /** ...and travels this far up. */
    static final float SWIPE_DP = 40f;

    /** The window full screen now; 0 for none. */
    private long fullscreenId;
    /** A window that asked while it was not in front; 0 for none. */
    private long askedId;

    long fullscreenId() {
        return fullscreenId;
    }

    boolean isFullscreen() {
        return fullscreenId != 0;
    }

    /**
     * Window {@code id} asked to be full screen ({@code on}) or to leave it; {@code frontId} is the
     * row window in front, 0 for none. Returns what the controller does now.
     */
    int request(long id, boolean on, long frontId) {
        if (id == 0) return NOTHING;
        if (!on) {
            if (askedId == id) askedId = 0;
            if (fullscreenId != id) return NOTHING;
            fullscreenId = 0;
            return LEAVE;
        }
        if (fullscreenId == id) return NOTHING;
        if (id != frontId) {
            askedId = id;
            return NOTHING;
        }
        askedId = 0;
        if (fullscreenId != 0) {
            // Only one window in front: the old one is already gone from it.
            fullscreenId = id;
            return SWITCH;
        }
        fullscreenId = id;
        return ENTER;
    }

    /** The row window in front changed ({@code frontId}, 0 for none: another page is in front). */
    int frontChanged(long frontId) {
        if (fullscreenId != 0 && fullscreenId != frontId) {
            fullscreenId = 0;
            if (askedId != frontId) askedId = 0;
            if (askedId != 0) {
                fullscreenId = askedId;
                askedId = 0;
                return SWITCH;
            }
            return LEAVE;
        }
        if (fullscreenId == 0 && askedId != 0 && askedId == frontId) {
            fullscreenId = askedId;
            askedId = 0;
            return ENTER;
        }
        // A window that asked does not take the screen later from a page it was never in front on.
        if (askedId != 0 && frontId != 0 && askedId != frontId) askedId = 0;
        return NOTHING;
    }

    /** Back: true when it left full screen (Back is used up). */
    boolean back() {
        if (fullscreenId == 0) return false;
        fullscreenId = 0;
        return true;
    }

    /** Window {@code id} went (destroyed, minimised, made a pane): true when it was full screen. */
    boolean gone(long id) {
        if (askedId == id) askedId = 0;
        if (fullscreenId != id || id == 0) return false;
        fullscreenId = 0;
        return true;
    }

    /** Nothing changes. */
    static final int NOTHING = 0;
    /** Hide the chrome and give the window the screen. */
    static final int ENTER = 1;
    /** Give the screen back and restore the chrome. */
    static final int LEAVE = 2;
    /** The old window leaves and {@link #fullscreenId()} takes the screen. */
    static final int SWITCH = 3;

    /** When the revealed row hides: {@link #REVEAL_MS} after the reveal or the last press. */
    static long revealHidesAt(long lastActivityMs) {
        return lastActivityMs + REVEAL_MS;
    }

    /** Whether a finger down at {@code downY} on a view {@code heightPx} high starts at the bottom edge. */
    static boolean startsAtBottomEdge(float downY, float heightPx, float density) {
        return heightPx > 0f && downY >= heightPx - EDGE_DP * density;
    }

    /** Whether a finger that went down at {@code downY} and is now at {@code y} swiped up far enough. */
    static boolean swipedUp(float downY, float y, float density) {
        return downY - y >= SWIPE_DP * density;
    }
}
