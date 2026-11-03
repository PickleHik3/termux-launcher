package com.termux.app.wall;

/**
 * Live neighbour windows (project-docs/active/live-neighbour-windows-2026-11-01.md): which
 * windows of the row stand ready beside the one in front, when they are shown, and where.
 *
 * <p>Only the two adjacent windows are ready. They are built at the Workspace page's size but
 * kept {@code GONE} at rest, shown from the moment a drag takes the wall until it settles, and
 * stand one page width either side of the Workspace page, so they move exactly as it does.
 * {@link PaneWallLayout} places them and the pane controller fills them; both read this.
 */
public final class LiveNeighbourPolicy {

    /** No window on that side. */
    public static final int NONE = -1;

    private LiveNeighbourPolicy() { }

    /**
     * The row index of the window on {@code side} (-1 before, +1 after) of {@code current} in a
     * row of {@code count} windows, or {@link #NONE}: off, no such side, or the row ends there.
     */
    public static int neighbourIndex(boolean enabled, int current, int count, int side) {
        if (!enabled || (side != -1 && side != 1)) return NONE;
        if (count <= 1 || current < 0 || current >= count) return NONE;
        int index = current + side;
        return index >= 0 && index < count ? index : NONE;
    }

    /**
     * Whether the neighbours stand beside the Workspace page now: only while it is the page in
     * front and the wall is moving it — a drag, the slide that settles it (forward or back), the
     * nudge that carries an arriving window in, or the step between a release and that nudge.
     * At rest they draw nothing.
     */
    public static boolean shown(boolean workspaceInFront, boolean dragging, boolean sliding,
                                boolean nudgingWorkspace, boolean stepping) {
        return workspaceInFront && (dragging || sliding || nudgingWorkspace || stepping);
    }

    /** Where the neighbour on {@code side} stands: one page width beyond the Workspace page. */
    public static float slidePx(int side, float workspaceSlidePx, int widthPx) {
        return side * (float) widthPx + workspaceSlidePx;
    }

    /** Whether a neighbour standing at {@code slidePx} shows any of itself on screen. */
    public static boolean onScreen(float slidePx, int widthPx) {
        return widthPx <= 0 || Math.abs(slidePx) < widthPx;
    }

    /** The slot (0 before, 1 after) a side's neighbour is kept in. */
    public static int slotOf(int side) {
        return side < 0 ? 0 : 1;
    }

    /**
     * The slot a released step {@code steps} away leaves empty until its settle ends: the one the
     * arriving window came from. The window beyond it would stand there a width past the
     * arriving one, which moves toward rest without passing it ({@link SettleSpring}), so that
     * slot stays off screen the whole settle. The other slot carries the window leaving.
     */
    public static int heldSlotAfterStep(int steps) {
        return slotOf(steps);
    }

    /** The side a slot's neighbour stands on. */
    public static int sideOf(int slot) {
        return slot == 0 ? -1 : 1;
    }
}
