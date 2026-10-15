package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextSizeFixtures;

import org.junit.Before;
import org.junit.Test;

/**
 * A scroll keeps the recordings of the rows still on screen: only the rows that came into view are
 * recorded. Here the screen is a window onto a longer transcript, so a row keeps its own line
 * object as it moves, exactly as it does on a real scroll.
 */
public class RowRenderCacheShiftTest {

    private static final int COLUMNS = 8;
    private static final int ROWS = 5;
    private static final int TRANSCRIPT = 20;
    private static final int CURSOR_COLOR = 0xFF00FF00;

    private final Object mEmulator = new Object();

    private RowRenderCache mCache;
    private TerminalRow[] mTranscript;
    private int[] mPalette;

    @Before
    public void setUp() {
        mCache = new RowRenderCache();
        mTranscript = new TerminalRow[TRANSCRIPT];
        for (int i = 0; i < TRANSCRIPT; i++) {
            mTranscript[i] = new TerminalRow(COLUMNS, 0L);
            mTranscript[i].setChar(0, 'a' + i, 0L);
        }
        mPalette = new int[260];
    }

    /** One frame showing the transcript from {@code top}; returns which rows were recorded. */
    private boolean[] frameAt(int top) {
        mCache.beginFrame(mEmulator, ROWS, COLUMNS, top, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        boolean[] recorded = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) {
            recorded[i] = mCache.rowChanged(i, mTranscript[top + i], COLUMNS, -1, 1, true, 0,
                CURSOR_COLOR, -1, -1, 0);
        }
        mCache.spreadTextBlockGroups(recorded);
        return recorded;
    }

    private static void assertRecorded(boolean[] recorded, int... expected) {
        boolean[] want = new boolean[recorded.length];
        for (int row : expected) want[row] = true;
        for (int i = 0; i < recorded.length; i++)
            assertEquals("row " + i, want[i], recorded[i]);
    }

    @Test
    public void scrollingDownOneRowRecordsOnlyTheRowThatCameIn() {
        frameAt(4);
        assertRecorded(frameAt(5), ROWS - 1);
        assertEquals(1, mCache.lastShift());
        assertRecorded(frameAt(5));
        assertEquals(0, mCache.lastShift());
    }

    @Test
    public void scrollingUpTwoRowsRecordsOnlyTheTwoRowsThatCameIn() {
        frameAt(6);
        assertRecorded(frameAt(4), 0, 1);
        assertEquals(-2, mCache.lastShift());
    }

    @Test
    public void aRowChangedDuringTheScrollIsStillRecorded() {
        frameAt(4);
        mTranscript[6].setChar(1, 'z', 0L);
        // Row 6 now sits at index 1; the row that came in is at the bottom.
        assertRecorded(frameAt(5), 1, ROWS - 1);
    }

    @Test
    public void aScrollByAWholeScreenRecordsEveryRow() {
        frameAt(0);
        assertRecorded(frameAt(ROWS), 0, 1, 2, 3, 4);
        assertEquals(0, mCache.lastShift());
    }

    @Test
    public void aScrollThatComesWithAGlobalChangeRecordsEveryRow() {
        frameAt(4);
        mPalette[1] = 0xFF123456;
        assertRecorded(frameAt(5), 0, 1, 2, 3, 4);
        assertEquals(0, mCache.lastShift());
    }

    @Test
    public void aScrollWhereTheLinesStayedPutRecordsEveryRow() {
        // Output arriving while scrolled back moves the top row but not the lines on screen.
        frameAt(4);
        mCache.beginFrame(mEmulator, ROWS, COLUMNS, 5, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        for (int i = 0; i < ROWS; i++) {
            assertTrue(mCache.rowChanged(i, mTranscript[4 + i], COLUMNS, -1, 1, true, 0,
                CURSOR_COLOR, -1, -1, 0));
        }
    }

    @Test
    public void rowsUnderATextBlockAreRecordedAgainAfterAShift() {
        // A two-row block anchored on transcript row 6; which visible row is first decides who
        // draws a block anchored above the screen, so the group re-records on every shift.
        TextSizeFixtures.markBlock(mTranscript, 6, 0, TextSizeFixtures.record(2, 1));
        frameAt(4);
        assertRecorded(frameAt(4));
        // Rows 6 and 7 are now at indices 1 and 2.
        assertRecorded(frameAt(5), 1, 2, ROWS - 1);
    }

    @Test
    public void shiftedSourceFollowsTheRows() {
        assertEquals(1, RowRenderCache.shiftedSource(0, 1, ROWS));
        assertEquals(-1, RowRenderCache.shiftedSource(ROWS - 1, 1, ROWS));
        assertEquals(-1, RowRenderCache.shiftedSource(0, -1, ROWS));
        assertEquals(0, RowRenderCache.shiftedSource(1, -1, ROWS));
    }

    @Test
    public void survivesShiftIsTheInverseOfShiftedSource() {
        for (int shift = -ROWS + 1; shift < ROWS; shift++) {
            int survivors = 0;
            for (int i = 0; i < ROWS; i++) {
                int source = RowRenderCache.shiftedSource(i, shift, ROWS);
                if (source >= 0) {
                    assertTrue(RowRenderCache.survivesShift(source, shift, ROWS));
                    survivors++;
                }
            }
            int surviving = 0;
            for (int j = 0; j < ROWS; j++)
                if (RowRenderCache.survivesShift(j, shift, ROWS)) surviving++;
            assertEquals(survivors, surviving);
            assertFalse(survivors + Math.abs(shift) != ROWS);
        }
    }
}
