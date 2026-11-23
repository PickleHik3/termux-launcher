package com.termux.app.wayland;

import com.termux.app.wayland.IAhbTestCallback;

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

    // Feasibility probe: Android-allocated buffers (AHardwareBuffer). Logs under TLWaylandAHB.

    /**
     * E1: allocates a 512x512 HardwareBuffer, writes a test pattern, hands it to cb.onBuffer, then
     * after 2 s rewrites it 60 times a second for 5 s, calling cb.onFrame after each.
     */
    void ahbViewTest(IAhbTestCallback cb);
    /** E2: logs the real layout of AHardwareBuffers of several usages. */
    void ahbLayoutProbe();
    /**
     * E3: allocates a buffer of usage variant (E2's index), offers its fd on
     * $PREFIX/tmp/tl-ahb.sock to tlwayland-ahbclient, reads the colour back and hands the buffer
     * to cb.onBuffer. modifier: 0 linear, 1 Qualcomm compressed.
     */
    void ahbClientTest(int variant, int modifier, IAhbTestCallback cb);
}
