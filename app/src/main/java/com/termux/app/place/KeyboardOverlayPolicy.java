package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.KeyboardForm;
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
 * <p>A floating keyboard is the exception on every place, the terminal included: it is a frame the
 * user has moved somewhere of their own choosing, so it is over the content by definition and
 * there is no arrangement in which it takes room from it.
 *
 * <p>Pure, so the answer can be read and tested without a window: the view layer only applies it.
 */
public final class KeyboardOverlayPolicy {

    private KeyboardOverlayPolicy() {}

    /** Whether an open keyboard floats over the place on screen rather than shrinking it. */
    public static boolean overlays(@NonNull PaneWallPage place, @NonNull PlaceLayout layout) {
        if (layout.keyboardForm == KeyboardForm.FLOATING) return true;
        if (place == PaneWallPage.WIDGETS) return true;
        return place == PaneWallPage.DISPLAY && layout.keyboardMode == KeyboardMode.OVERLAY;
    }

    /**
     * The place a keyboard question is asked of, derived from what is in front now: the display's
     * place is the Workspace with a display window in front of it, so a committed
     * {@link PaneWallPage#DISPLAY} stands only while that window is the one in front. Whatever
     * left it unrecorded (a window closed, another window brought forward, the display gone), the
     * answer is the terminal's again, and the stored Overlay/Resize choice, which is the display's
     * alone, is not read. Only a stale display is demoted: arriving at one is the settle's to
     * commit, never a pass mid-slide.
     */
    @NonNull
    public static PaneWallPage keyboardPlace(@NonNull PaneWallPage committed,
                                             boolean displayWindowInFront) {
        return committed == PaneWallPage.DISPLAY && !displayWindowInFront
            ? PaneWallPage.TERMINAL : committed;
    }

    /** {@link #overlays(PaneWallPage, PlaceLayout)} for the place {@link #keyboardPlace} derives. */
    public static boolean overlays(@NonNull PaneWallPage committed, boolean displayWindowInFront,
                                   @NonNull PlaceLayout layout) {
        return overlays(keyboardPlace(committed, displayWindowInFront), layout);
    }

    /**
     * Whether a move from {@code from} to {@code to} changes whether the keyboard floats, and so
     * the room the content keeps: the pass that settles the move has to lay the content out again
     * even when no slide held it, as when the display window goes off the Workspace and a
     * terminal window takes its place.
     */
    public static boolean changesBetween(@NonNull PaneWallPage from, @NonNull PaneWallPage to,
                                         @NonNull PlaceLayout layout) {
        return overlays(from, layout) != overlays(to, layout);
    }

    /**
     * How far the content root reaches past the top of the accessory stack: the keyboard's own
     * height while it floats, and nothing at all otherwise.
     *
     * <p>Applied as a negative bottom margin on the content root, this hands back exactly the room
     * the keyboard took, so the content keeps the bounds it has with the keyboard closed and the
     * stack — the keyboard and the dock rows riding on top of it — draws over it. The dock's own
     * rows still take their room from the content, in overlay mode as in resize mode, except on a
     * Home page ({@link #overlapPx}).
     */
    public static int contentOverlapPx(boolean overlays, boolean keyboardShown,
                                       int keyboardHeightPx) {
        return overlays && keyboardShown ? Math.max(0, keyboardHeightPx) : 0;
    }

    /**
     * How far the content root reaches past the top of the accessory stack once the dock is
     * counted too: on a Home page, where the dock floats over the grid, the whole stack and the
     * margin under it, so the widget grid keeps its full height and the dock and the keyboard draw
     * over it; everywhere else the keyboard's share alone, as {@link #contentOverlapPx} says.
     */
    public static int overlapPx(boolean dockOverlays, int stackHeightPx, int stackBottomMarginPx,
                                int keyboardOverlapPx) {
        return overlapPx(dockOverlays, stackHeightPx, stackBottomMarginPx, keyboardOverlapPx, 0);
    }

    /**
     * The same, with the system keyboard's take on the content root counted
     * ({@link #systemImeTakePx}): on a Home page the content reaches past it too, so the widget
     * grid keeps the height it has with the keyboard down and the system keyboard, with the dock
     * riding on it, covers the grid instead of squeezing it. Everywhere else the take stays the
     * content's to give up, as it always was: the text there wants the room.
     */
    public static int overlapPx(boolean dockOverlays, int stackHeightPx, int stackBottomMarginPx,
                                int keyboardOverlapPx, int systemImeTakePx) {
        if (!dockOverlays) return Math.max(0, keyboardOverlapPx);
        return Math.max(0, stackHeightPx) + Math.max(0, stackBottomMarginPx)
            + Math.max(0, systemImeTakePx);
    }

