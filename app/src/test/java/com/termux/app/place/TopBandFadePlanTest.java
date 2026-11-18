package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TopBandFadePlanTest {

    @Test
    public void aLookApartCrossingBuildsTheArrivingEndAtTheBegin() {
        assertEquals(7, TopBandFadePlan.arrivingLookAtBegin(3, 7));
    }

    @Test
    public void sidesOfOneLookLeaveItToTheFirstRedress() {
        assertEquals(TopBandFadePlan.NONE, TopBandFadePlan.arrivingLookAtBegin(3, 3));
    }

    @Test
    public void atFullShareTheBandWearsTheArrivingLookTheLandingReads() {
        assertEquals(7, TopBandFadePlan.settledLook(3, 7, 1f));
        assertEquals(3, TopBandFadePlan.settledLook(3, 7, 0f));
        assertEquals(3, TopBandFadePlan.settledLook(3, 7, 0.5f));
    }
}
