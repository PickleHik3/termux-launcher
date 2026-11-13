package com.termux.shared.shell.command.runner.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppShellJoinTest {

    @Test
    public void finishedThreadsJoinImmediately() throws Exception {
        Thread a = new Thread(() -> { });
        a.start();
        assertTrue(AppShell.joinWithin(2000, a));
    }

    @Test
    public void threadHoldingPipeDoesNotBlockPastDeadline() throws Exception {
        final Object never = new Object();
        Thread stuck = new Thread(() -> {
            synchronized (never) {
                try { never.wait(); } catch (InterruptedException ignored) { }
            }
        });
        stuck.setDaemon(true);
        stuck.start();
        long start = System.nanoTime();
        assertFalse(AppShell.joinWithin(200, stuck));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue("took " + elapsedMs, elapsedMs < 1500);
        stuck.interrupt();
    }
}
