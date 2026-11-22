package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import com.termux.app.page.PageKind;


/**
 * Whether the font toggle shows. The CPU and GPU: {@link StatusStatNeedsPolicy}. Where they stand is the user's order ({@link StatusRowOrder}), and the
 * dots between them follow who shows ({@link StatusRowOrder#dots}).
 */
public final class StatusStatsClusterPolicy {

    private StatusStatsClusterPolicy() {}

    /**
     * Whether the font toggle shows: its switch is on, the page is the Terminal, and the focused
     * pane is one it can toggle. Elsewhere, or with no terminal pane focused, it is hidden rather
     * than left to do nothing.
     */
    public static boolean fontToggleShown(boolean enabled, @NonNull PageKind page,
                                          boolean paneToggleable) {
        // Terminal only: on Home the focused pane is off screen, so a tap would change a pane
        // the user cannot see.
        return enabled && paneToggleable && page == PageKind.TERMINAL;
    }
}
