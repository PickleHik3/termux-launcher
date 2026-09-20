package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import java.util.List;

/**
 * Pure geometry for the status bar's place icons: where each place's icon sits, how present it
 * is, and how far the bar's glass has drifted towards that place's tint.
 *
 * <p>The bar is the pager. Every place is one bar-width from its neighbours, and a place's
 * distance from the one on screen, {@code t}, is measured in bar widths: 0 is the place on
 * screen, {@code -1} the one waiting past the left edge, {@code +1} the one past the right.
 * Three icons show at any time. The place on screen wears its icon at the home position beside
 * the clock; its two neighbours peek in from the edges, half past them. A drag moves all three
 * together along one line: the arriving icon travels from its edge to home while the one that was
 * home leaves through the other edge, and the third slips off and fades, to be found waiting at
 * the far edge once the wall lands.
 */
public final class StatusBarLensPolicy {

    /** Beyond one width away an icon is leaving; by this many widths it has gone. */
    private static final float FADE_OUT_WIDTHS = 0.5f;

    private StatusBarLensPolicy() {}

    /** Bar widths between {@code page} and the place on screen, with the wall's offset folded in. */
    public static float distance(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                                 @NonNull PaneWallPage page, float offsetPx, int widthPx) {
        int rel = page == current ? 0 : PaneWallPolicy.relativePosition(pages, current, page);
        float offset = widthPx <= 0 ? 0f : offsetPx / (float) widthPx;
        return rel + offset;
    }

    /** How far from home an icon is, 0 at home and 1 at either edge. */
    public static float presence(float t) {
        return Math.min(1f, Math.abs(t));
    }

    /** Icons within a width of home are whole; further out they dissolve as they leave. */
    public static float alpha(float t) {
        float beyond = Math.abs(t) - 1f;
        if (beyond <= 0f) return 1f;
        return Math.max(0f, 1f - beyond / FADE_OUT_WIDTHS);
    }

    /** The icon at home is full size; at the edges it is a little smaller. */
    public static float scale(float t) {
        return 1f - 0.14f * presence(t);
    }

    /**
     * The icon's left edge. {@code home} is where the place on screen rests, beside the clock;
     * {@code leftPeek} and {@code rightPeek} are the resting places of the two neighbours, half
     * past their edges. An icon further than a width away keeps travelling outward by its own
     * size per width, which is what carries it off while it fades.
     */
    public static float iconX(float t, float home, float leftPeek, float rightPeek, float size) {
        if (t >= -1f && t <= 0f) return leftPeek + (t + 1f) * (home - leftPeek);
        if (t > 0f && t <= 1f) return home + t * (rightPeek - home);
        if (t < -1f) return leftPeek + (t + 1f) * size * 1.5f;
        return rightPeek + (t - 1f) * size * 1.5f;
    }

    /** How much of a place's tint the glass wears: full on screen, gone one width away. */
    public static float tintWeight(float t) {
        return Math.max(0f, 1f - presence(t));
    }

    // ---------------------------------------------------------------- the lens opens inward

    /**
     * Which way the bar's expanded surfaces grow: <em>towards the middle of the screen</em>, away
     * from the edge the bar stands on. The same rule the A-Z index's match band and the app
     * drawer's pull already follow, so every chrome surface opens the way the screen has room.
     */
    public enum Growth { UP, DOWN, LEFT, RIGHT }

    /** One placed rectangle's top-left, in whichever space the bar and the canvas were given in. */
    public static final class Placement {
        public final int x;
        public final int y;

        Placement(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Placement
                && ((Placement) other).x == x && ((Placement) other).y == y;
        }

        @Override public int hashCode() { return x * 31 + y; }

        @NonNull @Override public String toString() { return "Placement{" + x + "," + y + "}"; }
    }

    @NonNull
    public static Growth growthFor(@NonNull Edge barEdge) {
        switch (barEdge) {
            case BOTTOM: return Growth.UP;
            case LEFT: return Growth.RIGHT;
            case RIGHT: return Growth.LEFT;
            case TOP:
            default: return Growth.DOWN;
        }
    }

    /** Whether the growth runs up or down the screen rather than across it. */
    public static boolean isVertical(@NonNull Growth growth) {
        return growth == Growth.UP || growth == Growth.DOWN;
    }

