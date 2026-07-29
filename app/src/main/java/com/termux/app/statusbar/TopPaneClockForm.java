package com.termux.app.statusbar;

/**
 * How much of the widget slot the clock is allowed to claim. All four faces implement every form
 * on the same grid, so the slot can compress content without changing the pane height.
 */
public enum TopPaneClockForm {
    /** Time band, meta column and date row: the idle state. */
    FULL,
    /** One row at the gutter, used whenever the slot is shared. */
    COMPACT,
    /** Single monospace row, the most compressed form. */
    MONO_CHIP
}
