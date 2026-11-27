package com.termux.app.controlbar;

/**
 * The marks' cells along the control bar's top row (#424), pure: packed from the row's start at
 * their own width with a fixed gap between, all narrowed alike when they would run past the
 * length. Positions run from the strip's start; the view maps them to x. Built only when the marks
 * or the length change, never per draw.
 */
public final class MarkStrip {

    /** No mark: {@link #indexAt} past the marks, or no marks at all. */
    public static final int NONE = -1;

    private final int mCount;
    private final float mCell;
    private final float mGap;

    /**
     * @param count how many marks
     * @param cellPx a mark's width when they fit
     * @param gapPx the gap between two marks
     * @param lengthPx the room the strip has
     */
    public MarkStrip(int count, float cellPx, float gapPx, float lengthPx) {
        mCount = Math.max(0, count);
        mGap = Math.max(0f, gapPx);
        float cell = Math.max(0f, cellPx);
        if (mCount > 0) {
            float fit = (Math.max(0f, lengthPx) - mGap * (mCount - 1)) / mCount;
            cell = Math.max(0f, Math.min(cell, fit));
        }
        mCell = cell;
    }

    public int count() {
        return mCount;
    }

    public float cellWidth() {
        return mCell;
    }

    public float cellStart(int index) {
        return index * (mCell + mGap);
    }

    public float cellEnd(int index) {
        return cellStart(index) + mCell;
    }

    public float cellCentre(int index) {
        return cellStart(index) + mCell / 2f;
    }

    /** From the first cell's start to the last one's end. */
    public float packedLength() {
        return mCount == 0 ? 0f : cellEnd(mCount - 1);
    }

    /**
     * The mark under {@code x}: a cell, or the gap beside it (each half goes to its own side);
     * {@link #NONE} before the first or past the last.
     */
    public int indexAt(float x) {
        if (mCount == 0 || x < 0f || x > packedLength()) return NONE;
        return nearestIndex(x);
    }

    /** The mark nearest {@code x}, clamped to the ends; {@link #NONE} with no marks. */
    public int nearestIndex(float x) {
        if (mCount == 0) return NONE;
        float pitch = mCell + mGap;
        if (pitch <= 0f) return 0;
        // A cell owns its own width and half the gap either side.
        int index = (int) Math.floor((x + mGap / 2f) / pitch);
        return Math.max(0, Math.min(mCount - 1, index));
    }
}
