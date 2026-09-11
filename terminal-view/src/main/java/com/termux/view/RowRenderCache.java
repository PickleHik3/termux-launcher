package com.termux.view;

import androidx.annotation.Nullable;

import com.termux.terminal.KittyUnicodePlaceholder;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import java.util.Arrays;

/**
 * Which of the visible rows have to be drawn again this frame.
 *
 * <p>A TUI streaming into the pane changes a handful of rows per frame and leaves the rest exactly
 * as they were, but the render loop has no way to know that: it walks every visible row twice per
 * draw, shaping and measuring text that is already on screen. This is the memory that makes the
 * difference visible — everything one row is drawn from, retained and compared as values, so an
 * unchanged row can be replayed instead of re-derived.
 *
 * <p>The comparison is exact rather than hashed. A hash collision here is a cell that silently
 * stops updating, and the inputs are small: a row's used text, its widths, one style long per
 * column, the two side tables, and the slice of screen-wide state that lands on this row — where
 * the cursor is, what the selection covers. Anything wider than one row (the emulator itself, the
 * geometry, the palette, the scroll position) invalidates every row at once, which is what
 * {@link #beginFrame} decides.
 *
 * <p>Two kinds of row draw from image state this class cannot see: a row carrying a bitmap, and a
 * row carrying a kitty placeholder, whose text is stable while the image under it is replaced.
 * Those rows are reported by {@link #rowCarriesAnImage} rather than by {@link #rowChanged}, so the
 * frame that redraws their pixels does not also re-shape their text — the two answers are recorded
 * into different nodes.
 *
 * <p>What those rows draw from is still comparable, one step removed: the emulator stamps each
 * stored image with a generation that moves when its pixels do. A row remembers the images it was
 * last recorded from and their stamps ({@link #noteRowImage}), and {@link #rowImagesMoved} asks
 * whether any of them has moved since — so an animation costs a row's image node once per frame
 * of the animation rather than once per frame of the display.
 *
 * <p>This class knows nothing about how a row is drawn or what it is drawn into — see
 * {@link TerminalRowNodes} for that half.
 */
final class RowRenderCache {

    /** The kitty placeholder as it is stored in a row's char[]: it is a supplementary code point. */
    private static final char PLACEHOLDER_HIGH =
        Character.highSurrogate(KittyUnicodePlaceholder.CODE_POINT);
    private static final char PLACEHOLDER_LOW =
        Character.lowSurrogate(KittyUnicodePlaceholder.CODE_POINT);

    private static final char[] NO_CHARS = new char[0];
    private static final byte[] NO_BYTES = new byte[0];
    private static final long[] NO_LONGS = new long[0];
    private static final int[] NO_INTS = new int[0];
    private static final Row[] NO_ROWS = new Row[0];

    /** Everything one row's drawing was last derived from. */
    private static final class Row {
        boolean recorded;
        @Nullable TerminalRow line;
        int spaceUsed;
        char[] text = NO_CHARS;
        byte[] widths = NO_BYTES;
        int textLength;
        long[] style = NO_LONGS;
        int styleLength;
        boolean hasDecorationColors;
        int[] decorationColors = NO_INTS;
        boolean hasHyperlinks;
        int[] hyperlinkIds = NO_INTS;
        /** Summary of the URL underlines drawn over the row; see {@link UrlUnderlines#keyFor}. */
        int urlKey;
        /** Drawn from image state this class cannot compare directly; see {@link #imageIds}. */
        boolean carriesAnImage;
        /** Holds a sixel, iTerm or kitty-placement cell, whose pixels the emulator swaps in place. */
        boolean carriesABitmapCell;
        /** The images this row's image node was last recorded from, and their stamps then. */
        long[] imageIds = NO_LONGS;
        long[] imageGenerations = NO_LONGS;
        int imageCount;
        /** More distinct images on one row than are tracked: such a row is never reported clean. */
        boolean imagesUntracked = true;
        /** The placement stamp when this row's bitmap cells were last recorded. */
        long placementGeneration;
        /** The column the cursor is drawn at on this row, or -1 when it is not drawn here. */
        int cursorColumn = -1;
        int cursorShape;
        int cursorColor;
        int selectionStart = -1;
        int selectionEnd = -1;
    }

