package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Where the display window stands in the Workspace's row (ADR 0008, amended 2026-10-19). The
 * display is a window of the Workspace like the terminal windows: its chip is in the row's
 * chips, the border drag steps onto it and off it, and it can be dragged along the row or
 * closed. It is not one of the session's terminal windows, though — it has no panes — so it is
 * kept beside that list rather than in it: the row is the terminal windows with the display
 * window standing at one place among them.
 *
 * <p>The place is held as the window it stands right after ({@code null}: first), so whatever
 * reorders, adds or closes terminal windows elsewhere in the row carries the display along with
 * its neighbour without being told. Only when that neighbour leaves the row does the display fall
 * back one place, to where the neighbour stood. Inserting a window right after that neighbour
 * puts it after the display; the one caller that means "right after the window in front" works
 * that out in row places ({@link #insertionRow}) and places the display again.
 *
 * <p>Row places count the display; window indices count the terminal windows alone, as the
 * session's list does. The static methods convert between the two and are the arithmetic the
 * activity applies; the instance is the one display's place.
 *
 * @param <W> the terminal window type, compared by identity through {@link List#indexOf}
 */
public final class DisplaySlot<W> {

    /** No place: the display window is not on this row. */
    public static final int NONE = -1;

    private boolean mOnRow;
    /** The window the display stands right after; null while it stands first. */
    @Nullable private W mAfter;
    /** The place it stood at when last read, for when {@link #mAfter} has left the row. */
    private int mLastSlot;
    /** Whether the display window is the row's window in front. */
    private boolean mFront;

    /** Whether the display window stands in a row. */
    public boolean isOnRow() {
        return mOnRow;
    }

    /** The display window joins the row at its end, as a new window joins (ADR 0008). */
    public void joinAtEnd(@NonNull List<W> windows) {
        mOnRow = true;
        place(windows, windows.size());
    }

    /** The display window leaves the row: it closed, or the display went down. */
    public void leave() {
        mOnRow = false;
        mAfter = null;
        mLastSlot = 0;
        mFront = false;
    }

    /**
     * The row place the display window stands at among {@code windows}, from 0 (before every
     * terminal window) to {@code windows.size()} (after them all); {@link #NONE} while it is not
     * on the row.
     */
    public int slot(@NonNull List<W> windows) {
        if (!mOnRow) return NONE;
        if (mAfter == null) {
            mLastSlot = 0;
            return 0;
        }
        int at = windows.indexOf(mAfter);
        if (at >= 0) {
            mLastSlot = at + 1;
            return mLastSlot;
        }
        // The window it stood after left the row: it keeps the place that window had.
        place(windows, mLastSlot - 1);
        return mLastSlot;
    }

    /** Stand the display window at row place {@code slot} among {@code windows}, clamped. */
    public void place(@NonNull List<W> windows, int slot) {
        int at = Math.max(0, Math.min(windows.size(), slot));
        mAfter = at == 0 ? null : windows.get(at - 1);
        mLastSlot = at;
    }

    /** Whether the display window is the row's window in front. */
    public boolean isFront() {
        return mOnRow && mFront;
    }

    public void setFront(boolean front) {
        mFront = mOnRow && front;
    }

    // ---- Row places and window indices --------------------------------------------------

    /** How many windows the row has: the terminal windows, and the display window at {@code slot}. */
    public static int rowCount(int windows, int slot) {
        return Math.max(0, windows) + (slot >= 0 ? 1 : 0);
    }

    /** Whether row place {@code row} is the display window's. */
    public static boolean isDisplay(int row, int slot) {
        return slot >= 0 && row == slot;
    }

    /**
     * The terminal window at row place {@code row}, as an index into the session's list; NONE for
     * the display window's place.
     */
    public static int windowAt(int row, int slot) {
        if (isDisplay(row, slot)) return NONE;
        return slot >= 0 && row > slot ? row - 1 : row;
    }

    /** The row place of terminal window {@code window}. */
    public static int rowOf(int window, int slot) {
        return slot >= 0 && window >= slot ? window + 1 : window;
    }

    /**
     * The row place in front: the display's while it is in front, else the current terminal
     * window's. A row of the display alone has it in front whatever was asked.
     */
    public static int rowCurrent(boolean displayFront, int current, int windows, int slot) {
        if (slot >= 0 && (displayFront || windows <= 0)) return slot;
        return rowOf(Math.max(0, Math.min(current, Math.max(0, windows - 1))), slot);
    }

    /**
     * Where a new window goes, in row places: right after the window in front
     * ({@link WallRow#insertionIndex}), the display window counting as one.
     */
    public static int insertionRow(int rowCurrent, int rowCount) {
        return WallRow.insertionIndex(rowCurrent, rowCount);
    }

    /**
     * The terminal window index a window inserted at row place {@code row} takes, with the display
     * at {@code slot} as the row stood before the insertion.
     */
    public static int windowIndexForInsert(int row, int slot) {
        return slot >= 0 && row > slot ? row - 1 : row;
    }

    /** The display's row place after a window was inserted at row place {@code row}. */
    public static int slotAfterInsert(int row, int slot) {
        if (slot < 0) return NONE;
        return row <= slot ? slot + 1 : slot;
    }

    /**
     * A window of the row dragged from row place {@code from} to row place {@code to}, with the
     * display at {@code slot} among {@code windows} terminal windows. The answer is what the
     * activity applies: the terminal window move, if any, and where the display stands after.
     */
    @NonNull
    public static Move move(int windows, int slot, int from, int to) {
        int count = rowCount(windows, slot);
        if (from < 0 || from >= count || to < 0 || to >= count) return Move.REFUSED;
        if (from == to) return new Move(NONE, NONE, slot);
        if (isDisplay(from, slot)) return new Move(NONE, NONE, to);
        int window = windowAt(from, slot);
        // The row without the moving window, then the window put back at `to`: the display's
        // place among the rest, and the window's among the terminal windows.
        int displayAmongRest = slot < 0 ? NONE : (from < slot ? slot - 1 : slot);
        int newSlot = displayAmongRest < 0 ? NONE
            : (to <= displayAmongRest ? displayAmongRest + 1 : displayAmongRest);
        int target = newSlot >= 0 && to > newSlot ? to - 1 : to;
        return new Move(window, target, newSlot);
    }

    /** {@link #move}'s answer. */
    public static final class Move {
        /** The row would not take the move: a place outside it. */
        public static final Move REFUSED = new Move(NONE, NONE, NONE, false);

        /** The terminal window that moves, as an index into the list before the move; or NONE. */
        public final int windowFrom;
        /** Where it goes in that list; NONE when no terminal window moves. */
        public final int windowTo;
        /** The display's row place afterwards; NONE when it is not on the row. */
        public final int slot;
        /** Whether the move is one the row takes. */
        public final boolean accepted;

        Move(int windowFrom, int windowTo, int slot) {
            this(windowFrom, windowTo, slot, true);
        }

        private Move(int windowFrom, int windowTo, int slot, boolean accepted) {
            this.windowFrom = windowFrom;
            this.windowTo = windowTo;
            this.slot = slot;
            this.accepted = accepted;
        }

        @NonNull @Override public String toString() {
            return accepted ? "Move{window " + windowFrom + "->" + windowTo + ", slot " + slot + "}"
                : "Move{refused}";
        }
    }

    /**
     * The row place that takes the front after the window in front, at row place
     * {@code removedRow}, left a row now holding {@code remainingRows}: its right-hand neighbour,
     * which moved up into its place, or the left one when it was last ({@code WindowCloseFocus}'s
     * rule, counted in row places so the display window can be that neighbour). NONE when the row
     * is empty.
     */
    public static int rowAfterRemoval(int removedRow, int remainingRows) {
        if (remainingRows <= 0) return NONE;
        return Math.max(0, Math.min(removedRow, remainingRows - 1));
    }

    /**
     * Where the row lands when the display window leaves it while in front: the window that stood
     * on its left, or the first window when it stood first; NONE when no terminal window is left.
     */
    public static int windowAfterDisplayLeaves(int slot, int windows) {
        if (windows <= 0) return NONE;
        return Math.max(0, Math.min(windows - 1, slot - 1));
    }
}
