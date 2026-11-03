package com.termux.app.launcher.drawer;

/**
 * Which search result the arrow keys have highlighted: an index over the current result list, or
 * none. Pure, so the movement rules are tested without a view.
 *
 * <p>Movement is in reading order. Left/Right step one; Up/Down step a whole row of
 * {@code columns}. A step that would leave the list is clamped to the nearest end rather than
 * wrapping, and Down from a short last row lands on the last result, not nowhere.
 */
public final class AppDrawerSearchSelection {

    public static final int NONE = -1;

    private int mIndex = NONE;
    private int mCount;

    public int index() {
        return mIndex;
    }

    public int count() {
        return mCount;
    }

    /**
     * A new result list. A fresh query highlights the first result; the same query over a changed
     * list (a package install) keeps its place, clamped.
     *
     * @return true when the highlighted index changed
     */
    public boolean onResults(int count, boolean hasQuery, boolean queryChanged) {
        int before = mIndex;
        mCount = Math.max(0, count);
        if (!hasQuery || mCount == 0) mIndex = NONE;
        else if (queryChanged || mIndex == NONE) mIndex = 0;
        else mIndex = Math.min(mIndex, mCount - 1);
        return mIndex != before;
    }

    /** Moves by {@code delta} results; @return true when the highlight moved. */
    public boolean moveBy(int delta) {
        if (mCount == 0 || mIndex == NONE) return false;
        int target = Math.max(0, Math.min(mCount - 1, mIndex + delta));
        if (target == mIndex) return false;
        mIndex = target;
        return true;
    }

    /** Moves {@code rows} rows (negative is up) in a grid {@code columns} wide. */
    public boolean moveRows(int rows, int columns) {
        return moveBy(rows * Math.max(1, columns));
    }

    /** Removes the highlight; @return true when there was one. */
    public boolean clear() {
        if (mIndex == NONE) return false;
        mIndex = NONE;
        return true;
    }
}
