package com.termux.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** {@link GenerationQueue#cancelWaiting}: the service's destroy clears what waits, never what runs. */
public class GenerationQueueTest {
    private static final long WAIT_MS = 5_000L;

    @Test
    public void cancellingTheWaitingJobsEndsThemWithoutRunningAndLeavesTheRunningOne() throws Exception {
        GenerationQueue queue = new GenerationQueue("test-lane", () -> { });
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstDone = new CountDownLatch(1);
        queue.submit("first", TaiRuntimeLink.Priority.NORMAL, () -> {
            running.countDown();
            try {
                release.await(WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            log.add("ran:first");
            firstDone.countDown();
        });
        assertTrue(running.await(WAIT_MS, TimeUnit.MILLISECONDS));
        queue.submit("second", TaiRuntimeLink.Priority.NORMAL, job("second", log));
        queue.submit("third", TaiRuntimeLink.Priority.INTERACTIVE, job("third", log));

        assertEquals(2, queue.cancelWaiting());
        assertEquals("first", queue.runningId());
        assertTrue(queue.waitingIds().isEmpty());
        release.countDown();
        assertTrue(firstDone.await(WAIT_MS, TimeUnit.MILLISECONDS));
        // Give the lane the chance to run anything left; nothing is.
        CountDownLatch after = new CountDownLatch(1);
        queue.submit("probe", TaiRuntimeLink.Priority.NORMAL, after::countDown);
        assertTrue(after.await(WAIT_MS, TimeUnit.MILLISECONDS));

        assertEquals(java.util.Arrays.asList("cancelled:second", "cancelled:third", "ran:first"), log);
        assertEquals(0, queue.cancelWaiting());
    }

    private static GenerationQueue.Job job(String id, List<String> log) {
        return new GenerationQueue.Job() {
            @Override public void run() { log.add("ran:" + id); }
            @Override public void onCancelled() { log.add("cancelled:" + id); }
        };
    }
}
