package com.termux.view;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * A top or bottom edge row runs on into the padding only when every cell in it has a background
 * of its own; a row of pills on the default background leaves the padding plain.
 */
public class EdgeFillWholeRowTest {

    private static final int PILL = 0xff5f5fff;
    private static final int BAR = 0xff303030;

    @Test
    public void aRowAllInColourKeepsItsColours() {
        int[] colors = {BAR, BAR, PILL, BAR};
        TerminalView.keepOnlyWholeRow(colors, 4);
        assertArrayEquals(new int[] {BAR, BAR, PILL, BAR}, colors);
    }

    @Test
    public void pillsOnTheDefaultBackgroundLeaveTheRowPlain() {
        // A cap on the default background, the pill's cells, the other cap, then empty cells.
        int[] colors = {0, PILL, PILL, PILL, 0, 0};
        TerminalView.keepOnlyWholeRow(colors, 6);
        assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0}, colors);
    }

    @Test
    public void onlyTheRowsOwnColumnsAreRead() {
        // The array is grown to the widest screen seen; cells past count belong to no row.
        int[] colors = {BAR, BAR, BAR, 0};
        TerminalView.keepOnlyWholeRow(colors, 3);
        assertArrayEquals(new int[] {BAR, BAR, BAR, 0}, colors);
    }
}
