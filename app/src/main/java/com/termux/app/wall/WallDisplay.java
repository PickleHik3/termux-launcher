package com.termux.app.wall;

/**
 * What the wall tells the Display: which Display page the one display view stands on, and that
 * the wall came to rest on it. The wall tells this seam instead of its Host. Main thread only.
 */
public interface WallDisplay {

    /**
     * Bind the one display view to Display page {@code pageId} (ADR 0015, A3b): the Display page
     * the wall is on or heading for, which shows the display live while the others show their
     * picture or card. -1 while the wall has no Display page.
     */
    void bind(int pageId);

    /**
     * The wall came to rest on Display page {@code pageId}, the bound one: the display starts
     * here when it is down ({@link DisplayPagePolicy#startsOnLanding}).
     */
    void landed(int pageId);

    /** A Display that hears nothing, for a wall whose host has none. */
    WallDisplay NONE = new WallDisplay() {
        @Override public void bind(int pageId) { }
        @Override public void landed(int pageId) { }
    };
}
