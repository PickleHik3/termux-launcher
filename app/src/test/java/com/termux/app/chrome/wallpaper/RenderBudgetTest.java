package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RenderBudgetTest {

    private static boolean fill(RenderBudget budget, int count, float millis) {
        boolean ok = true;
        for (int i = 0; i < count; i++) ok = budget.add(millis);
        return ok;
    }

    @Test
    public void staysHealthyUntilTheWindowCompletes() {
        RenderBudget budget = new RenderBudget();
        assertTrue(fill(budget, RenderBudget.WINDOW - 1, 50f));
        assertEquals(0f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void aFastWindowPasses() {
        RenderBudget budget = new RenderBudget();
        assertTrue(fill(budget, RenderBudget.WINDOW, 2f));
        assertEquals(2f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void aSlowWindowFailsOnItsLastSample() {
        RenderBudget budget = new RenderBudget();
        assertFalse(fill(budget, RenderBudget.WINDOW, 6f));
        assertEquals(6f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void onA120HzScreenRendersLandingWithinTheNextVsyncPass() {
        // pong: p90 9.0 ms whether the shader ran at ÷2 or ÷4, one 120 Hz frame of GPU queueing.
        RenderBudget budget = new RenderBudget();
        budget.setFramePeriodMs(8.33f);
        assertEquals(10.41f, budget.limitMs(), 0.01f);
        assertTrue(fill(budget, RenderBudget.WINDOW, 9.1f));
        assertFalse("a render that needs a second frame does not keep up", fill(budget, RenderBudget.WINDOW, 16.7f));
    }

    @Test
    public void theFrameLimitNeverDropsUnderTheFloor() {
        RenderBudget budget = new RenderBudget();
        budget.setFramePeriodMs(2f);
        assertEquals(RenderBudget.P90_LIMIT_MS, budget.limitMs(), 0f);
    }

    @Test
    public void tenPercentSlowOutliersDoNotTripTheLimit() {
        RenderBudget budget = new RenderBudget();
        boolean ok = true;
        for (int i = 0; i < RenderBudget.WINDOW; i++) ok = budget.add(i < 12 ? 20f : 1f);
        assertTrue(ok);
        assertEquals(1f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void moreThanTenPercentSlowTripsIt() {
        RenderBudget budget = new RenderBudget();
        boolean ok = true;
        for (int i = 0; i < RenderBudget.WINDOW; i++) ok = budget.add(i < 13 ? 20f : 1f);
        assertFalse(ok);
        assertEquals(20f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void theWindowStartsOverAfterItCompletes() {
        RenderBudget budget = new RenderBudget();
        assertFalse(fill(budget, RenderBudget.WINDOW, 6f));
        assertTrue(fill(budget, RenderBudget.WINDOW, 1f));
        assertEquals(1f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void rendersInsideTheWarmupDoNotCount() {
        RenderBudget budget = new RenderBudget();
        budget.restartWarmup(1_000L);
        // Hugely slow renders, 200 of them, but all within 2 s of the restart.
        boolean ok = true;
        for (int i = 0; i < 200; i++) ok &= budget.add(50f, 1_000L + i);
        assertTrue(ok);
        assertFalse(budget.windowClosed());
        assertEquals(0f, budget.lastP90Ms(), 0f);
    }

    @Test
    public void warmupEndsOnTheLaterOfTimeAndRenderCount() {
        RenderBudget budget = new RenderBudget();
        budget.restartWarmup(0L);
        // Time is up but only 10 renders so far: still warming.
        for (int i = 0; i < 10; i++) budget.add(50f, RenderBudget.WARMUP_MS + 1);
        assertTrue(budget.warmingUp(RenderBudget.WARMUP_MS + 1));
        for (int i = 10; i < RenderBudget.WARMUP_RENDERS; i++) budget.add(50f, RenderBudget.WARMUP_MS + 1);
        assertFalse(budget.warmingUp(RenderBudget.WARMUP_MS + 1));

        // Enough renders but not enough time: still warming.
        budget.restartWarmup(10_000L);
        for (int i = 0; i < RenderBudget.WARMUP_RENDERS + 5; i++) budget.add(50f, 10_001L);
        assertTrue(budget.warmingUp(10_001L));
        assertFalse(budget.warmingUp(10_000L + RenderBudget.WARMUP_MS));
    }

    @Test
    public void afterTheWarmupTheWindowCountsAndFailsOnSlowRenders() {
        RenderBudget budget = new RenderBudget();
        budget.restartWarmup(0L);
        long late = RenderBudget.WARMUP_MS + 1;
        for (int i = 0; i < RenderBudget.WARMUP_RENDERS; i++) assertTrue(budget.add(50f, late));
        boolean ok = true;
        for (int i = 0; i < RenderBudget.WINDOW; i++) ok = budget.add(6f, late);
        assertFalse(ok);
        assertTrue(budget.windowClosed());
        assertEquals(6f, budget.lastP90Ms(), 0f);
        assertEquals(6f, budget.lastP50Ms(), 0f);
        assertEquals(RenderBudget.WARMUP_RENDERS, budget.warmupSkipped());
    }

    @Test
    public void restartingTheWarmupDropsAPartialWindow() {
        RenderBudget budget = new RenderBudget();
        fill(budget, RenderBudget.WINDOW - 1, 50f);
        budget.restartWarmup(0L);
        long late = RenderBudget.WARMUP_MS + 1;
        for (int i = 0; i < RenderBudget.WARMUP_RENDERS; i++) budget.add(1f, late);
        // One more slow sample must not close the old window.
        assertTrue(budget.add(50f, late));
        assertFalse(budget.windowClosed());
    }
}
