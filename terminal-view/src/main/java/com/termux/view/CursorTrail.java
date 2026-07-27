package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;

/**
 * A short streak drawn between where the cursor was and where it is, so that a jump across the screen
 * is followed by the eye instead of being noticed after the fact.
 * <p>
 * This is a presentation effect only: it reads the cursor position and draws over the rendered frame,
 * and knows nothing about terminal cells or protocols. It costs nothing while the cursor is still,
 * since a frame is only requested while a streak is fading.
 * </p>
 */
final class CursorTrail {

    /** Shortest and longest a streak lasts, in milliseconds. Distance picks a value in between. */
    private static final long MIN_DURATION_MS = 70;

    private static final long MAX_DURATION_MS = 160;

    /** Cell distance mapped to {@link #MAX_DURATION_MS}. */
    private static final float DURATION_SATURATION_CELLS = 24f;

    /** Beyond this many rows the movement is a screen change rather than a cursor move. */
    private static final int MAX_TRAIL_ROWS = 8;

    /** Peak opacity of the streak, as a fraction of the cursor color's own alpha. */
    private static final float PEAK_ALPHA = 0.55f;

    private final Paint mPaint = new Paint();

    private final RectF mRect = new RectF();

    private boolean mEnabled = true;

    /**
     * The cell the cursor was last seen in, in the emulator's row coordinates so that scrolling the
     * view does not read as cursor movement. -1 when the cursor has not been seen yet.
     */
    private int mLastColumn = -1, mLastRow = -1;

    /** Pixel bounds the current streak runs between. */
    private float mFromLeft, mFromTop, mToLeft, mToTop;

    private float mCellWidth, mCellHeight;

    private long mAnimationStartMillis;

    private long mAnimationDurationMillis;

    CursorTrail() {
        mPaint.setAntiAlias(true);
    }

    void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled)
            mAnimationDurationMillis = 0;
    }

    boolean isEnabled() {
        return mEnabled;
    }

    /** Forget the cursor's position, so that the next frame starts no streak. Call on resize or reset. */
    void reset() {
        mLastColumn = mLastRow = -1;
        mAnimationDurationMillis = 0;
    }

    /**
     * Draw the streak for this frame, if any.
     *
     * @param row              the cursor row in the emulator's coordinates, not the view's.
     * @param topRow           the first row the view is showing, so negative when scrolled back.
     * @param verticalOffset   pixel offset of the first drawn row, matching {@link TerminalRenderer}.
     * @param cursorVisible    whether the cursor itself is being drawn. A hidden cursor leaves no
     *                         streak, and its movement is not remembered, so that unhiding it does not
     *                         streak from wherever it went while invisible.
     * @return true if another frame is needed to continue the animation.
     */
    boolean draw(Canvas canvas, int column, int row, int topRow, float cellWidth, float cellHeight, float horizontalOffset, float verticalOffset, int cursorColor, boolean cursorVisible) {
        if (!mEnabled || cellWidth <= 0 || cellHeight <= 0) {
            mLastColumn = mLastRow = -1;
            mAnimationDurationMillis = 0;
            return false;
        }
        if (!cursorVisible) {
            mLastColumn = mLastRow = -1;
            return drawInFlight(canvas, cursorColor);
        }
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mToLeft = horizontalOffset + column * cellWidth;
        mToTop = verticalOffset + (row - topRow) * cellHeight;
        if (mLastColumn >= 0 && (column != mLastColumn || row != mLastRow)) {
            int rowDistance = Math.abs(row - mLastRow);
            if (rowDistance <= MAX_TRAIL_ROWS) {
                mFromLeft = horizontalOffset + mLastColumn * cellWidth;
                mFromTop = verticalOffset + (mLastRow - topRow) * cellHeight;
                float distanceCells = Math.abs(column - mLastColumn) + rowDistance * 2f;
                float ratio = Math.min(1f, distanceCells / DURATION_SATURATION_CELLS);
                mAnimationDurationMillis = MIN_DURATION_MS + (long) ((MAX_DURATION_MS - MIN_DURATION_MS) * ratio);
                mAnimationStartMillis = SystemClock.uptimeMillis();
            } else {
                mAnimationDurationMillis = 0;
            }
        }
        mLastColumn = column;
        mLastRow = row;
        return drawInFlight(canvas, cursorColor);
    }

    private boolean drawInFlight(Canvas canvas, int cursorColor) {
        if (mAnimationDurationMillis <= 0)
            return false;
        long elapsed = SystemClock.uptimeMillis() - mAnimationStartMillis;
        if (elapsed >= mAnimationDurationMillis) {
            mAnimationDurationMillis = 0;
            return false;
        }
        float progress = (float) elapsed / mAnimationDurationMillis;
        // The tail catches up with the head, so the streak shrinks as it fades.
        float tailLeft = mFromLeft + (mToLeft - mFromLeft) * progress;
        float tailTop = mFromTop + (mToTop - mFromTop) * progress;
        mRect.set(Math.min(tailLeft, mToLeft), Math.min(tailTop, mToTop),
            Math.max(tailLeft, mToLeft) + mCellWidth, Math.max(tailTop, mToTop) + mCellHeight);
        int alpha = (int) (((cursorColor >>> 24) & 0xff) * PEAK_ALPHA * (1f - progress));
        if (alpha > 0) {
            mPaint.setColor((cursorColor & 0x00ffffff) | (alpha << 24));
            float radius = mCellWidth * 0.25f;
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
        }
        return true;
    }
}
