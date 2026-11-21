package com.termux.terminal;

/**
 * Rows that left the top of the screen, counted once per emulator and shared by both of its
 * buffers, so the alternate screen's scrolls reach the view the way they always have.
 */
final class ScrollCount {

    /** Rows scrolled since the view last took them ({@link TerminalContent#takeScrolledRows()}). */
    int pending;

    /** Rows ever scrolled, never reset; the absolute index of screen row 0. */
    long total;

    void add(int rows) {
        pending += rows;
        total += rows;
    }

    int take() {
        int rows = pending;
        pending = 0;
        return rows;
    }
}
