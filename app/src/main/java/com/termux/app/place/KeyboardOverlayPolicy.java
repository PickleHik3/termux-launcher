package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.wall.PaneWallPage;

/**
 * When an open keyboard floats over the place instead of shrinking it, and what that does to the
 * geometry underneath.
 *
 * <p>Two places are floated over. The Linux display draws a screen of its own, at a size the guest
 * chose, and has nothing to reflow — taking room away from it scales the whole picture rather than
 * giving the keyboard somewhere to go; it is the one place where this is a choice. The home place
 * is a fixed field of cells, and every widget in it is laid out against that field: a keyboard that
 * took room from it would move and re-cut every widget on the way in and again on the way out, so
 * it always floats there, whatever is stored. Everywhere else the content is text that wants the
 * room, so the keyboard takes it, and a stored overlay is ignored rather than obeyed.
 *
 * <p>Pure, so the answer can be read and tested without a window: the view layer only applies it.
 */
public final class KeyboardOverlayPolicy {

    private KeyboardOverlayPolicy() {}

    /** Whether an open keyboard floats over the place on screen rather than shrinking it. */
    public static boolean overlays(@NonNull PaneWallPage place, @NonNull PlaceLayout layout) {
        if (place == PaneWallPage.WIDGETS) return true;
        return place == PaneWallPage.DISPLAY && layout.keyboardMode == KeyboardMode.OVERLAY;
    }

    /**
     * How far the content root reaches past the top of the accessory stack: the keyboard's own
     * height while it floats, and nothing at all otherwise.
     *
     * <p>Applied as a negative bottom margin on the content root, this hands back exactly the room
     * the keyboard took, so the content keeps the bounds it has with the keyboard closed and the
     * stack — the keyboard and the dock rows riding on top of it — draws over it. The dock's own
     * rows still take their room from the content, in overlay mode as in resize mode.
     */
    public static int contentOverlapPx(boolean overlays, boolean keyboardShown,
                                       int keyboardHeightPx) {
        return overlays && keyboardShown ? Math.max(0, keyboardHeightPx) : 0;
    }

    /**
     * The room the chrome takes from the content: the accessory stack and the margin below it, less
     * whatever the overlay hands back. This is what the content root is measured from, so a pass
     * that leaves it where it was is a pass the terminal and the display never have to hear about.
     */
    public static int contentReservationPx(int accessoryStackHeightPx, int accessoryBottomMarginPx,
                                           int contentOverlapPx) {
        return Math.max(0, Math.max(0, accessoryStackHeightPx)
            + Math.max(0, accessoryBottomMarginPx)
            - Math.max(0, contentOverlapPx));
    }

    /** The content root's height for one arrangement: what the window leaves, less the reservation. */
    public static int contentHeightPx(int availableHeightPx, int contentReservationPx) {
        return Math.max(0, availableHeightPx - Math.max(0, contentReservationPx));
    }
}
