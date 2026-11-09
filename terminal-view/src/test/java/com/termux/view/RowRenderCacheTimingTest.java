package com.termux.view;

import static org.junit.Assert.assertEquals;

import com.termux.terminal.StyleFixtures;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import org.junit.Test;

import java.util.Arrays;

/**
 * What {@code RowRenderCache.rowChanged} costs per frame on a phone-sized screen (52 x 45, pong's
 * full-screen pane), with no row, one row or every row changed since the last frame. A host
 * measurement only: the numbers are printed (see the test report's standard output), and the test
 * asserts just that each case reported the rows it changed.
 */
public class RowRenderCacheTimingTest {

    private static final int COLUMNS = 52;
    private static final int ROWS = 45;
    private static final int WARMUP_FRAMES = 4000;
    private static final int TIMED_FRAMES = 4000;

    private final Object mEmulator = new Object();
    private final int[] mPalette = new int[260];

    private static TerminalRow[] screen() {
        TerminalRow[] rows = new TerminalRow[ROWS];
        long bold = StyleFixtures.style(3, 4, TextStyle.CHARACTER_ATTRIBUTE_BOLD);
        for (int y = 0; y < ROWS; y++) {
            rows[y] = new TerminalRow(COLUMNS, 0L);
            for (int x = 0; x < COLUMNS; x++)
                rows[y].setChar(x, 'a' + (x + y) % 26, (x / 8) % 2 == 0 ? 0L : bold);
        }
        return rows;
    }

    /** Median nanoseconds of the compare for one frame, with {@code changedRows} rows rewritten before each. */
    private long measure(int changedRows) {
        RowRenderCache cache = new RowRenderCache();
        TerminalRow[] rows = screen();
        long[] samples = new long[TIMED_FRAMES];
        int recordedTotal = 0;
        for (int frame = 0; frame < WARMUP_FRAMES + TIMED_FRAMES; frame++) {
            // Each changed row gets one new character, in a column that moves every frame. The rows
            // hold one cell per char, so the cell's character is mText at its column.
            for (int y = 0; y < changedRows; y++) {
                TerminalRow row = rows[(frame + y) % ROWS];
                int column = (frame + y) % COLUMNS;
                row.setChar(column, row.mText[column] == 'X' ? 'Y' : 'X', 0L);
            }
            long start = System.nanoTime();
            cache.beginFrame(mEmulator, ROWS, COLUMNS, 0, 0, 0f, false, 0, false, false,
                mPalette, 1080, 2200, ROWS);
            int recorded = 0;
            for (int y = 0; y < ROWS; y++) {
                if (cache.rowChanged(y, rows[y], COLUMNS, -1, 1, true, 0, 0, -1, -1, 0))
                    recorded++;
            }
            long elapsed = System.nanoTime() - start;
            if (frame >= WARMUP_FRAMES) {
                samples[frame - WARMUP_FRAMES] = elapsed;
                recordedTotal += recorded;
            }
        }
        assertEquals("rows reported changed", (long) changedRows * TIMED_FRAMES, recordedTotal);
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    @Test
    public void rowChangedCostPerFrame() {
        long none = measure(0);
        long one = measure(1);
        long all = measure(ROWS);
        System.out.println("RowRenderCache.rowChanged per 52x45 frame (median ns): no row changed "
            + none + ", one row " + one + ", every row " + all);
    }
}
