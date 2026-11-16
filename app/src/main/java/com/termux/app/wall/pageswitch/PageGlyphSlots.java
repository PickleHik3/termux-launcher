package com.termux.app.wall.pageswitch;

/**
 * Where and how the page number is drawn inside the Home and Terminal glyphs, in the glyphs'
 * 24-unit viewport. The number is the glyph's hero. The one place these live: new art means
 * replacing the four {@code ic_page_*.xml} files and these numbers.
 *
 * <p>Each slot is the numeral's visible ink box: its centre, its width and its cap height (the
 * height of the digits' ink, not the text size). Every number keeps the cap height; one wider
 * than the slot is condensed horizontally to fit ({@link #MIN_CONDENSE} for three characters and
 * more, then shrunk evenly).
 */
public final class PageGlyphSlots {

    public static final float VIEWPORT = 24f;

    /** The digits' typeface: Roboto Medium (weight 500). */
    public static final String DIGIT_FAMILY = "sans-serif-medium";
    public static final int DIGIT_WEIGHT = 500;
    /** The most a number of three characters or more is condensed before it is shrunk instead. */
    public static final float MIN_CONDENSE = 0.8f;

    public static final float HOME_SLOT_CX = 12f;
    public static final float HOME_SLOT_CY = 14.5f;
    public static final float HOME_SLOT_W = 12f;
    public static final float HOME_SLOT_H = 11f;

    public static final float TERMINAL_SLOT_CX = 12f;
    public static final float TERMINAL_SLOT_CY = 13.5f;
    public static final float TERMINAL_SLOT_W = 12f;
    public static final float TERMINAL_SLOT_H = 12f;

    private PageGlyphSlots() {}
}
