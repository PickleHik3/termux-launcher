package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class StatusBarStatSmootherTest {

    /** The controller's resting cadence with the card closed. */
    private static final long BAR_TICK_MS = 6000L;

    /** The faster cadence it switches to while the mini-btop card is open. */
    private static final long CARD_TICK_MS = 1500L;

    @Test
    public void firstSample_showsTheRealValueImmediatelyRatherThanFadingInFromZero() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();

        assertTrue(cpu.offer(44, 0L));
        assertEquals(44, cpu.publishedPercent());
        assertEquals("44%", cpu.text());
    }

    @Test
    public void stepChange_glidesTowardsTheNewLevelInsteadOfJumpingToIt() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        cpu.offer(10, 0L);

        // One sample of a build starting must not move the reading to 90: that jump is exactly the
        // distraction being fixed, and one sample is not yet evidence of a new level. About a third of
        // the way is what the 0.32 blend gives.
        assertTrue(cpu.offer(90, BAR_TICK_MS));
        assertEquals(36, cpu.publishedPercent());

        int previous = cpu.publishedPercent();
        int publishes = 1;
        for (int i = 2; i <= 8; i++) {
            if (cpu.offer(90, BAR_TICK_MS * i)) {
                assertTrue("the glide must not overshoot or reverse",
                    cpu.publishedPercent() > previous);
                previous = cpu.publishedPercent();
                publishes++;
            }
        }
        // Gradual: several intermediate readings on the way, and it arrives near the truth rather
        // than sticking a step below it forever.
        assertTrue("expected a multi-step climb, got " + publishes, publishes >= 4);
        assertTrue("ended at " + previous, previous >= 85 && previous <= 90);
    }

    @Test
    public void oscillationInsideTheDeadband_publishesOnceThenStaysPut() {
        StatusBarStatSmoother memory = StatusBarStatSmoother.forMemoryPercent();
        assertTrue(memory.offer(62, 0L));

        // Memory reported as 62/63/62/63... is one percent of sampling noise, not movement. The
        // reading has to sit still through it, or the widget blinks forever at a fixed workload.
        long now = 0L;
        for (int i = 1; i <= 12; i++) {
            now += BAR_TICK_MS;
            assertFalse("published on jitter sample " + i, memory.offer(i % 2 == 1 ? 63 : 62, now));
        }
        assertEquals(62, memory.publishedPercent());

        // A level that really did move still gets through, which is what separates a deadband from a
        // frozen widget.
        boolean moved = false;
        for (int i = 1; i <= 4 && !moved; i++) {
            now += BAR_TICK_MS;
            moved = memory.offer(63, now);
        }
        assertTrue("a sustained one-percent rise must eventually publish", moved);
        assertEquals(63, memory.publishedPercent());
    }

    @Test
    public void minimumPublishInterval_keepsTheBarCalmWhileTheCardSamplesFast() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        cpu.offer(0, 0L);

        // Card open: the controller drops to 1.5 s. The bar behind it must not repaint at that rate,
        // even with a metric ramping hard enough to clear the hysteresis on every single sample.
        long lastPublishMs = 0L;
        int publishes = 0;
        for (int i = 1; i <= 8; i++) {
            long now = CARD_TICK_MS * i;
            if (cpu.offer(100, now)) {
                assertTrue("published " + (now - lastPublishMs) + "ms after the previous repaint",
                    now - lastPublishMs >= StatusBarStatSmoother.MIN_PUBLISH_INTERVAL_MS);
                lastPublishMs = now;
                publishes++;
            }
        }
        // Eight samples over 12 s: four repaints at most, not eight.
        assertTrue("expected at most 4 repaints, got " + publishes, publishes <= 4);
        assertTrue("but the ramp must still show up", publishes >= 3);
    }

    @Test
    public void unchangedReading_reportsNoChangeAndHandsBackTheSameCachedText() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        assertTrue(cpu.offer(20, 0L));
        String published = cpu.text();

        for (int i = 1; i <= 6; i++) {
            assertFalse("a steady metric must not report a change", cpu.offer(20, BAR_TICK_MS * i));
            // Identity, not equality: the caller skips all view work, and the publish path itself
            // allocates no new string either.
            assertSame(published, cpu.text());
        }
        assertEquals(20, cpu.publishedPercent());
    }

    @Test
    public void quantization_snapsToTheStepAndResolvesAValueSittingExactlyOnABoundary() {
        int cpuStep = StatusBarStatSmoother.CPU_STEP_PERCENT;
        // Exactly on a step: unchanged.
        assertEquals(44, StatusBarStatSmoother.quantize(44d, cpuStep));
        // Exactly halfway between two steps: deterministic, half a step up. Left to floating-point
        // luck this is the one input that could render two different readings for one value.
        assertEquals(44, StatusBarStatSmoother.quantize(43d, cpuStep));
        assertEquals(42, StatusBarStatSmoother.quantize(42.9999d, cpuStep));
        assertEquals(63, StatusBarStatSmoother.quantize(62.5d, StatusBarStatSmoother.MEMORY_STEP_PERCENT));
        // Clamped: a reading outside 0..100 is not a reading.
        assertEquals(0, StatusBarStatSmoother.quantize(-3d, cpuStep));
        assertEquals(100, StatusBarStatSmoother.quantize(104d, cpuStep));
    }

    @Test
    public void hysteresisBand_isWiderThanQuantizationButNarrowerThanAStep() {
        // Half a step is what quantization alone gives, and it is what lets a boundary-parked value
        // flip. A whole step is worse: an EMA nears a new level asymptotically, so a band that wide is
        // one the value never leaves, and the reading freezes a step below the truth.
        for (int step : new int[] {StatusBarStatSmoother.MEMORY_STEP_PERCENT,
                                  StatusBarStatSmoother.CPU_STEP_PERCENT}) {
            double band = StatusBarStatSmoother.hysteresisFor(step);
            assertTrue("band " + band + " for step " + step, band > step * .5d);
            assertTrue("band " + band + " for step " + step, band < step);
        }
    }

    @Test
    public void longIdle_adoptsTheNextSampleOutrightInsteadOfGlidingOutOfStaleHistory() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        cpu.offer(5, 0L);

        // The activity was away, or the widgets were off, and sampling stopped. Gliding from a reading
        // that old would show a value that was never true and then visibly climb out of it.
        long resumedMs = StatusBarStatSmoother.STALE_HISTORY_MS + 1L;
        assertTrue(cpu.offer(90, resumedMs));
        assertEquals(90, cpu.publishedPercent());
        // And immediately, not after the minimum interval had it been treated as an ordinary sample.
        assertTrue(resumedMs - 0L > StatusBarStatSmoother.MIN_PUBLISH_INTERVAL_MS);

        // A gap inside the window is an ordinary sample and stays smoothed.
        StatusBarStatSmoother other = StatusBarStatSmoother.forCpuPercent();
        other.offer(5, 0L);
        other.offer(90, StatusBarStatSmoother.STALE_HISTORY_MS);
        assertTrue("a gap inside the window must still glide, got " + other.publishedPercent(),
            other.publishedPercent() < 45);
    }

    @Test
    public void reset_makesTheNextSampleBehaveLikeTheFirstOne() {
        StatusBarStatSmoother memory = StatusBarStatSmoother.forMemoryPercent();
        memory.offer(30, 0L);
        memory.reset();

        assertEquals(StatusBarStatSmoother.UNKNOWN, memory.publishedPercent());
        assertEquals(StatusBarStatSmoother.UNKNOWN_TEXT, memory.text());
        assertTrue(memory.offer(80, BAR_TICK_MS));
        assertEquals(80, memory.publishedPercent());
    }

    @Test
    public void unavailableReading_showsTheDashAndDoesNotSmoothTowardsZero() {
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        // /proc/stat needs two samples before a delta exists, so the very first reading is unknown.
        assertTrue(cpu.offer(StatusBarStatSmoother.UNKNOWN, 0L));
        assertEquals(StatusBarStatSmoother.UNKNOWN_TEXT, cpu.text());
        assertFalse("a second unknown is not a change", cpu.offer(-1, BAR_TICK_MS));

        // The first real reading lands exactly, not half way up from a dash treated as a zero.
        assertTrue(cpu.offer(70, BAR_TICK_MS * 2));
        assertEquals(70, cpu.publishedPercent());

        // Losing the reading again shows the dash at once rather than decaying towards zero.
        assertTrue(cpu.offer(-1, BAR_TICK_MS * 3));
        assertEquals(StatusBarStatSmoother.UNKNOWN_TEXT, cpu.text());
        assertEquals(StatusBarStatSmoother.UNKNOWN, cpu.publishedPercent());
    }

    @Test
    public void calmnessComesFromTheBlendAndTheThrottle_notFromACoarseGrid() {
        // CPU is the noisy one, and it reuses the 0.68/0.32 blend the card already applies to
        // per-process CPU so both surfaces settle at one rate.
        assertEquals(.32d, StatusBarStatSmoother.CPU_SAMPLE_WEIGHT, .0001d);
        assertTrue("CPU must be damped harder than memory",
            StatusBarStatSmoother.MEMORY_SAMPLE_WEIGHT > StatusBarStatSmoother.CPU_SAMPLE_WEIGHT);
        // Under the 6 s resting cadence, so the ordinary rhythm is never skipped by the throttle, and
        // over the 1.5 s card cadence, so an open card cannot make the bar behind it repaint faster
        // than the bar ever does on its own.
        assertTrue(StatusBarStatSmoother.MIN_PUBLISH_INTERVAL_MS < BAR_TICK_MS);
        assertTrue(StatusBarStatSmoother.MIN_PUBLISH_INTERVAL_MS > CARD_TICK_MS);
        // The grid stays fine. Its job is only to swallow the ±1 wobble of an idle device, so it is
        // one step past memory's whole percent and no further: everything else is done by the blend
        // and the throttle above, which cost no fidelity at all.
        assertEquals(1, StatusBarStatSmoother.MEMORY_STEP_PERCENT);
        assertEquals(2, StatusBarStatSmoother.CPU_STEP_PERCENT);
    }

    @Test
    public void cpuGridStaysCloseEnoughToAgreeWithTheCardOpenBeneathIt() {
        // Tapping the reading opens the card directly under it, so both numbers are read at once. Two
        // readings a whole coarse step apart do not look calm, they look like one of them is wrong —
        // which is why the grid is not the anti-jitter mechanism here. At rest the bar can trail the
        // card by the hysteresis band, and a fresh publish snaps within half a step, so the visible
        // disagreement budget is the sum of the two.
        double budget = StatusBarStatSmoother.CPU_STEP_PERCENT * .5d
            + StatusBarStatSmoother.hysteresisFor(StatusBarStatSmoother.CPU_STEP_PERCENT);
        assertTrue("CPU bar/card disagreement budget is " + budget + " points", budget <= 3d);

        // Concretely: a device pinned at a steady load settles on the neighbouring reading, not three
        // steps below it.
        StatusBarStatSmoother cpu = StatusBarStatSmoother.forCpuPercent();
        cpu.offer(0, 0L);
        for (int i = 1; i <= 40; i++) cpu.offer(47, BAR_TICK_MS * i);
        assertTrue("settled at " + cpu.publishedPercent(),
            Math.abs(cpu.publishedPercent() - 47) <= 3);
    }
}
