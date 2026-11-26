package com.termux.app.wall;

/**
 * Overview's side of the swipe past the first page (#356), as the wall drives it: the wall owns
 * the finger and the settle, and tells Overview where to stand by the one number both read, the
 * reveal ({@link OverviewEdgePolicy}). Implemented by Overview's controller.
 */
public interface OverviewEdge {

    /** Whether a swipe past the first page may bring Overview in now. */
    boolean available();

    /**
     * A pull starts: Overview goes up one width left of the screen, out of sight, with nothing
     * hidden under it yet. False refuses, and the wall resists at its edge as before.
     */
    boolean beginPull(int widthPx);

    /** Overview stands {@code revealPx} in from the left edge (a pull or a return). Per frame. */
    void pose(float revealPx, int widthPx);

    /**
     * The swipe landed: Overview is the screen ({@code open}), or it is gone and the first page
     * is. Called before the wall puts its page back at its rest.
     */
    void end(boolean open);
}
