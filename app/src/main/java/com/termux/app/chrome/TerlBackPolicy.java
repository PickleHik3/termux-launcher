package com.termux.app.chrome;

import androidx.annotation.NonNull;

import java.util.Set;

/**
 * What Back closes under Terl (#434): whatever lies over the windows, the topmost first, and with
 * nothing open, nothing (no v1 page fallback, no finish, no Home paging). {@link #ORDER} is the
 * order {@code TermuxActivity.createOverlayRegistry} registers the Terl surfaces in; the test
 * keeps the two from drifting.
 */
public final class TerlBackPolicy {

    private TerlBackPolicy() {}

    /** The surfaces over the windows, topmost first. */
    public enum Surface {
        /** The bar's search, typed into. */
        SEARCH,
        /** Edit mode and its card. */
        EDIT_MODE,
        /** The Workspace list. */
        LIST,
        /** A reading's card or a held mark's window menu. */
        SHEET,
        /** Overview. */
        OVERVIEW,
        /** The drawer sheet. */
        DRAWER_SHEET,
        /** Navigation mode (#433, not built: its slot). */
        NAVIGATION_MODE,
        /** A window's full screen: the lowest, a state of a window. */
        FULL_SCREEN
    }

    /** Topmost first. */
    public static final Surface[] ORDER = Surface.values();

    /** The surface Back closes among {@code open}, or null when none is open. */
    public static Surface topmost(@NonNull Set<Surface> open) {
        for (Surface surface : ORDER) if (open.contains(surface)) return surface;
        return null;
    }

    /**
     * Whether a Back nothing took is spent anyway: under Terl yes (it does nothing); with the
     * switch off v1's page chain runs as before.
     */
    public static boolean spendsBackWhenNothingOpen(boolean terlOn) {
        return terlOn;
    }
}
