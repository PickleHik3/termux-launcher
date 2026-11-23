package com.termux.app.wayland;

import android.hardware.HardwareBuffer;

/**
 * Feasibility probe (debug only): what the host tells the test screen during an AHB experiment
 * (ILinuxWindowsDebug.ahbViewTest, ahbClientTest).
 */
oneway interface IAhbTestCallback {
    /** A buffer to wrap and draw; experiment is "e1" or "e3". */
    void onBuffer(in HardwareBuffer buffer, String experiment);
    /** E1: the host rewrote the buffer for this frame. */
    void onFrame(int frame);
    /** The experiment ended; message is the host's summary. */
    void onDone(String experiment, String message);
}
