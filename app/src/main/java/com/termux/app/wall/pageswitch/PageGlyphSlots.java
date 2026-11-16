package com.termux.app.wall.pageswitch;

/**
 * Where the number sits inside the Home and Terminal glyphs, in the glyphs' 24-unit viewport:
 * the centre and height of each glyph's cut-out slot. The one place these live: dropping in the
 * final art means replacing the four {@code ic_page_*.xml} files and these numbers.
 */
public final class PageGlyphSlots {

    public static final float VIEWPORT = 24f;

    /** Placeholder house: slot x 8-16, y 10.5-18.5. */
    public static final float HOME_SLOT_CX = 12f;
    public static final float HOME_SLOT_CY = 14.5f;
    public static final float HOME_SLOT_H = 8f;
    public static final float HOME_SLOT_W = 8f;

    /** Placeholder terminal: slot x 7-17, y 8-16. */
    public static final float TERMINAL_SLOT_CX = 12f;
    public static final float TERMINAL_SLOT_CY = 12f;
    public static final float TERMINAL_SLOT_H = 8f;
    public static final float TERMINAL_SLOT_W = 10f;

    private PageGlyphSlots() {}
}
