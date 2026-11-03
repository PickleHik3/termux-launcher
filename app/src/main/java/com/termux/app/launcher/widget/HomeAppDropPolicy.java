package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * An app icon dragged onto Home from outside the page (the drawer): whether Home takes the drag
 * over, which cell it lands in, and what a release does. Launcher3's way: the icon drops into the
 * free cell nearest the finger on the page under it, a page with no free cell refuses it, and a
 * release anywhere but the grid puts nothing down. Pure, so all of it is testable without a view.
 */
public final class HomeAppDropPolicy {

    /** What a release does. */
    public enum Outcome {
        /** Over the grid with a free cell under the finger: the icon is put there. */
        PLACE,
        /** Over a page with no free cell: nothing is put down, and the page says so. */
        NO_ROOM,
        /** Off the grid, over the dock, or the drag was cancelled: nothing is put down. */
        CANCEL
    }

    private HomeAppDropPolicy() {}

    /**
     * Whether a drawer pickup becomes a drag onto Home. Home has to be there and free: no
     * layout lock, and no other drag, edit session or widget add already in flight. When it is
     * not, the pickup stays the drawer's own drag, which arranges the drawer.
     */
    public static boolean handsOverToHome(boolean homePresent, boolean layoutLocked, boolean busy) {
        return homePresent && !layoutLocked && !busy;
    }

    /** What letting go does, from where the finger is and what the page under it offered. */
    @NonNull
    public static Outcome resolve(boolean canceled, boolean overDock, boolean overGrid,
                                  boolean freeCellOffered) {
        if (canceled || overDock || !overGrid) return Outcome.CANCEL;
        return freeCellOffered ? Outcome.PLACE : Outcome.NO_ROOM;
    }

    /**
     * A pixel position along one axis of the grid in cell units, where cell {@code n}'s centre is
     * at {@code n + 0.5}.
     *
     * @param firstCenterPx the centre of the first cell on that axis
     * @param pitchPx       the distance from one cell's centre to the next
     */
    public static float toCellUnits(float px, float firstCenterPx, float pitchPx) {
        if (pitchPx <= 0f) return 0.5f;
        return (px - firstCenterPx) / pitchPx + 0.5f;
    }

    /**
     * The free 1×1 cell whose centre is nearest the finger, or null when the page has none. Ties
     * go to the upper row, then the leading column, so the answer never flickers between two.
     *
     * @param column   the finger's position across the grid in cell units ({@link #toCellUnits})
     * @param row      the same, down the grid
     * @param occupied the cells the page's items take
     */
    @Nullable
    public static WidgetCellRect nearestFreeCell(int columns, int rows,
                                                 @NonNull List<WidgetCellRect> occupied,
                                                 float column, float row) {
        if (columns <= 0 || rows <= 0) return null;
        int bestColumn = -1;
        int bestRow = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (isOccupied(occupied, c, r)) continue;
                float dx = c + 0.5f - column;
                float dy = r + 0.5f - row;
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestColumn = c;
                    bestRow = r;
                }
            }
        }
        return bestColumn < 0 ? null
            : new WidgetCellRect(bestColumn, bestRow, bestColumn + 1, bestRow + 1);
    }

    /** The cell under the finger, clamped into the grid: where a refusal is drawn. */
    @NonNull
    public static WidgetCellRect cellUnder(int columns, int rows, float column, float row) {
        int c = Math.max(0, Math.min(Math.max(0, columns - 1), (int) Math.floor(column)));
        int r = Math.max(0, Math.min(Math.max(0, rows - 1), (int) Math.floor(row)));
        return new WidgetCellRect(c, r, c + 1, r + 1);
    }

    private static boolean isOccupied(@NonNull List<WidgetCellRect> occupied, int column,
                                      int row) {
        for (int i = 0; i < occupied.size(); i++) {
            WidgetCellRect cell = occupied.get(i);
            if (column >= cell.left && column < cell.right && row >= cell.top && row < cell.bottom) {
                return true;
            }
        }
        return false;
    }
}
