package com.termux.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The X server applies, and logs, every window change it is sent, whether or not the size moved.
 * The view re-announces its size on every measure and every surface change, so only a change
 * that differs from the last one sent on this connection goes out.
 */
public class WindowChangeGateTest {

    @Test
    public void theFirstChangeOnAConnectionIsSent() {
        WindowChangeGate gate = new WindowChangeGate();
        assertTrue(gate.shouldSend(1032, 1193, 60, "builtin"));
    }

    @Test
    public void theSameSizeAgainIsNotSent() {
        WindowChangeGate gate = new WindowChangeGate();
        gate.shouldSend(1032, 1193, 60, "builtin");
        // The surface change that follows the measure, at the size the measure already sent.
        assertFalse(gate.shouldSend(1032, 1193, 60, "builtin"));
    }

    @Test
    public void aNewSizeIsSentOnceAndASwitchBackIsSentToo() {
        WindowChangeGate gate = new WindowChangeGate();
        gate.shouldSend(1032, 1782, 60, "builtin");
        assertTrue(gate.shouldSend(1032, 1193, 60, "builtin"));
        assertFalse(gate.shouldSend(1032, 1193, 60, "builtin"));
        assertTrue(gate.shouldSend(1032, 1782, 60, "builtin"));
    }

    @Test
    public void aNewRefreshRateOrDisplayIsSent() {
        WindowChangeGate gate = new WindowChangeGate();
        gate.shouldSend(1032, 1193, 60, "builtin");
        assertTrue(gate.shouldSend(1032, 1193, 120, "builtin"));
        assertTrue(gate.shouldSend(1032, 1193, 120, "external"));
    }

    @Test
    public void aNewConnectionHearsTheSizeAgain() {
        WindowChangeGate gate = new WindowChangeGate();
        gate.shouldSend(1032, 1193, 60, "builtin");
        gate.forget();
        assertTrue(gate.shouldSend(1032, 1193, 60, "builtin"));
    }
}
