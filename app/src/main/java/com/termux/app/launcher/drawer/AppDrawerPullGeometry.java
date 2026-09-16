package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Pull;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where the app drawer comes from, and which way it is pulled, for the edge the pinned apps stand
 * on. Pure: the edge in, a direction and a seed out.
 *
 * <p>The drawer has one gesture and one animation, and both are the pinned apps row's: the row is
 * the surface the pull starts on and the rectangle the plane grows out of. Standing that row on
 * another edge therefore turns both, and this is the one place that turn is decided — the row's
 * touch half ({@code SuggestionBarView}, {@link DockRailScrollView}) and the plane's geometry half
 * ({@code AppDrawerController}) read the same two answers.
 *
 * <p>Three of the four edges pull <em>towards the middle of the screen</em>: a left rail opens with
 * a swipe right, a right rail with a swipe left, a top row with a pull down. The bottom dock is the
 * odd one and deliberately so — its pull has always run down, off the dock and towards the edge it
 * is docked to, and that is the gesture every install already has in its thumbs.
 */
public final class AppDrawerPullGeometry {

    /** The view the plane's seed rectangle is taken from for a given edge. */
    public enum Seed {
        /** The dock's own glass: the pinned apps are the bottom row standing on it. */
        DOCK,
        /** The shared plank a row lying down off the dock stands on. */
        PLANK,
        /** The rail's scrolling host on a side. */
        RAIL
    }

    private AppDrawerPullGeometry() {}

    /** Which way a drag has to travel, from the edge the pinned apps row stands on. */
    @NonNull
    public static Pull pullFor(@NonNull Edge appsEdge) {
        switch (appsEdge) {
            case LEFT: return Pull.RIGHT;
            case RIGHT: return Pull.LEFT;
            case TOP:
            case BOTTOM:
            default: return Pull.DOWN;
        }
    }

    /** The rectangle the plane grows out of, and shrinks back into, for that same edge. */
    @NonNull
    public static Seed seedFor(@NonNull Edge appsEdge) {
        switch (appsEdge) {
            case LEFT:
            case RIGHT: return Seed.RAIL;
            case TOP: return Seed.PLANK;
            case BOTTOM:
            default: return Seed.DOCK;
        }
    }

    /** True for the two rail pulls, whose axis is the screen's width rather than its height. */
    public static boolean isHorizontal(@NonNull Pull pull) {
        return AppDrawerGestureArbiter.isHorizontal(pull);
    }

    /**
     * The span the open travel is a fraction of: the screen's width for a rail, its height for a
     * row, so a pull covers the same share of the distance it is actually crossing.
     */
    public static float travelSpanPx(@NonNull Pull pull, float hostWidthPx, float hostHeightPx) {
        return isHorizontal(pull) ? hostWidthPx : hostHeightPx;
    }

    /**
     * The dock's little hop as the drag starts. A rail has none: the hop is a vertical lift written
     * to the dock's glass and its rows, and a plane seeded from a column would be hopping a
     * rectangle that is not the one the finger is on.
     */
    public static float liftPxFor(@NonNull Pull pull, float dockLiftPx) {
        return isHorizontal(pull) ? 0f : dockLiftPx;
    }
}
