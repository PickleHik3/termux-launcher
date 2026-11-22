package com.termux.app.launcher.widget;

import com.termux.app.wall.PagePolicy;

/**
 * A second finger swiping sideways while the first one carries something on Home: which way the
 * page turns, if it does. The first finger keeps the carried icon or widget; this one only turns
 * pages under it, by the pager's own rules, so it reads as the swipe the thumb already knows.
 *
 * <p>The swipe sets off by the pager's slop ({@link HomeSwipePolicy#axis}); one that sets off
 * up or down is nobody's until the finger lifts. Sideways, it turns the page the moment it has
 * travelled the pager's commit distance from where it started, and starts counting again from
 * there, so one long swipe turns one page and a further full distance turns the next. A swipe
 * that lifts before the distance is read as a released page drag ({@link PagePolicy#turn}), so a
 * flick turns the page too; a swipe that already turned one does not turn another on its lift.
 * Left brings the next page in, right the previous one, as the page's own swipe does.
 *
 * <p>Pure: the pane reads the finger and hands it the numbers.
 */
public final class DragPageSwipe {

    /** No page turns. */
    public static final int NONE = 0;
    /** The next page comes in: the finger went left. */
    public static final int NEXT = 1;
    /** The previous page comes in: the finger went right. */
    public static final int PREVIOUS = -1;

    private int pointerId = -1;
    private float startX, startY;
    private boolean sideways;
    private boolean vertical;
    private boolean turned;

    /** The second finger landed at ({@code x}, {@code y}); a swipe of it is watched from here. */
    public void down(int id, float x, float y) {
        pointerId = id;
        startX = x;
        startY = y;
        sideways = false;
        vertical = false;
        turned = false;
    }

    /** Whether a finger is being watched. */
    public boolean tracking() { return pointerId >= 0; }

    /** The id of the finger being watched, or -1. */
    public int pointerId() { return pointerId; }

    /**
     * The watched finger moved to ({@code x}, {@code y}).
     *
     * @param slopPx   the pager's touch slop
     * @param commitPx the pager's commit distance: {@link PagePolicy#COMMIT_FRACTION} of the page
     * @return {@link #NEXT}, {@link #PREVIOUS} or {@link #NONE}
     */
    public int move(float x, float y, float slopPx, float commitPx) {
        if (pointerId < 0 || vertical) return NONE;
        float dx = x - startX;
        if (!sideways) {
            HomeSwipePolicy.Axis axis = HomeSwipePolicy.axis(dx, y - startY, slopPx);
            if (axis == HomeSwipePolicy.Axis.VERTICAL) { vertical = true; return NONE; }
            if (axis != HomeSwipePolicy.Axis.HORIZONTAL) return NONE;
            sideways = true;
        }
        if (commitPx <= 0f || Math.abs(dx) < commitPx) return NONE;
        turned = true;
        // A fresh crossing from here: the same distance again turns the next page.
        startX = x;
        startY = y;
        return dx < 0 ? NEXT : PREVIOUS;
    }

    /**
     * The watched finger lifted at {@code x}, moving at {@code velocityXPxPerSec}; the watch ends.
     *
     * @param pagePx  the page's width
     * @param density px per dp, which the pager's fling speeds are read in
     * @return {@link #NEXT}, {@link #PREVIOUS} or {@link #NONE}
     */
    public int lift(float x, float velocityXPxPerSec, float pagePx, float density) {
        int result = NONE;
        if (pointerId >= 0 && sideways && !vertical && !turned) {
            // PagePolicy answers on in the way the travel is positive; a rightward travel is the
            // previous page.
            result = -PagePolicy.turn(x - startX, velocityXPxPerSec, Math.max(1f, pagePx),
                density);
        }
        reset();
        return result;
    }

    /** Stops watching without a turn. */
    public void reset() {
        pointerId = -1;
        sideways = false;
        vertical = false;
        turned = false;
    }
}
