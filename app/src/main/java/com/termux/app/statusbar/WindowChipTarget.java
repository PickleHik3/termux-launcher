package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * What a tap on a window chip in the Workspace's bar brings in front. The chips are the row's
 * windows in row order — the display window's among them, at its place in the row
 * ({@link com.termux.app.wall.DisplaySlot}) — then the Home floats. The display window is a window
 * of the Workspace like any other (ADR 0008, amended 2026-10-19), so its chip is a window chip: the
 * activity tells which window a row place holds.
 *
 * <p>Pure: the activity hands it the counts, and applies the answer.
 */
public final class WindowChipTarget {

    /** What the chip stands for. */
    public enum Kind { NONE, WINDOW, HOME_FLOAT }

    @NonNull public final Kind kind;
    /** The window's place in the row, or the float's among the floats; -1 otherwise. */
    public final int index;

    private WindowChipTarget(@NonNull Kind kind, int index) {
        this.kind = kind;
        this.index = index;
    }

    private static final WindowChipTarget NONE = new WindowChipTarget(Kind.NONE, -1);

    /**
     * The chip at {@code chip}. {@code windows} counts the row's windows, the display window's
     * included, and {@code floats} the Home floats.
     */
    @NonNull
    public static WindowChipTarget of(int chip, int windows, int floats) {
        if (chip < 0) return NONE;
        if (chip < windows) return new WindowChipTarget(Kind.WINDOW, chip);
        int afterWindows = chip - windows;
        if (afterWindows < floats) return new WindowChipTarget(Kind.HOME_FLOAT, afterWindows);
        return NONE;
    }

    /** The chip the bar selects: the row's window in front, held to the row. */
    public static int selectedChip(int current, int windows) {
        return Math.max(0, Math.min(current, windows - 1));
    }

    @NonNull @Override public String toString() {
        return "WindowChipTarget{" + kind + ", index=" + index + "}";
    }
}
