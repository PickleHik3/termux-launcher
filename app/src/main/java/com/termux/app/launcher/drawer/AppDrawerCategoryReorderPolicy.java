package com.termux.app.launcher.drawer;

/**
 * Where a dragged Categories tile would land among the others. The tiles sit in a grid of
 * {@code columns} (one column is a list) read left to right, then down; the carried tile's centre
 * says where, and the answer is the tile it goes in front of, or the end, which is what
 * {@link AppDrawerCategoryOrder#moved} takes. Pure and reusable: {@link #begin} starts a drag.
 *
 * <p>Positions are insertion points {@code 0..count} in the tiles' shown order: the centre over
 * slot s at ratio r across it is at {@code s + r}, and the nearest point is the target. The
 * target holds until the centre is {@link #HYSTERESIS} of a slot past the boundary, so a finger
 * resting on a boundary does not flicker. The two points either side of the source are the same
 * order: {@link #isNoOp()}.
 */
public final class AppDrawerCategoryReorderPolicy {

    /** What {@link #before()} says for the end of the order. */
    public static final int END = -1;
    /** How far past a boundary, in slots, before the target changes. */
    public static final float HYSTERESIS = 0.12f;

    private int mCount;
    private int mColumns = 1;
    private float mLeft;
    private float mTop;
    private float mTileW = 1f;
    private float mTileH = 1f;
    private float mGapX;
    private float mGapY;
    private int mSource = -1;
    private int mPoint;

    /**
     * Starts a drag.
     *
     * @param count   how many tiles are shown
     * @param columns how many tiles a row holds
     * @param left    the first tile's left edge, in the coordinates the centre is given in
     * @param top     the first tile's top edge
     * @param source  the shown index of the lifted tile
     */
    public void begin(int count, int columns, float left, float top, float tileW, float tileH,
                      float gapX, float gapY, int source) {
        mCount = Math.max(0, count);
        mColumns = Math.max(1, columns);
        mLeft = left;
        mTop = top;
        mTileW = Math.max(1f, tileW);
        mTileH = Math.max(1f, tileH);
        mGapX = Math.max(0f, gapX);
        mGapY = Math.max(0f, gapY);
        mSource = source >= 0 && source < mCount ? source : -1;
        mPoint = mSource >= 0 ? mSource : mCount;
    }

    /**
     * The carried tile's centre moved.
     *
     * @return whether the target changed
     */
    public boolean hover(float cx, float cy) {
        if (mCount == 0 || Float.isNaN(cx) || Float.isNaN(cy)) return false;
        int rows = (mCount + mColumns - 1) / mColumns;
        float strideX = mTileW + mGapX;
        float strideY = mTileH + mGapY;
        int row = clamp((int) Math.floor((cy - mTop) / strideY), 0, rows - 1);
        int col = (int) Math.floor((cx - mLeft) / strideX);
        float u;
        if (col < 0) {
            u = row * mColumns;
        } else if (col >= mColumns || row * mColumns + col >= mCount) {
            u = Math.min(row * mColumns + mColumns, mCount);
        } else {
            float ratio = Math.min(1f, Math.max(0f, (cx - mLeft - col * strideX) / mTileW));
            u = row * mColumns + col + ratio;
        }
        int raw = clamp((int) Math.floor(u + 0.5f), 0, mCount);
        if (raw == mPoint || Math.abs(u - mPoint) <= 0.5f + HYSTERESIS) return false;
        mPoint = raw;
        return true;
    }

    /** The shown index of the tile the carried one goes in front of, or {@link #END}. */
    public int before() {
        int point = mPoint;
        if (mSource >= 0 && point == mSource) point = mSource + 1;
        return point >= mCount ? END : point;
    }

    /** Whether dropping now leaves the order as it is. */
    public boolean isNoOp() {
        return mSource >= 0 && (mPoint == mSource || mPoint == mSource + 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
