package com.termux.app.page;

import androidx.annotation.NonNull;

/**
 * The one page kind (ADR 0030 section 1): the wall, the dock, the page switch and the chrome all
 * name a page's kind with this enum. {@code LookGrid.Kind} and {@code LayoutKind} are storage keys
 * read from it ({@code LayoutKind.of}).
 */
public enum PageKind {
    HOME,
    TERMINAL,
    DISPLAY,
    ASSISTANT;

    private static final PageKind[] ROOM_KINDS = computeRoomKinds();

    /**
     * Whether this kind stands a page of its own room; a display window is the Terminal's, so it
     * does not.
     */
    public boolean hasOwnRoom() {
        return this != DISPLAY;
    }

    /**
     * The kind whose room a page of this kind stands in: its own, or the Terminal's for a display
     * window, which ends where the Terminal's rest (read off the display while it is in front)
     * puts it, so its keyboard mode decides whether the keyboard takes room from it.
     */
    @NonNull
    public PageKind roomKind() {
        return hasOwnRoom() ? this : TERMINAL;
    }

    /**
     * The kinds that stand a page of their own room, in enum order. The array is shared: callers
     * iterate it and never write to it.
     */
    @NonNull
    public static PageKind[] roomKinds() {
        return ROOM_KINDS;
    }

    private static PageKind[] computeRoomKinds() {
        PageKind[] all = values();
        int count = 0;
        for (PageKind kind : all) if (kind.hasOwnRoom()) count++;
        PageKind[] rooms = new PageKind[count];
        int next = 0;
        for (PageKind kind : all) if (kind.hasOwnRoom()) rooms[next++] = kind;
        return rooms;
    }

    /** Home's side for a Home page, the Terminal's for every window. */
    @NonNull
    public Side side() {
        return this == HOME ? Side.HOME : Side.TERMINAL;
    }
}
