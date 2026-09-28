package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Where a row is painted, for whatever tracks the cursor (the trail). */
public class TerminalViewRowTopTest {

    @Test public void aRowStartsAtTheDrawOffsetPlusTheAscentSlack() {
        // A grid anchored 30px down, rows 40px apart with 8px of ascent slack: row 2 starts at
        // 30 + 8 + 80, not at the 80 that row * height alone gives.
        assertEquals(118f, TerminalView.rowTop(30f, 8, 40, 2), 0f);
    }

    @Test public void aSmoothScrollOrATravelMovesEveryRowWithIt() {
        assertEquals(-12f + 8 + 40, TerminalView.rowTop(-12f, 8, 40, 1), 0f);
        assertEquals(8f, TerminalView.rowTop(0f, 8, 40, 0), 0f);
    }
}
