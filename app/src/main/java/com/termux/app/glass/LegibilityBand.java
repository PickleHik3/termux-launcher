package com.termux.app.glass;

/**
 * The bands that keep a legibility correction per page (ADR 0029 section 9). The dock and the
 * keyboard are the bands whose legends cross the seam; the strip under the pill and the panes are
 * named so their corrections have a place in the store. The status bar and window bar keep none
 * in this restructure.
 */
public enum LegibilityBand {
    DOCK,
    KEYBOARD,
    STRIP,
    PANES;

    /** Held once: {@code values()} copies its array on every call. */
    static final LegibilityBand[] ALL = values();
}
