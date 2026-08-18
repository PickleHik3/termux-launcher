package com.termux.app.launcher.widget;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.List;

/** Pure candidate math for widget edit mode: whole-cell move snapping and edge resizing. */
public final class WidgetEditPolicy {
    public enum Handle { LEFT, TOP, RIGHT, BOTTOM }

    public static final class Candidate {
        @NonNull public final WidgetCellRect rect;
        public final boolean valid;
        Candidate(@NonNull WidgetCellRect rect, boolean valid) {
            this.rect = rect;
            this.valid = valid;
        }
    }

    private WidgetEditPolicy() {}

    /**
     * Nearest collision-free same-span placement to the dragged pixel bounds. When no free
     * placement exists the original span is returned invalid so the drag springs back.
     */
    @NonNull
    public static Candidate snapMove(@NonNull WidgetGridMetrics metrics,
                                     @NonNull List<LauncherWidgetRecord> records,
                                     int appWidgetId, @NonNull WidgetCellRect span,
                                     @NonNull Rect draggedBounds) {
        WidgetGridDefinition grid = metrics.definition();
        int columns = span.columnSpan();
        int rows = span.rowSpan();
        WidgetCellRect best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int top = 0; top <= grid.rows - rows; top++) {
            for (int left = 0; left <= grid.columns - columns; left++) {
                WidgetCellRect candidate = new WidgetCellRect(left, top,
                    left + columns, top + rows);
                if (!WidgetGridPlacementPolicy.canPlace(grid, records, candidate, appWidgetId)) {
                    continue;
                }
                Rect bounds = metrics.boundsFor(candidate);
                long dx = bounds.centerX() - draggedBounds.centerX();
                long dy = bounds.centerY() - draggedBounds.centerY();
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best == null ? new Candidate(span, false) : new Candidate(best, true);
    }

    /**
     * Move one edge toward a desired pixel position in whole-cell steps. Only collision-free
     * rects are candidates, so the result is always placeable; the current rect is the floor.
     */
    @NonNull
    public static Candidate resize(@NonNull WidgetGridMetrics metrics,
                                   @NonNull List<LauncherWidgetRecord> records,
                                   int appWidgetId, @NonNull WidgetCellRect current,
                                   @NonNull Handle handle, int desiredEdgePx,
                                   int minColumnSpan, int minRowSpan) {
        WidgetGridDefinition grid = metrics.definition();
        int minColumns = Math.max(1, minColumnSpan);
        int minRows = Math.max(1, minRowSpan);
        WidgetCellRect best = current;
        long bestDistance = edgeDistance(metrics.boundsFor(current), handle, desiredEdgePx);
        int lo, hi;
        switch (handle) {
            case LEFT:   lo = 0; hi = current.right - minColumns; break;
            case RIGHT:  lo = current.left + minColumns; hi = grid.columns; break;
            case TOP:    lo = 0; hi = current.bottom - minRows; break;
            default:     lo = current.top + minRows; hi = grid.rows; break;
        }
        for (int edge = lo; edge <= hi; edge++) {
            WidgetCellRect candidate = withEdge(current, handle, edge);
            if (candidate.equals(current)) continue;
            if (!WidgetGridPlacementPolicy.canPlace(grid, records, candidate, appWidgetId)) {
                continue;
            }
            long distance = edgeDistance(metrics.boundsFor(candidate), handle, desiredEdgePx);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return new Candidate(best, true);
    }

    private static WidgetCellRect withEdge(WidgetCellRect rect, Handle handle, int edge) {
        switch (handle) {
            case LEFT:   return new WidgetCellRect(edge, rect.top, rect.right, rect.bottom);
            case RIGHT:  return new WidgetCellRect(rect.left, rect.top, edge, rect.bottom);
            case TOP:    return new WidgetCellRect(rect.left, edge, rect.right, rect.bottom);
            default:     return new WidgetCellRect(rect.left, rect.top, rect.right, edge);
        }
    }

    private static long edgeDistance(Rect bounds, Handle handle, int desiredEdgePx) {
        int actual;
        switch (handle) {
            case LEFT:   actual = bounds.left; break;
            case RIGHT:  actual = bounds.right; break;
            case TOP:    actual = bounds.top; break;
            default:     actual = bounds.bottom; break;
        }
        return Math.abs(actual - desiredEdgePx);
    }
}
