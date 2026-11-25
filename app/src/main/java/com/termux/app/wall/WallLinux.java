package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * The Linux windows as the session's row asks them (spec #384, #392): one view, which the wall
 * stands in the Terminal page's place while a Linux window is in front of the row, bound to one
 * window at a time. {@link #NONE} for a build without Wayland Display. Main thread only.
 */
public interface WallLinux {

    /** The view now shows window {@code id}; 0 for none. */
    default void bind(long id) { }

    /**
     * Whether the window bound to the view may hold its surface now: it is in front of the row or
     * next to it, on the Terminal page in front ({@code LinuxWindowRow.holdsSurface} over row
     * places). Told on every landing and row change.
     */
    default void setNearFront(boolean near) { }

    /** The bound window is the one in front: the Terminal page is in front and so is it in the row. */
    default void setInFront(boolean front) { }

    /**
     * The page now in front (told on every landing): a Linux window drawn over a page hides when
     * another page comes in front.
     */
    default void setFrontPage(@NonNull PageKind kind, int pageId) { }

    /** Dress the view from the surface style, as the panes beside it are. */
    default void applyStyle(@Nullable PaneSurfaceStyle style) { }

    WallLinux NONE = new WallLinux() { };
}
