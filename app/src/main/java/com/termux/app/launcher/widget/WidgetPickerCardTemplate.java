package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

/**
 * The handful of card shapes the picker draws, and how a provider's span picks one.
 *
 * <p>A square card throws away the one thing a preview is for — the shape. A card per distinct
 * span throws away the other thing a list is for: rows that line up. So the spans are snapped to
 * six templates, and a span picks the smallest template that contains it, clamped to the largest.
 * Two widgets with the same template get byte-identical cards whatever their exact spans are.
 */
public final class WidgetPickerCardTemplate {
    /** One grid cell of card, in dp. A 4x4 widget therefore shows as a 160 dp square. */
    public static final int CELL_DP = 40;

    private static final WidgetPickerCardTemplate[] TEMPLATES = {
        new WidgetPickerCardTemplate(1, 1),
        new WidgetPickerCardTemplate(2, 1),
        new WidgetPickerCardTemplate(2, 2),
        new WidgetPickerCardTemplate(4, 1),
        new WidgetPickerCardTemplate(4, 2),
        new WidgetPickerCardTemplate(4, 4),
    };
    /** The last template is the largest, and the one every oversized span clamps to. */
    private static final WidgetPickerCardTemplate LARGEST = TEMPLATES[TEMPLATES.length - 1];

    public final int columns;
    public final int rows;

    private WidgetPickerCardTemplate(int columns, int rows) {
        this.columns = columns; this.rows = rows;
    }

    /**
     * The smallest template that holds {@code columnSpan} x {@code rowSpan} whole. A span wider or
     * taller than the largest template — a widget on a big grid — gets the largest.
     */
    @NonNull public static WidgetPickerCardTemplate forSpan(int columnSpan, int rowSpan) {
        int columns = Math.max(1, columnSpan);
        int rows = Math.max(1, rowSpan);
        WidgetPickerCardTemplate best = null;
        for (WidgetPickerCardTemplate template : TEMPLATES) {
            if (template.columns < columns || template.rows < rows) continue;
            if (best == null || template.area() < best.area()) best = template;
        }
        return best == null ? LARGEST : best;
    }

    /** The template every oversized span clamps to; also the size the preview store budgets for. */
    @NonNull public static WidgetPickerCardTemplate largest() { return LARGEST; }

    public int widthPx(float density) { return Math.max(1, Math.round(columns * CELL_DP * density)); }
    public int heightPx(float density) { return Math.max(1, Math.round(rows * CELL_DP * density)); }

    /** The longest edge of the card, which is all artwork ever has to be shrunk to. */
    public int extentPx(float density) {
        return Math.max(widthPx(density), heightPx(density));
    }

    private int area() { return columns * rows; }

    @Override public boolean equals(Object other) {
        return other instanceof WidgetPickerCardTemplate
            && columns == ((WidgetPickerCardTemplate) other).columns
            && rows == ((WidgetPickerCardTemplate) other).rows;
    }
    @Override public int hashCode() { return columns * 31 + rows; }
    @Override public String toString() { return columns + "x" + rows; }
}
