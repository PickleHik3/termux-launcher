package com.termux.app.wall.pageswitch;

import android.graphics.Typeface;

/**
 * Where and how the page number is drawn inside the Home and Terminal glyphs, in the glyphs'
 * 24-unit viewport. The number is the glyph's hero: it fills almost the whole inner height of the
 * frame. The one place these live: dropping in the final art means replacing the four
 * {@code ic_page_*.xml} files and these numbers.
 *
 * <p>One size for every number: the text is scaled once so the widest of "1".."99" fits the slot's
 * width and its digits stand {@link #NUMBER_FILL} of the slot's height, whichever is smaller.
 */
public final class PageGlyphSlots {

    public static final float VIEWPORT = 24f;

    /** The digits' typeface family and style (a Roboto medium by default). */
    public static final String DIGIT_FAMILY = "sans-serif-medium";
    public static final int DIGIT_STYLE = Typeface.NORMAL;
    /** How much of the slot's height the digits stand. */
    public static final float NUMBER_FILL = 0.9f;

    /** Placeholder house: inner frame x 8-16, y 10.5-18.5. */
    public static final float HOME_SLOT_CX = 12f;
    public static final float HOME_SLOT_CY = 14.5f;
    public static final float HOME_SLOT_W = 8f;
    public static final float HOME_SLOT_H = 8f;

    /** Placeholder terminal: inner frame x 7-17, y 8-16. */
    public static final float TERMINAL_SLOT_CX = 12f;
    public static final float TERMINAL_SLOT_CY = 12f;
    public static final float TERMINAL_SLOT_W = 10f;
    public static final float TERMINAL_SLOT_H = 8f;

    private PageGlyphSlots() {}
}
