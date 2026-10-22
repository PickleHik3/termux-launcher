package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneEventsTest {

    private final float[] hills = new float[SceneEvents.MAX_HILLS * 4];
    private final float[] sparks = new float[SceneEvents.MAX_SPARKS * 4];
    private final float[] pulseRect = new float[SceneEvents.MAX_PULSES * 4];
    private final float[] pulseT = new float[SceneEvents.MAX_PULSES * 2];
    private final float[] basins = new float[SceneEvents.MAX_BASINS * 4];

    private void fill(SceneEvents e, long now) {
        e.fill(now, 2f, hills, sparks, pulseRect, pulseT, basins);
    }

    @Test public void hillRisesThenRelaxesToNothingWithinASecond() {
        assertEquals(0f, SceneEvents.hillAmp(0), 1e-6f);
        assertEquals(1f, SceneEvents.hillAmp(SceneEvents.HILL_RISE_MS), 1e-6f);
        assertTrue(SceneEvents.hillAmp(500) < SceneEvents.hillAmp(200));
        assertEquals(0f, SceneEvents.hillAmp(SceneEvents.HILL_MS), 0f);
        assertEquals(0f, SceneEvents.hillAmp(5000), 0f);
    }

    @Test public void eventsAreActiveUntilTheLastOneDecays() {
        SceneEvents e = new SceneEvents();
        assertFalse(e.active(0));
        e.addHill(10, 20, 1000);
        e.addSpark(5, 5, 1200);
        assertTrue(e.active(1500));
        assertTrue(e.active(1000 + SceneEvents.SPARK_MS + 100)); // spark ends 2000, hill 2000
        assertFalse(e.active(1200 + SceneEvents.SPARK_MS));
        e.addPulse(0, 0, 10, 10, 3000);
        assertTrue(e.active(3000 + SceneEvents.PULSE_MS - 1));
        assertFalse(e.active(3000 + SceneEvents.PULSE_MS));
    }

    @Test public void expiredSlotsFillAsZeroStrength() {
        SceneEvents e = new SceneEvents();
        e.addHill(10, 20, 0);
        fill(e, 100);
        assertTrue(hills[2] > 0f);
        assertEquals(10f, hills[0], 0f);
        fill(e, SceneEvents.HILL_MS + 1);
        assertEquals(0f, hills[2], 0f);
        e.addSpark(1, 2, 0);
        fill(e, 400);
        assertTrue(sparks[3] > 0f && sparks[2] > 0f);
        fill(e, SceneEvents.SPARK_MS);
        assertEquals(0f, sparks[3], 0f);
    }

    @Test public void sparkTravelsOutwardAtDensityScale() {
        SceneEvents e = new SceneEvents();
        e.addSpark(0, 0, 0);
        fill(e, 500);
        assertEquals(SceneEvents.SPARK_SPEED_DP * 2f * 0.5f, sparks[2], 1e-3f);
    }

    @Test public void fullSlotsOverwriteTheOldestAndNeverGrow() {
        SceneEvents e = new SceneEvents();
        for (int i = 0; i < SceneEvents.MAX_HILLS + 2; i++) e.addHill(i, 0, 100 + i);
        fill(e, 110);
        // the two newest overwrote the two oldest: x values 3, 4 and the survivor 2 are present
        boolean has3 = false, has4 = false, has0 = false;
        for (int i = 0; i < SceneEvents.MAX_HILLS; i++) {
            if (hills[i * 4] == 3f) has3 = true;
            if (hills[i * 4] == 4f) has4 = true;
            if (hills[i * 4] == 0f && hills[i * 4 + 2] > 0f) has0 = true;
        }
        assertTrue(has3 && has4);
        assertFalse(has0);
    }

    @Test public void basinsReportChangeOnlyWhenTheSetMoves() {
        SceneEvents e = new SceneEvents();
        float[] r = {100, 200, 50, 60};
        assertTrue(e.setBasins(r, 1));
        assertFalse(e.setBasins(r, 1));
        r[0] = 101;
        assertTrue(e.setBasins(r, 1));
        assertTrue(e.setBasins(r, 0));
        assertEquals(0, e.basinCount());
        fill(e, 0);
        assertEquals(0f, basins[2], 0f);
    }

    @Test public void ambientGateSkipsFramesDownToTheRate() {
        long period = SceneFramePolicy.ambientPeriodNs(30);
        long vsync = 1_000_000_000L / 90;
        long last = 0;
        int draws = 0;
        for (int i = 1; i <= 90; i++) {
            long now = i * vsync;
            if (SceneFramePolicy.shouldDraw(false, period, last, now)) {
                last = now;
                draws++;
            }
        }
        assertEquals(30, draws);
    }

    @Test public void gateDrawsEveryFrameForEventsAndNeverAtRateZero() {
        long never = SceneFramePolicy.ambientPeriodNs(0);
        assertFalse(SceneFramePolicy.shouldDraw(false, never, 0, 1_000_000_000L));
        assertTrue(SceneFramePolicy.shouldDraw(true, never, 0, 1));
        assertTrue(SceneFramePolicy.shouldDraw(false, SceneFramePolicy.ambientPeriodNs(90), 0, 1));
    }

    @Test public void propertiesParseWithTodaysBehaviourAsTheDefault() {
        assertFalse(SceneFramePolicy.sceneOn(null));
        assertFalse(SceneFramePolicy.sceneOn("off"));
        assertTrue(SceneFramePolicy.sceneOn("contour"));
        assertEquals(30, SceneFramePolicy.parseRate(""));
        assertEquals(30, SceneFramePolicy.parseRate("junk"));
        assertEquals(90, SceneFramePolicy.parseRate("90"));
        assertEquals(0, SceneFramePolicy.parseRate("0"));
        assertTrue(SceneFramePolicy.glassLowRes("lowres"));
        assertTrue(SceneFramePolicy.glassLowRes(null));
        assertFalse(SceneFramePolicy.glassLowRes("still"));
    }
}
