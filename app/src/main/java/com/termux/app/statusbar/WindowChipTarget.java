package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * What a tap on a window chip in the Workspace's bar brings in front. The chips are the row's
 * windows, then the display window's chip while a display is up, then the Home floats. They are
 * the same on every page of the Workspace, the display window's included: the display is a window
 * of the Workspace (ADR 0008), so its page wears the Workspace's chips with its own selected,
 * rather than a bar of the display's own apps.
 *
 * <p>Pure: the activity hands it the counts and the page, and applies the answer.
 */
public final class WindowChipTarget {

    /** What the chip stands for. */
    public enum Kind { NONE, WINDOW, DISPLAY, HOME_FLOAT }

    @NonNull public final Kind kind;
    /** The window's place in the row, or the float's among the floats; -1 otherwise. */
    public final int index;
    /**
     * The wall has to move to show it: a terminal window's chip tapped on the display window, or
     * the display's chip tapped on a terminal window. A float's chip moves the wall to Home itself.
     */
    public final boolean moveWall;

    private WindowChipTarget(@NonNull Kind kind, int index, boolean moveWall) {
        this.kind = kind;
        this.index = index;
        this.moveWall = moveWall;
    }

    private static final WindowChipTarget NONE = new WindowChipTarget(Kind.NONE, -1, false);

    /**
     * The chip at {@code chip} of a bar standing over a terminal window or, with
     * {@code onDisplay}, over the display window. {@code windows} counts the row's windows and
     * {@code floats} the Home floats; {@code displayUp} says whether the display has its chip.
     */
    @NonNull
    public static WindowChipTarget of(int chip, boolean onDisplay, int windows, boolean displayUp,
                                      int floats) {
        if (chip < 0) return NONE;
        if (chip < windows) return new WindowChipTarget(Kind.WINDOW, chip, onDisplay);
        int afterWindows = chip - windows;
        if (displayUp) {
            if (afterWindows == 0) return new WindowChipTarget(Kind.DISPLAY, -1, !onDisplay);
            afterWindows--;
        }
        if (afterWindows < floats) return new WindowChipTarget(Kind.HOME_FLOAT, afterWindows, true);
        return NONE;
    }

    /**
     * The chip the bar selects: the display's, after the row's windows, while the display window
     * is in front; otherwise the current window's, held to the row.
     */
    public static int selectedChip(boolean onDisplay, boolean displayUp, int current, int windows) {
        if (onDisplay && displayUp) return Math.max(0, windows);
        return Math.max(0, Math.min(current, windows - 1));
    }

    @NonNull @Override public String toString() {
        return "WindowChipTarget{" + kind + ", index=" + index + ", moveWall=" + moveWall + "}";
    }
}
