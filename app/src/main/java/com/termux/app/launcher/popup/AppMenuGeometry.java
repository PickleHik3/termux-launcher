package com.termux.app.launcher.popup;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * The App menu's shape and placement, as pure arithmetic: Launcher3's numbers, and where the card,
 * its arrow and its scale pivot go for an icon anywhere on screen.
 *
 * <p>The window is the card plus a margin on every side for the 2 dp shadow, plus the arrow's height
 * on the side facing the icon. All lengths in and out are pixels; the dp constants are converted by
 * the caller with the display density.
 */
public final class AppMenuGeometry {

    public static final int ROW_WIDTH_DP = 216;
    public static final int ROW_HEIGHT_DP = 52;
    /** A group's caption slab (the surface popup's "Status bar", "Keyboard"). */
    public static final int HEADER_HEIGHT_DP = 32;
    public static final int ROW_PADDING_DP = 16;
    public static final int GLYPH_DP = 24;
    /** Between the glyph and the label, so labels start 52 dp in. */
    public static final int GLYPH_GAP_DP = 12;
    public static final int LABEL_SP = 14;
    public static final int GROUP_GAP_DP = 4;
    /** The card's outer corners: the dialog corner radius of the chosen mockup. */
    public static final int OUTER_RADIUS_DP = 20;
    /** Where two rows of a group meet. */
    public static final int JOIN_RADIUS_DP = 4;
    /** A group of one row is a pill; the drawable clamps this to half the row's height. */
    public static final int PILL_RADIUS_DP = 100;
    public static final int ARROW_WIDTH_DP = 12;
    public static final int ARROW_HEIGHT_DP = 10;
    public static final int ELEVATION_DP = 2;
    /** Room around the card for its shadow, inside the window. */
    public static final int SHADOW_ROOM_DP = 4;
    /** Between the arrow's tip and the icon. */
    public static final int ANCHOR_GAP_DP = 4;
    /** Closest the card comes to the visible frame's sides. */
    public static final int EDGE_MARGIN_DP = 8;

    private AppMenuGeometry() {
    }

    // ------------------------------------------------------------------ shapes

    /**
     * The top and bottom corner radii of row {@code index} in a group of {@code size}: the outer
     * radius where the row is the group's edge, the join radius where it meets a neighbour, and a
     * pill for a lone row.
     */
    public static void rowRadii(int index, int size, float outer, float join, float pill,
                                @NonNull float[] outTopBottom) {
        if (size <= 1) {
            outTopBottom[0] = pill;
            outTopBottom[1] = pill;
            return;
        }
        outTopBottom[0] = index == 0 ? outer : join;
        outTopBottom[1] = index == size - 1 ? outer : join;
    }

    /**
     * Which of the three shades group {@code index} takes: one step per group from the top, the
     * way Launcher3 hands out popup_shade_first, second and third.
     */
    public static int shadeIndex(int index) {
        return Math.max(0, Math.min(2, index));
    }

    // ------------------------------------------------------------------ placement

    /** Where the window goes, and where its arrow and scale pivot sit inside it. */
    public static final class Placement {
        /** The window's top-left on screen. */
        public int x;
        public int y;
        public int windowWidth;
        public int windowHeight;
        /** The card's height without the arrow; shorter than its content when the rows scroll. */
        public int cardHeight;
        public boolean scrolls;
        /** True when the card opens below the icon, so the arrow is on its top edge. */
        public boolean arrowOnTop;
        /** The arrow's centre, from the window's left edge. */
        public int arrowCenterX;
        /** The arrow's tip, in window coordinates: the point the card scales from. */
        public float pivotX;
        public float pivotY;
    }

    /**
     * Places a card {@code cardWidth} wide whose rows want {@code contentHeight} against
     * {@code anchor}, inside {@code frame}.
     *
     * <p>The card opens above the icon, as on the dock; when there is no room above it opens below
     * (a top-row drawer cell); when it fits on neither side it takes the roomier one and scrolls.
     * Horizontally it centres on the icon and is held {@code edgeMargin} inside the frame, so near a
     * side the card stops at the margin while its arrow keeps pointing at the icon, never closer to
     * the card's edge than {@code arrowInset} (the corner radius plus half the arrow).
     */
    public static void place(@NonNull Rect anchor, @NonNull Rect frame, int cardWidth,
                             int contentHeight, int arrowWidth, int arrowHeight, int shadowRoom,
                             int gap, int edgeMargin, int arrowInset, @NonNull Placement out) {
        int roomAbove = anchor.top - gap - arrowHeight - frame.top;
        int roomBelow = frame.bottom - (anchor.bottom + gap + arrowHeight);
        boolean above;
        if (contentHeight <= roomAbove) {
            above = true;
        } else if (contentHeight <= roomBelow) {
            above = false;
        } else {
            above = roomAbove >= roomBelow;
        }
        int room = Math.max(0, above ? roomAbove : roomBelow);
        int cardHeight = Math.min(contentHeight, room);
        out.scrolls = cardHeight < contentHeight;
        out.cardHeight = cardHeight;
        out.arrowOnTop = !above;
        out.windowWidth = cardWidth + 2 * shadowRoom;
        out.windowHeight = cardHeight + arrowHeight + 2 * shadowRoom;

        int anchorCenterX = anchor.left + anchor.width() / 2;
        int minLeft = frame.left + edgeMargin;
        int maxLeft = frame.right - edgeMargin - cardWidth;
        int cardLeft = maxLeft < minLeft
            ? frame.left + (frame.width() - cardWidth) / 2
            : clamp(anchorCenterX - cardWidth / 2, minLeft, maxLeft);
        out.x = cardLeft - shadowRoom;

        if (above) {
            int tipY = anchor.top - gap;
            out.y = tipY - arrowHeight - cardHeight - shadowRoom;
            out.pivotY = shadowRoom + cardHeight + arrowHeight;
        } else {
            int tipY = anchor.bottom + gap;
            out.y = tipY - shadowRoom;
            out.pivotY = shadowRoom;
        }
        int inset = Math.min(arrowInset, cardWidth / 2);
        out.arrowCenterX = clamp(anchorCenterX - out.x, shadowRoom + inset,
            shadowRoom + cardWidth - inset);
        out.pivotX = out.arrowCenterX;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
