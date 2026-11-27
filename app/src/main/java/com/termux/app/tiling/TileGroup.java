package com.termux.app.tiling;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One slot in a Workspace's tile order: a column in Scrolling or a tile in Dwindle. It holds one
 * window, several stacked top over bottom, or several as tabs; {@link #active()} is the window a
 * sideways move lands on.
 */
public final class TileGroup {

    public enum Mode {
        /** Windows stacked top over bottom (a Scrolling column). */
        STACK("stack"),
        /** Windows as tabs of one tile, the active one shown. */
        TABS("tabs");

        @NonNull public final String key;

        Mode(@NonNull String key) {
            this.key = key;
        }

        @NonNull
        static Mode fromKey(String key) {
            return TABS.key.equals(key) ? TABS : STACK;
        }
    }

    final List<TilingWindow> windows = new ArrayList<>();
    @NonNull Mode mode = Mode.STACK;
    int active;

    TileGroup(@NonNull TilingWindow first) {
        windows.add(first);
    }

    TileGroup() {
    }

    @NonNull
    public List<TilingWindow> windows() {
        return Collections.unmodifiableList(windows);
    }

    @NonNull
    public Mode mode() {
        return mode;
    }

    @NonNull
    public TilingWindow active() {
        return windows.get(Math.max(0, Math.min(active, windows.size() - 1)));
    }
}
