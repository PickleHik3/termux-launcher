package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * How much of a place's height the terminal keeps, and what gives way when the chrome wants more
 * than there is.
 *
 * <p>{@link EdgeStackPolicy#contentInsets} adds up what stands on each edge and stops there: it is
 * never told how tall the container is, so no arrangement can ever be too tall for the screen it is
 * arranged on. Nor is the keyboard in that stack at all — it stands in the accessory stack, below
 * the dock's rows — which is how a landscape screen ends up with a status bar, a dock, an extra-keys
 * row and a keyboard sharing 600px and a terminal reading four lines. This class is the missing
 * half: the height is a budget, the canvas has a floor in it, and the bands are granted what is
 * left after that floor is taken out.
 *
 * <p>The floor is a share of the screen in landscape and a flat dp in portrait, deliberately. A
 * portrait screen has never run out of height — the flat 72dp slice the accessory stack has always
 * been held to is the whole story there, and it is kept to the pixel so nothing in portrait moves.
 * Landscape is the short axis: a floor in dp that is comfortable on a 2400px screen is most of a
 * 600px one, so the floor there is a fraction of what the place actually has, capped at the point
 * where more terminal stops being the thing the screen is short of.
 *
 * <p>The order things give way in is a statement about what the user is holding the phone for:
 * <ol>
 *   <li>the keyboard first, and only down to {@link #KEYBOARD_MIN_SHARE} of what it asked — it is
 *       the largest single claim, it is the one the user can put away, and it is the one whose own
 *       height they already set;</li>
 *   <li>then the bottom stack, down to {@link #BOTTOM_STACK_MIN_SHARE} — its bands shed in their
 *       own way (the dock's pinned row rescales its icons rather than clipping them);</li>
 *   <li>the top stack never. The status bar the wall's pager rides is not something to crush, and
 *       a bar too tall for a landscape screen has its own answer: it rests compact there
 *       ({@link PlaceLayoutStore#isStatusCompact}), per orientation, because the user said so.</li>
 * </ol>
 * What is still missing once both have given all they can is reported rather than taken: a screen
 * this short has no arrangement that fits, and silently clipping a band would be a worse answer
 * than a short canvas.
 *
 * <p>Pure: pixels, dp and a density in, one value out, no views — the relationship
 * {@link EdgeStackPolicy} and {@code DockLayoutPolicy} have with the screen.
 */
public final class CanvasBudgetPolicy {

    private CanvasBudgetPolicy() {}

    /**
     * The slice of a portrait screen the terminal has always been left, in dp. Also the floor under
     * the landscape share, so a screen short enough for the share to fall below it keeps this much.
     */
    public static final float CANVAS_FLOOR_DP = 72f;

    /** What a landscape place keeps for its canvas, as a share of its height. */
    public static final float LANDSCAPE_CANVAS_SHARE = 0.35f;

    /**
     * Where the landscape share stops growing, in dp. Past this the screen is not short of terminal
     * any more and the chrome may have the rest — a tablet held sideways is not the case this
     * exists for.
     */
    public static final float LANDSCAPE_CANVAS_FLOOR_MAX_DP = 200f;

    /** How much of the height it asked for a keyboard keeps once the canvas is under its floor. */
    public static final float KEYBOARD_MIN_SHARE = 0.6f;

    /** How much of the height it asked for the bottom stack keeps, once the keyboard has given. */
    public static final float BOTTOM_STACK_MIN_SHARE = 0.5f;

    /**
     * What the canvas is held to on a place of this height, in pixels. Landscape reads a share of
     * the container, bounded below by the flat portrait slice and above by
     * {@link #LANDSCAPE_CANVAS_FLOOR_MAX_DP}; portrait is that flat slice and nothing else.
     *
     * <p>Never more than the container: a floor taller than the screen would hand back a negative
     * budget for everything else.
     */
    public static int canvasFloorPx(int containerHeightPx, @NonNull PlaceOrientation orientation,
                                    float density) {
        int container = Math.max(0, containerHeightPx);
        float scale = Math.max(0f, density);
        int flat = Math.round(CANVAS_FLOOR_DP * scale);
        int floor = flat;
        if (orientation == PlaceOrientation.LANDSCAPE) {
            int share = Math.round(container * LANDSCAPE_CANVAS_SHARE);
            floor = Math.max(flat, Math.min(share, Math.round(LANDSCAPE_CANVAS_FLOOR_MAX_DP * scale)));
        }
        return Math.min(container, floor);
    }

    /**
     * The whole vertical budget for one place: what each band is granted, what the canvas comes out
     * at, and what the floor is still short of if the screen cannot hold the arrangement at all.
     *
     * @param containerHeightPx the height the place has to divide up
     * @param keyboardHeightPx  the keyboard's asked height, or {@code 0} while it is down or
     *                          floating — a floating keyboard is over the canvas, not beside it, so
     *                          it takes nothing from this budget
     * @param layout            the arrangement, which says what stands on the top and bottom edges
     * @param metrics           how thick each of those bands is
     */
    @NonNull
    public static Budget compute(int containerHeightPx, @NonNull PlaceOrientation orientation,
                                 int keyboardHeightPx, @NonNull PlaceLayout layout,
                                 @NonNull EdgeStackPolicy.Metrics metrics, float density) {
        int container = Math.max(0, containerHeightPx);
        int floorPx = canvasFloorPx(container, orientation, density);
        int top = EdgeStackPolicy.stackThicknessPx(layout, Edge.TOP, metrics);
        int bottom = EdgeStackPolicy.stackThicknessPx(layout, Edge.BOTTOM, metrics);
        int keyboard = Math.max(0, keyboardHeightPx);

        int over = top + bottom + keyboard + floorPx - container;
        if (over > 0) {
            int spare = keyboard - Math.round(keyboard * KEYBOARD_MIN_SHARE);
            int given = Math.min(over, Math.max(0, spare));
            keyboard -= given;
            over -= given;
        }
        if (over > 0) {
            int spare = bottom - Math.round(bottom * BOTTOM_STACK_MIN_SHARE);
            int given = Math.min(over, Math.max(0, spare));
            bottom -= given;
            over -= given;
        }
        int canvas = Math.max(0, container - top - bottom - keyboard);
        return new Budget(top, bottom, keyboard, canvas, floorPx, Math.max(0, floorPx - canvas));
    }

    /** What each band is granted on one place, and what that leaves the canvas. */
    public static final class Budget {
        /** The top edge's stack, granted whole: it is never what gives way. */
        public final int topPx;
        /** The bottom edge's stack. */
        public final int bottomPx;
        /** The keyboard, where it takes room from the canvas rather than floating over it. */
        public final int keyboardPx;
        /** What is left for the terminal. */
        public final int canvasPx;
        /** What the canvas was held to. */
        public final int floorPx;
        /** How far under that floor the canvas still is once everything has given way. */
        public final int shortfallPx;

        Budget(int topPx, int bottomPx, int keyboardPx, int canvasPx, int floorPx, int shortfallPx) {
            this.topPx = Math.max(0, topPx);
            this.bottomPx = Math.max(0, bottomPx);
            this.keyboardPx = Math.max(0, keyboardPx);
            this.canvasPx = Math.max(0, canvasPx);
            this.floorPx = Math.max(0, floorPx);
            this.shortfallPx = Math.max(0, shortfallPx);
        }

        /** Whether the arrangement fits the screen with the canvas still on its floor. */
        public boolean fits() {
            return shortfallPx == 0;
        }

        @Override public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Budget)) return false;
            Budget that = (Budget) other;
            return topPx == that.topPx && bottomPx == that.bottomPx
                && keyboardPx == that.keyboardPx && canvasPx == that.canvasPx
                && floorPx == that.floorPx && shortfallPx == that.shortfallPx;
        }

        @Override public int hashCode() {
            return ((((topPx * 31 + bottomPx) * 31 + keyboardPx) * 31 + canvasPx) * 31 + floorPx)
                * 31 + shortfallPx;
        }

        @NonNull @Override public String toString() {
            return "Budget{top=" + topPx + ",bottom=" + bottomPx + ",keyboard=" + keyboardPx
                + ",canvas=" + canvasPx + ",floor=" + floorPx + ",short=" + shortfallPx + "}";
        }
    }
}
