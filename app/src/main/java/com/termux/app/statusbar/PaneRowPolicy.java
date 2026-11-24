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

    public static int rowHeightPx(float density) {
        return Math.round(REST_HEIGHT_DP * density);
    }

    /**
     * Whether the status bar stands as the row on a page: the place is the pane, the bar is shown
     * (a bar hidden on a layout stays hidden) and the page has a pane to hold it. Home has none,
     * so Home shows the strip.
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
