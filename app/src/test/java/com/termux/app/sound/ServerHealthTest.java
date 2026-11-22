package com.termux.app.sound;

import static com.termux.app.sound.ServerHealth.Verdict.BROKEN;
import static com.termux.app.sound.ServerHealth.Verdict.OK;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerHealthTest {

    private static final long OLD = ServerHealth.GRACE_MS + 1;
    private static final long YOUNG = ServerHealth.GRACE_MS;
    private static final String INFO = "Server String: unix:/x\nServer Name: pulseaudio";

    @Test
    public void nullAnswerIsBrokenAtAnyAge() {
        assertEquals(BROKEN, ServerHealth.judge(true, 0, null));
        assertEquals(BROKEN, ServerHealth.judge(true, OLD, null));
    }

    @Test
    public void timeoutIsBrokenAtAnyAge() {
        assertEquals(BROKEN, ServerHealth.judge(true, 1, "Timeout while waiting"));
        assertEquals(BROKEN, ServerHealth.judge(true, OLD, "Timeout while waiting"));
    }

    @Test
    public void missingSocketCountsOnlyAfterTheGrace() {
        assertEquals(OK, ServerHealth.judge(false, YOUNG, INFO));
        assertEquals(BROKEN, ServerHealth.judge(false, OLD, INFO));
    }

    @Test
    public void refusalCountsOnlyAfterTheGrace() {
        assertEquals(OK, ServerHealth.judge(true, YOUNG, "Connection failure: Connection refused"));
        assertEquals(BROKEN, ServerHealth.judge(true, OLD, "Connection failure: Connection refused"));
        assertEquals(BROKEN, ServerHealth.judge(true, OLD, "Connection refused"));
        assertEquals(OK, ServerHealth.judge(true, YOUNG, "Connection failure: x"));
        assertEquals(BROKEN, ServerHealth.judge(true, OLD, "Connection failure: x"));
    }

    @Test
    public void aHealthyServerIsOk() {
        assertEquals(OK, ServerHealth.judge(true, OLD, INFO));
        assertEquals(OK, ServerHealth.judge(true, 0, INFO));
        assertEquals(OK, ServerHealth.judge(true, OLD, ""));
    }
}