    /**
     * How much shorter the system keyboard leaves the content root. The root fits the system
     * windows: a window that resizes for the keyboard pads its bottom by the keyboard
     * ({@code rootFitBottomPx}, the legacy system-window inset) where it would otherwise pad it by
     * the bars ({@code restFitBottomPx}, the navigation bar and any cutout below); a keyboard the
     * insets miss is answered by the root's own bottom margin instead
     * ({@code rootBottomMarginPx}). The sum is how far the root's bottom stands above where it
     * stands with the keyboard down; 0 with it down.
     */
    public static int systemImeTakePx(int rootFitBottomPx, int restFitBottomPx,
                                      int rootBottomMarginPx) {
        return Math.max(0, rootFitBottomPx - Math.max(0, restFitBottomPx))
            + Math.max(0, rootBottomMarginPx);
    }

    /**
     * Where the dock's top stands on screen with no system keyboard under it, given the bottom of
     * the accessory stack as the next layout leaves it ({@code stackBottomPx}) and how far a
     * system keyboard has pushed the stack up ({@code imePushPx}). Home floats are laid out above
     * this: the system keyboard, like the in-app one, is no resize of their room.
     */
    public static int homeDockRestTopPx(int stackBottomPx, int dockPx, int imePushPx) {
        return stackBottomPx + Math.max(0, imePushPx) - Math.max(0, dockPx);
    }

    /**
     * The band at the bottom of the widget pane the dock takes at rest (#104): on a Home page,
     * where the content reaches under the whole stack ({@link #overlapPx}) and the dock floats over
     * the grid, the dock's rows ({@code dockRestPx}, what the dock's layout reports for them, a
     * bottom status band included) and the edge gap under them; the grid's rows end above it.
     * Zero on the Workspace half, whose content already ends at the stack's top, and zero with no
     * dock rows on screen.
     *
     * <p>The rest height, not the stack's: a keyboard, the system's or the in-app one, still floats
     * over the grid with the dock riding on it, so it never re-cuts a widget on its way in or out.
     * With the dock the same on both halves the last row stands in the same place either side of
     * the seam.
     */
    public static int homeGridDockReservePx(boolean dockOverlays, int dockRestPx, int edgeGapPx) {
        if (!dockOverlays || dockRestPx <= 0) return 0;
        return dockRestPx + Math.max(0, edgeGapPx);
    }

    /**
     * The top of what covers Home from below while a keyboard is up, on screen, or -1 while none
     * is: the top of the accessory stack, the dock riding on the keyboard. Up for the in-app
     * keyboard ({@code inAppKeyboardShown}, the stack holding it) and for a system keyboard that
     * pushes the stack up ({@code imePushPx}, the stack standing on it). Floats and terminal
     * widgets on Home meet this edge, whichever keyboard it is.
     */
    public static int homeKeyboardTopPx(boolean inAppKeyboardShown, int imePushPx,
                                        int stackBottomPx, int stackPx) {
        if (!inAppKeyboardShown && imePushPx <= 0) return -1;
        return Math.max(0, stackBottomPx - Math.max(0, stackPx));
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

    /**
     * The room a place's chrome takes from the content at rest, built from the stack's parts the
     * way the geometry pass builds it: the dock's rows, the terminal's dock padding and the
     * keyboard when it is up, the margin under all of that, less what an overlaying keyboard hands
     * back. Two places' reservations from one model differ by exactly what differs between them,
     * which is how the wall's slide knows how much room the content gets back or loses on the way.
     */
    public static int restReservationPx(int dockContentPx, int flushPaddingPx,
                                        boolean keyboardShown, boolean keyboardOverlays,
                                        int keyboardPx, int bottomMarginPx) {
        return restReservationPx(false, dockContentPx, flushPaddingPx, keyboardShown,
            keyboardOverlays, keyboardPx, bottomMarginPx);
    }

    /**
     * The same, for a page whose dock may float over the content: a Home page's chrome takes no
     * room at all, keyboard up or down.
     */
    public static int restReservationPx(boolean dockOverlays, int dockContentPx,
                                        int flushPaddingPx, boolean keyboardShown,
                                        boolean keyboardOverlays, int keyboardPx,
                                        int bottomMarginPx) {
        if (dockOverlays) return 0;
        int keyboard = keyboardShown ? Math.max(0, keyboardPx) : 0;
        int stackPx = Math.max(0, dockContentPx) + Math.max(0, flushPaddingPx) + keyboard;
        return contentReservationPx(stackPx, bottomMarginPx,
            contentOverlapPx(keyboardOverlays, keyboardShown, keyboardPx));
    }

    /** The content root's height for one arrangement: what the window leaves, less the reservation. */
    public static int contentHeightPx(int availableHeightPx, int contentReservationPx) {
        return Math.max(0, availableHeightPx - Math.max(0, contentReservationPx));
    }
}
