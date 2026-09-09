package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.KittyUnicodePlaceholder;
import com.termux.terminal.StyleFixtures;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Test;

/**
 * A row that did not move must be reported clean, and every input the render loop reads from a row
 * must move it. Getting the second half wrong leaves a cell frozen on screen, which is why each
 * input has its own case here rather than one broad "content changed" test.
 *
 * <p>The two answers are kept apart on purpose: {@code rowChanged} says the row's text has to be
 * shaped again, {@code rowCarriesAnImage} says only its pixels do. A row showing an animation
 * answers no to the first and yes to the second on every frame, which is what stops the animation
 * from paying for the text beside it.
 */
public class RowRenderCacheTest {

    private static final int COLUMNS = 8;
    private static final int ROWS = 3;
    private static final int CURSOR_COLOR = 0xFF00FF00;

    /** Standing in for the emulator, which this class only ever compares by identity. */
    private final Object mEmulator = new Object();

    private RowRenderCache mCache;
    private TerminalRow[] mRows;
    private int[] mPalette;
    private int mTopRow;

    @Before
    public void setUp() {
        mCache = new RowRenderCache();
        mRows = new TerminalRow[ROWS];
        for (int i = 0; i < ROWS; i++) mRows[i] = new TerminalRow(COLUMNS, 0L);
        mPalette = new int[260];
        mTopRow = 0;
    }

    /** One frame with no cursor and no selection; returns which rows had to be recorded. */
    private boolean[] frame() {
        return frame(-1, -1, -1, -1);
    }

