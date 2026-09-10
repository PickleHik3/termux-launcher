package com.termux.app.launcher.widget;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure candidate math for widget edit mode: whole-cell move snapping and edge resizing. */
public final class WidgetEditPolicy {
    public enum Handle { LEFT, TOP, RIGHT, BOTTOM }

    public static final class Candidate {
        @NonNull public final WidgetCellRect rect;
        public final boolean valid;
        /**
         * Neighbours this candidate pushes aside, appWidgetId to its new cell. Empty whenever
         * the candidate displaces nobody, and always empty for a resize.
         */
        @NonNull public final Map<Integer, WidgetCellRect> displaced;

        Candidate(@NonNull WidgetCellRect rect, boolean valid) {
            this(rect, valid, Collections.emptyMap());
        }

        Candidate(@NonNull WidgetCellRect rect, boolean valid,
                  @NonNull Map<Integer, WidgetCellRect> displaced) {
            this.rect = rect;
            this.valid = valid;
            this.displaced = displaced.isEmpty()
                ? Collections.emptyMap() : Collections.unmodifiableMap(displaced);
        }
    }

    private WidgetEditPolicy() {}

    /**
     * Where a dragged widget should land. The nearest same-span position to the finger wins; if
     * it is taken, the widgets sitting there are pushed into the nearest free holes on the same
     * page and reported in {@link Candidate#displaced}. When they cannot all be rehomed the
     * result falls back to the nearest collision-free position, and to the original span marked
     * invalid when the page has no room at all, so the drag springs back.
     */
    @NonNull
    public static Candidate snapMove(@NonNull WidgetGridMetrics metrics,
                                     @NonNull List<LauncherWidgetRecord> records,
                                     int appWidgetId, @NonNull WidgetCellRect span,
                                     @NonNull Rect draggedBounds) {
        WidgetGridDefinition grid = metrics.definition();
        BitSet others = WidgetGridPlacementPolicy.occupancy(grid, records, appWidgetId);
        if (others == null) return new Candidate(span, false);
        int columns = span.columnSpan();
        int rows = span.rowSpan();
        WidgetCellRect nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        WidgetCellRect nearestFree = null;
        long nearestFreeDistance = Long.MAX_VALUE;
        for (int top = 0; top <= grid.rows - rows; top++) {
            for (int left = 0; left <= grid.columns - columns; left++) {
                WidgetCellRect candidate = new WidgetCellRect(left, top,
                    left + columns, top + rows);
                long distance = centreDistance(metrics.boundsFor(candidate), draggedBounds);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
                if (distance < nearestFreeDistance
                    && WidgetGridPlacementPolicy.isFree(grid, others, candidate)) {
                    nearestFreeDistance = distance;
                    nearestFree = candidate;
                }
            }
        }
        if (nearest == null) return new Candidate(span, false);
        if (nearest.equals(nearestFree)) return new Candidate(nearest, true);
        Map<Integer, WidgetCellRect> displaced = displace(metrics, records, appWidgetId, nearest);
        if (displaced != null) return new Candidate(nearest, true, displaced);
        return nearestFree == null ? new Candidate(span, false) : new Candidate(nearestFree, true);
    }

    /**
     * New homes for every widget standing on {@code target}, or null when one of them has
     * nowhere to go. Blockers keep their span, stay on the page and never displace anyone else:
     * the largest goes first, into the free position nearest to where it already sits.
     */
    @Nullable
    private static Map<Integer, WidgetCellRect> displace(WidgetGridMetrics metrics,
                                                         List<LauncherWidgetRecord> records,
                                                         int appWidgetId,
                                                         WidgetCellRect target) {
        WidgetGridDefinition grid = metrics.definition();
        List<LauncherWidgetRecord> blockers = new ArrayList<>();
        Set<Integer> blockerIds = new HashSet<>();
        for (LauncherWidgetRecord record : records) {
            if (record.appWidgetId == appWidgetId) continue;
            if (intersects(record.cell, target)) {
                blockers.add(record);
                blockerIds.add(record.appWidgetId);
            }
        }
        if (blockers.isEmpty()) return null;
        BitSet occupied = new BitSet(grid.rows * grid.columns);
        for (LauncherWidgetRecord record : records) {
            if (record.appWidgetId == appWidgetId
                || blockerIds.contains(record.appWidgetId)) continue;
            WidgetGridPlacementPolicy.mark(grid, occupied, record.cell);
        }
        WidgetGridPlacementPolicy.mark(grid, occupied, target);
        // Largest first: the hardest widget to rehome gets the pick of the holes.
        blockers.sort(Comparator
            .comparingInt((LauncherWidgetRecord r) -> -r.cell.columnSpan() * r.cell.rowSpan())
            .thenComparingInt(r -> r.cell.top)
            .thenComparingInt(r -> r.cell.left));
        Map<Integer, WidgetCellRect> displaced = new LinkedHashMap<>();
        for (LauncherWidgetRecord blocker : blockers) {
            WidgetCellRect home = nearestHole(metrics, grid, occupied, blocker.cell);
            if (home == null) return null;
            WidgetGridPlacementPolicy.mark(grid, occupied, home);
            displaced.put(blocker.appWidgetId, home);
        }
        return displaced;
    }

    @Nullable
    private static WidgetCellRect nearestHole(WidgetGridMetrics metrics, WidgetGridDefinition grid,
                                              BitSet occupied, WidgetCellRect from) {
        Rect origin = metrics.boundsFor(from);
        int columns = from.columnSpan();
        int rows = from.rowSpan();
        WidgetCellRect best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int top = 0; top <= grid.rows - rows; top++) {
            for (int left = 0; left <= grid.columns - columns; left++) {
                WidgetCellRect candidate = new WidgetCellRect(left, top,
                    left + columns, top + rows);
                if (!WidgetGridPlacementPolicy.isFree(grid, occupied, candidate)) continue;
                long distance = centreDistance(metrics.boundsFor(candidate), origin);
                // Row-major scan order breaks ties by smaller top, then smaller left.
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean intersects(WidgetCellRect a, WidgetCellRect b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }

    private static long centreDistance(Rect bounds, Rect other) {
        long dx = bounds.centerX() - other.centerX();
        long dy = bounds.centerY() - other.centerY();
        return dx * dx + dy * dy;
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
