package com.termux.app.terminal;

/**
 * The one predicate the pane layer's animations share, kept free of Android types so it can be
 * tested. kitty and niri both refuse to animate what they cannot see or have not measured — niri
 * skips a close with no snapshot, kitty snaps its cursor trail during a live resize — and the
 * launcher's ghost-over-hidden-pane and cursor-trail-target-from-a-detached-view faults were both
 * that rule missing.
 * <p>
 * The cursor trail's own law — kitty's per-corner decay — now lives in
 * {@code com.termux.view.KittyCursorTrail}, the engine that plays it back for both an in-pane
 * cursor move and a pane switch; this class no longer duplicates it.
 */
public final class PaneMotionMath {

    private PaneMotionMath() {}

    /**
     * Whether a view may be animated at all: laid out, attached, and on screen.
     *
     * <p>Size alone is not enough, which is the trap this exists for — a detached view keeps its
     * last measured width and height, so a size check passes while {@code getLocationOnScreen}
     * reports {@code 0,0} and every derived rect lands at the screen origin.
     */
    public static boolean canAnimate(boolean attached, boolean shown, int width, int height) {
        return attached && shown && width > 0 && height > 0;
    }
}
