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
}
