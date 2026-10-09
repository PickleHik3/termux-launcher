package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * Minimal mode (CONTEXT.md): a second saved layout ({@link LayoutVariant#MINIMAL}, edited in the
 * Layout editor while it is on), which starts with nothing but the place's own content. It is one
 * preference for the whole launcher rather than a state of a place: paging to another place never
 * leaves it, and only the corner tab's minimal button turns it on or off. It changes what the
 * layout shows and nothing else; panes, gestures and the bars it keeps work as in the normal
 * layout. This class holds the layout it starts from and the keyboard rule, which is not part of
 * any layout.
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
     * Whether the keyboard comes up as the wall lands on a place: what the place remembers, unless
     * the launcher is minimal, which puts the keyboard away. The memory itself is kept untouched
     * underneath, so leaving minimal mode brings the keyboard back the way the place last had it.
     */
    public static boolean keyboardOnEnter(boolean remembered, boolean minimal) {
        return remembered && !minimal;
    }
}
