package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * The status bar as a thin row inside the top pane: its numbers, where it stands, and what the
 * terminal gives up at its top in each place. Pure: densities, pixels and booleans.
 *
 * <p>The terminal pays for the status bar at its top in one of two ways. As a strip it is a band of
 * the top edge's stack ({@link #stripBandPx}); as the row it is the top pane's own inset, grown by
 * the row so the first row of text starts below it ({@link #paneTopInsetPx}, read by
 * {@code PaneShape.topInsetPx}). {@link #room} is the one answer for both: the prediction reads its
 * {@link Room#totalPx}, the settled layout reads the two parts, and they cannot differ. It changes
 * only when the place does, so the terminal's rows are recomputed once for that, never per frame.
 *
 * <p>The row is the same status bar, not a second one: the Marks line at {@link #MARK_DP}, the
 * readings at {@link #READING_SP} and the text size button, no divider. Under the Glyphs look the
 * row shows its readings without the window chips: the chips do not fit 16dp, and a row that
 * dropped the marks would leave no way to change page.
 */
public final class PaneRowPolicy {

    private PaneRowPolicy() {}

    /** The row at rest, in dp. If 12dp marks do not read on pong at arm's length this goes to 18. */
    public static final float REST_HEIGHT_DP = 16f;
    /** The Marks line's marks at rest, in dp. */
    public static final float MARK_DP = 12f;
    /** The readings at rest, in sp. */
    public static final float READING_SP = 9f;

    /** The row's content starts this far in from the pane's start edge, in dp (the mock-up's). */
    public static final float START_INSET_DP = 10f;
    /** The row's content ends this far in from the pane's end edge, in dp (the mock-up's). */
    public static final float END_INSET_DP = 16f;
    /**
     * How far above the row a press still reaches it, in dp: the mock-up's 6dp. Nothing below it,
     * so the terminal's first row of text keeps its own taps (#334 settles the reach).
     */
    public static final float TOUCH_ABOVE_DP = 6f;
    /**
     * The dark band the row lies on over a display's top edge, whatever the theme: it sits on the
     * display's own pixels, which no theme colours (the mock-up's rgba(10, 16, 26, .62)).
     */
    public static final int DISPLAY_BAND_ARGB = 0x9E0A101A;

    /** The row while pressed, in dp: drawn over the first row of text, never laid out as room. */
    public static final float ENLARGED_HEIGHT_DP = 32f;
    /** The Marks line's marks while the row is enlarged, in dp (the spec's full size). */
    public static final float ENLARGED_MARK_DP = 18f;
    /**
     * How far below the enlarged row a press still reaches it, in dp. At rest nothing below the
     * row is the row's: that is the terminal's first row of text.
     */
    public static final float TOUCH_BELOW_ENLARGED_DP = 6f;
    /** How far the enlarged row's ground reaches past the row on each side, in dp. */
    public static final float ENLARGED_GROUND_PAD_DP = 6f;
    /** The enlarged row's ground corner radius, in dp. */
    public static final float ENLARGED_GROUND_RADIUS_DP = 8f;
    /** The size change when it is not instant (reduced motion), in ms. */
    public static final int ENLARGE_TWEEN_MS = 120;

    public static int rowHeightPx(float density) {
        return Math.round(REST_HEIGHT_DP * density);
    }

    public static int enlargedHeightPx(float density) {
        return Math.round(ENLARGED_HEIGHT_DP * density);
    }

    /** The bottom of the row drawn from {@code topPx}: the pane's inset stays {@link #rowHeightPx}. */
    public static int rowBottomPx(int topPx, float density, boolean enlarged) {
        return topPx + (enlarged ? enlargedHeightPx(density) : rowHeightPx(density));
    }

    /**
     * Whether a press at ({@code x}, {@code y}) belongs to the row whose rest rect is
     * [{@code left}, {@code right}) by {@code topPx} down: the row itself, {@link #TOUCH_ABOVE_DP}
     * above it, and, only while enlarged, {@link #TOUCH_BELOW_ENLARGED_DP} below the enlarged row.
     * At rest the bottom edge is the first row of text's top, so it is the terminal's.
     */
    public static boolean hitsRow(float x, float y, int left, int topPx, int right, float density,
                                  boolean enlarged) {
        if (x < left || x >= right) return false;
        float above = Math.round(TOUCH_ABOVE_DP * density);
        float below = enlarged ? Math.round(TOUCH_BELOW_ENLARGED_DP * density) : 0f;
        return y >= topPx - above && y < rowBottomPx(topPx, density, enlarged) + below;
    }

    /**
     * How far a round corner of {@code radiusPx} stands in from the pane's side at {@code yPx}
     * below its top: 0 below the arc.
     */
    public static int arcInsetPx(float radiusPx, float yPx) {
        if (!(radiusPx > 0f) || yPx >= radiusPx) return 0;
        float dy = radiusPx - Math.max(0f, yPx);
        return Math.round(radiusPx - (float) Math.sqrt(radiusPx * radiusPx - dy * dy));
    }

    /** Where the row's content starts in from the pane's start edge: the mock-up's inset or the arc's. */
    public static int startInsetPx(float radiusPx, int rowPx, float density) {
        return Math.max(Math.round(START_INSET_DP * density), arcInsetPx(radiusPx, rowPx / 2f));
    }

    /** Where the row's content ends in from the pane's end edge: the mock-up's inset or the arc's. */
    public static int endInsetPx(float radiusPx, int rowPx, float density) {
        return Math.max(Math.round(END_INSET_DP * density), arcInsetPx(radiusPx, rowPx / 2f));
    }

    /**
     * Whether the status bar stands as the row on a page: the place is the pane, the bar is shown
     * (a bar hidden on a layout stays hidden) and the page has a pane to hold it. Home has none,
     * so Home shows the strip. The caller passes {@code statusShown} only for a bar the page's
     * layout stands along the top edge: the row takes the top strip's place, and a bar a layout
     * stands down a side or on the dock's plank keeps it.
     */
    public static boolean rowStands(@NonNull StatusPlace place, @NonNull PageKind page,
                                    boolean statusShown) {
        return place.inPane() && statusShown && page != PageKind.HOME;
    }

    /** The band the strip takes from the top edge: all of it, or none once the row took its place. */
    public static int stripBandPx(boolean rowStands, int stripPx) {
        return rowStands ? 0 : Math.max(0, stripPx);
    }

    /**
     * How much further a pane holds its content below its top: the row, for the top pane (top-left
     * in a side-by-side split) while the row stands, and nothing for every other pane.
     */
    public static int paneTopInsetPx(boolean rowStands, boolean topPane, int rowPx) {
        return rowStands && topPane ? Math.max(0, rowPx) : 0;
    }

    /** What the terminal's top gives up for the status bar, split the way each place pays it. */
    public static final class Room {
        public final int stripBandPx;
        public final int paneTopInsetPx;

        Room(int stripBandPx, int paneTopInsetPx) {
            this.stripBandPx = stripBandPx;
            this.paneTopInsetPx = paneTopInsetPx;
        }

        /** The whole of it, which the terminal's height loses at its top. */
        public int totalPx() {
            return stripBandPx + paneTopInsetPx;
        }
    }

    /**
     * The room for one place: {@code rowStands} as {@link #rowStands} answers it, whether the pane
     * asked is the top one, and the strip's and the row's own heights.
     */
    @NonNull
    public static Room room(boolean rowStands, boolean topPane, int stripPx, int rowPx) {
        return new Room(stripBandPx(rowStands, stripPx), paneTopInsetPx(rowStands, topPane, rowPx));
    }
}
