package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TouchDownHintTest {

    @Test
    public void picksTheRenderThreadTid() {
        String[] dirs = {"100", "101", "102"};
        String[] comms = {"main\n", null, "RenderThread\n"};
        assertEquals(102, TouchDownHint.pickTid(dirs, comms, "RenderThread"));
    }

    @Test
    public void missingOrBadDirGivesMinusOne() {
        assertEquals(-1, TouchDownHint.pickTid(new String[] {"1"}, new String[] {"x\n"}, "RenderThread"));
        assertEquals(-1, TouchDownHint.pickTid(new String[] {"abc"}, new String[] {"RenderThread"}, "RenderThread"));
    }
}
