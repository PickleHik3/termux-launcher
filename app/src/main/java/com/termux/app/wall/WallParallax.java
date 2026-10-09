package com.termux.app.wall;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Where the wallpaper sits under the wall: the parallax's geometry, pure and tested.
 *
 * <p>A managed wallpaper is picked one and a half screens wide, and the places keep fixed positions
 * on it — Home at its left edge, Terminal at its centre, Display at its right edge — whether or
 * not every place is on this install's wall. The half screen of spare width is what the picture
 * pans over, a quarter of a screen per place step, and the pan is a continuous function of the
 * wall's live position, so a drag, a fling and a settle all move the picture on the wall's own
 * curve and timing. Nothing here knows about frames or views; the activity turns the answer into
 * an x-offset every glass surface samples the shared frame at.
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
     * Where a place sits on the picture, as a fraction of the spare width: Home at the left edge,
     * Terminal in the centre, Display at the right edge. Fixed per place, not per slot on the
     * wall — a wall without a Home page still shows the Terminal over the picture's centre, which
     * is also the part the system was handed as the screen-sized wallpaper.
     */
    public static float placePosition(@NonNull PaneWallPage page) {
        switch (page) {
            case WIDGETS:
                return 0f;
            case DISPLAY:
                return 1f;
            case TERMINAL:
            default:
                return 0.5f;
        }
    }

    /**
     * The wallpaper's x-offset for the wall as it is right now: how far into the picture the
     * screen's left edge is, in px.
     *
     * <p>The wall's offset is signed distance of the pages from the current page's rest, in wall
     * widths: positive means the pages sit to the right of where they will land, so what is
     * coming into view is the place to the current one's left. The picture interpolates between
     * the current place's position and that neighbour's by the fraction of a width travelled. The
     * neighbour is the wall's own — on a ring, past the Display place comes Home — so a wrap
     * sweeps the picture back across its whole span over the slide, and a drag that turns into
     * a committed slide (the wall's {@code goTo} re-bases the offset against the new current page)
     * lands on the same value from either side, with no jump.</p>
     *
     * @param pages        the places this wall has, in spatial order
     * @param current      the page the wall's record says is current
     * @param wallOffsetPx the wall's signed offset from that page's rest
     * @param wallWidthPx  one page's width; the offset is measured against it
     * @param sparePx      the picture's width beyond one screen; 0 while nothing pans
     */
    public static float offsetPx(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                                 float wallOffsetPx, int wallWidthPx, int sparePx) {
        if (sparePx <= 0) return 0f;
        float from = placePosition(current);
        if (wallWidthPx <= 0 || wallOffsetPx == 0f) return from * sparePx;
        float fraction = Math.max(-1f, Math.min(1f, wallOffsetPx / wallWidthPx));
        PaneWallPage neighbour = PaneWallPolicy.neighbour(pages, current, fraction > 0f ? -1 : 1);
        float to = placePosition(neighbour);
        float travelled = Math.abs(fraction);
        return (from + (to - from) * travelled) * sparePx;
    }
}
