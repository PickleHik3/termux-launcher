package com.termux.app.launcher.animation;

/**
 * The pure rules of the gesture-contract handoff: when the launcher's safe mode latches and
 * lapses, and which icon the window shrinks into.
 */
public final class LauncherTransitionPolicy {

    /** Real errors in a row (a failed callback, an exception) that switch the handoff off. */
    public static final int FAILURE_THRESHOLD = 3;

    /** Where the end position of a handoff comes from. */
    public enum Source { DOCK, HOME, DRAWER, NONE }

    private LauncherTransitionPolicy() {}

    /** An app with no icon here is not an error: the system plays its default. */
    public static boolean shouldLatchSafeMode(int consecutiveErrors) {
        return consecutiveErrors >= FAILURE_THRESHOLD;
    }

    /**
     * Safe mode lasts until the next cold start, which an upgrade also is. A latch kept for good
     * could never be cleared by the handoff it switches off.
     */
    public static boolean shouldClearSafeModeOnStart(boolean safeMode, boolean firstStartInProcess) {
        return safeMode && firstStartInProcess;
    }

    /**
     * The dock icon when the app is pinned, else the icon on what is in front: the drawer's when
     * it is open, otherwise the current Home page's. Without one, none.
     */
    public static Source pickSource(boolean dock, boolean home, boolean drawer, boolean drawerOpen) {
        if (dock) return Source.DOCK;
        if (drawerOpen) return drawer ? Source.DRAWER : Source.NONE;
        return home ? Source.HOME : Source.NONE;
    }
}
