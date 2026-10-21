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
 * bar and its window column, Home's Taskbar run strip, the Wall strip's tiles, and the chips
 * minimised floats come back from. This is also the one place that says which chip is selected
 * and what a tap or a close on a chip means ({@link Meaning}); the activity carries the meaning
 * out.
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
         * The Workspace's bar and its window column: every chip, and nothing on Home, which wears
         * no status bar.
         */
        BAR,
        /** Home's Taskbar run strip: every chip; a window's tap slides the Workspace in. */
        RUN_STRIP,
        /** The Wall strip's tiles: as the run strip. */
        WALL_STRIP,
        /** The chips above Home's dock: the minimised floats that stand on a Home page. */
        FLOAT_CHIPS
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
        /** Whether a float stands on a Home page; a row's window always does. */
        final boolean onPage;

        Chip(@NonNull Kind kind, @Nullable W window, int index, int row, boolean minimised,
             boolean onPage) {
            this.kind = kind;
            this.window = window;
            this.index = index;
            this.row = row;
            this.minimised = minimised;
            this.onPage = onPage;
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
            /** Travel Home to {@link #window}'s page and bring that float back, focused. */
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
        /** Whether the Workspace slides in first: the chip was tapped away from it. */
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
            mChips.add(new Chip<>(Kind.WINDOW, rowWindows.get(i), i, mChips.size(), false, true));
        }
        if (displaySlot == windows) addDisplay();
    }

    private void addDisplay() {
        mChips.add(new Chip<>(Kind.DISPLAY, null, -1, mChips.size(), false, true));
    }

    /**
     * A Home float's chip after the row's and the floats' before it: {@code minimised} while it
     * is folded into its chip, {@code onPage} while it stands on a Home page, {@code focused}
     * while it holds the focus.
     */
    @NonNull
    public WindowChips<W> addFloat(@NonNull W window, boolean minimised, boolean onPage,
                                   boolean focused) {
        if (focused && !minimised) mFocusedChip = mChips.size();
        mChips.add(new Chip<>(Kind.HOME_FLOAT, window, mFloats++, -1, minimised, onPage));
        return this;
    }

    /** The chips {@code surface} shows, in its order: its index is a place in this list. */
    @NonNull
    public List<Chip<W>> chips(@NonNull Surface surface) {
        switch (surface) {
            case BAR:
                return barShows() ? Collections.unmodifiableList(mChips)
                    : Collections.<Chip<W>>emptyList();
            case FLOAT_CHIPS: {
                List<Chip<W>> minimised = new ArrayList<>(mFloats);
                for (Chip<W> chip : mChips) {
                    if (chip.kind == Kind.HOME_FLOAT && chip.minimised && chip.onPage)
                        minimised.add(chip);
                }
                return minimised;
            }
            default:
                return Collections.unmodifiableList(mChips);
        }
    }

    /** How many chips are the row's windows, the display window's included; the floats follow. */
    public int rowCount() {
        return mRowCount;
    }

    /** The display window's chip, or -1 while it is not on the row. */
    public int displayChip() {
        return mDisplayChip;
    }

    /**
     * The chip {@code surface} selects, or -1: on the bar the row's window in front, held to the
     * row; on the strips the float in focus, else the same; none among the float chips.
     */
    public int selected(@NonNull Surface surface) {
        switch (surface) {
            case BAR:
                return barShows() ? rowFront() : -1;
            case FLOAT_CHIPS:
                return -1;
            default:
                if (mFocusedChip >= 0) return mFocusedChip;
                return mRowShown ? rowFront() : -1;
        }
    }

    /**
     * What a tap on {@code surface}'s chip at {@code index} means: a window's brings it in front,
     * the Workspace sliding in when the chip is on Home's dock; a float's travels Home to its
     * page and brings it back.
     */
    @NonNull
    public Meaning<W> tap(@NonNull Surface surface, int index) {
        Chip<W> chip = chipAt(surface, index);
        if (chip == null) return nothing();
        if (chip.kind == Kind.HOME_FLOAT)
            return new Meaning<>(Meaning.Action.RESTORE_FLOAT, -1, chip.window, false);
        return new Meaning<>(Meaning.Action.SHOW_WINDOW, chip.row, null,
            surface == Surface.RUN_STRIP || surface == Surface.WALL_STRIP);
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

    /** Home wears no status bar, and there is no bar without a row. */
    private boolean barShows() {
        return !mHome && mRowShown;
    }

    /** The row's window in front, held to the row. */
    private int rowFront() {
        return Math.max(0, Math.min(mRowCurrent, mRowCount - 1));
    }

    @Nullable
    private Chip<W> chipAt(@NonNull Surface surface, int index) {
        if (index < 0) return null;
        List<Chip<W>> chips = chips(surface);
        return index < chips.size() ? chips.get(index) : null;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static <W> Meaning<W> nothing() {
        return (Meaning<W>) NOTHING;
    }
}
