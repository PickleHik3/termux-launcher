package com.termux.app.wall;

import androidx.annotation.NonNull;

import com.termux.app.terminal.TerminalBodySwipe;

/**
 * The seam where a window meets the dock, and who a finger that lands near it belongs to. The
 * border drag between windows starts there more than anywhere: a sideways swipe on the window's
 * bottom border. The border's own air is only the window's Margin tall (12 dp at the default,
 * 6 dp at the least), a third of a fingertip, so a thumb aimed at the line lands in the window's
 * last row or on the dock's top about as often as in the air, and found the terminal's scroll or
 * the dock's apps paging there instead.
 *
 * <p>So the seam is a band a fingertip tall around the air: the air itself ({@link Start#AIR}),
 * {@link #PANE_REACH_DP} above it into the window's last row ({@link Start#PANE}) and {@link
 * #DOCK_REACH_DP} below it into the dock's top ({@link Start#DOCK}), the last row's reach grown
 * where the air is so thin the band would come out short of {@link #BAND_DP}. The dock's part is
 * kept to its top air on purpose: the dock's top row may be the apps row, the extra keys or the
 * A-Z strip, each with a sideways drag of its own, and a swipe from the middle of an icon, a key
 * or a letter is theirs. The window's last row gives up less: a terminal that gives the swipe
 * away has no use for a sideways drag. All of it lies between the window's corner squares, which
 * are the corner tabs'.
 *
 * <p>What a finger there does is decided once it has travelled the touch slop, never before, so a
 * tap and a long press are never this band's ({@link #decide}):
 * <ul>
 *   <li>In the air, as the border drag has always been: more up or down than across is the
 *       keyboard swipe, anything flatter pages, and a still finger's hold pages too.</li>
 *   <li>In the window's last row and on the dock's top, only a start clearly sideways pages: at
 *       least {@link TerminalBodySwipe#SIDEWAYS_RATIO} times as far across as up or down, about
 *       34 degrees off the horizontal at most, the same lean the body swipe asks. A steeper start
 *       is the content's (the terminal's scroll, the dock's own swipe), and so is a still finger:
 *       the terminal's long press, an app's menu. In the last row it pages only where the
 *       terminal there gives the swipe away (no selection, no program tracking the mouse), and
 *       only the box by the grabber pill turns a vertical start into the keyboard swipe.</li>
 *   <li>Anywhere else — the terminal's text past the last row, the dock's apps row past its top —
 *       the band is not there, and the content keeps every swipe that starts there.</li>
 * </ul>
 * Pure, so the band and the decision are tested without a view.
 */
public final class SeamSwipe {

    private SeamSwipe() {}

    /**
     * How far the seam band reaches above the air into the window's last row, in dp, at least:
     * a row and a half of the terminal at its usual size, so a thumb aimed at the line and landing
     * a row high still finds it. Grown where the air is thin ({@link #paneReachPx}).
     */
    public static final float PANE_REACH_DP = 24f;

    /**
     * How far the seam band reaches below the air into the dock's top, in dp: the dock's own air
     * above its top row (4 to 6 dp) and a little of the row's padding, never the middle of an
     * icon, a key or a letter.
     */
    public static final float DOCK_REACH_DP = 8f;

    /**
     * The least the whole band is tall, in dp: a fingertip's contact (7 to 9 mm, 44 to 48 dp).
     * With the default Margin's 12 dp of air it is exactly {@link #PANE_REACH_DP}, the air and
     * {@link #DOCK_REACH_DP}; at a thinner Margin the last row's reach makes up the shortfall.
     */
    public static final float BAND_DP = 44f;

    /** Where on the seam a finger landed. */
    public enum Start {
        /** Off the band: the content's, whichever way it goes. */
        NONE,
        /** The window's last row, inside the page. */
        PANE,
        /** The air between the window and the dock: the bottom border itself. */
        AIR,
        /** The dock's top, below the air. */
        DOCK
    }

    /** What a finger on the seam turned out to be. */
    public enum Decision {
        /** Still inside the touch slop: nothing is decided, and nothing moves. */
        UNDECIDED,
        /** The border drag: the wall pages under the finger from here to the lift. */
        BORDER_DRAG,
        /** The keyboard swipe off the bottom border. */
        KEYBOARD,
        /** The content's: the terminal's scroll, selection or long press, or the dock's own. */
        CONTENT
    }

