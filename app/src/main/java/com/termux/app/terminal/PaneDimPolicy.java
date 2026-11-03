package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which panes of the window on screen are drawn dimmed (spec decision 8): with more than one pane
 * in it, every pane but the focused one; with one, none. Panes of other windows are off screen and
 * never dimmed.
 */
public final class PaneDimPolicy {

    private PaneDimPolicy() {}

    /**
     * @param active the window's focused pane, or null
     * @param leaves every pane of the window on screen (tiled and floating); nulls are skipped
     * @return the panes to dim
     */
    @NonNull
    public static <T> Set<T> dimmed(@Nullable T active, @NonNull List<T> leaves) {
        Set<T> out = new HashSet<>();
        if (leaves.size() <= 1) return out;
        for (T leaf : leaves)
            if (leaf != null && !leaf.equals(active)) out.add(leaf);
        return out;
    }
}
