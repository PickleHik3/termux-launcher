package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Glass answers its pose only while motion is allowed: reduced motion and Animation speed Off keep it flat. */
public class GlassMotionGateTest {

    @Test
    public void theGateIsClosedForEveryReasonToKeepStill() {
        assertTrue(GlassMotionGate.allowed(true, false, false, false, 1f));
        assertTrue(GlassMotionGate.allowed(true, false, false, false, 1.5f));
        assertFalse("the phone's animations off", GlassMotionGate.allowed(false, false, false, false, 1f));
        assertFalse("reduced motion", GlassMotionGate.allowed(true, true, false, false, 1f));
        assertFalse("lazy mode", GlassMotionGate.allowed(true, false, true, false, 1f));
        assertFalse("battery saver", GlassMotionGate.allowed(true, false, false, true, 1f));
        assertFalse("Animation speed Off", GlassMotionGate.allowed(true, false, false, false, 0f));
    }
}
