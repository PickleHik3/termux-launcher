package com.termux.app.terminal;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Consume and expel, Niri's words for moving panes into and out of columns, on the Workspace's
 * row: each window of the row is a column. Arithmetic only — the activity takes the window out of
 * or puts it into the row, the pane controller moves the panes; this decides which window and
 * where.
 *
 * <p>The rules:
 * <ul>
 *   <li><b>Consume</b> pulls the current window's neighbour into it, the one on the right unless
 *   the left one is asked for. At the row's end on that side, or with one window, there is nothing
 *   to pull ({@link #INVALID}). The neighbour leaves the row; the current window stays current,
 *   and its index drops by one when the neighbour stood before it ({@link #currentAfterRemove}).
 *   <li><b>Expel</b> pushes the current window's focused pane out into a new window of its own,
 *   placed right after the current window, or right before it when asked. Focus goes with the
 *   expelled pane, as in Niri: the new window becomes the current one ({@link #currentAfterExpel}).
 * </ul>
 */
public final class ColumnPolicy {

    private ColumnPolicy() {}

    /** No window to consume, no place to expel to, or an argument that names neither side. */
    public static final int INVALID = -1;

    /** {@link #side}'s answer for the right-hand side, the default. */
    public static final int RIGHT = 1;
    /** {@link #side}'s answer for the left-hand side. */
    public static final int LEFT = 0;

    /**
     * The side a {@code from} or {@code to} argument names: {@link #RIGHT} when it is absent or
     * blank, {@link #LEFT} or {@link #RIGHT} for those words, {@link #INVALID} for anything else.
     */
    public static int side(@Nullable String value) {
        if (value == null) return RIGHT;
        String side = value.trim().toLowerCase(Locale.ROOT);
        if (side.isEmpty() || side.equals("right")) return RIGHT;
        if (side.equals("left")) return LEFT;
        return INVALID;
    }

    /**
     * The index of the window a consume pulls into the window at {@code current}: its neighbour on
     * the left when {@code fromLeft}, on the right otherwise. {@link #INVALID} at the row's end on
     * that side, with one window, or with {@code current} outside the row.
     */
    public static int consumeSource(int current, int count, boolean fromLeft) {
        if (count < 2 || current < 0 || current >= count) return INVALID;
        int source = fromLeft ? current - 1 : current + 1;
        return source >= 0 && source < count ? source : INVALID;
    }

    /**
     * Where the window that was at {@code current} stands once the one at {@code removed} left the
     * row: one place earlier when the removed one stood before it, the same place otherwise.
     * {@code removed} is never {@code current} here; a consume never takes the current window.
     */
    public static int currentAfterRemove(int current, int removed) {
        return removed < current ? current - 1 : current;
    }

    /**
     * The index the window an expel makes takes in a row of {@code count} with {@code current}
     * shown: right after it ({@code current + 1}), or right before it ({@code current}) when
     * {@code toLeft}, which moves the current window one place on. {@link #INVALID} with
     * {@code current} outside the row.
     */
    public static int expelTarget(int current, int count, boolean toLeft) {
        if (count < 1 || current < 0 || current >= count) return INVALID;
        return toLeft ? current : current + 1;
    }

    /** The current index after an expel put the new window at {@code target}: the new window's. */
    public static int currentAfterExpel(int target) {
        return target;
    }
}
