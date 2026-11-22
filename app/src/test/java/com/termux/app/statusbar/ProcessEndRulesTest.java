package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcessEndRulesTest {

    private static SystemStatsController.Proc proc(int pid, String name, boolean kernel) {
        return new SystemStatsController.Proc(pid, name, 1.0, 1000, kernel);
    }

    @Test
    public void anOrdinaryAppProcessCanBeEnded() {
        assertTrue(ProcessEndRules.canEnd(proc(4000, "com.example.app", false), 99, "com.termux"));
        assertTrue(ProcessEndRules.canEnd(proc(4001, "com.example.app:service", false), 99, "com.termux"));
        assertTrue(ProcessEndRules.canEnd(proc(4002, "logd", false), 99, "com.termux"));
    }

    @Test
    public void kernelThreadsAndLowPidsAreNeverOffered() {
        assertFalse(ProcessEndRules.canEnd(proc(300, "kworker/0:1", true), 99, "com.termux"));
        assertFalse(ProcessEndRules.canEnd(proc(1, "something", false), 99, "com.termux"));
        assertFalse(ProcessEndRules.canEnd(proc(-1, "something", false), 99, "com.termux"));
    }

    @Test
    public void theLauncherItselfIsNeverOffered() {
        assertFalse(ProcessEndRules.canEnd(proc(99, "other", false), 99, "com.termux"));
        assertFalse(ProcessEndRules.canEnd(proc(100, "com.termux", false), 99, "com.termux"));
        assertFalse(ProcessEndRules.canEnd(proc(101, "com.termux:remote", false), 99, "com.termux"));
    }

    @Test
    public void protectedSystemProcessesAreNeverOffered() {
        for (String name : new String[] {"system_server", "zygote", "zygote64", "surfaceflinger",
            "servicemanager", "init", "lmkd", "vold", "magiskd"}) {
            assertFalse(name, ProcessEndRules.canEnd(proc(500, name, false), 99, "com.termux"));
        }
    }

    @Test
    public void packageOfStripsTheSuffix() {
        assertEquals("com.a.b", ProcessEndRules.packageOf("com.a.b:ui"));
        assertEquals("com.a.b", ProcessEndRules.packageOf("com.a.b"));
        assertEquals(":x", ProcessEndRules.packageOf(":x"));
    }
}
