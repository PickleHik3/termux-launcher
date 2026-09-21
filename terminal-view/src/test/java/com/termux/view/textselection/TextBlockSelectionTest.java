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
 * D5: a text sizing block is one selection unit, and the selection that says so lives on the
 * block's anchor row only.
 *
 * <p>The rule the first cut got wrong is the one most of these cases guard: reaching down to a
 * block's bottom right makes a two-row selection, and a two-row selection is a stream — its first
 * row runs from its column to the end of the row — so a press on one block highlighted the whole
 * row and copied every block on it. The rows a block covers underneath are the renderer's job.
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

    /** A second block, two rows tall and two columns wide, anchored at row 5, column 10. */
    private void secondBlock() {
        mScreen.writeTextBlock(10, 5, "Yo", TextSizeFixtures.record(2, 1), 0L,
            TextStyle.DECORATION_COLOR_DEFAULT, 0);
    }

    /** {@code x1, y1, x2, y2}. */
    private static int[] selection(int x1, int y1, int x2, int y2) {
        return new int[] { x1, y1, x2, y2 };
    }

    @Test
    public void aSelectionThatMissesEveryBlockIsLeftAlone() {
        int[] selection = selection(0, 0, 2, 1);
        assertFalse(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(0, 0, 2, 1), selection);
    }

    @Test
    public void aPressInsideTheBlockSelectsItsRunOnTheAnchorRowOnly() {
        int[] selection = selection(5, 2, 5, 2);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 7, 2), selection);
    }

    @Test
    public void aPressOnAContinuationCellComesBackUpToTheAnchorRow() {
        // The row below the anchor is the block's too, and it must not become a second row of
        // selection: that is what painted a band across the whole width.
        int[] selection = selection(6, 3, 6, 3);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 7, 2), selection);
    }

    @Test
    public void aStartInsideTheBlockGoesToItsFirstColumnOnTheAnchorRow() {
        int[] selection = selection(6, 3, 12, 4);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 12, 4), selection);
    }

    @Test
    public void anEndInsideTheBlockGoesToItsLastColumnOnTheAnchorRow() {
        int[] selection = selection(0, 0, 5, 2);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(0, 0, 7, 2), selection);
    }

    @Test
    public void anEndOnAContinuationCellDoesNotAddARow() {
        int[] selection = selection(0, 0, 5, 3);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(0, 0, 7, 2), selection);
    }

    @Test
    public void aSelectionFromOneBlockToAnotherKeepsItsRows() {
        secondBlock();
        int[] selection = selection(6, 3, 11, 6);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        // Start at the first block's first column on row 2, end at the second block's last
        // column on row 5. Everything between them is an ordinary stream selection.
        assertArrayEquals(selection(4, 2, 11, 5), selection);
    }

    @Test
    public void anEndPulledBackBeforeTheStartStillCoversBoth() {
        // The end sits on the block's lower row, to the left of a start that is plain text further
        // along the anchor row: pulling it up must not leave the pair inside out.
        int[] selection = selection(9, 2, 5, 3);
        assertTrue(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 9, 2), selection);
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
    public void aSelectionAlreadyOnTheBlocksRunIsLeftAlone() {
        int[] selection = selection(4, 2, 7, 2);
        assertFalse(TextBlockSelection.snap(mScreen, selection));
        assertArrayEquals(selection(4, 2, 7, 2), selection);
    }
}
