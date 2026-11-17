package com.termux.app.page;

import androidx.annotation.NonNull;

/**
 * A side of the seam: Home or the Workspace (ADR 0030 section 2). Home pages are Home's; the
 * terminal, display and Assistant windows are the Workspace's. A side owns the rows its pages
 * share, and they travel with it: within a side they stay put, between sides they slide with it
 * (the stack's scene places them, {@code StackScene.Frame#sideTx}).
 */
public enum Side {
    HOME,
    WORKSPACE;

    /** The number of sides: a frame's per-side arrays are this long, indexed by ordinal. */
    public static final int COUNT = 2;

    /** The rows a side may own. */
    public enum Row {
        /** Home's pinned apps row. */
        APPS,
        /** Home's A-Z row. */
        AZ,
        /** Home's search field. */
        SEARCH,
        /** An extra-keys bar: each side owns its own, so when both show one both travel. */
        EXTRA_KEYS,
        /** The Workspace's minimal-layout tab. */
        MINIMAL_TAB,
        /** The Workspace's window chips. */
        WINDOW_CHIPS
    }

    /** Home's side for a Home page, the Workspace's for any window. */
    @NonNull
    public static Side of(boolean homePage) {
        return homePage ? HOME : WORKSPACE;
    }

    /** The side across the seam. */
    @NonNull
    public Side other() {
        return this == HOME ? WORKSPACE : HOME;
    }

    /** Whether this side owns {@code row}. */
    public boolean owns(@NonNull Row row) {
        switch (row) {
            case APPS:
            case AZ:
            case SEARCH:
                return this == HOME;
            case EXTRA_KEYS:
                return true;
            default:
                return this == WORKSPACE;
        }
    }
}
