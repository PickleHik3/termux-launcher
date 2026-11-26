package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Where the marks row's touch draws, pure: the glass ground that hugs the widened line, the frame
 * of the view that holds it and the title, and where the title stands. Positions run along the
 * line in the rest layout's coordinates ({@link MarksTouchLayout}'s), and across it in the line
 * view's own coordinates; the view maps them to x and y.
 *
 * <p>Across the line the ground stays inside the row when it fits. Taller than the row, it reaches
 * at most the row's touch reach outward (toward the screen's edge: above a row at the top, where
 * the pane's edge and the system bar are) and the rest of its height inward, over the pane's first
 * text line. The zoomed marks are centred in the ground; the title stays on the row's centre.
 *
 * <p>Made once at touch-down from the touch layout; a move asks only {@link #titleStartAt} and
 * {@link #titleAtStart}, which are arithmetic.
 */
public final class MarksZoomPlan {

    /** The ground's room around the zoomed marks, in dp. */
    public static final float GROUND_PAD_DP = 4f;
    /** The title's text size, as a share of the rest mark's box. */
    public static final float TITLE_TEXT_SHARE = 0.9f;
    /** The most of the line a title takes when there are no readings to take the place of. */
    public static final float TITLE_LINE_SHARE = 1f / 3f;
    /** The readings fade away and back, in ms (at once under reduced motion). */
    public static final long READINGS_FADE_MS = 120L;
    /** The title's travel round the row's ends, in ms (at once under reduced motion). */
    public static final long TITLE_SLIDE_MS = 160L;

    private final float mLineStart;
    private final float mLineEnd;
    private final float mGroundStart;
    private final float mGroundEnd;
    private final float mCrossHalf;
    private final float mTitleWidth;
    private final float mRowCrossStart;
    private final float mRowCrossEnd;
    private final float mGroundCrossStart;

    /**
     * @param touch the layout made at touch-down
     * @param markPx the rest mark's box
     * @param density display density, for the pad
     * @param lineStartPx where the row starts, along the line
     * @param lineEndPx where the row ends, along the line
     * @param titleMaxPx the most the title may take ({@link #titleMaxPx}); 0 for no title
     * @param rowCrossStartPx where the row starts across the line (its top, or a column's left)
     * @param rowCrossEndPx where the row ends across the line
     * @param outwardAtStart whether the screen's edge lies on the start side across the line
     * @param reachOutPx the most the ground may stand past the row on the screen's-edge side
     */
    public MarksZoomPlan(@NonNull MarksTouchLayout touch, float markPx, float density,
                         float lineStartPx, float lineEndPx, float titleMaxPx,
                         float rowCrossStartPx, float rowCrossEndPx, boolean outwardAtStart,
                         float reachOutPx) {
        mLineStart = lineStartPx;
        mLineEnd = Math.max(lineStartPx, lineEndPx);
        float pad = GROUND_PAD_DP * density;
        float zoomed = markPx * MarksTouchLayout.ZOOM_AT_FINGER;
        mGroundStart = touch.contentStartPx() - pad;
        mGroundEnd = touch.contentStartPx() + touch.contentLengthPx() + pad;
        mCrossHalf = zoomed / 2f + pad;
        mTitleWidth = Math.max(0f, Math.min(titleMaxPx, (mLineEnd - mLineStart) / 2f));
        mRowCrossStart = rowCrossStartPx;
        mRowCrossEnd = Math.max(rowCrossStartPx, rowCrossEndPx);
        mGroundCrossStart = groundCrossStart(mRowCrossStart, mRowCrossEnd, 2f * mCrossHalf,
            outwardAtStart, Math.max(0f, reachOutPx));
    }

    /**
     * Where a ground {@code heightPx} tall starts across a row from {@code rowStart} to
     * {@code rowEnd}: centred inside the row when it fits; otherwise past the row by at most
     * {@code reachOut} on the outward side and by the rest on the inward side.
     */
    static float groundCrossStart(float rowStart, float rowEnd, float heightPx,
                                  boolean outwardAtStart, float reachOut) {
        float extra = heightPx - (rowEnd - rowStart);
        if (extra <= 0f) return rowStart - extra / 2f;
        float out = Math.min(reachOut, extra);
        return outwardAtStart ? rowStart - out : rowStart - (extra - out);
    }

    /**
     * The room a title may take: the readings' width it stands in for, or a share of the line when
     * there are none; never more than half the line.
     */
    public static float titleMaxPx(float readingsPx, float lineLengthPx) {
        float room = readingsPx > 0f ? readingsPx : lineLengthPx * TITLE_LINE_SHARE;
        return Math.max(0f, Math.min(room, lineLengthPx / 2f));
    }

    public float groundStart() {
        return mGroundStart;
    }

    public float groundEnd() {
        return mGroundEnd;
    }

    /** Half the ground's extent across the line: tall enough for the zoomed mark. */
    public float crossHalf() {
        return mCrossHalf;
    }

    /** Where the ground starts across the line. */
    public float groundCrossStart() {
        return mGroundCrossStart;
    }

    /** Where the ground ends across the line. */
    public float groundCrossEnd() {
        return mGroundCrossStart + 2f * mCrossHalf;
    }

    /** Where the zoomed marks are centred across the line: the ground's middle. */
    public float markCross() {
        return mGroundCrossStart + mCrossHalf;
    }

    /** Where the title is centred across the line: the row's middle, the readings' line. */
    public float titleCross() {
        return (mRowCrossStart + mRowCrossEnd) / 2f;
    }

    /** Where the view holding the ground and the title starts across the line. */
    public float frameCrossStart() {
        return Math.min(mGroundCrossStart, mRowCrossStart);
    }

    /** Where the view holding the ground and the title ends across the line. */
    public float frameCrossEnd() {
        return Math.max(groundCrossEnd(), mRowCrossEnd);
    }

    /** The ground's corner radius: half its height, a capsule. */
    public float groundRadius() {
        return mCrossHalf;
    }

    /** Where the view holding the ground and the title starts along the line. */
    public float frameStart() {
        return Math.min(mGroundStart, mLineStart);
    }

    /** Where the view holding the ground and the title ends along the line. */
    public float frameEnd() {
        return Math.max(mGroundEnd, mLineEnd);
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
