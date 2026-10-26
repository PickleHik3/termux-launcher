package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * Where the wallpaper sits under the wall: the parallax's geometry, pure and tested.
 *
 * <p>A managed wallpaper is picked one and a half screens wide, and the half screen of spare width
 * is spread over the whole row (ADR 0008): Home page 1 at the picture's left edge, the row's last
 * window — the display window among them when it is last — at its right edge, and every page
 * between at an even step. The pan is a continuous function of the row's live position, so a
 * Home page swipe, the seam into the Workspace and a border drag between windows all move the
 * picture on their own curve and timing, with no jump where one stretch of the row meets the
 * next. Nothing here knows about frames or views; the activity turns the
 * answer into an x-offset every glass surface samples the shared frame at.
 */
public final class WallParallax {

    /** How much wider than the screen the picker crops a managed wallpaper. */
    public static final float SPAN_FACTOR = 1.5f;

    /**
     * A stored picture narrower than this, relative to the screen, is one screen wide as far as
     * the pan is concerned: a wallpaper cropped before the wide picker existed comes out a few
     * pixels either side of the screen once its aspect is measured against a different decor,
     * and those pixels are not a pan worth having.
     */
    private static final float LEGACY_TOLERANCE = 0.05f;

    private WallParallax() {}

    /** The width the picker crops at for a screen {@code screenWidthPx} wide. */
    public static int pickerWidthPx(int screenWidthPx) {
        return Math.max(1, Math.round(Math.max(1, screenWidthPx) * SPAN_FACTOR));
    }

    /**
     * How wide the stored wallpaper is once scaled to the screen's height, in screen pixels —
     * the width of the frame the glass samples. A picture that is not clearly wider than the
     * screen answers exactly one screen: a wallpaper picked before the wide crop simply does not
     * pan until it is picked again. Nothing wider than {@link #SPAN_FACTOR} screens is used
     * either, so the pre-blurred frame's size stays bounded whatever the file holds.
     *
     * @return the span in screen pixels, at least {@code screenWidthPx}
     */
    public static int spanPx(int imageWidth, int imageHeight, int screenWidthPx, int screenHeightPx) {
        int screenWidth = Math.max(1, screenWidthPx);
        if (imageWidth <= 0 || imageHeight <= 0 || screenHeightPx <= 0) return screenWidth;
        long scaled = Math.round((double) imageWidth * screenHeightPx / imageHeight);
        if (scaled < screenWidth * (1.0 + LEGACY_TOLERANCE)) return screenWidth;
        return (int) Math.min(scaled, pickerWidthPx(screenWidth));
    }

    /**
     * How many pages the row has: the Home pages (at least one), then the windows, the display
     * window counted among them while it is on the row.
     */
    public static int rowCount(int homeCount, int windowCount) {
        return Math.max(1, homeCount) + Math.max(0, windowCount);
    }

    /**
     * Where a page sits in the row, counting from 0: Home page {@code index} is {@code index},
     * and window {@code index} — the display window by its place in the row — comes after every
     * Home page. An index past its kind's end is held at the kind's last page.
     */
    public static int rowIndex(@NonNull PaneWallPage kind, int index, int homeCount,
                               int windowCount) {
        int homes = Math.max(1, homeCount);
        int windows = Math.max(0, windowCount);
        switch (kind) {
            case WIDGETS:
                return clamp(index, 0, homes - 1);
            case TERMINAL:
            default:
                return homes + clamp(index, 0, Math.max(0, windows - 1));
        }
    }

    /** {@link #innerPosition}'s departure when it is simply the page next door. */
    public static final int ADJACENT = -1;

