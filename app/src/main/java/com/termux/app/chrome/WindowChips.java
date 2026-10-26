package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.DisplaySlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The window chips (ADR 0009, "The window chips"): one ordered list of the Workspace's row in row
 * order, the display window's chip at its place among the terminal windows' like any window's
 * (ADR 0008, amended 2026-10-19), then the Home floats in the order they were opened, each marked
 * minimised or not. Every surface that shows window chips adapts this list ({@link Surface}): the
 * bar and its window column, the Wall strip's tiles. A minimised
 * float folds into its chip in the bar, on both halves (#91), and comes back from it. This is
 * also the one place that says which chip is selected and what a tap or a close on a chip means
 * ({@link Meaning}); the activity carries the meaning out.
 *
 * <p>Built afresh per refresh and per tap from what the activity holds: the row's windows, where
 * the display window stands, which window is in front, and the floats. Pure: {@code W} is the
 * activity's window, never looked into.
 */
public final class WindowChips<W> {

    /** What a chip stands for. */
    public enum Kind { WINDOW, DISPLAY, HOME_FLOAT }

    /** A surface that shows the chips, each counting its own index. */
    public enum Surface {
        /**
         * The status bar and its window column: every chip, the same on both halves of the wall
         * (#90), a minimised float's among them (#91); a window's tap on Home slides the
         * Workspace in, a float's in the Workspace travels Home.
         */
        BAR,
        /** The Wall strip's tiles: every chip; a window's tap slides the Workspace in. */
        WALL_STRIP
    }

    /** One window chip. */
    public static final class Chip<W> {
        @NonNull public final Kind kind;
        /** The window it stands for; null for the display window's. */
        @Nullable public final W window;
        /**
         * The terminal window's index in the row's list, or the float's among the floats; -1 for
         * the display window's.
         */
        public final int index;
        /** The chip's place in the row, the display window's counted; -1 for a float. */
        public final int row;
        /** Whether this is a Home float folded into its chip. */
        public final boolean minimised;

        Chip(@NonNull Kind kind, @Nullable W window, int index, int row, boolean minimised) {
            this.kind = kind;
            this.window = window;
            this.index = index;
            this.row = row;
            this.minimised = minimised;
        }

        @NonNull @Override public String toString() {
            return "Chip{" + kind + " " + window + " index=" + index + " row=" + row
                + (minimised ? " minimised" : "") + "}";
        }
    }

    /** What a tap or a close on a chip means. */
    public static final class Meaning<W> {
        public enum Action {
            NONE,
            /** Bring the row's window at {@link #row} in front, the display window's included. */
            SHOW_WINDOW,
            /**
             * Bring the float {@link #window} back on its Home page, focused: the wall travels
             * Home first when the chip was tapped in the Workspace ({@link #travel}).
             */
            RESTORE_FLOAT,
            /** Close the row's window at {@link #row}: the display window's stops the display. */
            CLOSE_WINDOW,
            /** Close the float {@link #window}: its shell ends. */
            CLOSE_FLOAT
        }

        @NonNull public final Action action;
        /** The row place, for a window's meaning; -1 otherwise. */
        public final int row;
        /** The float, for a float's meaning; null otherwise. */
        @Nullable public final W window;
        /**
         * Whether the wall travels first: to the Workspace for a window's chip tapped on Home,
         * Home for a float's chip tapped in the Workspace.
         */
        public final boolean travel;

        Meaning(@NonNull Action action, int row, @Nullable W window, boolean travel) {
            this.action = action;
            this.row = row;
            this.window = window;
            this.travel = travel;
        }

        @NonNull @Override public String toString() {
            return "Meaning{" + action + " row=" + row + " " + window
                + (travel ? " travel" : "") + "}";
        }
    }

    private static final Meaning<?> NOTHING = new Meaning<>(Meaning.Action.NONE, -1, null, false);

    private final boolean mHome;
    private final boolean mRowShown;
    private final int mRowCount;
    private final int mDisplayChip;
    private final int mRowCurrent;
    private final List<Chip<W>> mChips;
    private int mFloats;
    /** The focused float's chip, while it is not minimised; -1 for none. */
    private int mFocusedChip = -1;

    /**
     * The chips of the row {@code rowWindows} (the terminal windows, in order) with the display
     * window at row place {@code displaySlot} ({@link DisplaySlot#NONE} when it is not on the row)
     * and row place {@code rowCurrent} in front, seen from Home when {@code home}. Without
     * {@code rowShown} (no session is current and the display window is not on the row) there is
     * no row, and only the floats are chips. Floats follow with {@link #addFloat}.
     */
    public WindowChips(boolean home, boolean rowShown, @NonNull List<W> rowWindows,
                       int displaySlot, int rowCurrent) {
        mHome = home;
        mRowShown = rowShown;
        mRowCurrent = rowCurrent;
        mChips = new ArrayList<>(rowWindows.size() + 2);
        if (!rowShown) {
            mRowCount = 0;
            mDisplayChip = -1;
            return;
        }
        int windows = rowWindows.size();
        mRowCount = DisplaySlot.rowCount(windows, displaySlot);
        mDisplayChip = displaySlot >= 0 ? displaySlot : -1;
        for (int i = 0; i < windows; i++) {
            if (i == displaySlot) addDisplay();
            mChips.add(new Chip<>(Kind.WINDOW, rowWindows.get(i), i, mChips.size(), false));
        }
        if (displaySlot == windows) addDisplay();
    }

    private void addDisplay() {
        mChips.add(new Chip<>(Kind.DISPLAY, null, -1, mChips.size(), false));
    }

    /**
     * A Home float's chip after the row's and the floats' before it: {@code minimised} while it
     * is folded into its chip, {@code focused} while it holds the focus.
     */
    @NonNull
    public WindowChips<W> addFloat(@NonNull W window, boolean minimised, boolean focused) {
        if (focused && !minimised) mFocusedChip = mChips.size();
        mChips.add(new Chip<>(Kind.HOME_FLOAT, window, mFloats++, -1, minimised));
        return this;
    }

    /**
     * The chips, in their order: every surface shows them all, so a surface's index is a place in
     * this list.
     */
    @NonNull
    public List<Chip<W>> chips() {
        return Collections.unmodifiableList(mChips);
    }

    /** How many chips are the row's windows, the display window's included; the floats follow. */
    public int rowCount() {
        return mRowCount;
    }

    /**
     * The bar's chip for the float {@code window}, minimised or not, or -1 when it is no float's:
     * where minimise's flight lands.
     */
    public int barChipOf(@NonNull W window) {
        for (int i = mRowCount; i < mChips.size(); i++) {
            Chip<W> chip = mChips.get(i);
            if (chip.kind == Kind.HOME_FLOAT && window.equals(chip.window)) return i;
        }
        return -1;
    }

    /** The display window's chip, or -1 while it is not on the row. */
    public int displayChip() {
        return mDisplayChip;
    }

    /**
     * The chip {@code surface} selects, or -1: on the bar the row's window in front, held to the
     * row, alike on both halves so the bar stands still on the swipe; on the strips the float in
     * focus, else the same.
     */
    public int selected(@NonNull Surface surface) {
        switch (surface) {
            case BAR:
                return mRowShown ? rowFront() : -1;
            default:
                if (mFocusedChip >= 0) return mFocusedChip;
                return mRowShown ? rowFront() : -1;
        }
    }

    /**
     * What a tap on {@code surface}'s chip at {@code index} means: a window's brings it in front,
     * the Workspace sliding in when the chip is on Home's dock or on the bar seen from Home; a
     * float's brings it back on its Home page, the wall travelling Home first when the chip was
     * tapped in the Workspace.
     */
    @NonNull
    public Meaning<W> tap(@NonNull Surface surface, int index) {
        Chip<W> chip = chipAt(surface, index);
        if (chip == null) return nothing();
        if (chip.kind == Kind.HOME_FLOAT)
            return new Meaning<>(Meaning.Action.RESTORE_FLOAT, -1, chip.window, !mHome);
        return new Meaning<>(Meaning.Action.SHOW_WINDOW, chip.row, null,
            surface == Surface.WALL_STRIP
                || surface == Surface.BAR && mHome);
    }

    /** What a close on {@code surface}'s chip at {@code index} means: that window closes. */
    @NonNull
    public Meaning<W> close(@NonNull Surface surface, int index) {
        Chip<W> chip = chipAt(surface, index);
        if (chip == null) return nothing();
        if (chip.kind == Kind.HOME_FLOAT)
            return new Meaning<>(Meaning.Action.CLOSE_FLOAT, -1, chip.window, false);
        return new Meaning<>(Meaning.Action.CLOSE_WINDOW, chip.row, null, false);
    }

    /** The row's window in front, held to the row. */
    private int rowFront() {
        return Math.max(0, Math.min(mRowCurrent, mRowCount - 1));
    }

    @Nullable
    private Chip<W> chipAt(@NonNull Surface surface, int index) {
        if (index < 0) return null;
        List<Chip<W>> chips = chips();
        return index < chips.size() ? chips.get(index) : null;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static <W> Meaning<W> nothing() {
        return (Meaning<W>) NOTHING;
    }
}
