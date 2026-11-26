package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Where the marks row's touch draws, pure: the glass ground that hugs the widened line, the frame
 * of the view that holds it and the title, and where the title stands. Positions run along the
 * line in the rest layout's coordinates ({@link MarksTouchLayout}'s), across it from the line's
 * centre; the view maps them to x and y.
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

    /**
     * @param touch the layout made at touch-down
     * @param markPx the rest mark's box
     * @param density display density, for the pad
     * @param lineStartPx where the row starts, along the line
     * @param lineEndPx where the row ends, along the line
     * @param titleMaxPx the most the title may take ({@link #titleMaxPx}); 0 for no title
     */
    public MarksZoomPlan(@NonNull MarksTouchLayout touch, float markPx, float density,
                         float lineStartPx, float lineEndPx, float titleMaxPx) {
        mLineStart = lineStartPx;
        mLineEnd = Math.max(lineStartPx, lineEndPx);
        float pad = GROUND_PAD_DP * density;
        float zoomed = markPx * MarksTouchLayout.ZOOM_AT_FINGER;
        // The current page's ring is wider than its cell: the ground reaches past it.
        float ring = PageMarkShapes.RING_W * zoomed + PageMarkShapes.ringStroke(zoomed);
        float overhang = Math.max(0f, (ring - touch.pitchPx()) / 2f);
        mGroundStart = touch.contentStartPx() - pad - overhang;
        mGroundEnd = touch.contentStartPx() + touch.contentLengthPx() + pad + overhang;
        mCrossHalf = (PageMarkShapes.RING_H * zoomed + PageMarkShapes.ringStroke(zoomed)) / 2f
            + pad;
        mTitleWidth = Math.max(0f, Math.min(titleMaxPx, (mLineEnd - mLineStart) / 2f));
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

    /** Half the ground's extent across the line: tall enough for the zoomed mark and its ring. */
    public float crossHalf() {
        return mCrossHalf;
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
