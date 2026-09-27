package com.termux.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Where the pane wall's slide draws the terminal's rows while the grid waits for the settle's
 * resize ({@link TerminalView#travelDisplacementPx}): the change in the headroom of a bottom-aligned
 * grid plus the rows the buffer will shift the screen by.
 */
public class TravelDisplacementTest {

    private static final float EPSILON = 1e-4f;
    private static final int LINE = 40;
    private static final int ASCENT = 8;

    /** A grid that fits {@code heightPx} the way TerminalView.updateSize sizes it. */
    private static int rowsFor(int heightPx) {
        return Math.max(4, (heightPx - ASCENT) / LINE);
    }

    @Test
    public void aPromptAtTheBottomRisesByExactlyTheKeyboardsHeight() {
        // The keyboard takes 850 px: 21 rows of 40 and 10 px of headroom. With no blank rows
        // under the cursor the buffer scrolls all 21 away, and the rest lands 850 px higher.
        int from = 1900, to = 1050;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        assertEquals(-850f, TerminalView.travelDisplacementPx(from, to, fromRows, toRows,
            toRows - fromRows, LINE, ASCENT, false), EPSILON);
    }

    @Test
    public void blankRowsUnderTheCursorAbsorbPartOfTheShrink() {
        int from = 1900, to = 1050;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        int dropped = fromRows - toRows;
        // Five blank rows go first, so the rows move up by five rows less than the keyboard.
        int shift = -(dropped - 5);
        assertEquals(-850f + 5 * LINE, TerminalView.travelDisplacementPx(from, to, fromRows,
            toRows, shift, LINE, ASCENT, false), EPSILON);
    }

    @Test
    public void aBottomAnchoredGrowthMovesTheRowsDownWithTheEdge() {
        int from = 1050, to = 1900;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        // The anchor reveals exactly the added rows above, so the prompt follows the edge down.
        assertEquals(850f, TerminalView.travelDisplacementPx(from, to, fromRows, toRows,
            toRows - fromRows, LINE, ASCENT, false), EPSILON);
    }

    @Test
    public void aGrowthWithNoTranscriptLeavesTheRowsWhereTheyAre() {
        int from = 1050, to = 1900;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        // Nothing above to reveal: blank rows are added below, and only the headroom's change -
        // the sub-row remainder - moves the rows.
        float headroomFrom = from - (fromRows * LINE + ASCENT);
        float headroomTo = to - (toRows * LINE + ASCENT);
        assertEquals(headroomTo - headroomFrom, TerminalView.travelDisplacementPx(from, to,
            fromRows, toRows, 0, LINE, ASCENT, false), EPSILON);
    }

    @Test
    public void theAlternateScreenIsTopAlignedSoOnlyTheBufferShiftCounts() {
        assertEquals(-3f * LINE, TerminalView.travelDisplacementPx(1900, 1050, rowsFor(1900),
            rowsFor(1050), -3, LINE, ASCENT, true), EPSILON);
        assertEquals(0f, TerminalView.travelDisplacementPx(1050, 1900, rowsFor(1050),
            rowsFor(1900), 0, LINE, ASCENT, true), EPSILON);
    }

    @Test
    public void theSameHeightMovesNothing() {
        assertEquals(0f, TerminalView.travelDisplacementPx(1900, 1900, rowsFor(1900),
            rowsFor(1900), 0, LINE, ASCENT, false), EPSILON);
    }
}
