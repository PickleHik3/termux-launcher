package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FrostStopsTest {

    @Test
    public void eachStopHasTheSpecsBlurAndGrain() {
        int[] blur = {0, 5, 7, 12, 17, 25, 31, 36};
        int[] grain = {0, 3, 3, 4, 5, 7, 7, 8};
        assertEquals(8, FrostStops.COUNT);
        for (int stop = 0; stop < FrostStops.COUNT; stop++) {
            assertEquals("blur at stop " + stop, blur[stop], FrostStops.blurDpFor(stop));
            assertEquals("grain at stop " + stop, grain[stop], FrostStops.grainPercentFor(stop));
        }
    }

    @Test
    public void theShippedLooksSitOnStops() {
        assertEquals(0, FrostStops.positionOf(FrostStops.SOLID));
        assertEquals(20, FrostStops.positionOf(FrostStops.CLEAR));
        assertEquals(40, FrostStops.positionOf(FrostStops.TINT));
        assertEquals(75, FrostStops.positionOf(FrostStops.MIST));
    }

    @Test
    public void theCurveRoundsHalfUpAndStartsAtFour() {
        assertEquals(0, FrostStops.blurDpAt(0));
        assertEquals(4, FrostStops.blurDpAt(1));
        assertEquals(2, FrostStops.grainPercentAt(1));
        assertEquals(36, FrostStops.blurDpAt(100));
        assertEquals(8, FrostStops.grainPercentAt(100));
        assertEquals(36, FrostStops.blurDpAt(250));
        assertEquals(0, FrostStops.blurDpAt(-3));
    }

    @Test
    public void aPositionSnapsToTheNearestStopTheLowerOnATie() {
        assertEquals(0, FrostStops.nearestStop(4));
        assertEquals(0, FrostStops.nearestStop(5));
        assertEquals(1, FrostStops.nearestStop(6));
        assertEquals(2, FrostStops.nearestStop(30));
        assertEquals(3, FrostStops.nearestStop(47));
        assertEquals(4, FrostStops.nearestStop(48));
        assertEquals(7, FrostStops.nearestStop(100));
    }

    @Test
    public void aPairSnapsToTheNearestStop() {
        for (int stop = 0; stop < FrostStops.COUNT; stop++)
            assertEquals(stop, FrostStops.nearestStop(FrostStops.blurDpFor(stop),
                FrostStops.grainPercentFor(stop)));
        // Blur 9, grain 4 (Glass 30, between Clear and Tint): Clear's 7/3 is nearer than 12/4.
        assertEquals(FrostStops.CLEAR, FrostStops.nearestStop(9, 4));
        assertEquals(2, FrostStops.nearestStopByBlur(8));
        assertEquals(3, FrostStops.nearestStopByBlur(10));
        assertEquals(7, FrostStops.nearestStopByBlur(48));
    }
}
