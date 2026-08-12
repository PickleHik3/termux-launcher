package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

/** Immutable half-open rectangle in logical widget-grid cells. */
public final class WidgetCellRect {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public WidgetCellRect(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("Widget cell spans must be positive");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int columnSpan() { return right - left; }
    public int rowSpan() { return bottom - top; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WidgetCellRect)) return false;
        WidgetCellRect rect = (WidgetCellRect) other;
        return left == rect.left && top == rect.top && right == rect.right
            && bottom == rect.bottom;
    }

    @Override public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        return 31 * result + bottom;
    }

    @NonNull @Override public String toString() {
        return "WidgetCellRect{" + left + "," + top + "-" + right + "," + bottom + "}";
    }
}
