package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Scrolling recycles the oldest history row. A sixel bitmap whose last row leaves that way is
 * dropped; one that still has a row in the buffer is kept; and a row without bitmap cells — every
 * row of ordinary output — leaves without any bitmap bookkeeping at all.
 */
public class ScrolledOutBitmapTest {

    private static final int COLUMNS = 4;
    private static final int TOTAL_ROWS = 5;
    private static final int SCREEN_ROWS = 3;

    private final TerminalBuffer mBuffer = new TerminalBuffer(COLUMNS, TOTAL_ROWS, SCREEN_ROWS);

    private void placeBitmapCell(int bitmap, int row) {
        mBuffer.setChar(0, row, ' ', TextStyle.encodeBitmap(bitmap, 0, row));
        // The pixels are irrelevant here; only the buffer's bookkeeping of the number is.
        mBuffer.bitmaps.put(bitmap, null);
    }

    /** Scrolls until the screen's first row is the next to be recycled: the history is full. */
    private static final int UNTIL_FIRST_ROW_RECYCLED = TOTAL_ROWS - SCREEN_ROWS + 1;

    private void scroll(int lines) {
        for (int i = 0; i < lines; i++) mBuffer.scrollDownOneLine(0, SCREEN_ROWS, TextStyle.NORMAL);
    }

    @Test
    public void aBitmapWhoseLastRowIsRecycledIsDropped() {
        placeBitmapCell(7, 0);
        scroll(UNTIL_FIRST_ROW_RECYCLED);
        assertFalse(mBuffer.bitmaps.containsKey(7));
    }

    @Test
    public void aBitmapStillOnTheNextRowIsKept() {
        placeBitmapCell(7, 0);
        placeBitmapCell(7, 1);
        scroll(UNTIL_FIRST_ROW_RECYCLED);
        assertTrue("Its second row is still in the buffer", mBuffer.bitmaps.containsKey(7));
    }

    @Test
    public void rowsWithoutBitmapsScrollThroughTheRingUntouched() {
        placeBitmapCell(7, 2);
        mBuffer.setChar(0, 0, 'a', TextStyle.NORMAL);
        // Recycle the two plain rows above it only: the bitmap row is next, but not yet.
        scroll(UNTIL_FIRST_ROW_RECYCLED + 1);
        assertTrue(mBuffer.bitmaps.containsKey(7));
        assertEquals(1, mBuffer.bitmaps.size());
    }
}