    /**
     * Where a detail card of this size lands: clear of the bar by {@code gapPx} on the side the
     * lens grows towards, centred on the canvas along the other axis, and clamped inside the
     * canvas so a card taller than the room beside the bar is moved rather than lost off an edge.
     *
     * <p>Pure, and in whichever space the two rectangles were given in — the caller uses screen
     * coordinates for both, because the card is a window of its own.
     */
    @NonNull
    public static Placement card(@NonNull Growth growth,
                                 int barLeft, int barTop, int barRight, int barBottom,
                                 int cardWidthPx, int cardHeightPx, int gapPx,
                                 int canvasLeft, int canvasTop, int canvasRight, int canvasBottom) {
        int x;
        int y;
        switch (growth) {
            case UP:
                x = canvasLeft + (canvasRight - canvasLeft - cardWidthPx) / 2;
                y = barTop - gapPx - cardHeightPx;
                break;
            case RIGHT:
                x = barRight + gapPx;
                y = canvasTop + (canvasBottom - canvasTop - cardHeightPx) / 2;
                break;
            case LEFT:
                x = barLeft - gapPx - cardWidthPx;
                y = canvasTop + (canvasBottom - canvasTop - cardHeightPx) / 2;
                break;
            case DOWN:
            default:
                x = canvasLeft + (canvasRight - canvasLeft - cardWidthPx) / 2;
                y = barBottom + gapPx;
                break;
        }
        return new Placement(clamp(x, canvasLeft, canvasRight - cardWidthPx),
            clamp(y, canvasTop, canvasBottom - cardHeightPx));
    }

    /**
     * The widest a card may be and still stand clear of the bar it grew out of: off a column, the
     * run between the bar and the far side of the canvas, less the margin the card keeps there. Off
     * a row the card grows along the canvas's width and the answer is the whole of it, which is
     * what every card has always been given.
     */
    public static int widthCapPx(@NonNull Growth growth, int barLeft, int barRight, int gapPx,
                                 int canvasLeft, int canvasRight, int marginPx) {
        int whole = Math.max(0, canvasRight - canvasLeft - 2 * marginPx);
        if (isVertical(growth)) return whole;
        int run = growth == Growth.RIGHT
            ? canvasRight - barRight - gapPx - marginPx
            : barLeft - canvasLeft - gapPx - marginPx;
        return Math.max(0, Math.min(whole, run));
    }

    /**
     * How far the card starts from its resting place, so it slides in <em>out of the bar</em>
     * whichever edge that bar stands on: down off a top bar, up off a bottom one, and sideways
     * off a column.
     */
    public static float enterOffsetXPx(@NonNull Growth growth, float distancePx) {
        if (growth == Growth.RIGHT) return -distancePx;
        if (growth == Growth.LEFT) return distancePx;
        return 0f;
    }

    public static float enterOffsetYPx(@NonNull Growth growth, float distancePx) {
        if (growth == Growth.DOWN) return -distancePx;
        if (growth == Growth.UP) return distancePx;
        return 0f;
    }

    /**
     * Where the status row sits inside the bar, as an offset from the bar's own top, while the bar
     * is {@code barLengthPx} long.
     *
     * <p>The row <em>keeps the screen edge its bar stands on</em>: a bottom bar's row stays at the
     * foot of the panel and the expanded content grows upward above it, which is the same reading
     * order a top bar has always had — the clock's band over the stats. The offset the resize
     * geometry computes is measured from the panel's foot for both, so there is nothing to mirror;
     * mirroring it is what used to lift a bottom bar's row 70dp off the screen's edge the moment
     * the bar opened.
     *
     * <p>A bar down a side never rests expanded ({@link StatusBarGesturePolicy#expansionAllowed}),
     * and its row is stacked under the column clock along the bar's length rather than across it,
     * so the answer there is the resting offset it was given.
     */
    public static int rowOffsetPx(@NonNull Edge barEdge, int barLengthPx, int rowLengthPx,
                                  int restingOffsetPx) {
        int span = Math.max(0, barLengthPx);
        int row = Math.max(0, rowLengthPx);
        int resting = Math.max(0, restingOffsetPx);
        // A column's row runs out the rest of the bar's length rather than sitting inside it, so
        // the length it was given is not a bound on where it starts.
        if (StatusBarGesturePolicy.isVertical(barEdge)) return resting;
        return Math.min(resting, Math.max(0, span - row));
    }

    /**
     * Whether the modular widget slot — the clock, the media card, a pinned notification — takes
     * the panel's near end, the one with the lower coordinate. It always does on a row: the slot
     * is what grows, so it stands between the row and the middle of the screen on a bottom bar and
     * between the row and the system status bar on a top one.
     */
    public static boolean slotLeadsRow(@NonNull Edge barEdge) {
        return !StatusBarGesturePolicy.isVertical(barEdge);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
