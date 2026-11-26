package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WatchOnlyTerminalTest {
    @Test
    public void textFillsTheColumnsWhenInsideTheBounds() {
        // 640 px across 64 columns of 0.6-wide cells: 640 / (64 * 0.6) = 16.67 -> 16.
        assertEquals(16, WatchOnlyTerminal.fitTextSize(640, 64, 0.6f, 10f, 40f));
    }

    @Test
    public void textIsKeptBetweenTheBounds() {
        assertEquals(30, WatchOnlyTerminal.fitTextSize(10000, 64, 0.6f, 10f, 30f));
        assertEquals(10, WatchOnlyTerminal.fitTextSize(100, 64, 0.6f, 10f, 30f));
    }

    @Test
    public void textIsLargestBeforeThereIsAWidth() {
        assertEquals(30, WatchOnlyTerminal.fitTextSize(0, 64, 0.6f, 10f, 30f));
        assertEquals(30, WatchOnlyTerminal.fitTextSize(-5, 64, 0.6f, 10f, 30f));
    }

    @Test
    public void transcriptIsBounded() {
        assertEquals(WatchOnlyTerminal.MIN_TRANSCRIPT_ROWS, WatchOnlyTerminal.boundedRows(0));
        assertEquals(WatchOnlyTerminal.MAX_TRANSCRIPT_ROWS, WatchOnlyTerminal.boundedRows(1_000_000));
        assertEquals(600, WatchOnlyTerminal.boundedRows(600));
        assertEquals(WatchOnlyTerminal.TRANSCRIPT_ROWS,
            WatchOnlyTerminal.boundedRows(WatchOnlyTerminal.TRANSCRIPT_ROWS));
    }
}
