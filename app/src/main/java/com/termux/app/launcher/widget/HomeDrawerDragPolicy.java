package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import com.termux.app.launcher.home.HomeGestures;

/**
 * Whether a finger's travel on Home's free area hands the gesture to the app drawer's live drag
 * (Settings › Home › Gestures, swipe up set to the drawer). The claim uses the page swipe's own
 * slop race ({@link HomeSwipePolicy#axis}), so the same move that would have been the palette
 * gesture is the one that takes the drawer, and a page swipe is neither stolen nor stealing.
 *
 * <p>Pure: the pane reads the finger, the activity runs the drag.
 */
public final class HomeDrawerDragPolicy {

    private HomeDrawerDragPolicy() {}

    /**
     * True for a clearly vertical, upward move ({@code dx}, {@code dy} since the press, y growing
     * down) while swipe up is set to the drawer. Anything else keeps the gesture's discrete path.
     */
    public static boolean claims(@NonNull HomeGestures.Action swipeUpAction, float dx, float dy,
                                 float slopPx) {
        if (swipeUpAction != HomeGestures.Action.DRAWER) return false;
        if (!(dy < 0f)) return false;
        return HomeSwipePolicy.axis(dx, dy, slopPx) == HomeSwipePolicy.Axis.VERTICAL;
    }
}