    private Row[] mRows = NO_ROWS;
    private int mVisibleRows;

    /** Raised from outside, and consumed by the next frame. */
    private boolean mInvalidated = true;

    /** Whether the frame {@link #beginFrame} opened has to record every row. */
    private boolean mAllDirty = true;

    @Nullable private Object mEmulator;
    private int mEmulatorRows = -1;
    private int mColumns = -1;
    private int mTopRow = Integer.MIN_VALUE;
    private int mExtraRows = -1;
    private float mHorizontalOffset = Float.NaN;
    private boolean mTransparentBackground;
    private int mTransparentOverlayColor;
    private boolean mReverseVideo;
    private boolean mBoldWithBright;
    private int mViewWidth = -1;
    private int mViewHeight = -1;
    /**
     * A copy of the palette, because {@code mColors.mCurrentColors} is mutated in place: an OSC 4
     * from the shell repaints the screen without replacing the array.
     */
    private int[] mPalette = NO_INTS;

    /**
     * How many distinct images one row's clean answer is worth tracking. A row is a strip of one
     * image in every case that matters — a logo, a plot, an image grid one row tall — and the cost
     * of the check is linear in this, so a row that somehow references more images than this is
     * left redrawing every frame instead.
     */
    private static final int MAX_TRACKED_IMAGES = 8;

    /** Answers the current generation of a stored image; see {@link #rowImagesMoved}. */
    interface ImageGenerations {
        long generationOf(long imageId);
    }

    /** Force every row to be recorded again on the next frame. */
    void invalidate() {
        mInvalidated = true;
    }

    /** How many visible rows the last {@link #beginFrame} was told about. */
    int visibleRows() {
        return mVisibleRows;
    }

    /**
     * Take in the state every row of this frame shares, and report whether it moved. Anything that
     * changes how the whole screen draws is settled here so the per-row comparison stays local.
     *
     * @return true when every row has to be recorded again.
     */
    boolean beginFrame(Object emulator, int emulatorRows, int columns, int topRow, int extraRows,
                       float horizontalOffset, boolean transparentBackground,
                       int transparentOverlayColor, boolean reverseVideo, boolean boldWithBright,
                       int[] palette, int viewWidth, int viewHeight, int visibleRows) {
        boolean dirty = mInvalidated
            || mEmulator != emulator
            || mEmulatorRows != emulatorRows
            || mColumns != columns
            || mTopRow != topRow
            || mExtraRows != extraRows
            // Compared bitwise, so that a NaN offset is only equal to the NaN before it.
            || Float.floatToRawIntBits(mHorizontalOffset)
                != Float.floatToRawIntBits(horizontalOffset)
            || mTransparentBackground != transparentBackground
            || mTransparentOverlayColor != transparentOverlayColor
            || mReverseVideo != reverseVideo
            || mBoldWithBright != boldWithBright
            || mViewWidth != viewWidth
            || mViewHeight != viewHeight
            || mVisibleRows != visibleRows;
        if (paletteMoved(palette)) dirty = true;
        mInvalidated = false;
        mEmulator = emulator;
        mEmulatorRows = emulatorRows;
        mColumns = columns;
        mTopRow = topRow;
        mExtraRows = extraRows;
        mHorizontalOffset = horizontalOffset;
        mTransparentBackground = transparentBackground;
        mTransparentOverlayColor = transparentOverlayColor;
        mReverseVideo = reverseVideo;
        mBoldWithBright = boldWithBright;
        mViewWidth = viewWidth;
        mViewHeight = viewHeight;
        mVisibleRows = visibleRows;
        resize(visibleRows);
        mAllDirty = dirty;
        return dirty;
    }

