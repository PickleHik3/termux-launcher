package com.termux.view.textselection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TextSizeFixtures;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Test;

/**
 * D5: a text sizing block is one selection unit. An end of the selection that lands anywhere
 * inside a block is pushed out to that block's corner, so the highlight covers all of it and the
 * handles sit where its edges are.
 */
public class TextBlockSelectionTest {

    private static final int COLUMNS = 20;
    private static final int SCREEN_ROWS = 10;

    private TerminalBuffer mScreen;

    @Before
    public void setUp() {
        mScreen = new TerminalBuffer(COLUMNS, SCREEN_ROWS * 2, SCREEN_ROWS);
        // A block two rows tall and four columns wide, anchored at row 2, column 4.
        mScreen.writeTextBlock(4, 2, "Hi", TextSizeFixtures.record(2, 2), 0L,
            TextStyle.DECORATION_COLOR_DEFAULT, 0);
    }

    /** {@code x1, y1, x2, y2}. */
    private static int[] selection(int x1, int y1, int x2, int y2) {
        return new int[] { x1, y1, x2, y2 };
    }

    @Test
    public void aSelectionThatMissesTheBlockIsLeftAlone() {
        int[] selection = selection(0, 0, 2, 1);
        assertFalse(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(0, 0, 2, 1), selection);
    }

    @Test
    public void aStartInsideTheBlockGoesToItsTopLeft() {
        int[] selection = selection(6, 3, 12, 4);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 12, 4), selection);
    }

    @Test
    public void anEndInsideTheBlockGoesToItsBottomRight() {
        int[] selection = selection(0, 0, 5, 2);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(0, 0, 7, 3), selection);
    }

    @Test
    public void aSelectionInsideTheBlockBecomesTheWholeBlock() {
        int[] selection = selection(5, 2, 6, 3);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 7, 3), selection);
    }

    @Test
    public void snappingIsIdempotent() {
        int[] selection = selection(5, 3, 6, 2);
        TextBlockSelection.snap(mScreen, selection);
        int[] once = selection.clone();
        assertFalse(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(once, selection);
    }

    @Test
    public void aSelectionTouchingTheBlocksCornerAlreadyCoversIt() {
        int[] selection = selection(4, 2, 7, 3);
        assertFalse(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 7, 3), selection);
    }
}
