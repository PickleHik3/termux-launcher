package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The text's top inset (Full screen's camera band, FullScreenCornerPolicy), with pong's numbers:
 * a 126 px safe top over a pane that reaches the screen's top edge.
 */
public class TerminalViewTextTopInsetTest {

    // A pane 2000 px tall, rows 50 px apart with 12 px of ascent allowance.
    private static final int HEIGHT = 2000;
    private static final int ASCENT = 12;
    private static final int LINE = 50;

    @Test public void noInsetIsTheOldLayout() {
        int rows = TerminalView.rowsFor(HEIGHT, 0, ASCENT, LINE);
        assertEquals((HEIGHT - ASCENT) / LINE, rows);
        int content = rows * LINE + ASCENT;
        assertEquals(TerminalView.centredSlackPx(HEIGHT, content),
            TerminalView.verticalContentOffsetPx(HEIGHT, 0, content));
    }

    @Test public void theCameraBandCostsTheRowsItCovers() {
        // 1988 / 50 = 39 rows without; 1862 / 50 = 37 with the 126 px band kept clear.
        assertEquals(39, TerminalView.rowsFor(HEIGHT, 0, ASCENT, LINE));
        assertEquals(37, TerminalView.rowsFor(HEIGHT, 126, ASCENT, LINE));
    }

    @Test public void theGridStartsBelowTheBandAndCentresInWhatIsLeft() {
        int rows = TerminalView.rowsFor(HEIGHT, 126, ASCENT, LINE);
        int content = rows * LINE + ASCENT; // 1862
        // 1874 px below the band, 12 left over: 6 above the grid, so the grid starts at 132.
        assertEquals(132, TerminalView.verticalContentOffsetPx(HEIGHT, 126, content));
        // The first row's cells start a further ascent down, clear of the camera's 126 px.
        assertEquals(144f, TerminalView.rowTop(132f, ASCENT, LINE, 0), 0f);
    }

    @Test public void aShortPaneKeepsItsFourRows() {
        assertEquals(4, TerminalView.rowsFor(200, 126, ASCENT, LINE));
        // A grid taller than what is left starts right at the band, never above it.
        assertEquals(126, TerminalView.verticalContentOffsetPx(200, 126, 4 * LINE + ASCENT));
    }

    @Test public void aNegativeInsetIsNone() {
        assertEquals(TerminalView.rowsFor(HEIGHT, 0, ASCENT, LINE),
            TerminalView.rowsFor(HEIGHT, -5, ASCENT, LINE));
    }
}
