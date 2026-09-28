package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where the pane wall's slide draws the terminal's rows while the grid waits for the settle's
 * resize ({@link TerminalView#travelDisplacementPx}): the change in the slack above a centred
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

    /** The slack above the grid, the way TerminalView.getVerticalContentOffset centres it. */
    private static int headroomFor(int heightPx) {
        return TerminalView.centredSlackPx(heightPx, rowsFor(heightPx) * LINE + ASCENT);
    }

    @Test
    public void aPromptAtTheBottomRisesByTheKeyboardsHeightLessTheSlackItGivesUp() {
        // The keyboard takes 850 px: 21 rows of 40 and 10 px of leftover. With no blank rows
        // under the cursor the buffer scrolls all 21 away; the rest lands 840 px higher, plus
        // whatever the halved leftover above the grid moves by (6 px down to 1 px).
        int from = 1900, to = 1050;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        assertEquals(6, headroomFor(from));
        assertEquals(1, headroomFor(to));
        assertEquals(-21 * LINE - 5f, TerminalView.travelDisplacementPx(from, to, fromRows,
            toRows, toRows - fromRows, LINE, ASCENT), EPSILON);
    }

    @Test
    public void blankRowsUnderTheCursorAbsorbPartOfTheShrink() {
        int from = 1900, to = 1050;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        int dropped = fromRows - toRows;
        // Five blank rows go first, so the rows move up by five rows less than the keyboard.
        int shift = -(dropped - 5);
        assertEquals(-21 * LINE - 5f + 5 * LINE, TerminalView.travelDisplacementPx(from, to,
            fromRows, toRows, shift, LINE, ASCENT), EPSILON);
    }

    @Test
    public void aGrowthThatRevealsTranscriptMovesTheRowsDownWithTheEdge() {
        int from = 1050, to = 1900;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        // The buffer reveals exactly the added rows above, so the prompt follows the edge down,
        // and the slack above the grid grows from 1 px to 6 px on top of that.
        assertEquals(21 * LINE + 5f, TerminalView.travelDisplacementPx(from, to, fromRows,
            toRows, toRows - fromRows, LINE, ASCENT), EPSILON);
    }

    @Test
    public void aGrowthWithNoTranscriptMovesOnlyTheCentringSlack() {
        int from = 1050, to = 1900;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        // Nothing above to reveal: blank rows are added below, and only the change in the slack
        // above the grid - half the sub-row remainder - moves the rows.
        assertEquals(headroomFor(to) - headroomFor(from), TerminalView.travelDisplacementPx(from,
            to, fromRows, toRows, 0, LINE, ASCENT), EPSILON);
    }

    @Test
    public void theSlackIsNeverMoreThanHalfARow() {
        for (int height = 200; height < 3000; height += 7) {
            int headroom = headroomFor(height);
            assertTrue("height " + height + " leaves " + headroom + " px above the grid",
                headroom >= 0 && headroom <= LINE / 2);
            assertEquals("the far edge takes the odd pixel", (height - rowsFor(height) * LINE
                - ASCENT) / 2, headroom);
        }
    }

    @Test
    public void theSameHeightMovesNothing() {
        assertEquals(0f, TerminalView.travelDisplacementPx(1900, 1900, rowsFor(1900),
            rowsFor(1900), 0, LINE, ASCENT), EPSILON);
    }

    @Test
    public void aGridThatOverflowsItsViewStartsAtTheEdge() {
        assertEquals(0, TerminalView.centredSlackPx(100, 160));
        assertEquals(0, TerminalView.centredSlackPx(0, 0));
        assertEquals(3, TerminalView.centredSlackPx(167, 160));
        assertEquals(3, TerminalView.centredSlackPx(166, 160));
    }
}
