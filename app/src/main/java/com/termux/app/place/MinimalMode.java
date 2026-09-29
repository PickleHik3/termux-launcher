package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Minimal mode (CONTEXT.md): the launcher shown with nothing but the place's own content, unless
 * the user has chosen otherwise. It is one mode for the whole launcher rather than a state of a
 * place: paging to another place never leaves it, and only the corner tab's minimal button turns
 * it on or off. Since it has a layout of its own ({@link LayoutVariant#MINIMAL}, edited in the
 * Layout editor while the mode is on), what it shows is that layout; this class holds the layout
 * it starts from, the slide's pre-roll and the keyboard rules, which are not part of any layout.
 *
 * <p>Pure, so what the mode means can be read and tested without a window; the activity only
 * applies it, and {@link PlaceLayoutStore} only remembers whether it is on.
 */
public final class MinimalMode {

    private MinimalMode() {}

    /**
     * The layout the minimal variant starts from: every element put away, on whatever edge it was,
     * so that a user who adds one back in the Layout editor finds it where it stood. The status bar
     * goes too, which is what the mode has always looked like (it used to be drawn at no
     * thickness); the keyboard's own type, switch and the widget grid are left as they were.
     */
    @NonNull
    public static PlaceLayout apply(@NonNull PlaceLayout layout) {
        PlaceLayout result = layout;
        for (Element element : Element.values()) {
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
}
