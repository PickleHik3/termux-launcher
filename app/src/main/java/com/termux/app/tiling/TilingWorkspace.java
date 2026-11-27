package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One numbered Workspace: its tile groups in order, the Floats above them, its layout and its
 * name. The stack renumbers it as Workspaces above it come and go; its name stays with it.
 */
public final class TilingWorkspace {

    final List<TileGroup> groups = new ArrayList<>();
    final List<TilingWindow> floats = new ArrayList<>();
    @NonNull WorkspaceLayout layout = WorkspaceLayout.SCROLLING;
    @Nullable String layoutState;
    @Nullable String name;
    @Nullable TilingWindow focused;
    int number;

    /** Its number, from 1, counted down from the top of the stack. */
    public int number() {
        return number;
    }

    /** The name it was given, or null to show its number alone. */
    @Nullable
    public String name() {
        return name;
    }

    @NonNull
    public WorkspaceLayout layout() {
        return layout;
    }

    /**
     * What the layout policy keeps for this Workspace (column widths, split ratios), saved and
     * restored as it is; the stack never reads it.
     */
    @Nullable
    public String layoutState() {
        return layoutState;
    }

    public boolean isEmpty() {
        return groups.isEmpty() && floats.isEmpty();
    }

    @NonNull
    public List<TileGroup> groups() {
        return Collections.unmodifiableList(groups);
    }

    @NonNull
    public List<TilingWindow> floats() {
        return Collections.unmodifiableList(floats);
    }

    /** Its tiled windows in order, group by group. */
    @NonNull
    public List<TilingWindow> tiledWindows() {
        List<TilingWindow> all = new ArrayList<>();
        for (TileGroup group : groups) all.addAll(group.windows);
        return all;
    }

    /** Every window it owns: the tiles in order, then the Floats. */
    @NonNull
    public List<TilingWindow> windows() {
        List<TilingWindow> all = tiledWindows();
        all.addAll(floats);
        return all;
    }

    /**
     * The window whose icon stands for it: the biggest, and among equals the one open longest.
     * Null for an empty Workspace.
     */
    @Nullable
    public TilingWindow mainWindow() {
        TilingWindow main = null;
        for (TilingWindow window : windows()) {
            if (main == null || window.area > main.area
                || (window.area == main.area && window.id() < main.id())) {
                main = window;
            }
        }
        return main;
    }
}
