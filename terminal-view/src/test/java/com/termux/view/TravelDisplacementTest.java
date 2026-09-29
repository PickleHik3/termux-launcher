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
    public void theRowsARevealDrawsAreTheOnesTheTranscriptHas() {
        // Five rows revealed over deep history: all five are drawn.
        assertEquals(5, TerminalView.travelFillRows(5, 0, 40));
        // Three lines of history: the other two are the blank rows the resize adds too.
        assertEquals(3, TerminalView.travelFillRows(5, 0, 3));
        // Scrolled back to two lines from the top, only those two are left above.
        assertEquals(2, TerminalView.travelFillRows(5, -38, 40));
        assertEquals(0, TerminalView.travelFillRows(5, -40, 40));
        assertEquals(0, TerminalView.travelFillRows(5, 0, 0));
        // A shrink, or a screen that does not move, reveals nothing.
        assertEquals(0, TerminalView.travelFillRows(0, 0, 40));
        assertEquals(0, TerminalView.travelFillRows(-4, 0, 40));
    }

    @Test
    public void theRevealedRowsComeOutFromUnderTheGridsMovingTopEdge() {
        int from = 1050, to = 1900;
        float anchor = headroomFor(from);
        float headroomChange = TerminalView.travelHeadroomChangePx(from, to, rowsFor(from),
            rowsFor(to), LINE, ASCENT);
        assertEquals(headroomFor(to) - headroomFor(from), headroomChange, EPSILON);
        // At the start the cut is the grid's top edge as it stands: nothing above row 0 shows.
        assertEquals(anchor + ASCENT, TerminalView.travelFillClipTopPx(anchor, headroomChange,
            0f, ASCENT), EPSILON);
        // On landing it is exactly where the resized grid's first row begins.
        assertEquals(headroomFor(to) + ASCENT, TerminalView.travelFillClipTopPx(anchor,
            headroomChange, 1f, ASCENT), EPSILON);
    }

    @Test
    public void aRevealedRowLandsWhereTheResizePutsIt() {
        int from = 1050, to = 1900;
        int fromRows = rowsFor(from), toRows = rowsFor(to);
        int shift = toRows - fromRows;
        float target = TerminalView.travelDisplacementPx(from, to, fromRows, toRows, shift, LINE,
            ASCENT);
        for (int row = -shift; row < fromRows; row++) {
            // Drawn at the end of the travel, from the anchor, displaced the whole way ...
            float drawn = headroomFor(from) + target + ASCENT + row * LINE;
            // ... and laid by the resize as row + shift of the taller grid.
            float laid = headroomFor(to) + ASCENT + (row + shift) * LINE;
            assertEquals("row " + row, laid, drawn, EPSILON);
        }
        // And the first revealed row sits right on the cut, so it is whole when the resize lands.
        assertEquals(TerminalView.travelFillClipTopPx(headroomFor(from),
                TerminalView.travelHeadroomChangePx(from, to, fromRows, toRows, LINE, ASCENT), 1f,
                ASCENT),
            headroomFor(from) + target + ASCENT - shift * LINE, EPSILON);
    }

    @Test
    public void aGridThatOverflowsItsViewStartsAtTheEdge() {
        assertEquals(0, TerminalView.centredSlackPx(100, 160));
        assertEquals(0, TerminalView.centredSlackPx(0, 0));
        assertEquals(3, TerminalView.centredSlackPx(167, 160));
        assertEquals(3, TerminalView.centredSlackPx(166, 160));
    }
}