    /**
     * Whether the text, style, cursor or selection of the row at this visible index moved, so that
     * its glyphs have to be recorded again — remembering what they will be recorded from either
     * way. Says nothing about the row's images: ask {@link #rowCarriesAnImage} for those. Must be
     * called once per visible row, in order, after {@link #beginFrame}.
     */
    boolean rowChanged(int index, TerminalRow line, int columns, int cursorColumn, int cursorShape,
                       int cursorColor, int selectionStart, int selectionEnd, int urlKey) {
        final Row state = mRows[index];
        boolean changed = mAllDirty || !state.recorded || state.line != line;
        // The cursor's shape and colour only reach the row it is drawn on.
        if (state.cursorColumn != cursorColumn
            || (cursorColumn >= 0
                && (state.cursorShape != cursorShape || state.cursorColor != cursorColor)))
            changed = true;
        if (state.selectionStart != selectionStart || state.selectionEnd != selectionEnd)
            changed = true;
        if (state.urlKey != urlKey) changed = true;
        state.urlKey = urlKey;
        state.line = line;
        state.cursorColumn = cursorColumn;
        state.cursorShape = cursorShape;
        state.cursorColor = cursorColor;
        state.selectionStart = selectionStart;
        state.selectionEnd = selectionEnd;

        final int spaceUsed = line.getSpaceUsed();
        if (state.spaceUsed != spaceUsed) changed = true;
        state.spaceUsed = spaceUsed;
        if (captureText(state, line, spaceUsed)) changed = true;
        if (captureStyle(state, line, columns)) changed = true;
        if (captureDecorationColors(state, line, columns)) changed = true;
        if (captureHyperlinks(state, line, columns)) changed = true;
        state.recorded = true;
        return changed;
    }

    /**
     * Whether the row at this visible index holds a kitty placeholder or a bitmap cell, as seen by
     * the last {@link #rowChanged} for it. Such a row's pixels can be replaced without anything
     * this class compares moving, so its image draws are re-recorded every frame — and the frame it
     * stops carrying an image is the last frame it costs anything, because the answer is read after
     * the row's state was captured.
     */
    boolean rowCarriesAnImage(int index) {
        return mRows[index].carriesAnImage;
    }

    /**
     * Whether the pixels the row at this visible index draws its images from have moved since its
     * image node was last recorded — or were never recorded at all. Call it only for a row
     * {@link #rowCarriesAnImage} reported, after that row's {@link #rowChanged}.
     *
     * <p>The set of images a row references comes out of its own text, which {@link #rowChanged}
     * compares, so a row whose text held still references the same images as when it was recorded
     * and the stamps of exactly those images are the whole answer. An image that has gone, or has
     * not arrived yet, stamps as 0 — a value no stored image has — so the cell that draws nothing
     * today is redrawn on the frame its image lands.
     */
    boolean rowImagesMoved(int index, long placementGeneration, ImageGenerations generations) {
        final Row state = mRows[index];
        if (state.imagesUntracked) return true;
        if (state.carriesABitmapCell && state.placementGeneration != placementGeneration)
            return true;
        for (int i = 0; i < state.imageCount; i++) {
            if (generations.generationOf(state.imageIds[i]) != state.imageGenerations[i])
                return true;
        }
        return false;
    }

    /**
     * Start collecting what the row at this visible index is being recorded from, forgetting what
     * it was recorded from before. Every image the recording pass draws — and every one it tried
     * to and could not — is then reported through {@link #noteRowImage}.
     */
    void beginRowImages(int index, long placementGeneration) {
        final Row state = mRows[index];
        state.imageCount = 0;
        state.imagesUntracked = false;
        state.placementGeneration = placementGeneration;
    }

    /**
     * Remember that the row being recorded draws from this image at this generation. Repeats are
     * folded, because a placeholder grid references its image once per cell.
     */
    void noteRowImage(int index, long imageId, long generation) {
        final Row state = mRows[index];
        if (state.imagesUntracked) return;
        for (int i = 0; i < state.imageCount; i++) {
            if (state.imageIds[i] == imageId) {
                state.imageGenerations[i] = generation;
                return;
            }
        }
        if (state.imageCount == MAX_TRACKED_IMAGES) {
            state.imagesUntracked = true;
            state.imageCount = 0;
            return;
        }
        if (state.imageIds.length == 0) {
            state.imageIds = new long[MAX_TRACKED_IMAGES];
            state.imageGenerations = new long[MAX_TRACKED_IMAGES];
        }
        state.imageIds[state.imageCount] = imageId;
        state.imageGenerations[state.imageCount] = generation;
        state.imageCount++;
    }

