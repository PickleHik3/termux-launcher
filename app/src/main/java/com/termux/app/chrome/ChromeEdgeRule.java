package com.termux.app.chrome;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout;

/**
 * The one rule for what a glass surface draws along each of its edges: a containing stroke, and
 * the Fancier Glass rim and bend ({@link GlassRefraction}), or neither.
 *
 * <p>Docked is two cards peeking in from the top and the bottom of the screen. An edge of a docked
 * surface that touches the screen, or runs on into the strip behind a system bar, is not an edge of
 * the card at all: it takes no stroke and it is a refraction seam, so the glass behind the status
 * bar and under the navigation pill reads as the same sheet as the card beside it. Only the inner
 * edge, the one facing the terminal, keeps the line it has always had. A capsule stands clear of
 * every edge and keeps its whole rim, exactly as before.</p>
 *
 * <p>Edges are the {@link GlassRefraction} seam bits, so an answer here can be handed to a frost or
 * a stack as it is. Pure: no view, no drawable.</p>
 */
public final class ChromeEdgeRule {

    private ChromeEdgeRule() {}

    public static final int NONE = 0;
    public static final int LEFT = GlassRefraction.SEAM_LEFT;
    public static final int TOP = GlassRefraction.SEAM_TOP;
    public static final int RIGHT = GlassRefraction.SEAM_RIGHT;
    public static final int BOTTOM = GlassRefraction.SEAM_BOTTOM;
    public static final int ALL = LEFT | TOP | RIGHT | BOTTOM;

    /** What leads the top edge's stack, and so what the strip behind the system status bar continues. */
    public enum TopLead {
        /** Nothing there, a capsule, or fullscreen: the strip is not shown. */
        NONE,
        /** The status bar's window bar: the strip continues its glass, as it always has. */
        WINDOW_BAR,
        /** A sheet in the dock's material off the dock: the pinned apps plank, the alphabets bar. */
        DOCK_SHEET
    }

    /** The one edge a bar standing on {@code edge} of the screen touches. */
    public static int edgeBit(@NonNull PlaceLayout.Edge edge) {
        switch (edge) {
            case TOP: return TOP;
            case BOTTOM: return BOTTOM;
            case LEFT: return LEFT;
            case RIGHT:
            default: return RIGHT;
        }
    }

    /**
     * The edges of a flush row standing on the top or the bottom of the screen: that edge and the
     * two sides. A column down a side touches only its own side: the stacks above and below it
     * stand between it and the screen's corners.
     */
    public static int flushEdges(@NonNull PlaceLayout.Edge standsOn) {
        return standsOn.isOnSide() ? edgeBit(standsOn) : edgeBit(standsOn) | LEFT | RIGHT;
    }

    /** The edge facing the terminal of a surface standing on {@code standsOn}. */
    public static int innerEdge(@NonNull PlaceLayout.Edge standsOn) {
        switch (standsOn) {
            case TOP: return BOTTOM;
            case BOTTOM: return TOP;
            case LEFT: return RIGHT;
            case RIGHT:
            default: return LEFT;
        }
    }

    /**
     * The refraction seams: edges that take no rim and no bend.
     *
     * @param capsule the floating style
     * @param outer   edges touching the screen or a system-bar strip (docked only)
     * @param joined  edges another glass surface continues past, whatever the style
     */
    public static int seams(boolean capsule, int outer, int joined) {
        return (capsule ? joined : outer | joined) & ALL;
    }

    /**
     * The edges that draw the containing stroke.
     *
     * @param capsuleRim     whether the surface wears a stroke as a capsule (all four sides)
     * @param dockedInnerRim whether its inner edge has always carried one in docked; the outer
     *                       edges never do
     */
    public static int strokeEdges(boolean capsule, boolean capsuleRim, boolean dockedInnerRim,
                                  int outer, int joined) {
        if (capsule) return capsuleRim ? ALL : NONE;
        if (!dockedInnerRim) return NONE;
        return ALL & ~(outer | joined);
    }

    /** Whether any stroke is drawn: the question a surface with only an all-or-nothing rim asks. */
    public static boolean strokes(boolean capsule, boolean capsuleRim, boolean dockedInnerRim,
                                  int outer, int joined) {
        return strokeEdges(capsule, capsuleRim, dockedInnerRim, outer, joined) != NONE;
    }

    /**
     * Whether a surface keeps its corners only on its inner edge: docked, with an outer edge. Its
     * outer corners run into the screen or the strip and are square there.
     */
    public static boolean innerCornersOnly(boolean capsule, int outer) {
        return !capsule && (outer & ALL) != NONE;
    }

    /**
     * The strip behind the system status bar: shown in docked only, with a status inset to fill,
     * outside fullscreen, and with something leading the top edge's stack to continue.
     */
    @NonNull
    public static TopLead statusInsetLead(boolean capsule, boolean fullscreen, int statusInsetPx,
                                          @NonNull TopLead lead) {
        if (capsule || fullscreen || statusInsetPx <= 0) return TopLead.NONE;
        return lead;
    }

    /**
     * Where the light model splits between the strip and the surface it continues: the strip
     * renders {@code [0, f]} and the surface {@code [f, 1]}, so one sheen runs over both.
     */
    public static float stripFraction(int stripPx, int surfacePx) {
        int total = Math.max(0, stripPx) + Math.max(0, surfacePx);
        return total <= 0 ? 0f : Math.max(0, stripPx) / (float) total;
    }
}
