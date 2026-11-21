package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Which page's even-out the dock's glass wears across a crossing (pong 2026-11-20, Home 2 and the
 * Terminal with the keyboard up): never the page it left from the first frame after the landing.
 */
public class DockFilterPlanTest {

    private static final int HOME = 4;
    private static final int TERMINAL = 0;

    @Test
    public void atRestTheGlassMeasuresLiveAndKeepsWhatItMeasured() {
        DockFilterPlan plan = new DockFilterPlan();
        assertTrue(plan.measures(HOME, false, false));
        assertTrue(plan.keepsAtRest(false));
        assertFalse("a crossing keeps nothing", plan.keepsAtRest(true));
        assertEquals(HOME, plan.standingLook(HOME));
    }

    @Test
    public void aCrossingBlendsFromHereToThereAndLandsOnThere() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(HOME, TERMINAL, true);
        assertFalse("a pass on the way stands on the blend", plan.measures(HOME, true, true));
        assertEquals(HOME, plan.standingLook(HOME));
        assertTrue(plan.step(0.5f));
        assertFalse("the same step blends nothing again", plan.step(0.5f));
        assertEquals(DockFilterPlan.NONE, plan.standingLook(HOME));
        assertTrue(plan.step(1f));
        assertEquals(TERMINAL, plan.standingLook(HOME));
        plan.land(true);
        assertEquals(DockFilterPlan.Phase.LANDED, plan.phase());
        assertEquals(1f, plan.share(), 0f);
        assertEquals(TERMINAL, plan.standingLook(HOME));
    }

    /**
     * Pong r1: the landing turned the blend off and a pass at rest ran while the stack still wore
     * Home's look and the travel's hold was on: it put Home's even-out up for two frames.
     */
    @Test
    public void theLandingsPassesStandOnTheLandedEndUntilTheGlassCanBeMeasuredWhereItRests() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(HOME, TERMINAL, true);
        plan.step(1f);
        plan.land(true);
        assertFalse("the stack still wears the left page's look",
            plan.measures(HOME, false, false));
        assertFalse("the even-out is still held from the travel",
            plan.measures(TERMINAL, false, true));
        assertFalse("nothing is kept while landed", plan.keepsAtRest(false));
        assertEquals(TERMINAL, plan.standingLook(HOME));
        assertTrue("the stack wears the landed look and nothing is held",
            plan.measures(TERMINAL, false, false));
        assertEquals(DockFilterPlan.Phase.REST, plan.phase());
        assertTrue(plan.keepsAtRest(false));
        assertEquals(TERMINAL, plan.standingLook(TERMINAL));
    }

    /**
     * Pong r3: the first crossing after a start had no kept end for the Terminal, so nothing
     * blended and Home's even-out stood through the swipe and the landing. The arriving end is
     * worked out as the crossing begins, so the plan has both ends without history.
     */
    @Test
    public void theFirstCrossingAfterAStartBlendsAndLandsOnTheArrivingEnd() {
        DockFilterPlan plan = new DockFilterPlan();
        assertTrue("first pass at rest", plan.measures(HOME, false, false));
        plan.begin(HOME, TERMINAL, true);
        assertTrue(plan.standsOnEnds());
        for (int step = 1; step <= 32; step++) plan.step(step / 32f);
        assertEquals(TERMINAL, plan.standingLook(HOME));
        plan.land(true);
        assertFalse(plan.measures(HOME, false, true));
        assertEquals(TERMINAL, plan.standingLook(HOME));
    }

    @Test
    public void aCrossingThatComesBackLandsOnWhereItBegan() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(TERMINAL, HOME, true);
        plan.step(0.25f);
        plan.step(0f);
        plan.land(false);
        assertEquals(0f, plan.share(), 0f);
        assertEquals(TERMINAL, plan.standingLook(TERMINAL));
        assertFalse(plan.measures(HOME, false, false));
        assertTrue(plan.measures(TERMINAL, false, false));
    }

    @Test
    public void theSettleEndsALandingWhateverLookTheStackReads() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(HOME, TERMINAL, true);
        plan.land(true);
        plan.settle(true);
        assertEquals("a crossing under way again keeps it", DockFilterPlan.Phase.LANDED,
            plan.phase());
        plan.settle(false);
        assertEquals(DockFilterPlan.Phase.REST, plan.phase());
        assertTrue(plan.measures(HOME, false, false));
    }

    @Test
    public void withoutBothEndsEveryPassMeasuresAsBefore() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(HOME, TERMINAL, false);
        assertFalse(plan.standsOnEnds());
        assertFalse(plan.step(0.5f));
        assertTrue(plan.measures(HOME, true, true));
        plan.land(true);
        assertTrue(plan.measures(HOME, false, true));
        assertEquals(DockFilterPlan.Phase.REST, plan.phase());
    }

    @Test
    public void aNewCrossingFromALandingBeginsAfresh() {
        DockFilterPlan plan = new DockFilterPlan();
        plan.begin(HOME, TERMINAL, true);
        plan.step(1f);
        plan.land(true);
        plan.begin(TERMINAL, HOME, true);
        assertEquals(DockFilterPlan.Phase.CROSSING, plan.phase());
        assertEquals(0f, plan.share(), 0f);
        assertEquals(TERMINAL, plan.standingLook(TERMINAL));
    }
}
