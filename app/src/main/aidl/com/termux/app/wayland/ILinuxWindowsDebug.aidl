package com.termux.app.wayland;

/**
 * Debug calls on the Linux windows host, kept out of ILinuxWindows so the launcher never holds
 * them. Bound only by the test screen, with LinuxWindowsService.ACTION_DEBUG.
 */
oneway interface ILinuxWindowsDebug {
    /** abort() the host process, to check that the launcher survives it. */
    void crash();
    /** Logs what the GPU drivers offer (TLWayland, "probe" lines), in the host process. */
    void probe();
    /** Logs each window's copy times once a second (TLWayland, "copy" lines), or stops. */
    void setCopyLog(boolean on);
    /**
     * Writes what window id shows (its last frame, cropped, before the Surface) as a PNG to path,
     * which must lie inside the app's data directory; the host logs the path and size.
     */
    void dumpWindow(long id, String path);
    /**
     * Experiment (exp/wayland-turnip): windows created from now on show their GPU buffers through
     * the bundled Turnip driver without a CPU copy (on), or copy (off). Kept for later starts;
     * TLWayland "direct" lines say what happened. Without -PwaylandTurnip=true it only logs.
     */
    void setDirect(boolean on);
}
