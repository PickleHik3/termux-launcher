package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Where the marks row's touch draws, pure: the frame of the view that holds the zoomed marks and
 * the title, and where the title stands. Positions run along the line in the rest layout's
 * coordinates, and across it in the line view's own coordinates; the view maps them to x and y.
 * There is no ground behind the marks: the zoomed marks are centred on the row's middle across the
 * line, and the frame holds the row and the marks where they are taller or longer than it.
 *
 * <p>Made once at touch-down from the touch layout; a move asks only {@link #titleStartAt} and
 * {@link #titleAtStart}, which are arithmetic.
 */
public final class MarksZoomPlan {

    /** The title's text size, as a share of the rest mark's box. */
    public static final float TITLE_TEXT_SHARE = 0.9f;
    /** The most of the line a title takes when there are no readings to take the place of. */
    public static final float TITLE_LINE_SHARE = 1f / 3f;
    /** The title capsule's even inset at its ends, in dp. */
    public static final float CAPSULE_INSET_DP = 6f;
    /** The readings fade away and back, in ms (at once under reduced motion). */
    public static final long READINGS_FADE_MS = 120L;
    /** The title's travel round the row's ends, in ms (at once under reduced motion). */
    public static final long TITLE_SLIDE_MS = 160L;

    private final float mLineStart;
    private final float mLineEnd;
    private final float mFrameStart;
    private final float mFrameEnd;
    private final float mTitleWidth;
    private final float mRowCrossStart;
    private final float mRowCrossEnd;
    private final float mFrameCrossStart;
    private final float mFrameCrossEnd;

    /**
     * @param touch the layout made at touch-down
     * @param markPx the rest mark's box
     * @param lineStartPx where the row starts, along the line
     * @param lineEndPx where the row ends, along the line
     * @param titleMaxPx the most the title may take ({@link #titleMaxPx}); 0 for no title
     * @param rowCrossStartPx where the row starts across the line (its top, or a column's left)
     * @param rowCrossEndPx where the row ends across the line
     */
    public MarksZoomPlan(@NonNull MarksTouchLayout touch, float markPx, float lineStartPx,
                         float lineEndPx, float titleMaxPx, float rowCrossStartPx,
                         float rowCrossEndPx) {
        mLineStart = lineStartPx;
        mLineEnd = Math.max(lineStartPx, lineEndPx);
        mFrameStart = Math.min(mLineStart, touch.contentStartPx());
        mFrameEnd = Math.max(mLineEnd, touch.contentStartPx() + touch.contentLengthPx());
        mTitleWidth = Math.max(0f, Math.min(titleMaxPx, (mLineEnd - mLineStart) / 2f));
        mRowCrossStart = rowCrossStartPx;
        mRowCrossEnd = Math.max(rowCrossStartPx, rowCrossEndPx);
        float half = markPx * MarksTouchLayout.ZOOM_AT_FINGER / 2f;
        float middle = (mRowCrossStart + mRowCrossEnd) / 2f;
        mFrameCrossStart = Math.min(mRowCrossStart, middle - half);
        mFrameCrossEnd = Math.max(mRowCrossEnd, middle + half);
    }

    /** The title capsule's width round a text {@code textPx} wide: the text and the inset at both ends. */
    public static float capsuleWidthPx(float textPx, float density) {
        return textPx + 2f * CAPSULE_INSET_DP * density;
    }

    /**
     * Whether a mark centred at {@code centrePx} along the line lies under the title capsule
     * {@code widthPx} wide at {@code progress} of its travel, wherever it stands (against the end,
     * leaving past it, or coming in from the start): the capsule has priority, so that mark is not
     * drawn.
     */
    public boolean covers(float centrePx, float progress, float widthPx) {
        if (mTitleWidth <= 0f || widthPx <= 0f) return false;
        float start = titleStartAt(progress, widthPx);
        if (centrePx >= start && centrePx <= start + widthPx) return true;
        if (progress <= 0f) return false;
        float wrap = titleWrapStartAt(progress, widthPx);
        return centrePx >= wrap && centrePx <= wrap + widthPx;
    }

    /** Where the row starts across the line: the capsule's top. */
    public float rowCrossStart() {
        return mRowCrossStart;
    }

    /** Where the row ends across the line: the capsule's bottom. */
    public float rowCrossEnd() {
        return mRowCrossEnd;
    }

    /**
     * The room a title may take: the readings' width it stands in for, or a share of the line when
     * there are none; never more than half the line.
     */
    public static float titleMaxPx(float readingsPx, float lineLengthPx) {
        float room = readingsPx > 0f ? readingsPx : lineLengthPx * TITLE_LINE_SHARE;
        return Math.max(0f, Math.min(room, lineLengthPx / 2f));
    }

    /** Where the zoomed marks and the title are centred across the line: the row's middle. */
    public float markCross() {
        return (mRowCrossStart + mRowCrossEnd) / 2f;
    }

    /** Where the view holding the marks and the title starts across the line. */
    public float frameCrossStart() {
        return mFrameCrossStart;
    }

    /** Where the view holding the marks and the title ends across the line. */
    public float frameCrossEnd() {
        return mFrameCrossEnd;
    }

    /** Where the view holding the marks and the title starts along the line. */
    public float frameStart() {
        return mFrameStart;
    }

    /** Where the view holding the marks and the title ends along the line. */
    public float frameEnd() {
        return mFrameEnd;
    }

    public float lineStart() {
        return mLineStart;
    }

    public float lineEnd() {
        return mLineEnd;
    }

    /** The title's extent along the line; 0 for none. */
    public float titleWidth() {
        return mTitleWidth;
    }

    /**
     * Whether the finger at {@code fingerPos} is over the title's place at the row's end (the most
     * a title may take, so the answer does not flicker as names change), so the title goes round to
     * the start.
     */
    public boolean titleAtStart(float fingerPos) {
        return mTitleWidth > 0f && fingerPos >= mLineEnd - mTitleWidth;
    }

    /**
     * {@link #titleAtStart(float)} for the finger and for the mark the focus holds under it: the
     * title keeps clear of the real finger and of the focused mark, which past an end of the
     * marks is not where the finger is.
     */
    public boolean titleAtStart(float fingerPos, float focusPos) {
        return titleAtStart(fingerPos) || titleAtStart(focusPos);
    }

    /**
     * Where a title {@code widthPx} long starts along the line at {@code progress} of its travel:
     * 0 against the row's end, 1 against its start. The row is one continuous surface, so the title
     * leaves past the end while its other half ({@link #titleWrapStartAt}) comes in from the start;
     * the view clips both to the line.
     */
    public float titleStartAt(float progress, float widthPx) {
        float p = Math.max(0f, Math.min(1f, progress));
        return mLineEnd - widthPx + p * widthPx;
    }

    /** The same title one line's length back: the part that comes in from the row's start. */
    public float titleWrapStartAt(float progress, float widthPx) {
        return titleStartAt(progress, widthPx) - (mLineEnd - mLineStart);
    }
}
