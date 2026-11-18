package com.termux.app.page;

import androidx.annotation.NonNull;

/**
 * The one page kind (ADR 0030 section 1): the wall, the dock, the page switch and the chrome all
 * name a page's kind with this enum. {@code LookGrid.Kind} and {@code LayoutKind} are storage keys
 * read from it ({@code WallLookKinds.of}, {@code LayoutKind.of}).
 */
public enum PageKind {
    HOME,
    TERMINAL,
    DISPLAY,
    ASSISTANT;

    /** Home's side for a Home page, the Workspace's for every window. */
    @NonNull
    public Side side() {
        return this == HOME ? Side.HOME : Side.WORKSPACE;
    }
}
