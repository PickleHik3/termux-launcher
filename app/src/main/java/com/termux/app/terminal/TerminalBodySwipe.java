package com.termux.app.terminal;

/**
 * The one rule for a sideways swipe that starts on a terminal's text, wherever the terminal
 * stands: a window in the Workspace pages the wall's row (between windows, the display window
 * among them, and from the first window back to Home's last page), and a Home float pages Home.
 * Either way the swipe moves the place the terminal stands in, exactly as the border slide does,
 * unless the terminal has a use for the drag itself.
 *
 * <p>Two questions, both pure. {@link #yieldsSideways} is asked as the finger lands: whether the
 * terminal under it can give a sideways drag away at all. {@link #isSideways} is asked once the
 * finger has travelled the touch slop: whether it set off sideways enough to be a page swipe
 * rather than a scroll that drifted. Nothing moves before both have said yes; once they have, the
 * terminal is sent a cancel and keeps nothing of the gesture. Taps, the long press (a selection's
 * start), vertical scrolls and pinches are the terminal's whatever these answer.
 */
public final class TerminalBodySwipe {

    private TerminalBodySwipe() {}

    /**
     * How much more sideways than up or down a finger has to set off, at the touch slop, for the
     * swipe to page: {@code |dx| >= SIDEWAYS_RATIO * |dy|}, about 34 degrees off the horizontal at
     * most. A vertical scroll a thumb lets drift stays the terminal's.
     */
    public static final float SIDEWAYS_RATIO = 1.5f;

    /**
     * Whether a press on a terminal gives a sideways swipe to the place it stands in. Not where the
     * press is on the terminal's own chrome ({@code onChrome}: a Home float's titlebar, which moves
     * it, or its resize corner); not while a selection is under way ({@code selecting}), which the
     * drag extends; not while a program tracks the mouse ({@code mouseTracking}) or asked for touch
     * reports ({@code touchReports}), since the drag is the program's; and not in mouse mode
     * ({@code touchMouseMode}), where a finger drives the pointer.
     */
    public static boolean yieldsSideways(boolean onChrome, boolean selecting, boolean mouseTracking,
                                         boolean touchReports, boolean touchMouseMode) {
        return !onChrome && !selecting && !mouseTracking && !touchReports && !touchMouseMode;
    }

    /**
     * Whether a finger that has travelled {@code (dx, dy)} past the touch slop set off sideways
     * enough to page ({@link #SIDEWAYS_RATIO}). A finger that has not moved has set off nowhere.
     */
    public static boolean isSideways(float dx, float dy) {
        float across = Math.abs(dx);
        return across > 0f && across >= SIDEWAYS_RATIO * Math.abs(dy);
    }
}
