package com.termux.app.wall;

import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * The Linux windows as the wall asks them (spec #384): one view for every Linux window's page,
 * bound to one page at a time as the display view is. {@link #NONE} for a build without Wayland
 * Display. Main thread only.
 */
public interface WallLinux {

    /** The Linux view now stands at page {@code pageId}; -1 when the wall has no Linux page. */
    default void bind(int pageId) { }

    /**
     * Whether the window bound to the view may hold its surface now: its cell is in front or next
     * to it ({@code LinuxWindowRow.holdsSurface}). Told on every landing and page-list change.
     */
    default void setNearFront(boolean near) { }

    /** Dress the view from the surface style, as the panes beside it are. */
    default void applyStyle(@Nullable PaneSurfaceStyle style) { }

    WallLinux NONE = new WallLinux() { };
}
