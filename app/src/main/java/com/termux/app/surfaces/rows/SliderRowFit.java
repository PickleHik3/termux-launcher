package com.termux.app.surfaces.rows;

/**
 * Whether a slider row stands on one line (glyph, name, then the track with its value inside it)
 * or keeps two (the name and value over a full-width track), decided per row while it measures
 * (developer, 2026-11-04). One line holds when the whole name fits beside the track, the widest
 * value fits inside the track with room at its end, and the value's line fits the thick track's
 * height. A large font scale or a long name keeps the two-line row, so nothing is cut. Pure.
 */
public final class SliderRowFit {

    private SliderRowFit() {}

    /** The share of the text column the track takes on one line. */
    static final float TRACK_SHARE = 0.5f;
    /** The least track a one-line row draws. */
    static final int MIN_TRACK_DP = 128;
    /** The gap between the name and the track. */
    static final int NAME_GAP_DP = 12;

    /**
     * The track's width on one line in a text column of {@code columnPx}: half of it, at least
     * {@code minTrackPx}, never more than the column.
     */
    public static int trackPx(int columnPx, int minTrackPx) {
        int share = Math.round(columnPx * TRACK_SHARE);
        return Math.max(0, Math.min(columnPx, Math.max(minTrackPx, share)));
    }

    /** What the name has beside the track on one line. */
    public static int nameRoomPx(int columnPx, int minTrackPx, int gapPx) {
        return Math.max(0, columnPx - trackPx(columnPx, minTrackPx) - gapPx);
    }

    /**
     * Whether the row stands on one line.
     *
     * @param columnPx      the row's width after the glyph's column
     * @param minTrackPx    {@link #MIN_TRACK_DP} in pixels
     * @param gapPx         {@link #NAME_GAP_DP} in pixels
     * @param namePx        the name's natural width, its source dot included
     * @param valuePx       the widest value the track may print
     * @param valuePadPx    the room the value keeps from the track's end, and from the handle
     *                      at the start side
     * @param valueLinePx   the value's line height
     * @param trackHeightPx the thick track's height
     */
    public static boolean oneLine(int columnPx, int minTrackPx, int gapPx, int namePx,
                                  int valuePx, int valuePadPx, int valueLinePx,
                                  int trackHeightPx) {
        if (columnPx <= 0)
            return false;
        int track = trackPx(columnPx, minTrackPx);
        return namePx <= nameRoomPx(columnPx, minTrackPx, gapPx)
            && valuePx + 2 * valuePadPx <= track
            && valueLinePx <= trackHeightPx;
    }

    /**
     * Whether a readout is a note (a disabled row's "Off while ..."), not a number: it has no
     * digit. A note is measured as it stands and drawn in the row's text face; the numeric
     * readouts keep the widest-number width so a drag never lays the row out again.
     */
    public static boolean isNote(CharSequence readout) {
        if (readout == null)
            return false;
        for (int i = 0; i < readout.length(); i++) {
            if (Character.isDigit(readout.charAt(i)))
                return false;
        }
        return readout.length() > 0;
    }

    /** The width the one-line fit gives the value: a note's own, else the widest number's. */
    public static int fitValuePx(boolean note, int widestNumberPx, int textPx) {
        return note ? textPx : widestNumberPx;
    }

    /**
     * The width the two-line row's value takes: the widest number's, or a note's own, at most
     * the track's share of the column so the name keeps the rest (the note ellipsizes).
     */
    public static int twoLineValuePx(boolean note, int widestNumberPx, int textPx,
                                     int columnPx) {
        if (!note)
            return widestNumberPx;
        return Math.max(0, Math.min(textPx, Math.round(columnPx * TRACK_SHARE)));
    }
}
