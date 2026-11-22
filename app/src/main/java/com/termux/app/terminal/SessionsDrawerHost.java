package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the sessions drawer asks of the wall: where Home floats live, which displays are up and what
 * is open on them, and the moves between Home, a display and a float. Every method defaults to doing
 * nothing and knowing nothing, so the drawer builds, runs and tests without the activity; the wall
 * installs a real one with {@link SessionsDrawerView#setHost}.
 *
 * <p>The drawer only asks. Closing itself after a move is the host's call, as it is for
 * {@link SessionsDrawerView.Listener}.
 */
public interface SessionsDrawerHost {

    /** Returned by {@link #homeFloatPage} when the host cannot say which page holds the float. */
    int UNKNOWN_PAGE = -1;

    /** The host that does nothing and knows of no float and no display. */
    SessionsDrawerHost NONE = new SessionsDrawerHost() { };

    /** One open window on a display, by the name its window manager lists it under. */
    final class Toplevel {
        /** The X window id, as {@code X11WindowList.Window#id}. */
        public final int id;
        @NonNull public final String label;

        public Toplevel(int id, @Nullable String label) {
            this.id = id;
            this.label = label == null ? "" : label.trim();
        }
    }

    /** One display window, and the toplevels open on it, in the window manager's order. */
    final class Display {
        /** The X display name the host knows it by, such as {@code ":0"}. */
        @NonNull public final String id;
        /** What its row says; the display's name when blank. */
        @NonNull public final String label;
        @NonNull public final List<Toplevel> toplevels;

        public Display(@NonNull String id, @Nullable String label,
                       @NonNull List<Toplevel> toplevels) {
            this.id = id;
            String trimmed = label == null ? "" : label.trim();
            this.label = trimmed.isEmpty() ? id : trimmed;
            this.toplevels = Collections.unmodifiableList(new ArrayList<>(toplevels));
        }
    }

    /** The zero-based Home page the float is pinned to, or {@link #UNKNOWN_PAGE}. */
    default int homeFloatPage(long windowId) {
        return UNKNOWN_PAGE;
    }

    /** Every display window that is up; empty when there is none, which hides the group. */
    @NonNull
    default List<Display> displays() {
        return Collections.emptyList();
    }

    /** Slides the wall to Home page 1. */
    default void goHome() { }

    /** Slides the wall to the display window and brings it to front. */
    default void goToDisplay(@NonNull String displayId) { }

    /** Raises the toplevel on its display, and the display with it. */
    default void raiseToplevel(@NonNull String displayId, int toplevelId) { }

    /**
     * Pages Home to the float's page and brings the float to front there, restoring it if
     * minimised. Window ids are unique across sessions, so the id alone names the float.
     */
    default void restoreFloat(long windowId) { }
}