    /**
     * One frame in which the cursor sits at {@code cursorRow}/{@code cursorColumn} and the
     * selection covers {@code selectionStart}..{@code selectionEnd} of every row it touches.
     */
    private boolean[] frame(int cursorRow, int cursorColumn, int selectionStart,
                            int selectionEnd) {
        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        boolean[] recorded = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) {
            final boolean onCursorRow = i == cursorRow;
            recorded[i] = mCache.rowChanged(i, mRows[i], COLUMNS,
                onCursorRow ? cursorColumn : -1, 0, CURSOR_COLOR,
                selectionStart, selectionEnd);
        }
        return recorded;
    }

    private static void assertOnly(int expected, boolean[] recorded) {
        for (int i = 0; i < recorded.length; i++) {
            if (i == expected) assertTrue("row " + i + " must be recorded", recorded[i]);
            else assertFalse("row " + i + " must not be recorded", recorded[i]);
        }
    }

    private static void assertNone(boolean[] recorded) {
        for (int i = 0; i < recorded.length; i++)
            assertFalse("row " + i + " must not be recorded", recorded[i]);
    }

    private static void assertAll(boolean[] recorded) {
        for (int i = 0; i < recorded.length; i++)
            assertTrue("row " + i + " must be recorded", recorded[i]);
    }

    /** Which rows the frame just run reported as carrying an image. */
    private boolean[] carriesAnImage() {
        boolean[] carries = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) carries[i] = mCache.rowCarriesAnImage(i);
        return carries;
    }

    @Test
    public void theFirstFrameRecordsEveryRowAndTheSecondRecordsNone() {
        assertAll(frame());
        assertNone(frame());
    }

    @Test
    public void oneChangedCharacterRecordsOnlyItsRow() {
        frame();
        mRows[1].setChar(3, 'x', 0L);

        assertOnly(1, frame());
        assertNone(frame());
    }

    @Test
    public void aChangedStyleRecordsOnlyItsRow() {
        frame();
        mRows[2].setChar(0, ' ', StyleFixtures.style(3, 4, TextStyle.CHARACTER_ATTRIBUTE_BOLD));

        assertOnly(2, frame());
    }

    @Test
    public void aChangedDecorationColorRecordsOnlyItsRow() {
        frame();
        mRows[0].setChar(2, 'a', 0L, 0xFF112233, 0);

        assertOnly(0, frame());
        assertNone(frame());
    }

    @Test
    public void aChangedHyperlinkIdRecordsOnlyItsRow() {
        frame();
        mRows[1].setChar(4, 'a', 0L, TextStyle.DECORATION_COLOR_DEFAULT, 7);

        assertOnly(1, frame());
        assertNone(frame());
    }

    @Test
    public void aRowSwappedForAnotherObjectIsRecordedEvenWithTheSameContent() {
        frame();
        mRows[2] = new TerminalRow(COLUMNS, 0L);

        assertOnly(2, frame());
    }

    @Test
    public void theCursorMovingOntoAndOffARowRecordsBothRowsItTouched() {
        assertAll(frame(1, 2, -1, -1));
        assertNone(frame(1, 2, -1, -1));

        // Along its own row.
        assertOnly(1, frame(1, 3, -1, -1));
        // Onto the next row: the row it left and the row it reached.
        boolean[] moved = frame(2, 3, -1, -1);
        assertTrue(moved[1]);
        assertTrue(moved[2]);
        assertFalse(moved[0]);
        assertNone(frame(2, 3, -1, -1));
    }

    @Test
    public void aCursorShapeOrColourChangeOnlyRecordsTheRowTheCursorIsOn() {
        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        for (int i = 0; i < ROWS; i++)
            mCache.rowChanged(i, mRows[i], COLUMNS, i == 1 ? 2 : -1, 0, CURSOR_COLOR, -1, -1);

        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        boolean[] recorded = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++)
            recorded[i] = mCache.rowChanged(i, mRows[i], COLUMNS, i == 1 ? 2 : -1, 1, CURSOR_COLOR,
                -1, -1);

        assertOnly(1, recorded);
    }

    @Test
    public void aSelectionThatTouchesTheRowsRecordsThemAndOnlyOnce() {
        frame();

        assertAll(frame(-1, -1, 1, 4));
        assertNone(frame(-1, -1, 1, 4));
        assertAll(frame(-1, -1, 1, 5));
        assertAll(frame());
    }

    @Test
    public void onePaletteEntryChangingRecordsEveryRow() {
        frame();
        // The palette is mutated in place, exactly as an OSC 4 from the shell does it.
        mPalette[17] = 0xFFAABBCC;

        assertAll(frame());
        assertNone(frame());
    }

    @Test
    public void scrollingRecordsEveryRow() {
        frame();
        mTopRow = -1;

        assertAll(frame());
        assertNone(frame());
    }

    @Test
    public void aRowCarryingABitmapReportsTheImageAndNotItsText() {
        mRows[1].setChar(0, 'a', TextStyle.encodeBitmap(1, 0, 0));

        assertAll(frame());
        assertOnly(1, carriesAnImage());
        // Its text settles like any other row's; only the pixels are redrawn from here on.
        assertNone(frame());
        assertOnly(1, carriesAnImage());
        assertNone(frame());
        assertOnly(1, carriesAnImage());
    }

    @Test
    public void aRowCarryingAKittyPlaceholderReportsTheImageAndNotItsText() {
        mRows[2].setChar(0, KittyUnicodePlaceholder.CODE_POINT, 0L);

        assertAll(frame());
        assertOnly(2, carriesAnImage());
        assertNone(frame());
        assertOnly(2, carriesAnImage());

        // The placeholder is ordinary text, so overwriting it moves the row once and then stops it
        // carrying an image at all.
        mRows[2].setChar(0, 'a', 0L);
        assertOnly(2, frame());
        assertNone(carriesAnImage());
        assertNone(frame());
    }

    @Test
    public void anAnimatedRowIsNotReshapedWhileARowWhoseTextMovedIs() {
        // The fastfetch case: one row holds both the logo's placeholder and the text beside it.
        mRows[0].setChar(0, KittyUnicodePlaceholder.CODE_POINT, 0L);
        mRows[0].setChar(1, 'i', 0L);
        frame();

        // Frame after frame of animation: the image is redrawn, no text is shaped again.
        assertNone(frame());
        assertOnly(0, carriesAnImage());
        assertNone(frame());
        assertOnly(0, carriesAnImage());

        // A row whose text did move is still recorded, and the placeholder row still is not.
        mRows[1].setChar(2, 'z', 0L);
        assertOnly(1, frame());
        assertOnly(0, carriesAnImage());

        // Including when the text sharing the animated row is what moved.
        mRows[0].setChar(1, 'j', 0L);
        assertOnly(0, frame());
        assertOnly(0, carriesAnImage());
    }

    @Test
    public void aRowErasedAfterCarryingABitmapStopsCarryingAnImage() {
        mRows[1].setChar(0, 'a', TextStyle.encodeBitmap(1, 0, 0));
        frame();
        assertOnly(1, carriesAnImage());

        // The row keeps its bitmap flag until it is erased — overwriting the cell does not clear
        // it — so this is the point at which its image node can be dropped.
        mRows[1].clear(0L);

        assertOnly(1, frame());
        assertNone(carriesAnImage());
        assertNone(frame());
    }

    @Test
    public void anExplicitInvalidateRecordsEveryRowOnce() {
        frame();
        assertNone(frame());

        mCache.invalidate();

        assertAll(frame());
        assertNone(frame());
    }

    @Test
    public void aChangeToAnyGlobalRecordsEveryRowOnce() {
        frame();
        assertNone(frame());

        // A different horizontal offset moves every cell in the pane.
        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 4f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        for (int i = 0; i < ROWS; i++)
            assertTrue(mCache.rowChanged(i, mRows[i], COLUMNS, -1, 0, CURSOR_COLOR, -1, -1));

        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 4f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        for (int i = 0; i < ROWS; i++)
            assertFalse(mCache.rowChanged(i, mRows[i], COLUMNS, -1, 0, CURSOR_COLOR, -1, -1));
    }

    @Test
    public void aDifferentEmulatorRecordsEveryRow() {
        frame();

        mCache.beginFrame(new Object(), ROWS, COLUMNS, mTopRow, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS);
        for (int i = 0; i < ROWS; i++)
            assertTrue(mCache.rowChanged(i, mRows[i], COLUMNS, -1, 0, CURSOR_COLOR, -1, -1));
    }

    @Test
    public void aPaneThatLosesAndRegainsARowRecordsEverythingAgain() {
        frame();

        mCache.beginFrame(mEmulator, ROWS, COLUMNS, mTopRow, 0, 0f, false, 0, false, false,
            mPalette, 1080, 600, ROWS - 1);
        for (int i = 0; i < ROWS - 1; i++)
            assertTrue(mCache.rowChanged(i, mRows[i], COLUMNS, -1, 0, CURSOR_COLOR, -1, -1));

        assertAll(frame());
    }
}