    /** True when the palette's contents moved; the copy is refreshed either way. */
    private boolean paletteMoved(int[] palette) {
        if (mPalette.length != palette.length) {
            mPalette = palette.clone();
            return true;
        }
        boolean moved = false;
        for (int i = 0; i < palette.length; i++) {
            if (mPalette[i] != palette[i]) {
                mPalette[i] = palette[i];
                moved = true;
            }
        }
        return moved;
    }

    private void resize(int visibleRows) {
        if (mRows.length == visibleRows) return;
        Row[] resized = new Row[visibleRows];
        final int kept = Math.min(mRows.length, visibleRows);
        System.arraycopy(mRows, 0, resized, 0, kept);
        for (int i = kept; i < visibleRows; i++) resized[i] = new Row();
        mRows = resized;
    }

    /**
     * The used prefix of the row's text and of its stored cell widths. The widths are stored
     * rather than derived, and {@code widenCell} moves one without touching the text, so both are
     * compared. The kitty placeholder is recognised here, on the pass that is already reading
     * every char.
     */
    private static boolean captureText(Row state, TerminalRow line, int spaceUsed) {
        boolean changed = state.textLength != spaceUsed;
        if (state.text.length < spaceUsed) {
            state.text = new char[spaceUsed];
            state.widths = new byte[spaceUsed];
            changed = true;
        }
        final char[] text = line.mText;
        boolean placeholder = false;
        for (int i = 0; i < spaceUsed; i++) {
            final char c = text[i];
            if (state.text[i] != c) {
                state.text[i] = c;
                changed = true;
            }
            final byte width = (byte) line.getDisplayWidthAt(i);
            if (state.widths[i] != width) {
                state.widths[i] = width;
                changed = true;
            }
            if (c == PLACEHOLDER_HIGH && i + 1 < spaceUsed && text[i + 1] == PLACEHOLDER_LOW)
                placeholder = true;
        }
        state.textLength = spaceUsed;
        state.carriesABitmapCell = line.mHasBitmap;
        state.carriesAnImage = placeholder || line.mHasBitmap;
        return changed;
    }

    private static boolean captureStyle(Row state, TerminalRow line, int columns) {
        boolean changed = state.styleLength != columns;
        if (state.style.length < columns) {
            state.style = new long[columns];
            changed = true;
        }
        for (int column = 0; column < columns; column++) {
            final long style = line.getStyle(column);
            if (state.style[column] != style) {
                state.style[column] = style;
                changed = true;
            }
            // A sixel cell draws from a bitmap the buffer owns, which can be replaced under an
            // unchanged style. The row flag says the same thing; this does not trust it alone.
            if (TextStyle.isBitmap(style)) {
                state.carriesAnImage = true;
                state.carriesABitmapCell = true;
            }
        }
        state.styleLength = columns;
        return changed;
    }

    private static boolean captureDecorationColors(Row state, TerminalRow line, int columns) {
        final boolean has = line.hasDecorationColors();
        boolean changed = state.hasDecorationColors != has;
        state.hasDecorationColors = has;
        if (!has) return changed;
        if (state.decorationColors.length < columns) {
            state.decorationColors = new int[columns];
            Arrays.fill(state.decorationColors, TextStyle.DECORATION_COLOR_DEFAULT);
            changed = true;
        }
        for (int column = 0; column < columns; column++) {
            final int color = line.getDecorationColor(column);
            if (state.decorationColors[column] != color) {
                state.decorationColors[column] = color;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean captureHyperlinks(Row state, TerminalRow line, int columns) {
        final boolean has = line.hasHyperlinks();
        boolean changed = state.hasHyperlinks != has;
        state.hasHyperlinks = has;
        if (!has) return changed;
        if (state.hyperlinkIds.length < columns) {
            state.hyperlinkIds = new int[columns];
            changed = true;
        }
        for (int column = 0; column < columns; column++) {
            final int id = line.getHyperlinkId(column);
            if (state.hyperlinkIds[column] != id) {
                state.hyperlinkIds[column] = id;
                changed = true;
            }
        }
        return changed;
    }
}
