package com.termux.app.wall.switcher;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Where the picker's cells stand, in the layer's pixels. The picker grows out of the dot: a
 * horizontal row of the four page kinds beside the dot, inward from its edge, with each kind's
 * stack of cells above its glyph. The row order is fixed (Home, Terminal, Display, Assistant,
 * left to right) on either edge. Pure, so the hit test the finger rides is checked without a
 * view; the caller converts dp to px through the density it passes.
 */
public final class PickerLayout {

    public static final float CELL_W_DP = 56f;
    /** A glyph cell's height. */
    public static final float CELL_H_DP = 52f;
    public static final float STACK_CELL_H_DP = 42f;
    public static final float PAD_DP = 6f;
    /** The dot's edge to the row. */
    public static final float DOT_GAP_DP = 4f;
    /** The row's top to the first stack cell. */
    public static final float STACK_GAP_DP = 6f;
    public static final float LABEL_RISE_DP = 64f;
    public static final float LABEL_MARGIN_DP = 4f;

    private static final int COLUMNS = 4;
    private static final float PILL_OVERHANG_DP = 2f;
    private static final float CARD_OVERHANG_DP = 1f;

    private final float mWidth;
    private final float mHeight;
    private final float mDensity;
    @NonNull private final Edge mEdge;
    private final float mCellW;
    private final float mCellH;
    private final float mStackCellH;
    private final float mPad;
    private final float mStackGap;
    private final float mRowLeft;
    private final float mRowTop;
    private final float mPillLeft;
    private final float mPillRight;
    private final int mCapacity;

    /**
     * @param cx,cy the dot's centre; {@code d} its diameter
     * @param topLimit,bottomLimit the band the picker may stand in
     * @param wantedRows the tallest column's cells, uncapped
     */
    public PickerLayout(float width, float height, float density, float cx, float cy, float d,
                        @NonNull Edge edge, float topLimit, float bottomLimit, int wantedRows) {
        mWidth = width;
        mHeight = height;
        mDensity = density;
        mEdge = edge;
        mCellW = CELL_W_DP * density;
        mCellH = CELL_H_DP * density;
        mStackCellH = STACK_CELL_H_DP * density;
        mPad = PAD_DP * density;
        mStackGap = STACK_GAP_DP * density;
        float gap = DOT_GAP_DP * density;
        float overhang = PILL_OVERHANG_DP * density;

        float rowWidth = COLUMNS * mCellW;
        mRowLeft = edge == Edge.LEFT ? cx + d / 2f + gap : cx - d / 2f - gap - rowWidth;

        int maxRows = Math.max(0,
            (int) Math.floor((bottomLimit - topLimit - 2f * mPad - mCellH - mStackGap)
                / mStackCellH));
        int rows = Math.min(Math.max(wantedRows, 0), maxRows);
        float lo = topLimit + mPad + rows * mStackCellH + mStackGap + mCellH / 2f;
        float hi = bottomLimit - mPad - mCellH / 2f;
        float rcy = Math.min(hi, Math.max(lo, cy));
        mRowTop = rcy - mCellH / 2f;

        mCapacity = Math.max(0,
            (int) Math.floor((mRowTop - mStackGap - topLimit - mPad) / mStackCellH));

        if (edge == Edge.LEFT) {
            mPillLeft = cx - d / 2f - mPad - overhang;
            mPillRight = mRowLeft + rowWidth + mPad;
        } else {
            mPillLeft = mRowLeft - mPad;
            mPillRight = cx + d / 2f + mPad + overhang;
        }
    }

    /** How many stack cells a column may hold: what the model is built with. */
    public int capacity() {
        return mCapacity;
    }

    @NonNull
    public Edge edge() {
        return mEdge;
    }

    /** The glass behind the row, wrapping the dot. */
    public void pillRect(@NonNull RectF out) {
        out.set(mPillLeft, mRowTop - mPad, mPillRight, mRowTop + mCellH + mPad);
    }

    private float left(int column) {
        return mRowLeft + column * mCellW;
    }

    /** The cell's rectangle: the glyph in the row, or a stack cell above it. */
    public void cellRect(@NonNull PickerModel model, @NonNull PickerModel.Cell cell,
                         @NonNull RectF out) {
        int column = cell.pageKind.ordinal();
        float left = left(column);
        if (cell.kind == PickerModel.CellKind.GLYPH) {
            out.set(left, mRowTop, left + mCellW, mRowTop + mCellH);
            return;
        }
        int j = model.column(cell.pageKind).cells.indexOf(cell) + 1;
        out.set(left, mRowTop - mStackGap - j * mStackCellH, left + mCellW,
            mRowTop - mStackGap - (j - 1) * mStackCellH);
    }

    /** The card behind a column's stack; false when the column has no stack cells. */
    public boolean stackRect(@NonNull PickerModel model, @NonNull PickerModel.Kind kind,
                             @NonNull RectF out) {
        int n = model.column(kind).cells.size();
        if (n <= 0) return false;
        float left = left(kind.ordinal());
        float overhang = CARD_OVERHANG_DP * mDensity;
        out.set(left, mRowTop - mStackGap - n * mStackCellH - overhang, left + mCellW,
            mRowTop - mStackGap + overhang);
        return true;
    }

    /** The cell under a point: a glyph or a stack cell, else null. */
    @Nullable
    public PickerModel.Cell cellAt(@NonNull PickerModel model, float x, float y) {
        float rel = x - mRowLeft;
        if (rel < 0f) return null;
        int column = (int) Math.floor(rel / mCellW);
        if (column >= COLUMNS) return null;
        PickerModel.Column col = model.columns().get(column);
        if (y >= mRowTop && y < mRowTop + mCellH) return col.glyph;
        float t = mRowTop - mStackGap - y;
        if (t < 0f) return null;
        int j = (int) Math.floor(t / mStackCellH) + 1;
        List<PickerModel.Cell> cells = col.cells;
        return j <= cells.size() ? cells.get(j - 1) : null;
    }

    /** The order cells appear in when the picker opens: glyphs from the dot out, stacks after. */
    public int staggerIndex(@NonNull PickerModel.Cell cell) {
        int column = cell.pageKind.ordinal();
        int glyph = mEdge == Edge.LEFT ? column : COLUMNS - 1 - column;
        return cell.kind == PickerModel.CellKind.GLYPH ? glyph : glyph + 1;
    }

    /** The name label's left edge: centred on the finger, kept on screen. */
    public float labelX(float fingerX, float labelW) {
        float margin = LABEL_MARGIN_DP * mDensity;
        return Math.max(margin, Math.min(mWidth - labelW - margin, fingerX - labelW / 2f));
    }

    /** The name label's top edge: above the finger, never above the margin. */
    public float labelTop(float fingerY, float labelH) {
        return Math.max(LABEL_MARGIN_DP * mDensity, fingerY - LABEL_RISE_DP * mDensity);
    }

    /**
     * The first-run hint's top edge: clear of the row and of the tallest stack. Below the row when
     * the screen has room there, else above the stack, else pinned to whichever edge fits best.
     */
    public float hintTop(float hintH, int stackRows) {
        float margin = LABEL_MARGIN_DP * mDensity;
        float below = mRowTop + mCellH + mPad + margin;
        if (below + hintH + margin <= mHeight) return below;
        float stackTop = mRowTop - mStackGap - Math.max(0, stackRows) * mStackCellH
            - CARD_OVERHANG_DP * mDensity;
        float above = Math.min(stackTop, mRowTop - mPad) - margin - hintH;
        if (above >= margin) return above;
        return Math.max(margin, Math.min(mHeight - hintH - margin, below));
    }
}
