package com.termux.app.tour;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where the card sits for the control it is about, as pure arithmetic.
 *
 * <p>A card floating in the middle of the screen makes the user find the glow for themselves. This
 * puts it against its target — centred on it, on the side of it with room — and hands back the
 * pointer's tip so the two read as one object.
 *
 * <p>The overlay is the whole window, so its two ends are not interchangeable: the top of it is
 * behind the system status bar and the bottom behind the gesture bar, which is why the vertical
 * keep-out is two numbers rather than one. It is also what makes the flip below do anything — with
 * equal ends, a side that cannot hold the card is always the side the target is furthest from.
 *
 * <p>All of it is integers in overlay pixels and none of it touches a view, so every case below —
 * a target at the top, at the bottom, against either edge, too tall to sit on its own side, absent
 * altogether — is a unit test rather than a phone.
 */
public final class TourCardPlacement {

    /** The card carries no pointer: it is not anchored to anything it can point at. */
    public static final int POINTER_NONE = 0;
    /** The pointer is on the card's top edge, so the target is above the card. */
    public static final int POINTER_TOP = 1;
    /** The pointer is on the card's bottom edge, so the target is below the card. */
    public static final int POINTER_BOTTOM = 2;

    public final int left;
    public final int top;
    public final int pointerEdge;
    /** Where the pointer's tip sits along the card's edge, in overlay pixels. */
    public final int pointerCenterX;

    private TourCardPlacement(int left, int top, int pointerEdge, int pointerCenterX) {
        this.left = left;
        this.top = top;
        this.pointerEdge = pointerEdge;
        this.pointerCenterX = pointerCenterX;
    }

    public boolean hasPointer() {
        return pointerEdge != POINTER_NONE;
    }

    /**
     * Places a measured card against a measured target.
     *
     * @param target the control's bounds in overlay pixels, or null for a card that points at
     *     nothing — which keeps the middle of the overlay, the position the run has always used
     *     for the closing card
     * @param sideMargin the gap the card keeps from the overlay's left and right edges
     * @param topMargin how far down the overlay the card may start
     * @param bottomMargin how far up from the overlay's bottom the card must end
     * @param gap the gap between the target and the card's pointer
     * @param pointerHeight how far the pointer stands off the card's edge
     * @param pointerHalfWidth half the pointer's base, used to keep its tip off the card's corners
     */
    @NonNull
    public static TourCardPlacement place(int overlayWidth, int overlayHeight,
                                          int cardWidth, int cardHeight, @Nullable Rect target,
                                          int sideMargin, int topMargin, int bottomMargin,
                                          int gap, int pointerHeight, int pointerHalfWidth) {
        if (overlayWidth <= 0 || overlayHeight <= 0 || cardWidth <= 0 || cardHeight <= 0)
            return new TourCardPlacement(sideMargin, topMargin, POINTER_NONE, 0);

        int maxLeft = Math.max(sideMargin, overlayWidth - sideMargin - cardWidth);
        int maxTop = Math.max(topMargin, overlayHeight - bottomMargin - cardHeight);

        if (target == null || target.isEmpty()) {
            return new TourCardPlacement(
                clamp((overlayWidth - cardWidth) / 2, sideMargin, maxLeft),
                clamp((overlayHeight - cardHeight) / 2, topMargin, maxTop),
                POINTER_NONE, 0);
        }

        int standOff = gap + pointerHeight;
        int topBelow = target.bottom + standOff;
        int topAbove = target.top - standOff - cardHeight;
        // The target's own half of the overlay decides which side is tried first; the other side
        // is taken whenever the first one has no room, which is the same test as "the card would
        // have to be clamped back over the control it is pointing at".
        boolean preferBelow = target.centerY() < overlayHeight / 2;
        int preferredTop = preferBelow ? topBelow : topAbove;
        int flippedTop = preferBelow ? topAbove : topBelow;

        int top;
        int pointerEdge;
        if (fits(preferredTop, cardHeight, overlayHeight, topMargin, bottomMargin)) {
            top = preferredTop;
            pointerEdge = preferBelow ? POINTER_TOP : POINTER_BOTTOM;
        } else if (fits(flippedTop, cardHeight, overlayHeight, topMargin, bottomMargin)) {
            top = flippedTop;
            pointerEdge = preferBelow ? POINTER_BOTTOM : POINTER_TOP;
        } else {
            // Neither side holds the card. It is clamped on screen and drops the pointer rather
            // than drawing one that points at the middle of its own body.
            top = clamp(preferredTop, topMargin, maxTop);
            pointerEdge = POINTER_NONE;
        }

        int left = clamp(target.centerX() - (cardWidth / 2), sideMargin, maxLeft);
        int pointerCenterX = 0;
        if (pointerEdge != POINTER_NONE) {
            // The tip leans toward the target but stays off the card's rounded corners.
            int inset = Math.min(2 * pointerHalfWidth, Math.max(0, (cardWidth / 2) - 1));
            pointerCenterX = clamp(target.centerX(), left + inset, left + cardWidth - inset);
        }
        return new TourCardPlacement(left, top, pointerEdge, pointerCenterX);
    }

    /**
     * At the top of the overlay, under the status bar, centred across it and pointing at nothing.
     *
     * <p>Where a card goes when it must not sit on what it is about: the A-Z scrub, which fills
     * the bottom of the screen with the thing the user is choosing, and any card still asking to
     * close the full-plane surface it would otherwise be buried under.
     */
    @NonNull
    public static TourCardPlacement placeUnderStatusBar(int overlayWidth, int overlayHeight,
                                                        int cardWidth, int cardHeight,
                                                        int sideMargin, int topMargin,
                                                        int bottomMargin) {
        if (overlayWidth <= 0 || overlayHeight <= 0 || cardWidth <= 0 || cardHeight <= 0)
            return new TourCardPlacement(sideMargin, topMargin, POINTER_NONE, 0);
        int maxLeft = Math.max(sideMargin, overlayWidth - sideMargin - cardWidth);
        int maxTop = Math.max(topMargin, overlayHeight - bottomMargin - cardHeight);
        return new TourCardPlacement(
            clamp((overlayWidth - cardWidth) / 2, sideMargin, maxLeft),
            clamp(topMargin, topMargin, maxTop), POINTER_NONE, 0);
    }

    private static boolean fits(int top, int cardHeight, int overlayHeight,
                                int topMargin, int bottomMargin) {
        return top >= topMargin && top + cardHeight <= overlayHeight - bottomMargin;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