    /**
     * The row position while a page slides in inside its own stretch of the row: a Home page
     * turned by Home's swipe, a window brought in by the border drag or the window bar. The page
     * at {@code arrivalAt} stands {@code shiftFraction} of a page width off its rest (positive
     * to the right, as a translation); the travel left over is that fraction of the way from the
     * page it left, {@code departureAt}. A jump of several windows from the bar therefore pans
     * over all of them on the arrival's own curve rather than snapping the rest of the way.
     *
     * @param departureAt the page the slide left, or {@link #ADJACENT} for the neighbour on the
     *                    side the page comes in from: a shift to the right came from the left
     */
    public static float innerPosition(int arrivalAt, float shiftFraction, int departureAt) {
        if (shiftFraction == 0f) return arrivalAt;
        int from = departureAt == ADJACENT
            ? arrivalAt - (shiftFraction > 0f ? 1 : -1) : departureAt;
        float left = Math.min(1f, Math.abs(shiftFraction));
        return arrivalAt + (from - arrivalAt) * left;
    }

    /**
     * The row position while the wall itself is off its rest: the wall's offset is the signed
     * distance of the pages from the current page's rest (positive: they sit to the right, so
     * what is coming into view is on the current page's left), and the position runs from the
     * current page's {@code currentAt} to the incoming one's {@code neighbourAt} by the fraction
     * of a width travelled. A drag that commits re-bases the offset against the new page, which
     * lands on the same value from either side, so the seam between Home and the Workspace is
     * crossed without a jump.
     */
    public static float slidePosition(float currentAt, float neighbourAt, float wallOffsetPx,
                                      int wallWidthPx) {
        if (wallWidthPx <= 0 || wallOffsetPx == 0f) return currentAt;
        float travelled = Math.min(1f, Math.abs(wallOffsetPx) / wallWidthPx);
        return currentAt + (neighbourAt - currentAt) * travelled;
    }

    /**
     * The wallpaper's x-offset for a row position: how far into the picture the screen's left
     * edge is, in px. The whole spare width is spread over the row, linearly, so the first page
     * rests at the picture's left edge and the last at its right whatever the count: a long row
     * pans a little per page, a two-page row the whole spare in one step, and a one-page row not
     * at all. Past either end the picture holds at its edge.
     *
     * @param rowPosition the live position, 0 for Home page 1, fractional mid-slide
     * @param rowCount    the row's pages ({@link #rowCount})
     * @param sparePx     the picture's width beyond one screen; 0 while nothing pans
     */
    public static float offsetPx(float rowPosition, int rowCount, int sparePx) {
        if (sparePx <= 0 || rowCount <= 1) return 0f;
        float last = rowCount - 1;
        float position = Math.max(0f, Math.min(last, rowPosition));
        return position / last * sparePx;
    }

    /**
     * How far the wall would have slid to move the wallpaper {@code gapPx}, in px of the wall's
     * own travel: the gap in pages ({@link #offsetPx} spreads {@code sparePx} over
     * {@code rowCount - 1} steps) times a page's width. Edit pages holds the wallpaper still while
     * the wall jumps from card to card under it; when it lets go the wallpaper settles the rest
     * of the way on a page's spring ({@link SettleSpring#page}) over this distance, so it moves
     * as it would had the wall slid there. 0 while nothing pans.
     */
    public static float settleTravelPx(float gapPx, int rowCount, int sparePx, int pageWidthPx) {
        if (sparePx <= 0 || rowCount <= 1 || pageWidthPx <= 0) return 0f;
        return gapPx / sparePx * (rowCount - 1) * pageWidthPx;
    }

    /**
     * The wallpaper's x-offset while a hold settles: the live offset plus the share of the gap
     * left at the release still to go ({@code remaining}, 1 at the release and 0 once landed),
     * held inside the picture. The live offset is read every frame, so a swipe during the settle
     * carries the wallpaper with it instead of fighting it.
     */
    public static float settlingOffsetPx(float livePx, float gapPx, float remaining, int sparePx) {
        if (sparePx <= 0) return 0f;
        float left = Math.max(0f, Math.min(1f, remaining));
        return Math.max(0f, Math.min(sparePx, livePx + gapPx * left));
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
