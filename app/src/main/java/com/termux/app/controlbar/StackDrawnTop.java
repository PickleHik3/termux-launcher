package com.termux.app.controlbar;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

/**
 * Where what the accessory stack draws begins, read off its views (terl-kbd-room, pong 11-28): the
 * rows over the keyboard and the keyboard's column, each in the stack's own coordinates. The rows
 * count where they are drawn, not where they are laid out: Home's keyboard cover (#309) draws them
 * at their keyboard-down rect, under the keyboard, while their room stays laid out above it, and a
 * side's lane slid off the stack draws nothing there. A row host whose rows are all away draws
 * nothing either. {@link ControlBarPlacement#emptyTopPx} takes the two tops and answers how much
 * room the bar does not stand on.
 *
 * <p>At rest ({@code atRest}) the translations are left out: while another surface holds the
 * stack (the drawer, an editor) its writer draws nothing new, and the bar stands on the rest it
 * will draw when it lets go. Read on every frame the bar rides: it allocates nothing.
 */
public final class StackDrawnTop {

    private StackDrawnTop() {}

    /**
     * The top of the highest row {@code rows} draws, in its parent's coordinates, or -1 when it
     * draws none.
     */
    public static int rowsTopPx(@Nullable View rows, boolean atRest) {
        if (!shows(rows)) return -1;
        float top = Float.MAX_VALUE;
        if (rows instanceof ViewGroup && ((ViewGroup) rows).getChildCount() > 0) {
            ViewGroup group = (ViewGroup) rows;
            for (int i = 0; i < group.getChildCount(); i++) {
                float child = childTopPx(group.getChildAt(i), rows.getWidth(), atRest);
                if (!Float.isNaN(child)) top = Math.min(top, child);
            }
            if (top == Float.MAX_VALUE) return -1;
        } else {
            top = 0f;
        }
        return Math.max(0, Math.round(rows.getTop() + (atRest ? 0f : rows.getTranslationY()) + top));
    }

    /** The keyboard column's top in its parent's coordinates, as drawn, or -1 when it is not shown. */
    public static int keyboardTopPx(@Nullable View column, boolean atRest) {
        if (!shows(column)) return -1;
        return Math.max(0, Math.round(column.getTop() + (atRest ? 0f : column.getTranslationY())));
    }

    /**
     * A row host's drawn top inside the rows, or NaN: shown, on the stack across its width, and, for
     * a host of rows, holding one that is shown.
     */
    private static float childTopPx(View child, int widthPx, boolean atRest) {
        if (!shows(child)) return Float.NaN;
        float tx = atRest ? 0f : child.getTranslationX();
        if (child.getRight() + tx <= 0f || child.getLeft() + tx >= widthPx) return Float.NaN;
        float top = child.getTop() + (atRest ? 0f : child.getTranslationY());
        if (!(child instanceof ViewGroup) || ((ViewGroup) child).getChildCount() == 0) return top;
        ViewGroup host = (ViewGroup) child;
        float inner = Float.MAX_VALUE;
        for (int i = 0; i < host.getChildCount(); i++) {
            View row = host.getChildAt(i);
            if (!shows(row)) continue;
            inner = Math.min(inner, row.getTop() + (atRest ? 0f : row.getTranslationY()));
        }
        return inner == Float.MAX_VALUE ? Float.NaN : top + inner;
    }

    private static boolean shows(@Nullable View view) {
        return view != null && view.getVisibility() == View.VISIBLE && view.getHeight() > 0
            && view.getWidth() > 0;
    }
}
