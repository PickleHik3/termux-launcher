package com.termux.terminal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A circular buffer of {@link TerminalRow}:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 * <p>
 * See {@link #externalToInternalRow(int)} for how to map from logical screen rows to array indices.
 */
public final class TerminalBuffer {

    private TerminalSessionClient mClient;

    TerminalRow[] mLines;

    /**
     * The length of {@link #mLines}.
     */
    int mTotalRows;

    /**
     * The number of rows and columns visible on the screen.
     */
    int mScreenRows, mColumns;

    /**
     * The number of rows kept in history.
     */
    private int mActiveTranscriptRows = 0;

    /**
     * The index in the circular buffer where the visible screen starts.
     */
    private int mScreenFirstRow = 0;

    public HashMap<Integer, TerminalBitmap> bitmaps;

    public TerminalSixel terminalSixel;

    private boolean hasBitmaps;

    private long bitmapLastGC;

    /**
     * Whether {@link #getSelectedText} trims a wrapped row's trailing padding spaces like an
     * unwrapped row, instead of keeping them. Set by {@link TerminalEmulator#setTrimWrappedTrailingSpaces(boolean)};
     * defaults to on so a fresh buffer matches the shipped default of the setting.
     */
    private boolean mTrimWrappedTrailingSpaces = true;

    /**
     * Create a transcript screen.
     *
     * @param columns    the width of the screen in characters.
     * @param totalRows  the height of the entire text area, in rows of text.
     * @param screenRows the height of just the screen, not including the transcript that holds lines that have scrolled off
     *                   the top of the screen.
     */
    public TerminalBuffer(int columns, int totalRows, int screenRows) {
        this(null, columns, totalRows, screenRows);
    }

    public TerminalBuffer(TerminalSessionClient client, int columns, int totalRows, int screenRows) {
        mClient = client;
        mColumns = columns;
        mTotalRows = totalRows;
        mScreenRows = screenRows;
        mLines = new TerminalRow[totalRows];
        blockSet(0, 0, columns, screenRows, ' ', TextStyle.NORMAL);
        hasBitmaps = false;
        bitmaps = new HashMap<Integer, TerminalBitmap>();
        bitmapLastGC = SystemClock.uptimeMillis();
    }

    public TerminalSessionClient getClient() {
        return mClient;
    }

    void setTrimWrappedTrailingSpaces(boolean trimWrappedTrailingSpaces) {
        mTrimWrappedTrailingSpaces = trimWrappedTrailingSpaces;
    }

    boolean isTrimWrappedTrailingSpaces() {
        return mTrimWrappedTrailingSpaces;
    }

    public String getTranscriptText() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows).trim();
    }

    public String getTranscriptTextWithoutJoinedLines() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, false).trim();
    }

    public String getTranscriptTextWithFullLinesJoined() {
        return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, true, true).trim();
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2) {
        return getSelectedText(selX1, selY1, selX2, selY2, true);
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2, boolean joinBackLines) {
        return getSelectedText(selX1, selY1, selX2, selY2, joinBackLines, false);
    }

    public String getSelectedText(int selX1, int selY1, int selX2, int selY2, boolean joinBackLines, boolean joinFullLines) {
        final StringBuilder builder = new StringBuilder();
        final int columns = mColumns;
        // A text sizing block is one unit: touching any of its cells copies its text, once.
        Set<Long> blocksTaken = null;
        if (selY1 < -getActiveTranscriptRows())
            selY1 = -getActiveTranscriptRows();
        if (selY2 >= mScreenRows)
            selY2 = mScreenRows - 1;
        for (int row = selY1; row <= selY2; row++) {
            int x1 = (row == selY1) ? selX1 : 0;
            int x2;
            if (row == selY2) {
                x2 = selX2 + 1;
                if (x2 > columns)
                    x2 = columns;
            } else {
                x2 = columns;
            }
            TerminalRow lineObject = mLines[externalToInternalRow(row)];
            if (lineObject != null && lineObject.hasTextSizes()) {
                if (blocksTaken == null) blocksTaken = new HashSet<>();
                StringBuilder rowText = new StringBuilder();
                appendRowWithTextBlocks(rowText, row, lineObject, x1, x2, blocksTaken);
                boolean blockRowWrap = getLineWrap(row);
                boolean trimThisBlockRow = blockRowWrap && x2 == columns && mTrimWrappedTrailingSpaces && !joinFullLines;
                int lastPrinting = -1;
                if (blockRowWrap && x2 == columns && !trimThisBlockRow) {
                    lastPrinting = rowText.length() - 1;
                } else {
                    for (int i = 0; i < rowText.length(); i++)
                        if (rowText.charAt(i) != ' ') lastPrinting = i;
                }
                if (lastPrinting >= 0) builder.append(rowText, 0, lastPrinting + 1);
                if (trimThisBlockRow && joinBackLines && row < selY2 && lastPrinting < rowText.length() - 1) {
                    TerminalRow nextLineObject = mLines[externalToInternalRow(row + 1)];
                    if (nextLineObject != null && nextLineObject.mText.length > 0 && nextLineObject.mText[0] != ' ')
                        builder.append(' ');
                }
                boolean blockRowFillsWidth = lastPrinting == rowText.length() - 1;
                if ((!joinBackLines || !blockRowWrap) && (!joinFullLines || !blockRowFillsWidth)
                    && row < selY2 && row < mScreenRows - 1)
                    builder.append('\n');
                continue;
            }
            int x1Index = lineObject.findStartOfColumn(x1);
            int x2Index = (x2 < mColumns) ? lineObject.findStartOfColumn(x2) : lineObject.getSpaceUsed();
            if (x2Index == x1Index) {
                // Selected the start of a wide character.
                x2Index = lineObject.findStartOfColumn(x2 + 1);
            }
            char[] line = lineObject.mText;
            int lastPrintingCharIndex = -1;
            int i;
            boolean rowLineWrap = getLineWrap(row);
            // joinFullLines is only ever true for internal fixed-width uses (getWordAtLocation's
            // column math, getTranscriptTextWithFullLinesJoined) that need every wrapped row to
            // keep contributing exactly `columns` characters; the trim setting never applies there.
            boolean trimThisWrappedRow = rowLineWrap && x2 == columns && mTrimWrappedTrailingSpaces && !joinFullLines;
            if (rowLineWrap && x2 == columns && !trimThisWrappedRow) {
                // If the line was wrapped, we shouldn't lose trailing space:
                lastPrintingCharIndex = x2Index - 1;
            } else {
                for (i = x1Index; i < x2Index; ++i) {
                    char c = line[i];
                    if (c != ' ')
                        lastPrintingCharIndex = i;
                }
            }
            int len = lastPrintingCharIndex - x1Index + 1;
            if (lastPrintingCharIndex != -1 && len > 0)
                builder.append(line, x1Index, len);
            if (trimThisWrappedRow && joinBackLines && row < selY2 && lastPrintingCharIndex < x2Index - 1) {
                // The row wrapped and its trailing padding was trimmed away, and it will be joined
                // onto the next row with no newline between them. If the next row picks up mid-word,
                // put back exactly one space so joined prose doesn't run two words together.
                TerminalRow nextLineObject = mLines[externalToInternalRow(row + 1)];
                if (nextLineObject.mText.length > 0 && nextLineObject.mText[0] != ' ')
                    builder.append(' ');
            }
            boolean lineFillsWidth = lastPrintingCharIndex == x2Index - 1;
            if ((!joinBackLines || !rowLineWrap) && (!joinFullLines || !lineFillsWidth) && row < selY2 && row < mScreenRows - 1)
                builder.append('\n');
        }
        return builder.toString();
    }

    /**
     * The selected part of a row that carries text sizing blocks. A block contributes its text
     * once, at the first of its cells the selection reaches; its other cells contribute nothing.
     */
    private void appendRowWithTextBlocks(StringBuilder out, int row, TerminalRow line,
                                         int x1, int x2, Set<Long> blocksTaken) {
        final int spaceUsed = line.getSpaceUsed();
        int[] cellStart = new int[mColumns];
        int[] cellEnd = new int[mColumns];
        Arrays.fill(cellStart, -1);
        int column = 0;
        int index = 0;
        while (index < spaceUsed && column < mColumns) {
            int start = index;
            index += Character.isHighSurrogate(line.mText[index]) ? 2 : 1;
            int width = line.getDisplayWidthAt(start);
            while (index < spaceUsed && line.getDisplayWidthAt(index) <= 0)
                index += Character.isHighSurrogate(line.mText[index]) ? 2 : 1;
            cellStart[column] = start;
            cellEnd[column] = index;
            column += Math.max(1, width);
        }
        for (int c = Math.max(0, x1); c < x2 && c < mColumns; c++) {
            if (line.getTextSizeRecord(c) != 0) {
                TextBlock block = getTextBlockAt(row, c);
                if (block != null && blocksTaken.add(((long) block.row << 32) | (long) block.column))
                    out.append(getTextBlockText(block));
                continue;
            }
            if (cellStart[c] >= 0) out.append(line.mText, cellStart[c], cellEnd[c] - cellStart[c]);
        }
    }

    public String getWordAtLocation(int x, int y) {
        // Set y1 and y2 to the lines where the wrapped line starts and ends.
        // I.e. if a line that is wrapped to 3 lines starts at line 4, and this
        // is called with y=5, then y1 would be set to 4 and y2 would be set to 6.
        int y1 = y;
        int y2 = y;
        while (y1 > 0 && !getSelectedText(0, y1 - 1, mColumns, y, true, true).contains("\n")) {
            y1--;
        }
        while (y2 < mScreenRows && !getSelectedText(0, y, mColumns, y2 + 1, true, true).contains("\n")) {
            y2++;
        }
        // A sized block is one unit, so tapping any of its cells gives the whole of its text.
        TextBlock tappedBlock = getTextBlockAt(y, x);
        if (tappedBlock != null)
            return getTextBlockText(tappedBlock);
        // Get the text for the whole wrapped line
        String text = getSelectedText(0, y1, mColumns, y2, true, true);
        // The index of x in text
        int textOffset = joinedTextOffset(y1, y2, y, x);
        if (textOffset >= text.length()) {
            // The click was to the right of the last word on the line, so
            // there's no word to return
            return "";
        }
        // Set x1 and x2 to the indices of the last space before x and the
        // first space after x in text respectively
        int x1 = text.lastIndexOf(' ', textOffset);
        int x2 = text.indexOf(' ', textOffset);
        if (x2 == -1) {
            x2 = text.length();
        }
        if (x1 == x2) {
            // The click was on a space, so there's no word to return
            return "";
        }
        return text.substring(x1 + 1, x2);
    }

    /**
     * Where the cell at ({@code y}, {@code x}) starts inside the joined text of the wrapped rows
     * {@code y1} to {@code y2}. Every plain row contributes one character per column, so the
     * position is simple arithmetic; a row carrying a sized block does not, because the block's
     * text is contributed once and its other cells contribute nothing, so those rows are counted
     * the same way {@link #appendRowWithTextBlocks} writes them.
     */
    private int joinedTextOffset(int y1, int y2, int y, int x) {
        boolean anyTextBlocks = false;
        for (int row = y1; row <= y2 && row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line != null && line.hasTextSizes()) {
                anyTextBlocks = true;
                break;
            }
        }
        if (!anyTextBlocks)
            return (y - y1) * mColumns + x;
        Set<Long> blocksTaken = new HashSet<>();
        int offset = 0;
        for (int row = y1; row < y; row++)
            offset += contributedLength(row, mColumns, blocksTaken);
        return offset + contributedLength(y, x, blocksTaken);
    }

    /** How many characters columns 0 up to {@code x2} of a row put into the joined text. */
    private int contributedLength(int row, int x2, Set<Long> blocksTaken) {
        if (row < -mActiveTranscriptRows || row >= mScreenRows) return 0;
        TerminalRow line = mLines[externalToInternalRow(row)];
        if (line == null) return 0;
        if (line.hasTextSizes()) {
            StringBuilder contributed = new StringBuilder();
            appendRowWithTextBlocks(contributed, row, line, 0, x2, blocksTaken);
            return contributed.length();
        }
        int x2Index = (x2 < mColumns) ? line.findStartOfColumn(x2) : line.getSpaceUsed();
        return Math.max(0, x2Index - line.findStartOfColumn(0));
    }

    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }

    public int getActiveRows() {
        return mActiveTranscriptRows + mScreenRows;
    }

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -mActiveTranscriptRows to mScreenRows-1, with the screen being 0..mScreenRows-1.
     * - Internal coordinate system: the mScreenRows lines starting at mScreenFirstRow comprise the screen, while the
     *   mActiveTranscriptRows lines ending at mScreenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -mActiveTranscriptRows         ]     [ mScreenFirstRow - mActiveTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible screen starts here) ]  ↔  [ mScreenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ mScreenRows-1                  ]     [ mScreenFirstRow + mScreenRows-1         ]
     * </pre>
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    public int externalToInternalRow(int externalRow) {
        if (externalRow < -mActiveTranscriptRows || externalRow > mScreenRows)
            throw new IllegalArgumentException("extRow=" + externalRow + ", mScreenRows=" + mScreenRows + ", mActiveTranscriptRows=" + mActiveTranscriptRows);
        final int internalRow = mScreenFirstRow + externalRow;
        return (internalRow < 0) ? (mTotalRows + internalRow) : (internalRow % mTotalRows);
    }

    public void setLineWrap(int row) {
        mLines[externalToInternalRow(row)].mLineWrap = true;
    }

    public boolean getLineWrap(int row) {
        return mLines[externalToInternalRow(row)].mLineWrap;
    }

    public void clearLineWrap(int row) {
        mLines[externalToInternalRow(row)].mLineWrap = false;
    }

    /**
     * Resize the screen which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows    The number of rows the screen should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    public void resize(int newColumns, int newRows, int newTotalRows, int[] cursor, long currentStyle, boolean altScreen) {
        resize(newColumns, newRows, newTotalRows, cursor, currentStyle, altScreen, false);
    }

    /**
     * Resize with an optional bottom anchor. The anchored form exposes transcript rows (or blank
     * rows when history is exhausted) above the old screen so the cursor retains its distance from
     * the bottom edge as rows are added.
     */
    public void resize(int newColumns, int newRows, int newTotalRows, int[] cursor,
                       long currentStyle, boolean altScreen, boolean keepCursorAtBottom) {
        // newRows > mTotalRows should not normally happen since mTotalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == mColumns && newRows <= mTotalRows) {
            // Fast resize where just the rows changed.
            int shiftDownOfTopRow = mScreenRows - newRows;
            if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < mScreenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (int i = mScreenRows - 1; i > 0; i--) {
                    if (cursor[1] >= i)
                        break;
                    int r = externalToInternalRow(i);
                    if (mLines[r] == null || mLines[r].isBlank()) {
                        if (--shiftDownOfTopRow == 0)
                            break;
                    }
                }
            } else if (shiftDownOfTopRow < 0) {
                if (keepCursorAtBottom) {
                    int rowsAdded = -shiftDownOfTopRow;
                    // Existing transcript fills the first new rows. Any remaining rows are clean
                    // padding above the old screen rather than below its cursor.
                    for (int i = mActiveTranscriptRows + 1; i <= rowsAdded; i++) {
                        int internalRow = (mScreenFirstRow - i) % mTotalRows;
                        if (internalRow < 0) internalRow += mTotalRows;
                        allocateFullLineIfNecessary(internalRow).clear(currentStyle);
                    }
                } else {
                    // Negative shift down = expanding. Only move screen up if there is transcript to show:
                    int actualShift = Math.max(shiftDownOfTopRow, -mActiveTranscriptRows);
                    if (shiftDownOfTopRow != actualShift) {
                        // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                        for (int i = 0; i < actualShift - shiftDownOfTopRow; i++) allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + i) % mTotalRows).clear(currentStyle);
                        shiftDownOfTopRow = actualShift;
                    }
                }
            }
            mScreenFirstRow += shiftDownOfTopRow;
            mScreenFirstRow = (mScreenFirstRow < 0) ? (mScreenFirstRow + mTotalRows) : (mScreenFirstRow % mTotalRows);
            mTotalRows = newTotalRows;
            mActiveTranscriptRows = altScreen ? 0 : Math.max(0, mActiveTranscriptRows + shiftDownOfTopRow);
            cursor[1] -= shiftDownOfTopRow;
            mScreenRows = newRows;
        } else {
            // Copy away old state and update new:
            TerminalRow[] oldLines = mLines;
            mLines = new TerminalRow[newTotalRows];
            for (int i = 0; i < newTotalRows; i++) mLines[i] = new TerminalRow(newColumns, currentStyle);
            final int oldActiveTranscriptRows = mActiveTranscriptRows;
            final int oldScreenFirstRow = mScreenFirstRow;
            final int oldScreenRows = mScreenRows;
            final int oldTotalRows = mTotalRows;
            mTotalRows = newTotalRows;
            mScreenRows = newRows;
            mActiveTranscriptRows = mScreenFirstRow = 0;
            mColumns = newColumns;
            int newCursorRow = -1;
            int newCursorColumn = -1;
            int oldCursorRow = cursor[1];
            int oldCursorColumn = cursor[0];
            boolean newCursorPlaced = false;
            int currentOutputExternalRow = 0;
            int currentOutputExternalColumn = 0;
            // Loop over every character in the initial state.
            // Blank lines should be skipped only if at end of transcript (just as is done in the "fast" resize), so we
            // keep track how many blank lines we have skipped if we later on find a non-blank line.
            int skippedBlankLines = 0;
            for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) {
                // Do what externalToInternalRow() does but for the old state:
                int internalOldRow = oldScreenFirstRow + externalOldRow;
                internalOldRow = (internalOldRow < 0) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);
                TerminalRow oldLine = oldLines[internalOldRow];
                boolean cursorAtThisRow = externalOldRow == oldCursorRow;
                // The cursor may only be on a non-null line, which we should not skip:
                if (oldLine == null || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
                    skippedBlankLines++;
                    continue;
                } else if (skippedBlankLines > 0) {
                    // After skipping some blank lines we encounter a non-blank line. Insert the skipped blank lines.
                    for (int i = 0; i < skippedBlankLines; i++) {
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            scrollDownOneLine(0, mScreenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }
                    skippedBlankLines = 0;
                }
                if (oldLine.mKittyPlacements != null) {
                    // Pictures ride along with the row their top-left cell was on. The new row is
                    // where this old row's text starts; later scrolling moves it by reference.
                    java.util.ArrayList<KittyPlacement> carried = oldLine.mKittyPlacements;
                    oldLine.mKittyPlacements = null;
                    TerminalRow target = allocateFullLineIfNecessary(externalToInternalRow(currentOutputExternalRow));
                    for (KittyPlacement placement : carried) {
                        placement.fitToColumns(mColumns);
                        target.addKittyPlacement(placement);
                        if (placement.rows > mKittyMaxRows) mKittyMaxRows = placement.rows;
                    }
                }
                int lastNonSpaceIndex = 0;
                boolean justToCursor = false;
                if (cursorAtThisRow || oldLine.mLineWrap) {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.getSpaceUsed();
                    if (cursorAtThisRow)
                        justToCursor = true;
                } else {
                    for (int i = 0; i < oldLine.getSpaceUsed(); i++) // NEWLY INTRODUCED BUG! Should not index oldLine.mStyle with char indices
                    if (oldLine.mText[i] != ' ')
                        /* || oldLine.mStyle[i] != currentStyle */
                        lastNonSpaceIndex = i + 1;
                }
                // A wrapped old row becomes several new ones; its mark belongs on the first of them.
                if (oldLine.mShellIntegrationMark != TerminalRow.MARK_NONE)
                    setShellIntegrationMark(currentOutputExternalRow, oldLine.mShellIntegrationMark);
                int currentOldCol = 0;
                long styleAtCol = 0;
                int decorationAtCol = TextStyle.DECORATION_COLOR_DEFAULT;
                int hyperlinkAtCol = 0;
                for (int i = 0; i < lastNonSpaceIndex; i++) {
                    // Note that looping over java character, not cells.
                    char c = oldLine.mText[i];
                    int codePoint = (Character.isHighSurrogate(c)) ? Character.toCodePoint(c, oldLine.mText[++i]) : c;
                    int displayWidth = oldLine.getDisplayWidthAt(
                        i - (Character.isSupplementaryCodePoint(codePoint) ? 1 : 0));
                    if (justToCursor && newCursorPlaced && displayWidth > 0) break;
                    int sizeRecord = (displayWidth > 0) ? oldLine.getTextSizeRecord(currentOldCol) : 0;
                    if (sizeRecord != 0) {
                        // A block is replayed as a block: its cells are one column each, its text
                        // lives in the anchor, and the cells beside it are blanks that carry only
                        // an offset. Copying them character by character would scatter it.
                        int clusterStart = i - (Character.isSupplementaryCodePoint(codePoint) ? 1 : 0);
                        int clusterEnd = i + 1;
                        while (clusterEnd < oldLine.getSpaceUsed()
                            && oldLine.getDisplayWidthAt(clusterEnd) <= 0)
                            clusterEnd += Character.isHighSurrogate(oldLine.mText[clusterEnd]) ? 2 : 1;
                        if (KittyTextSizing.isAnchor(sizeRecord)) {
                            String blockText = new String(oldLine.mText, clusterStart, clusterEnd - clusterStart);
                            long blockStyle = oldLine.getStyle(currentOldCol);
                            int blockDecoration = oldLine.getDecorationColor(currentOldCol);
                            int blockHyperlink = oldLine.getHyperlinkId(currentOldCol);
                            int scale = KittyTextSizing.scaleOf(sizeRecord);
                            int cellWidth = KittyTextSizing.widthOf(sizeRecord);
                            // The full block if it fits the new width, otherwise one row of
                            // normal-size cells that keeps the record for when it fits again.
                            boolean fits = scale * cellWidth <= mColumns && scale <= mScreenRows;
                            int placeColumns = fits ? scale * cellWidth : cellWidth;
                            int placeRows = fits ? scale : 1;
                            if (placeColumns <= mColumns) {
                                if (currentOutputExternalColumn + placeColumns > mColumns) {
                                    setLineWrap(currentOutputExternalRow);
                                    if (currentOutputExternalRow == mScreenRows - 1) {
                                        if (newCursorPlaced) newCursorRow--;
                                        scrollDownOneLine(0, mScreenRows, currentStyle);
                                    } else {
                                        currentOutputExternalRow++;
                                    }
                                    currentOutputExternalColumn = 0;
                                }
                                // A demoted block only grows back into cells that are still blank.
                                if (fits && KittyTextSizing.isDemoted(sizeRecord) && placeRows > 1
                                    && !isAreaBlank(currentOutputExternalColumn,
                                        Math.min(currentOutputExternalRow + 1, mScreenRows - 1),
                                        placeColumns, Math.min(placeRows - 1, mScreenRows - currentOutputExternalRow - 1))) {
                                    fits = false;
                                    placeColumns = cellWidth;
                                    placeRows = 1;
                                }
                                int rowsOverflowing = currentOutputExternalRow + placeRows - mScreenRows;
                                for (int scrolled = 0; scrolled < rowsOverflowing; scrolled++) {
                                    if (newCursorPlaced) newCursorRow--;
                                    scrollDownOneLine(0, mScreenRows, currentStyle);
                                }
                                if (rowsOverflowing > 0) currentOutputExternalRow -= rowsOverflowing;
                                writeTextBlock(currentOutputExternalColumn, currentOutputExternalRow,
                                    blockText, KittyTextSizing.withDemoted(sizeRecord, !fits),
                                    blockStyle, blockDecoration, blockHyperlink);
                                if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                                    newCursorColumn = currentOutputExternalColumn;
                                    newCursorRow = currentOutputExternalRow;
                                    newCursorPlaced = true;
                                }
                                currentOutputExternalColumn += placeColumns;
                            }
                        } else if (KittyTextSizing.offsetYOf(sizeRecord) > 0) {
                            // A cell on one of the block's lower rows: the anchor is not on this
                            // row, so keep the column it took so following text stays put.
                            currentOutputExternalColumn++;
                        }
                        currentOldCol++;
                        i = clusterEnd - 1;
                        continue;
                    }
                    // Use the last style if this is a zero-width character:
                    if (displayWidth > 0) {
                        styleAtCol = oldLine.getStyle(currentOldCol);
                        decorationAtCol = oldLine.getDecorationColor(currentOldCol);
                        hyperlinkAtCol = oldLine.getHyperlinkId(currentOldCol);
                    }
                    // Line wrap as necessary:
                    if (currentOutputExternalColumn + displayWidth > mColumns) {
                        setLineWrap(currentOutputExternalRow);
                        if (currentOutputExternalRow == mScreenRows - 1) {
                            if (newCursorPlaced)
                                newCursorRow--;
                            scrollDownOneLine(0, mScreenRows, currentStyle);
                        } else {
                            currentOutputExternalRow++;
                        }
                        currentOutputExternalColumn = 0;
                    }
                    int offsetDueToCombiningChar = ((displayWidth <= 0 && currentOutputExternalColumn > 0) ? 1 : 0);
                    int outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar;
                    if (displayWidth > 0) {
                        setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol,
                            decorationAtCol, hyperlinkAtCol);
                    } else {
                        allocateFullLineIfNecessary(externalToInternalRow(currentOutputExternalRow))
                            .appendCodePointToCell(outputColumn, codePoint);
                    }
                    if (displayWidth > 0) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn;
                            newCursorRow = currentOutputExternalRow;
                            newCursorPlaced = true;
                        }
                        currentOldCol += displayWidth;
                        currentOutputExternalColumn += displayWidth;
                    }
                }
                // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.mLineWrap) {
                    if (currentOutputExternalRow == mScreenRows - 1) {
                        if (newCursorPlaced)
                            newCursorRow--;
                        scrollDownOneLine(0, mScreenRows, currentStyle);
                    } else {
                        currentOutputExternalRow++;
                    }
                    currentOutputExternalColumn = 0;
                }
            }
            cursor[0] = newCursorColumn;
            cursor[1] = newCursorRow;
        }
        // Handle cursor scrolling off screen:
        if (cursor[0] < 0 || cursor[1] < 0)
            cursor[0] = cursor[1] = 0;
    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private void blockCopyLinesDown(int srcInternal, int len) {
        if (len == 0)
            return;
        int totalRows = mTotalRows;
        int start = len - 1;
        // Save away line to be overwritten:
        TerminalRow lineToBeOverWritten = mLines[(srcInternal + start + 1) % totalRows];
        // Do the copy from bottom to top.
        for (int i = start; i >= 0; --i) mLines[(srcInternal + i + 1) % totalRows] = mLines[(srcInternal + i) % totalRows];
        // Put back overwritten line, now above the block:
        mLines[(srcInternal) % totalRows] = lineToBeOverWritten;
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    public void scrollDownOneLine(int topMargin, int bottomMargin, long style) {
        if (topMargin > bottomMargin - 1 || topMargin < 0 || bottomMargin > mScreenRows)
            throw new IllegalArgumentException("topMargin=" + topMargin + ", bottomMargin=" + bottomMargin + ", mScreenRows=" + mScreenRows);
        // Rows move by reference, so a whole-screen scroll carries every block along with it. A
        // scroll region does not: the row leaving at the top of the region, and any block straddling
        // a margin, would be cut in half, and a block always goes as a whole.
        if (topMargin > 0)
            dropTextBlocksIn(0, topMargin, mColumns, 1, style);
        if (bottomMargin < mScreenRows)
            dropTextBlocksCrossingRow(bottomMargin, style);
        // Copy the fixed topMargin lines one line down so that they remain on screen in same position:
        blockCopyLinesDown(mScreenFirstRow, topMargin);
        // Copy the fixed mScreenRows-bottomMargin lines one line down so that they remain on screen in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin);
        // Update the screen location in the ring buffer:
        mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows;
        // Note that the history has grown if not already full:
        if (mActiveTranscriptRows < mTotalRows - mScreenRows)
            mActiveTranscriptRows++;
        // Blank the newly revealed line above the bottom margin:
        int blankRow = externalToInternalRow(bottomMargin - 1);
        if (mLines[blankRow] == null) {
            mLines[blankRow] = new TerminalRow(mColumns, style);
        } else {
            // find if a bitmap is completely scrolled out
            Set<Integer> used = new HashSet<Integer>();
            if (mLines[blankRow].mHasBitmap) {
                for (int column = 0; column < mColumns; column++) {
                    final long st = mLines[blankRow].getStyle(column);
                    if (TextStyle.isBitmap(st)) {
                        used.add((int) (st >> 16) & 0xffff);
                    }
                }
                TerminalRow nextLine = mLines[(blankRow + 1) % mTotalRows];
                if (nextLine.mHasBitmap) {
                    for (int column = 0; column < mColumns; column++) {
                        final long st = nextLine.getStyle(column);
                        if (TextStyle.isBitmap(st)) {
                            used.remove((int) (st >> 16) & 0xffff);
                        }
                    }
                }
                for (Integer bm : used) {
                    bitmaps.remove(bm);
                }
            }
            boolean hadPlacements = mLines[blankRow].hasKittyPlacements();
            mLines[blankRow].clear(style);
            if (hadPlacements) notifyKittyCellsCollected();
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the screen, or else an InvalidParameterException will
     * be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w  width
     * @param h  height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    public void blockCopy(int sx, int sy, int w, int h, int dx, int dy) {
        if (w == 0)
            return;
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows || dx < 0 || dx + w > mColumns || dy < 0 || dy + h > mScreenRows)
            throw new IllegalArgumentException();
        // A block is never copied in part, and the source rectangle is about to lose what was
        // there, so every block either rectangle touches goes as a whole first.
        dropTextBlocksIn(sx, sy, w, h);
        dropTextBlocksIn(dx, dy, w, h);
        boolean copyingUp = sy > dy;
        for (int y = 0; y < h; y++) {
            int y2 = copyingUp ? y : (h - (y + 1));
            TerminalRow sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2));
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the screen, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    public void blockSet(int sx, int sy, int w, int h, int val, long style) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
            throw new IllegalArgumentException("Illegal arguments! blockSet(" + sx + ", " + sy + ", " + w + ", " + h + ", " + val + ", " + mColumns + ", " + mScreenRows + ")");
        }
        dropTextBlocksIn(sx, sy, w, h, style);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) setChar(sx + x, sy + y, val, style);
            if (sx + w == mColumns && val == ' ') {
                clearLineWrap(sy + y);
                if (sx == 0) {
                    // The whole row was blanked, so whatever prompt or output started on it is gone.
                    setShellIntegrationMark(sy + y, TerminalRow.MARK_NONE);
                }
            }
        }
    }

    public TerminalRow allocateFullLineIfNecessary(int row) {
        return (mLines[row] == null) ? (mLines[row] = new TerminalRow(mColumns, 0)) : mLines[row];
    }

    public void setChar(int column, int row, int codePoint, long style) {
        setChar(column, row, codePoint, style, TextStyle.DECORATION_COLOR_DEFAULT, 0);
    }

    public void setChar(int column, int row, int codePoint, long style, int decorationColor, int hyperlinkId) {
        if (row < 0 || row >= mScreenRows || column < 0 || column >= mColumns)
            throw new IllegalArgumentException("TerminalBuffer.setChar(): row=" + row + ", column=" + column + ", mScreenRows=" + mScreenRows + ", mColumns=" + mColumns);
        TerminalRow line = allocateFullLineIfNecessary(externalToInternalRow(row));
        if (line.hasTextSizes() && line.isTextSizeCell(column)) {
            // Writing over the cell that holds a block's text takes the whole block away; writing
            // over any other cell of it leaves a hole and the rest standing.
            if (line.isTextSizeAnchor(column))
                dropTextBlockAt(row, column, true, style);
            else
                line.setTextSizeRecord(column, 0);
        }
        line.setChar(column, codePoint, style, decorationColor, hyperlinkId);
    }

    /** Attach a code point to the grapheme already stored in a cell without consuming a new cell. */
    public void appendCodePointToCell(int column, int row, int codePoint) {
        if (row < 0 || row >= mScreenRows || column < 0 || column >= mColumns)
            throw new IllegalArgumentException("TerminalBuffer.appendCodePointToCell(): row=" + row
                + ", column=" + column + ", mScreenRows=" + mScreenRows + ", mColumns=" + mColumns);
        allocateFullLineIfNecessary(externalToInternalRow(row)).appendCodePointToCell(column, codePoint);
    }

    public boolean widenCell(int column, int row) {
        if (row < 0 || row >= mScreenRows || column < 0 || column >= mColumns) return false;
        return allocateFullLineIfNecessary(externalToInternalRow(row)).widenCell(column);
    }

    /** The OSC 133 mark of a row, one of the {@code TerminalRow.MARK_*} values. */
    public byte getShellIntegrationMark(int externalRow) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).mShellIntegrationMark;
    }

    public void setShellIntegrationMark(int externalRow, byte mark) {
        allocateFullLineIfNecessary(externalToInternalRow(externalRow)).mShellIntegrationMark = mark;
    }

    /**
     * Search for the closest row carrying a given mark.
     *
     * @param fromRow   the row to search away from, exclusive, in external coordinates.
     * @param backwards search towards the transcript rather than towards the bottom of the screen.
     * @return the row found, or {@link Integer#MIN_VALUE} when there is none.
     */
    public int findRowWithMark(int fromRow, byte mark, boolean backwards) {
        int first = -getActiveTranscriptRows();
        int last = mScreenRows - 1;
        if (backwards) {
            for (int row = Math.min(fromRow - 1, last); row >= first; row--) {
                if (getShellIntegrationMark(row) == mark)
                    return row;
            }
        } else {
            for (int row = Math.max(fromRow + 1, first); row <= last; row++) {
                if (getShellIntegrationMark(row) == mark)
                    return row;
            }
        }
        return Integer.MIN_VALUE;
    }

    public int getDecorationColorAt(int externalRow, int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getDecorationColor(column);
    }

    public int getHyperlinkIdAt(int externalRow, int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getHyperlinkId(column);
    }

    // --- Text sizing blocks (OSC 66) ------------------------------------------------------

    /**
     * Where one text sizing block sits, in external row coordinates. The cell at
     * ({@link #row}, {@link #column}) is the anchor: it holds the block's text, and every other
     * cell of the rectangle is blank and points back to it.
     */
    public static final class TextBlock {

        /** The anchor's row, in external coordinates; may be negative for a transcript row. */
        public final int row;

        /** The anchor's column. */
        public final int column;

        /** How many rows the block covers. One for a demoted block. */
        public final int rows;

        /** How many columns the block covers. */
        public final int columns;

        /** How many times normal size the text is drawn. */
        public final int scale;

        /** How many cells wide the text is before scaling. */
        public final int cellWidth;

        /** The {@code n} of the {@code n/d} height fraction; 0 means the whole height. */
        public final int fractionNumerator;

        /** The {@code d} of the {@code n/d} height fraction; 0 means the whole height. */
        public final int fractionDenominator;

        /** 0 top, 1 bottom, 2 centre. */
        public final int verticalAlign;

        /** 0 left, 1 right, 2 centre. */
        public final int horizontalAlign;

        /** Whether the block did not fit the screen width and sits on one row at normal size. */
        public final boolean demoted;

        TextBlock(int row, int column, int record) {
            this.row = row;
            this.column = column;
            this.rows = KittyTextSizing.rowsOf(record);
            this.columns = KittyTextSizing.columnsOf(record);
            this.scale = KittyTextSizing.scaleOf(record);
            this.cellWidth = KittyTextSizing.widthOf(record);
            this.fractionNumerator = KittyTextSizing.numeratorOf(record);
            this.fractionDenominator = KittyTextSizing.denominatorOf(record);
            this.verticalAlign = KittyTextSizing.verticalAlignOf(record);
            this.horizontalAlign = KittyTextSizing.horizontalAlignOf(record);
            this.demoted = KittyTextSizing.isDemoted(record);
        }

        /** Whether a cell is inside this block. */
        public boolean contains(int externalRow, int cellColumn) {
            return externalRow >= row && externalRow < row + rows
                && cellColumn >= column && cellColumn < column + columns;
        }

        @Override
        public String toString() {
            return "TextBlock[row=" + row + ", column=" + column + ", rows=" + rows
                + ", columns=" + columns + ", scale=" + scale + ", cellWidth=" + cellWidth
                + (demoted ? ", demoted" : "") + "]";
        }
    }

    /**
     * The text sizing block a cell belongs to, or null when the cell is a plain one. A cell whose
     * anchor has scrolled out of the transcript resolves to null too, so half a block left behind
     * at the top of history reads as blank rather than throwing.
     */
    @Nullable
    public TextBlock getTextBlockAt(int externalRow, int column) {
        if (column < 0 || column >= mColumns) return null;
        if (externalRow < -mActiveTranscriptRows || externalRow >= mScreenRows) return null;
        TerminalRow line = mLines[externalToInternalRow(externalRow)];
        if (line == null) return null;
        int record = line.getTextSizeRecord(column);
        if (!KittyTextSizing.isPresent(record)) return null;
        int anchorRow = externalRow - KittyTextSizing.offsetYOf(record);
        int anchorColumn = column - KittyTextSizing.offsetXOf(record);
        if (anchorColumn < 0 || anchorRow < -mActiveTranscriptRows) return null;
        TerminalRow anchorLine = mLines[externalToInternalRow(anchorRow)];
        if (anchorLine == null) return null;
        int anchorRecord = anchorLine.getTextSizeRecord(anchorColumn);
        if (!KittyTextSizing.isAnchor(anchorRecord) || !KittyTextSizing.sameBlock(anchorRecord, record))
            return null;
        return new TextBlock(anchorRow, anchorColumn, anchorRecord);
    }

    /** The text a block draws, read from its anchor cell. Empty when the anchor has gone. */
    @NonNull
    public String getTextBlockText(@NonNull TextBlock block) {
        if (block.row < -mActiveTranscriptRows || block.row >= mScreenRows) return "";
        TerminalRow line = mLines[externalToInternalRow(block.row)];
        if (line == null) return "";
        int start = line.findStartOfColumn(block.column);
        int end = (block.column + 1 < mColumns)
            ? line.findStartOfColumn(block.column + 1) : line.getSpaceUsed();
        return (end <= start) ? "" : new String(line.mText, start, end - start);
    }

    /**
     * Write one block: blank its rectangle, put the whole text in the top left cell as a single
     * cluster, and stamp each cell with where in the block it sits. The record says how many rows
     * and columns the block covers.
     */
    public void writeTextBlock(int column, int row, @NonNull String text, int record,
                               long style, int decorationColor, int hyperlinkId) {
        int rows = KittyTextSizing.rowsOf(record);
        int columns = KittyTextSizing.columnsOf(record);
        if (column < 0 || row < 0 || column + columns > mColumns || row + rows > mScreenRows) return;
        dropTextBlocksIn(column, row, columns, rows, style);
        for (int y = 0; y < rows; y++) {
            TerminalRow line = allocateFullLineIfNecessary(externalToInternalRow(row + y));
            for (int x = 0; x < columns; x++)
                line.setChar(column + x, ' ', style, decorationColor, hyperlinkId);
        }
        TerminalRow anchorLine = allocateFullLineIfNecessary(externalToInternalRow(row));
        boolean first = true;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (first) {
                anchorLine.setBlockAnchorChar(column, codePoint, style, decorationColor, hyperlinkId);
                first = false;
            } else {
                anchorLine.appendCodePointToCell(column, codePoint);
            }
        }
        for (int y = 0; y < rows; y++) {
            TerminalRow line = allocateFullLineIfNecessary(externalToInternalRow(row + y));
            for (int x = 0; x < columns; x++)
                line.setTextSizeRecord(column + x, KittyTextSizing.withOffsets(record, x, y));
        }
    }

    /** Whether every cell of a rectangle is a plain blank, which is what a block may grow into. */
    public boolean isAreaBlank(int column, int row, int columns, int rows) {
        for (int y = row; y < row + rows; y++) {
            if (y < 0 || y >= mScreenRows) return false;
            TerminalRow line = mLines[externalToInternalRow(y)];
            if (line == null) continue;
            for (int x = column; x < column + columns; x++) {
                if (x < 0 || x >= mColumns) return false;
                if (line.getTextSizeRecord(x) != 0) return false;
                int start = line.findStartOfColumn(x);
                int end = (x + 1 < mColumns) ? line.findStartOfColumn(x + 1) : line.getSpaceUsed();
                for (int i = start; i < end; i++)
                    if (line.mText[i] != ' ') return false;
            }
        }
        return true;
    }

    /** Take away every block that touches a rectangle, keeping each cleared cell's own style. */
    public void dropTextBlocksIn(int x, int y, int w, int h) {
        dropTextBlocksIn(x, y, w, h, false, 0);
    }

    /** Take away every block that touches a rectangle, blanking its cells with the given style. */
    public void dropTextBlocksIn(int x, int y, int w, int h, long style) {
        dropTextBlocksIn(x, y, w, h, true, style);
    }

    private void dropTextBlocksIn(int x, int y, int w, int h, boolean useStyle, long style) {
        if (w <= 0 || h <= 0) return;
        int firstRow = Math.max(y, -mActiveTranscriptRows);
        int lastRow = Math.min(y + h - 1, mScreenRows - 1);
        int firstColumn = Math.max(0, x);
        int lastColumn = Math.min(x + w, mColumns) - 1;
        for (int row = firstRow; row <= lastRow; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || !line.hasTextSizes()) continue;
            for (int column = firstColumn; column <= lastColumn; column++) {
                if (line.getTextSizeRecord(column) != 0)
                    dropTextBlockAt(row, column, useStyle, style);
            }
        }
    }

    /** Take away every block that straddles a row boundary, one that a scroll would cut. */
    private void dropTextBlocksCrossingRow(int boundary, long style) {
        if (boundary < 0 || boundary >= mScreenRows) return;
        TerminalRow line = mLines[externalToInternalRow(boundary)];
        if (line == null || !line.hasTextSizes()) return;
        for (int column = 0; column < mColumns; column++) {
            int record = line.getTextSizeRecord(column);
            if (record != 0 && KittyTextSizing.offsetYOf(record) > 0)
                dropTextBlockAt(boundary, column, true, style);
        }
    }

    /** Turn the whole block a cell belongs to back into plain blanks. */
    void dropTextBlockAt(int externalRow, int column, boolean useStyle, long style) {
        TextBlock block = getTextBlockAt(externalRow, column);
        if (block == null) {
            // An orphan left behind when its anchor scrolled away: just make the cell plain.
            TerminalRow line = mLines[externalToInternalRow(externalRow)];
            if (line != null) {
                line.setTextSizeRecord(column, 0);
                line.setChar(column, ' ', useStyle ? style : line.getStyle(column));
            }
            return;
        }
        for (int y = 0; y < block.rows; y++) {
            int row = block.row + y;
            if (row < -mActiveTranscriptRows || row >= mScreenRows) continue;
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null) continue;
            for (int x = 0; x < block.columns; x++) {
                int cellColumn = block.column + x;
                if (cellColumn >= mColumns) break;
                line.setTextSizeRecord(cellColumn, 0);
                line.setChar(cellColumn, ' ', useStyle ? style : line.getStyle(cellColumn));
            }
        }
    }

    /** Mark hyperlink ids referenced by the visible screen and active transcript only. */
    void markUsedHyperlinkIds(boolean[] used) {
        for (int row = -mActiveTranscriptRows; row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line != null) line.markUsedHyperlinkIds(used);
        }
    }

    /** used to read aloud the character under the cursor in A11Y */
    public Character getChar(int column, int row) {
        if (row  < 0 || row >= mScreenRows || column < 0 || column >= mColumns)
            throw new IllegalArgumentException("TerminalBuffer.setChar(): row=" + row + ", column=" + column + ", mScreenRows=" + mScreenRows + ", mColumns=" + mColumns);
        row = externalToInternalRow(row);
        if(column < mLines[row].mText.length)
            return mLines[row].mText[column];
        else
            return null;
    }

    public long getStyleAt(int externalRow, int column) {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column);
    }

    /**
     * Support for http://vt100.net/docs/vt510-rm/DECCARA and http://vt100.net/docs/vt510-rm/DECCARA
     */
    public void setOrClearEffect(int bits, boolean setOrClear, boolean reverse, boolean rectangular, int leftMargin, int rightMargin, int top, int left, int bottom, int right) {
        for (int y = top; y < bottom; y++) {
            TerminalRow line = mLines[externalToInternalRow(y)];
            int startOfLine = (rectangular || y == top) ? left : leftMargin;
            int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
            for (int x = startOfLine; x < endOfLine; x++) {
                long currentStyle = line.getStyle(x);
                int foreColor = TextStyle.decodeForeColor(currentStyle);
                int backColor = TextStyle.decodeBackColor(currentStyle);
                int effect = TextStyle.decodeEffect(currentStyle);
                if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    effect = (effect & ~bits) | (bits & ~effect);
                } else if (setOrClear) {
                    effect |= bits;
                } else {
                    effect &= ~bits;
                }
                // Rewrite only the effect, so that the underline style of these cells survives DECCARA.
                line.mStyle[x] = TextStyle.withColorsAndEffect(currentStyle, foreColor, backColor, effect);
            }
        }
    }

    public void clearTranscript() {
        if (mScreenFirstRow < mActiveTranscriptRows) {
            Arrays.fill(mLines, mTotalRows + mScreenFirstRow - mActiveTranscriptRows, mTotalRows, null);
            Arrays.fill(mLines, 0, mScreenFirstRow, null);
        } else {
            Arrays.fill(mLines, mScreenFirstRow - mActiveTranscriptRows, mScreenFirstRow, null);
        }
        mActiveTranscriptRows = 0;
        // Visible bitmap cells still reference their image data after ED 3 clears scrollback.
        collectUnusedBitmaps();
        terminalSixel = null;
        notifyKittyCellsCollected();
    }

    public Bitmap getSixelBitmap(int codePoint, long style) {
        TerminalBitmap bitmap = bitmaps.get(TextStyle.bitmapNum(style));
        return bitmap == null ? null : bitmap.bitmap;
    }

    public Rect getSixelRect(int codePoint, long style) {
        TerminalBitmap bm = bitmaps.get(TextStyle.bitmapNum(style));
        if (bm == null) return new Rect();
        int x = TextStyle.bitmapX(style);
        int y = TextStyle.bitmapY(style);
        Rect r = new Rect(x * bm.cellWidth, y * bm.cellHeight, (x + 1) * bm.cellWidth, (y + 1) * bm.cellHeight);
        return r;
    }

    public void sixelStart(int width, int height) {
        terminalSixel = TerminalSixel.build(mClient, width, height);
    }

    public void sixelChar(int c, int rep) {
        if (terminalSixel != null && !terminalSixel.readData(c, rep)) {
            sixelIgnore();
        }
    }

    public void sixelSetColor(int col) {
        if (terminalSixel != null) {
            terminalSixel.setColor(col);
        }
    }

    public void sixelSetColor(int col, int r, int g, int b) {
        sixelSetRGBColor(col, r, g, b);
    }

    public void sixelSetRGBColor(int col, int r, int g, int b) {
        if (terminalSixel != null) {
            terminalSixel.setRGBColor(col, r, g, b);
        }
    }

    public void sixelResize(int width, int height) {
        if (terminalSixel != null && !terminalSixel.resize(width, height)) {
            sixelIgnore();
        }
    }

    public void sixelIgnore() {
        terminalSixel = null;
    }

    private int findFreeBitmap() {
        int i = 0;
        while (bitmaps.containsKey(i)) {
            i++;
        }
        return i;
    }

    public int sixelEnd(int Y, int X, int cellW, int cellH) {
        if (terminalSixel == null) {
            return 0;
        }
        int num = findFreeBitmap();
        bitmaps.put(num, new TerminalBitmap(num, terminalSixel, Y, X, cellW, cellH, this));
        terminalSixel = null;
        if (bitmaps.get(num).bitmap == null) {
            bitmaps.remove(num);
            return 0;
        }
        hasBitmaps = true;
        bitmapGC(30000);
        return bitmaps.get(num).scrollLines;
    }

    public int[] addImage(byte[] image, int Y, int X, int cellW, int cellH, int width, int height, boolean aspect) {
        int num = findFreeBitmap();
        bitmaps.put(num, new TerminalBitmap(num, image, Y, X, cellW, cellH, width, height, aspect, this));
        if (bitmaps.get(num).bitmap == null) {
            bitmaps.remove(num);
            return new int[] { 0, 0 };
        }
        hasBitmaps = true;
        bitmapGC(30000);
        return bitmaps.get(num).cursorDelta;
    }

    /**
     * Anchor a kitty placement at a screen row. The placement is a layer over the cells, so nothing
     * in the cells changes; it goes where the row goes.
     */
    void putKittyPlacement(KittyPlacement placement, int externalRow) {
        TerminalRow anchor = allocateFullLineIfNecessary(externalToInternalRow(externalRow));
        anchor.addKittyPlacement(placement);
        placement.row = externalRow;
        if (placement.rows > mKittyMaxRows) mKittyMaxRows = placement.rows;
    }

    /**
     * The tallest placement this buffer has held, in rows: how far above the first row in view an
     * anchor can be and still reach down into it. Only ever grows, which errs towards looking at a
     * few rows too many.
     */
    private int mKittyMaxRows = 1;

    /** Find the placement with this (image, placement) id pair anywhere in the buffer. */
    KittyPlacement findKittyPlacement(long imageId, long placementId) {
        for (int row = -getActiveTranscriptRows(); row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || line.mKittyPlacements == null) continue;
            for (KittyPlacement placement : line.mKittyPlacements) {
                if (placement.imageId == imageId && placement.placementId == placementId) {
                    placement.row = row;
                    return placement;
                }
            }
        }
        return null;
    }

    /**
     * The placements that reach into the {@code rowCount} rows starting at external row
     * {@code topRow}, in the order they were anchored row by row. Each one's {@link
     * KittyPlacement#getRow()} is set to where its anchor is now. This is what the renderer draws.
     */
    public void collectVisibleKittyPlacements(int topRow, int rowCount, java.util.List<KittyPlacement> out) {
        out.clear();
        int firstRow = Math.max(-getActiveTranscriptRows(), topRow - mKittyMaxRows + 1);
        int lastRow = Math.min(mScreenRows, topRow + rowCount);
        for (int row = firstRow; row < lastRow; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || line.mKittyPlacements == null) continue;
            for (KittyPlacement placement : line.mKittyPlacements) {
                placement.row = row;
                if (placement.intersectsRows(topRow, rowCount)) out.add(placement);
            }
        }
    }

    /**
     * Whether any U+10EEEE placeholder cell survives anywhere in this buffer, scrollback included.
     *
     * <p>A virtual placement paints nothing itself: it is a prototype that placeholder cells point
     * at, so an image placed that way is reachable exactly as long as one of those cells exists.
     * The question asked here is deliberately the coarse one — <em>any</em> placeholder cell, not
     * one naming a particular image — because that is enough to decide whether frames can go, and
     * it needs neither the id decode nor the run-inheritance chain the renderer carries. Two
     * animations on screen keep each other's frames alive until the last cell of either goes,
     * which errs towards keeping pixels that are still being displayed.</p>
     */
    boolean hasAnyKittyPlaceholderCell() {
        int firstRow = -getActiveTranscriptRows();
        for (int row = firstRow; row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null) continue;
            char[] text = line.mText;
            int used = line.getSpaceUsed();
            for (int i = 0; i < used - 1; i++) {
                if (text[i] == KITTY_PLACEHOLDER_HIGH && text[i + 1] == KITTY_PLACEHOLDER_LOW)
                    return true;
            }
        }
        return false;
    }

    /**
     * The same coarse question as {@link #hasAnyKittyPlaceholderCell}, asked of the
     * {@code rowCount} rows starting at external row {@code topRow} — what the user is looking at.
     * A placeholder-displayed image cannot be located per image without decoding every cluster and
     * carrying the run-inheritance chain, so an animation drawn that way plays on while any
     * placeholder cell is on screen and stops flipping when none is.
     */
    boolean hasKittyPlaceholderCellInRows(int topRow, int rowCount) {
        int firstRow = Math.max(-getActiveTranscriptRows(), topRow);
        int lastRow = Math.min(mScreenRows, topRow + rowCount);
        for (int row = firstRow; row < lastRow; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null) continue;
            char[] text = line.mText;
            int used = line.getSpaceUsed();
            for (int i = 0; i < used - 1; i++) {
                if (text[i] == KITTY_PLACEHOLDER_HIGH && text[i + 1] == KITTY_PLACEHOLDER_LOW)
                    return true;
            }
        }
        return false;
    }

    /** U+10EEEE as a surrogate pair, which is how it sits in a row's char array. */
    private static final char KITTY_PLACEHOLDER_HIGH =
        Character.highSurrogate(KittyUnicodePlaceholder.CODE_POINT);
    private static final char KITTY_PLACEHOLDER_LOW =
        Character.lowSurrogate(KittyUnicodePlaceholder.CODE_POINT);

    /**
     * Whether any placement of {@code imageId} reaches into the {@code rowCount} rows starting at
     * external row {@code topRow} — what the user is actually looking at. Rows without an anchor
     * cost one field read, so this is cheap enough to ask on every animation frame.
     */
    boolean hasKittyImageInRows(long imageId, int topRow, int rowCount) {
        int firstRow = Math.max(-getActiveTranscriptRows(), topRow - mKittyMaxRows + 1);
        int lastRow = Math.min(mScreenRows, topRow + rowCount);
        for (int row = firstRow; row < lastRow; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || line.mKittyPlacements == null) continue;
            for (KittyPlacement placement : line.mKittyPlacements) {
                placement.row = row;
                if (placement.imageId == imageId && placement.intersectsRows(topRow, rowCount))
                    return true;
            }
        }
        return false;
    }

    /** Collect the live placements of one kitty image, for animation frame flips. */
    void collectKittyPlacements(long imageId, java.util.List<KittyPlacement> out) {
        for (int row = -getActiveTranscriptRows(); row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || line.mKittyPlacements == null) continue;
            for (KittyPlacement placement : line.mKittyPlacements) {
                if (placement.imageId == imageId) {
                    placement.row = row;
                    out.add(placement);
                }
            }
        }
    }

    /** Bytes owned by kitty placements in this buffer; a placement of a stored image owns none. */
    public long getKittyImageBytes() {
        long result = 0;
        for (TerminalRow line : mLines) {
            if (line == null || line.mKittyPlacements == null) continue;
            for (KittyPlacement placement : line.mKittyPlacements) result += placement.ownedBytes;
        }
        return result;
    }

    /** Selects kitty placements for deletion. Receives the anchor's external row (negative in scrollback). */
    public interface KittyPlacementFilter {
        boolean matches(KittyPlacement placement, int externalRow);
    }

    /** Delete kitty placements, either only those reaching the screen or also the scrollback's. A negative id matches all images. */
    public int deleteKittyImages(long imageId, boolean includeScrollback) {
        return deleteKittyImages((placement, row) ->
            imageId < 0 || placement.imageId == imageId, includeScrollback).size();
    }

    /**
     * Delete every placement the filter matches and return them. Without {@code includeScrollback}
     * only placements that reach into the screen are considered, which includes one whose anchor
     * has scrolled just above it.
     */
    public java.util.List<KittyPlacement> deleteKittyImages(KittyPlacementFilter filter, boolean includeScrollback) {
        java.util.List<KittyPlacement> deleted = new java.util.ArrayList<>();
        for (int row = -getActiveTranscriptRows(); row < mScreenRows; row++) {
            TerminalRow line = mLines[externalToInternalRow(row)];
            if (line == null || line.mKittyPlacements == null) continue;
            for (int i = line.mKittyPlacements.size() - 1; i >= 0; i--) {
                KittyPlacement placement = line.mKittyPlacements.get(i);
                placement.row = row;
                if (!includeScrollback && !placement.intersectsRows(0, mScreenRows)) continue;
                if (!filter.matches(placement, row)) continue;
                line.removeKittyPlacement(placement);
                deleted.add(placement);
            }
        }
        if (!deleted.isEmpty()) notifyKittyCellsCollected();
        return deleted;
    }

    /**
     * Notified after a sweep that could have left stored kitty images with no cell to display
     * them — a scroll past the transcript limit, or a cleared transcript.
     */
    interface UnreachableImageListener {
        void onKittyCellsCollected();
    }

    private UnreachableImageListener mUnreachableImageListener;

    void setUnreachableImageListener(UnreachableImageListener listener) {
        mUnreachableImageListener = listener;
    }

    private void notifyKittyCellsCollected() {
        if (mUnreachableImageListener != null) mUnreachableImageListener.onKittyCellsCollected();
    }

    public void bitmapGC(int timeDelta) {
        if (!hasBitmaps || bitmapLastGC + timeDelta > SystemClock.uptimeMillis()) {
            return;
        }
        collectUnusedBitmaps();
        bitmapLastGC = SystemClock.uptimeMillis();
        notifyKittyCellsCollected();
    }

    private void collectUnusedBitmaps() {
        Set<Integer> used = new HashSet<Integer>();
        for (int line = 0; line < mLines.length; line++) {
            if (mLines[line] != null && mLines[line].mHasBitmap) {
                for (int column = 0; column < mColumns; column++) {
                    final long st = mLines[line].getStyle(column);
                    if (TextStyle.isBitmap(st)) {
                        used.add((int) (st >> 16) & 0xffff);
                    }
                }
            }
        }
        Set<Integer> keys = new HashSet<Integer>(bitmaps.keySet());
        for (Integer bn : keys) {
            if (!used.contains(bn)) {
                bitmaps.remove(bn);
            }
        }
        hasBitmaps = !bitmaps.isEmpty();
    }
}
