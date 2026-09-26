package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;

/**
 * Minimal mode (CONTEXT.md): a place shown with only its pane. The status bar shrinks to a thin
 * strip, the apps bar, the alphabets index, the extra keys and the keyboard go away, and the pane
 * takes the room they gave up, in either orientation. It is a state of the place, like whether the
 * keyboard is up, never a layout of its own (ADR 0003): the arrangement underneath stays the one
 * every place shares, and minimal mode is what is taken off it.
 *
 * <p>Pure, so what the mode means can be read and tested without a window; the activity only
 * applies it, and {@link PlaceLayoutStore} only remembers which places have it on.
 */
public final class MinimalMode {

    private MinimalMode() {}

    /**
     * How thick the status bar stands while its place is in minimal mode, in dp. Thin enough to
     * read as an edge rather than a bar, and still wide enough for a thumb to find: the strip is
     * the place the swipe that leaves minimal mode starts from, and the wall still pages from it.
     */
    public static final float STRIP_DP = 12f;

    /**
     * Whether a place can be minimal at all. Home is a field of widgets laid out against the chrome
     * around it and has no single pane to give the screen to; the terminal and the display do.
     */
    public static boolean available(@NonNull PaneWallPage place) {
        return place == PaneWallPage.TERMINAL || place == PaneWallPage.DISPLAY;
    }

    /**
     * The arrangement a minimal place stands in: every element but the status bar put away, on
     * whatever edge it was, so that turning the mode off puts each one back exactly where it stood.
     * The status bar is never hidden (the wall rides it); it only shrinks, which is the status bar's
     * own business rather than the arrangement's.
     */
    @NonNull
    public static PlaceLayout apply(@NonNull PlaceLayout layout) {
        PlaceLayout result = layout;
        for (Element element : Element.values()) {
            if (element == Element.STATUS) continue;
            Slot slot = result.slot(element);
            if (!slot.hidden) result = result.withSlot(element, slot.withHidden(true));
        }
        return result;
    }

    /**
     * The arrangement the chrome is pre-rolled into while the wall slides away from a minimal place
     * toward one that is not: only the elements standing along the bottom come back, because those
     * stand in the accessory stack, whose height the content can be held against for the length of
     * the slide. A rail or a column on a side, or a bar along the top, would take its room from the
     * pane the moment it was laid out, and the pane must not resize before the wall settles. Those
     * arrive with the rest of the arrangement at settle.
     */
    @NonNull
    public static PlaceLayout bottomOnly(@NonNull PlaceLayout layout) {
        PlaceLayout result = layout;
        for (Element element : Element.values()) {
            if (element == Element.STATUS) continue;
            Slot slot = result.slot(element);
            if (!slot.hidden && slot.edge != Edge.BOTTOM)
                result = result.withSlot(element, slot.withHidden(true));
        }
        return result;
    }

    /**
     * Whether the keyboard comes up as the wall lands on a place: what the place remembers, unless
     * it is minimal, which puts the keyboard away. The memory itself is kept untouched underneath,
     * so leaving minimal mode brings the keyboard back the way the place last had it.
     */
    public static boolean keyboardOnEnter(boolean remembered, boolean minimal) {
        return remembered && !minimal;
    }

    /** The status bar's thickness while its place is minimal, in pixels. */
    public static int stripThicknessPx(float density) {
        return Math.max(1, Math.round(STRIP_DP * density));
    }
}
