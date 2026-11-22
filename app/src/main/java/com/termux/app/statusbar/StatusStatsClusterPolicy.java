package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import com.termux.app.page.PageKind;


/**
 * Whether the row's readings that depend on more than their own switch show: the CPU, the GPU
 * and the font toggle. Where they stand is the user's order ({@link StatusRowOrder}), and the
 * dots between them follow who shows ({@link StatusRowOrder#dots}).
 */
public final class StatusStatsClusterPolicy {

    private StatusStatsClusterPolicy() {}

    /**
     * Whether the CPU widget shows: its switch is on and a privileged backend can read
     * {@code /proc/stat}. Without one the reading could only ever be a dash, so it hides instead.
     */
    public static boolean cpuShown(boolean cpuEnabled, boolean privilegedAvailable) {
        return cpuEnabled && privilegedAvailable;
    }

    /**
     * Whether the GPU widget shows: its switch is on and the last sample read a GPU load. A phone
     * that reports none (no node, no privilege) keeps it hidden rather than showing a dash.
     */
    public static boolean gpuShown(boolean gpuEnabled, boolean gpuKnown) {
        return gpuEnabled && gpuKnown;
    }

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