    /**
     * How far the band reaches above {@code airPx} of air into the window's last row, in px:
     * {@link #PANE_REACH_DP}, or what the air and the dock's reach lack of {@link #BAND_DP} where
     * that is more.
     */
    public static float paneReachPx(float airPx, float density) {
        float d = Math.max(0f, density);
        return Math.max(PANE_REACH_DP * d,
            (BAND_DP - DOCK_REACH_DP) * d - Math.max(0f, airPx));
    }

    /** How far the band reaches below the air into the dock's top, in px ({@link #DOCK_REACH_DP}). */
    public static float dockReachPx(float density) {
        return DOCK_REACH_DP * Math.max(0f, density);
    }

    /**
     * Where {@code (x, y)} lies on the seam below a window whose bottom edge is at {@code
     * paneBottom}, with the air down to {@code airBottom} (the dock's top, or the wall's edge
     * where no dock stands below), the band reaching {@code paneReachPx} above it ({@link
     * #paneReachPx}) and {@code dockReachPx} below it ({@link #dockReachPx}). The band runs between the window's corner squares, {@code left +
     * corner} to {@code right - corner}; a window too narrow for that, or a dock overlapping the
     * window, has no band. {@code dockBelow} false leaves out the dock's part: nothing of the
     * dock's stands below the air.
     */
    @NonNull
    public static Start startAt(float x, float y, float left, float right, float corner,
                                float paneBottom, float airBottom, float paneReachPx,
                                float dockReachPx, boolean dockBelow) {
        float l = left + Math.max(0f, corner);
        float r = right - Math.max(0f, corner);
        if (!(r > l) || !(airBottom >= paneBottom)) return Start.NONE;
        if (x < l || x >= r) return Start.NONE;
        if (y < paneBottom - Math.max(0f, paneReachPx)) return Start.NONE;
        if (y < paneBottom) return Start.PANE;
        if (y < airBottom) return Start.AIR;
        if (dockBelow && y < airBottom + Math.max(0f, dockReachPx)) return Start.DOCK;
        return Start.NONE;
    }

    /**
     * What a finger that landed at {@code start} and has travelled {@code (dx, dy)} since is.
     * Inside the touch slop ({@code slopPx}) nothing is decided. Past it, a start more up or down
     * than across is the keyboard swipe where {@code keyboard} allows it there (the air, or the
     * box by the grabber pill) and the content's otherwise; a flatter one pages from the air, and
     * from the window's last row or the dock's top only when it is clearly sideways ({@link
     * TerminalBodySwipe#isSideways}). Either way only where {@code pages}: the wall has another
     * place to go, and in the last row the terminal there gives the swipe away.
     */
    @NonNull
    public static Decision decide(@NonNull Start start, float dx, float dy, float slopPx,
                                  boolean pages, boolean keyboard) {
        float slop = Math.max(0f, slopPx);
        if (dx * dx + dy * dy <= slop * slop) return Decision.UNDECIDED;
        if (start == Start.NONE) return Decision.CONTENT;
        if (Math.abs(dy) > Math.abs(dx)) return keyboard ? Decision.KEYBOARD : Decision.CONTENT;
        if (!pages) return Decision.CONTENT;
        if (start == Start.AIR) return Decision.BORDER_DRAG;
        return isSideways(dx, dy) ? Decision.BORDER_DRAG : Decision.CONTENT;
    }

    /**
     * What a finger that landed at {@code start} and held still through the hold is: the border
     * drag in the air, where nothing else wants a hold, and the content's long press in the
     * window's last row and on the dock's top.
     */
    @NonNull
    public static Decision hold(@NonNull Start start, boolean pages) {
        return start == Start.AIR && pages ? Decision.BORDER_DRAG : Decision.CONTENT;
    }

    /**
     * Whether a finger that travelled {@code (dx, dy)} set off clearly sideways: the body swipe's
     * own lean ({@link TerminalBodySwipe#isSideways}), so one swipe feels the same on the text and
     * on the seam.
     */
    public static boolean isSideways(float dx, float dy) {
        return TerminalBodySwipe.isSideways(dx, dy);
    }
}
