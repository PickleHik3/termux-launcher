package com.termux.app.editorshell;

import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * Applies {@link EditorShellMetrics} to a row the shell has just inflated.
 *
 * <p>The row layouts carry the same numbers as literals so the editor preview draws right, but the
 * metrics class is what the running app reads: one constant moved there reaches both editors
 * without either layout being edited, and the unit tests hold the number rather than a resource.
 */
public final class EditorShellRows {

    private EditorShellRows() {}

    /** Sizes one inflated row's columns. Every id it looks for is optional. */
    public static void apply(@NonNull View row) {
        float density = row.getResources().getDisplayMetrics().density;
        EditorShellMetrics.RowMetrics metrics = EditorShellMetrics.rowMetrics(density);

        View label = row.findViewById(R.id.editor_shell_row_label);
        setWidth(label, metrics.labelWidthPx);
        setEndMargin(label, metrics.labelGapPx);

        View control = row.findViewById(R.id.editor_shell_row_control);
        setEndMargin(control, metrics.controlGapPx);

        View value = row.findViewById(R.id.editor_shell_row_value);
        setWidth(value, metrics.valueWidthPx);
        setEndMargin(value, metrics.valueGapPx);

        View chip = row.findViewById(R.id.editor_shell_row_chip);
        if (chip != null) {
            setWidth(chip, metrics.chipSizePx);
            setHeight(chip, metrics.chipSizePx);
            // Drawn at 28 dp so it does not shout, tapped at the platform floor: the chip is how a
            // row gets back onto Base, and a 28 dp target is not a control.
            expandTouchTarget(chip, metrics.minHeightPx);
        }

        View inner = innerRow(row);
        if (inner != null) {
            inner.setMinimumHeight(metrics.minHeightPx);
            inner.setPadding(inner.getPaddingLeft(), metrics.verticalPaddingPx,
                inner.getPaddingRight(), metrics.verticalPaddingPx);
        }
    }

    /**
     * The line the columns stand on. A slider or switch row wraps it in a column that carries the
     * note underneath; a pills or action row is the line itself.
     */
    @Nullable
    private static View innerRow(@NonNull View row) {
        View label = row.findViewById(R.id.editor_shell_row_label);
        return label == null ? row : (View) label.getParent();
    }

    /** Grows a small view's touch target inside its parent, up to the row's own height. */
    public static void expandTouchTarget(@NonNull View target, int minSizePx) {
        View parent = (View) target.getParent();
        if (parent == null)
            return;
        parent.post(() -> {
            Rect bounds = new Rect();
            target.getHitRect(bounds);
            int growX = Math.max(0, (minSizePx - bounds.width()) / 2);
            int growY = Math.max(0, (minSizePx - bounds.height()) / 2);
            bounds.inset(-growX, -growY);
            parent.setTouchDelegate(new TouchDelegate(bounds, target));
        });
    }

    private static void setWidth(@Nullable View view, int widthPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.width == widthPx)
            return;
        params.width = widthPx;
        view.setLayoutParams(params);
    }

    private static void setHeight(@Nullable View view, int heightPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == heightPx)
            return;
        params.height = heightPx;
        view.setLayoutParams(params);
    }

    private static void setEndMargin(@Nullable View view, int marginPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.getMarginEnd() == marginPx)
            return;
        margins.setMarginEnd(marginPx);
        view.setLayoutParams(params);
    }
}
