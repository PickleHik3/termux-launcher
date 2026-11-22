package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * The font toggle's rules: one tap moves a pane between its own size and the Compact size, and
 * the next tap restores exactly what it had. Pure; sizes are in the font size's own unit.
 *
 * <p>A pane's state is one number, the size to return to: {@link #NOT_COMPACT} while it is at its
 * own size, 0 when it was following the app-wide default, else the pinned size it had.
 */
public final class FontCompactPolicy {

    private FontCompactPolicy() {}

    /** The pane is at its own size. */
    public static final int NOT_COMPACT = -1;

    /** Compact's share of the app-wide default size, in percent. */
    public static final int DEFAULT_PERCENT = 70;

    /** What a tap leaves on the pane: its pinned size (0 follows the default) and its state. */
    public static final class Next {
        public final int pinnedSize;
        public final int returnSize;
        Next(int pinnedSize, int returnSize) {
            this.pinnedSize = pinnedSize;
            this.returnSize = returnSize;
        }
    }

    /**
     * The tap: from its own size the pane remembers it (0 while it follows the default) and takes
     * {@code compactSize}; from Compact it takes back what it remembered.
     */
    @NonNull
    public static Next toggle(int pinnedSize, int returnSize, int compactSize) {
        if (isCompact(returnSize)) return new Next(returnSize, NOT_COMPACT);
        return new Next(compactSize, Math.max(0, pinnedSize));
    }

    public static boolean isCompact(int returnSize) {
        return returnSize >= 0;
    }

    /** A pinch or a font increase or decrease makes the new size the pane's own. */
    public static int afterManualChange(int returnSize) {
        return NOT_COMPACT;
    }

    /** The Compact size: the stored one, or else {@link #DEFAULT_PERCENT} of the default, clamped. */
    public static int compactSize(int stored, int appDefault, int min, int max) {
        int size = stored > 0 ? stored : Math.round(appDefault * DEFAULT_PERCENT / 100f);
        return Math.max(min, Math.min(size, max));
    }

    /** The saved form of a pane's state, or {@link #NOT_COMPACT} when nothing was saved. */
    public static int restore(int saved) {
        return saved < 0 ? NOT_COMPACT : saved;
    }
}
