package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
    private static final long MIN_DURATION_MS = 60;

    private static final long MAX_DURATION_MS = 120;

    /** Cell distance mapped to {@link #MAX_DURATION_MS}. */
    private static final float DURATION_SATURATION_CELLS = 24f;

    /** Beyond this many rows the movement is a screen change rather than a cursor move. */
    private static final int MAX_TRAIL_ROWS = 8;

    /**
     * Moves shorter than this many cells are not animated. Typing advances the cursor one column per
     * keystroke, and a streak on every keystroke is noise rather than a cue.
     */
    private static final int MIN_TRAIL_CELLS = 2;

    /**
     * Peak opacity of the streak, as a fraction of the cursor color's own alpha. Kept low because the
     * streak is drawn over the text it passes, so anything heavier washes out glyphs on its way.
     */
    private static final float PEAK_ALPHA = 0.3f;

    private final Paint mPaint = new Paint();

    /** The streak outline, rebuilt each frame. Reused so the render loop stays allocation free. */
    private final Path mPath = new Path();

    /** Scratch for the corners of the two cursor cells, as x,y pairs. */
    private final float[] mCorners = new float[16];

    /** Scratch for the hull of {@link #mCorners}, as indices into it. */
    private final int[] mHull = new int[9];

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
     * @param cursorEnabled    whether the program is showing a cursor at all. Deliberately not
     *                         "is the cursor being drawn this frame": a blinking cursor is invisible
     *                         half the time, and gating on that made a streak appear or not depending
     *                         on the blink phase at the moment the cursor moved. A cursor the program
     *                         has hidden does leave no streak, and its movement is not remembered, so
     *                         that unhiding it does not streak from wherever it went while hidden.
     * @return true if another frame is needed to continue the animation.
     */
    boolean draw(Canvas canvas, int column, int row, int topRow, float cellWidth, float cellHeight, float horizontalOffset, float verticalOffset, int cursorColor, boolean cursorEnabled) {
        if (!mEnabled || cellWidth <= 0 || cellHeight <= 0) {
            mLastColumn = mLastRow = -1;
            mAnimationDurationMillis = 0;
            return false;
        }
        if (!cursorEnabled) {
            mLastColumn = mLastRow = -1;
            return drawInFlight(canvas, cursorColor);
        }
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mToLeft = horizontalOffset + column * cellWidth;
        mToTop = verticalOffset + (row - topRow) * cellHeight;
        if (mLastColumn >= 0 && (column != mLastColumn || row != mLastRow)) {
            int rowDistance = Math.abs(row - mLastRow);
            int cellDistance = Math.max(Math.abs(column - mLastColumn), rowDistance);
            if (rowDistance <= MAX_TRAIL_ROWS && cellDistance >= MIN_TRAIL_CELLS) {
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
        // The tail catches up with the head, easing out so most of the streak is gone early and it
        // cannot be mistaken for a selection while it lingers.
        float eased = 1f - (1f - progress) * (1f - progress);
        float tailLeft = mFromLeft + (mToLeft - mFromLeft) * eased;
        float tailTop = mFromTop + (mToTop - mFromTop) * eased;
        float fade = (1f - progress) * (float) Math.sqrt(1f - progress);
        int alpha = (int) (((cursorColor >>> 24) & 0xff) * PEAK_ALPHA * fade);
        if (alpha > 0) {
            mPaint.setColor((cursorColor & 0x00ffffff) | (alpha << 24));
            buildSmearPath(tailLeft, tailTop, mToLeft, mToTop);
            canvas.drawPath(mPath, mPaint);
        }
        return true;
    }

    /**
     * Build the streak as the convex hull of the two cursor cells, rather than the rectangle that
     * bounds them.
     * <p>
     * The difference only shows on a diagonal move, and there it is the whole point: the bounding box
     * of a jump nine columns across and five rows down is a 45 cell block that tints every glyph
     * inside it, while the hull is a band one cell wide running along the direction of travel. For a
     * purely horizontal or vertical move the hull collapses to the same rectangle as before.
     * </p>
     */
    private void buildSmearPath(float tailLeft, float tailTop, float headLeft, float headTop) {
        float w = mCellWidth, h = mCellHeight;
        float[] c = mCorners;
        c[0] = tailLeft;     c[1] = tailTop;
        c[2] = tailLeft + w; c[3] = tailTop;
        c[4] = tailLeft + w; c[5] = tailTop + h;
        c[6] = tailLeft;     c[7] = tailTop + h;
        c[8] = headLeft;     c[9] = headTop;
        c[10] = headLeft + w; c[11] = headTop;
        c[12] = headLeft + w; c[13] = headTop + h;
        c[14] = headLeft;     c[15] = headTop + h;
        int count = convexHull(mCorners, mHull);
        mPath.rewind();
        for (int i = 0; i < count; i++) {
            int p = mHull[i] * 2;
            if (i == 0) {
                mPath.moveTo(c[p], c[p + 1]);
            } else {
                mPath.lineTo(c[p], c[p + 1]);
            }
        }
        mPath.close();
    }

    /**
     * Andrew's monotone chain over eight corners, writing point indices into {@code hull}. Eight
     * points is small enough that the general algorithm costs less than the case analysis for the
     * eight directions a cursor can move in, and it cannot get one of them wrong.
     *
     * <p>Static and free of Android types so the geometry can be tested on its own.</p>
     *
     * @return how many entries of {@code hull} are used.
     */
    static int convexHull(float[] corners, int[] hull) {
        // Order the points by x then y, which is what the chain needs.
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = 1; i < order.length; i++) {
            int key = order[i];
            int j = i - 1;
            while (j >= 0 && comparePoints(corners, order[j], key) > 0) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
        int count = 0;
        // Lower hull, then upper hull, each keeping only counter-clockwise turns.
        for (int pass = 0; pass < 2; pass++) {
            int start = count;
            for (int i = 0; i < order.length; i++) {
                int index = (pass == 0) ? order[i] : order[order.length - 1 - i];
                while (count >= start + 2 && cross(corners, hull[count - 2], hull[count - 1], index) <= 0) count--;
                hull[count++] = index;
            }
            // The last point of each pass is the first of the next, so drop it.
            count--;
        }
        return count;
    }

    private static int comparePoints(float[] corners, int a, int b) {
        float ax = corners[a * 2], ay = corners[a * 2 + 1];
        float bx = corners[b * 2], by = corners[b * 2 + 1];
        if (ax != bx)
            return ax < bx ? -1 : 1;
        if (ay != by)
            return ay < by ? -1 : 1;
        return 0;
    }

    /** Cross product of (b-a) and (c-a); positive when a, b, c turn counter-clockwise. */
    private static float cross(float[] corners, int a, int b, int c) {
        float ax = corners[a * 2], ay = corners[a * 2 + 1];
        float bx = corners[b * 2], by = corners[b * 2 + 1];
        float cx = corners[c * 2], cy = corners[c * 2 + 1];
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }
}
