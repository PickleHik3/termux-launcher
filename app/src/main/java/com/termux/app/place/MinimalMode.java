package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Minimal mode (CONTEXT.md): the launcher shown with nothing but the place's own content. The
 * status bar, the apps bar, the alphabets index and the extra keys go away, the keyboard is put
 * down, and the content takes the whole screen, in either orientation. It is one mode for the
 * whole launcher rather than a state of a place: paging to another place never leaves it, and
 * only the corner tab's minimal button turns it on or off. It is never a layout of its own (ADR
 * 0003): the arrangement underneath stays the one every place shares, and minimal mode is what is
 * taken off it.
 *
 * <p>Pure, so what the mode means can be read and tested without a window; the activity only
 * applies it, and {@link PlaceLayoutStore} only remembers whether it is on.
 */
public final class MinimalMode {

    private MinimalMode() {}

    /**
     * The arrangement a minimal place stands in: every element but the status bar put away, on
     * whatever edge it was, so that turning the mode off puts each one back exactly where it stood.
     * The status bar's slot is never hidden (the model has no hidden status bar); it is drawn at
     * no thickness instead, which is the status bar's own business rather than the arrangement's.
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
     *
     * <p>With the mode shared by every place, a slide never crosses from a minimal place to one
     * that is not; the pre-roll is kept for the frame that asks for it all the same.
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
     * the launcher is minimal, which puts the keyboard away. The memory itself is kept untouched
     * underneath, so leaving minimal mode brings the keyboard back the way the place last had it.
     */
    public static boolean keyboardOnEnter(boolean remembered, boolean minimal) {
        return remembered && !minimal;
    }

    /**
     * The status bar's thickness while the launcher is minimal, in pixels: nothing. The bar used
     * to stay as a thin strip to swipe out of the mode from; that swipe started under the phone's
     * own status bar and opened the notification shade instead, so the strip is gone and the mode
     * reserves nothing along the bar's edge. The corner tab is the way out.
     */
    public static int statusBarThicknessPx(boolean minimal, int restingThicknessPx) {
        return minimal ? 0 : Math.max(0, restingThicknessPx);
    }
}
