package com.termux.launcherctl;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoalescingTaskTest {

    /** Holds scheduled tasks until the test runs them, like a handler that never gets a turn. */
    private static final class ManualScheduler implements CoalescingTask.Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        boolean accept = true;

        @Override public boolean schedule(Runnable task, long delayMs) {
            if (!accept) return false;
            tasks.add(task);
            delays.add(delayMs);
            return true;
        }

        void runAll() {
            List<Runnable> due = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : due) task.run();
        }
    }

    @Test
    public void aBurstOfRequestsRunsTheWorkOnce() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] runs = {0};
        CoalescingTask task = new CoalescingTask(120L, scheduler, () -> runs[0]++);

        for (int i = 0; i < 10; i++) task.request();

        assertEquals(1, scheduler.tasks.size());
        assertEquals(Long.valueOf(120L), scheduler.delays.get(0));
        assertTrue(task.isPending());
        scheduler.runAll();
        assertEquals(1, runs[0]);
        assertFalse(task.isPending());
    }

    @Test
    public void aRequestAfterARunSchedulesAnotherRun() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] runs = {0};
        CoalescingTask task = new CoalescingTask(100L, scheduler, () -> runs[0]++);

        task.request();
        scheduler.runAll();
        task.request();
        task.request();
        scheduler.runAll();

        assertEquals(2, runs[0]);
    }

    @Test
    public void aRequestMadeWhileTheWorkRunsIsNotLost() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] runs = {0};
        CoalescingTask[] self = new CoalescingTask[1];
        self[0] = new CoalescingTask(100L, scheduler, () -> {
            runs[0]++;
            if (runs[0] == 1) self[0].request();
        });

        self[0].request();
        scheduler.runAll();
        assertEquals(1, scheduler.tasks.size());
        scheduler.runAll();

        assertEquals(2, runs[0]);
    }

    @Test
    public void aRefusedScheduleLeavesTheTaskFreeToTryAgain() {
        ManualScheduler scheduler = new ManualScheduler();
        int[] runs = {0};
        CoalescingTask task = new CoalescingTask(100L, scheduler, () -> runs[0]++);

        scheduler.accept = false;
        task.request();
        assertFalse(task.isPending());

        scheduler.accept = true;
        task.request();
        scheduler.runAll();
        assertEquals(1, runs[0]);
    }

    @Test
    public void aNegativeDelayIsTreatedAsNow() {
        ManualScheduler scheduler = new ManualScheduler();
        new CoalescingTask(-5L, scheduler, () -> { }).request();
        assertEquals(Long.valueOf(0L), scheduler.delays.get(0));
    }
}
