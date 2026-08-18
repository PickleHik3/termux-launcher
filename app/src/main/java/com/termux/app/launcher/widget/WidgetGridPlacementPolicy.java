package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.BitSet;
import java.util.List;

/** Deterministic row-major placement and shared snapshot validation policy. */
public final class WidgetGridPlacementPolicy {
    public enum Outcome { PLACED, SPAN_EXCEEDS_GRID, NO_CONTIGUOUS_SPACE, INVALID_SNAPSHOT }

    public static final class Result {
        @NonNull public final Outcome outcome;
        @Nullable public final WidgetCellRect rect;
        private Result(@NonNull Outcome outcome, @Nullable WidgetCellRect rect) {
            this.outcome = outcome;
            this.rect = rect;
        }
        public static Result placed(@NonNull WidgetCellRect rect) {
            return new Result(Outcome.PLACED, rect);
        }
        public static Result of(@NonNull Outcome outcome) { return new Result(outcome, null); }
    }

    private WidgetGridPlacementPolicy() {}

    @NonNull
    public static Result findPlacement(@NonNull WidgetGridDefinition grid,
                                       @NonNull List<LauncherWidgetRecord> records,
                                       int columnSpan, int rowSpan) {
        return findPlacement(grid, records, columnSpan, rowSpan, -1);
    }

    @NonNull
    public static Result findPlacement(@NonNull WidgetGridDefinition grid,
                                       @NonNull List<LauncherWidgetRecord> records,
                                       int columnSpan, int rowSpan, int ignoredAppWidgetId) {
        if (columnSpan <= 0 || rowSpan <= 0 || columnSpan > grid.columns
            || rowSpan > grid.rows) {
            return Result.of(Outcome.SPAN_EXCEEDS_GRID);
        }
        BitSet occupied = occupancy(grid, records, ignoredAppWidgetId);
        if (occupied == null) return Result.of(Outcome.INVALID_SNAPSHOT);
        for (int top = 0; top <= grid.rows - rowSpan; top++) {
            for (int left = 0; left <= grid.columns - columnSpan; left++) {
                WidgetCellRect candidate = new WidgetCellRect(left, top,
                    left + columnSpan, top + rowSpan);
                if (isFree(grid, occupied, candidate)) return Result.placed(candidate);
            }
        }
        return Result.of(Outcome.NO_CONTIGUOUS_SPACE);
    }

    public static boolean validate(@NonNull WidgetGridDefinition grid,
                                   @NonNull List<LauncherWidgetRecord> records) {
        return occupancy(grid, records, -1) != null;
    }

    public static boolean canPlace(@NonNull WidgetGridDefinition grid,
                                   @NonNull List<LauncherWidgetRecord> records,
                                   @NonNull WidgetCellRect candidate, int ignoredAppWidgetId) {
        if (!inBounds(grid, candidate)) return false;
        BitSet occupied = occupancy(grid, records, ignoredAppWidgetId);
        return occupied != null && isFree(grid, occupied, candidate);
    }

    @Nullable
    private static BitSet occupancy(WidgetGridDefinition grid,
                                    List<LauncherWidgetRecord> records, int ignoredId) {
        BitSet occupied = new BitSet(grid.rows * grid.columns);
        for (LauncherWidgetRecord record : records) {
            if (record.appWidgetId == ignoredId) continue;
            if (!inBounds(grid, record.cell)) return null;
            for (int row = record.cell.top; row < record.cell.bottom; row++) {
                for (int column = record.cell.left; column < record.cell.right; column++) {
                    int index = row * grid.columns + column;
                    if (occupied.get(index)) return null;
                }
            }
            mark(grid, occupied, record.cell);
        }
        return occupied;
    }

    private static boolean inBounds(WidgetGridDefinition grid, WidgetCellRect rect) {
        return rect.left >= 0 && rect.top >= 0 && rect.right <= grid.columns
            && rect.bottom <= grid.rows;
    }

    private static boolean isFree(WidgetGridDefinition grid, BitSet occupied,
                                  WidgetCellRect rect) {
        for (int row = rect.top; row < rect.bottom; row++) {
            for (int column = rect.left; column < rect.right; column++) {
                if (occupied.get(row * grid.columns + column)) return false;
            }
        }
        return true;
    }

    private static void mark(WidgetGridDefinition grid, BitSet occupied, WidgetCellRect rect) {
        for (int row = rect.top; row < rect.bottom; row++) {
            for (int column = rect.left; column < rect.right; column++) {
                occupied.set(row * grid.columns + column);
            }
        }
    }
}
