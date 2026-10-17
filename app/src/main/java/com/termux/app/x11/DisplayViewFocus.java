package com.termux.app.x11;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.function.Predicate;

/**
 * Whether the display's own view may hold the focus, and so the keys. Typing anywhere else in the
 * launcher — the terminal, and every focusless overlay over it, the palette included — reaches the
 * app only through a focused terminal view. A display view that keeps the focus after its window
 * has left the screen, or that is removed or hidden while holding it (Android then hands the
 * focus to the first focusable view it finds), leaves typed keys with nowhere to go.
 *
 * <p>The display view also asks for the focus on its own whenever the activity's window regains
 * focus — a dialog closing, the Stop confirmation among them — even from a hidden page. It may
 * therefore be focusable only while its window is the one at rest: {@link #admit} on arrival,
 * {@link #release} when the wall leaves it and before the window is taken down.
 */
public final class DisplayViewFocus {

    private DisplayViewFocus() {}

    /** The display window is the one at rest: its view may take the focus and the keys. */
    public static void admit(@NonNull View display) {
        display.setFocusable(true);
        display.setFocusableInTouchMode(true);
    }

    /**
     * The display window left the screen, or is about to go. Unless a view that types — a
     * terminal — already holds the focus, {@code handBack} gives it to the active one first, so
     * the keys never stay with the display or with whatever view Android found; then the display
     * view stops being able to take them back.
     *
     * @param typesHere whether a focused view is one the keys belong to
     * @param handBack  focuses the active terminal pane
     */
    public static void release(@NonNull View display, @NonNull Predicate<View> typesHere,
                               @NonNull Runnable handBack) {
        View focused = display.getRootView().findFocus();
        if (focused == null || display.hasFocus() || !typesHere.test(focused)) handBack.run();
        display.setFocusableInTouchMode(false);
        display.setFocusable(false);
    }
}
