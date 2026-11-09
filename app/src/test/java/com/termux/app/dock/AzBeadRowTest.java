package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The A–Z row's letter beads under Lens beads: their geometry, the lift and its spring. */
public class AzBeadRowTest {

    private static final float DENSITY = 2.625f;

    /** 28 letters (☆, A–Z, #) across a 411 dp row 19 dp tall. */
    private static AzBeadRow fullRow() {
        float width = 411f * DENSITY;
        float pitch = width / 28f;
        return AzBeadRow.compute(pitch * 0.5f, pitch, 28, 9.5f * DENSITY, 19f * DENSITY,
            DENSITY);
    }

    @Test
    public void everyLetterGetsABeadThatKeepsAirToItsNeighbour() {
        AzBeadRow row = fullRow();
        assertNotNull(row);
        assertEquals(28, row.count);
        float diameter = 2f * row.radiusPx;
        // Air between neighbours, a bead no wider than its slot, inside the row's band.
        assertTrue(row.pitchPx - diameter >= AzBeadRow.MIN_GAP_DP * DENSITY - 1e-3f);
        assertTrue(diameter <= 19f * DENSITY - 2f * LensBeadGeometry.EDGE_CLEARANCE_DP * DENSITY);
        // About 13 dp on a 411 dp phone: a small lens.
        assertEquals(12.9f, diameter / DENSITY, 0.2f);
        // The beads stand on the letters' slots.
        assertEquals(row.firstCenterX + 27 * row.pitchPx, row.centerX(27), 1e-3f);
    }

    @Test
    public void aShortBandHoldsTheBeadToIt() {
        float pitch = 30f * DENSITY;
        AzBeadRow row = AzBeadRow.compute(15f * DENSITY, pitch, 10, 6f * DENSITY,
            12f * DENSITY, DENSITY);
        assertNotNull(row);
        assertEquals(12f * DENSITY - 2f * LensBeadGeometry.EDGE_CLEARANCE_DP * DENSITY,
            2f * row.radiusPx, 1e-3f);
    }

    @Test
    public void noLettersOrNoRoomIsNoBeads() {
        assertNull(AzBeadRow.compute(0f, 10f, 0, 0f, 40f, DENSITY));
        // Slots so narrow a bead would be under 6 dp.
        assertNull(AzBeadRow.compute(0f, 5f * DENSITY, 60, 0f, 19f * DENSITY, DENSITY));
        assertNull(AzBeadRow.compute(0f, 20f * DENSITY, 20, 0f, 4f * DENSITY, DENSITY));
    }

    @Test
    public void offsetMovesEveryBead() {
        AzBeadRow row = fullRow();
        AzBeadRow moved = row.offset(5f, -3f);
        assertEquals(row.centerX(4) + 5f, moved.centerX(4), 1e-3f);
        assertEquals(row.centerY - 3f, moved.centerY, 1e-3f);
        assertEquals(row.radiusPx, moved.radiusPx, 0f);
        assertEquals(row, row.offset(0f, 0f));
    }

    // ---------------------------------------------------------------- the lift

    @Test
    public void theLiftFallsOffOverAboutTwoLettersEachSide() {
        assertEquals(1f, AzBeadRow.liftShare(0f), 1e-6f);
        // Symmetric, falling, gone from 2.5 letters.
        float previous = 1f;
        for (float d = 0.25f; d <= 3f; d += 0.25f) {
            float share = AzBeadRow.liftShare(d);
            assertEquals(share, AzBeadRow.liftShare(-d), 1e-6f);
            assertTrue(share <= previous);
            previous = share;
        }
        assertTrue(AzBeadRow.liftShare(1f) > 0.5f);
        assertTrue(AzBeadRow.liftShare(2f) > 0f && AzBeadRow.liftShare(2f) < 0.15f);
        assertEquals(0f, AzBeadRow.liftShare(AzBeadRow.LIFT_REACH_SLOTS), 0f);
        assertEquals(0f, AzBeadRow.liftShare(10f), 0f);
        assertEquals(0f, AzBeadRow.liftShare(Float.NaN), 0f);
    }

    @Test
    public void theBeadUnderTheFingerRisesSixToEightDp() {
        AzBeadRow row = fullRow();
        float peak = AzBeadRow.LIFT_PEAK_DP * DENSITY;
        // Wherever the finger is in a letter's slot, that letter's bead rises 6 to 8 dp.
        for (float f = -0.5f; f <= 0.5f; f += 0.1f) {
            float finger = row.centerX(10) + f * row.pitchPx;
            float lift = AzBeadRow.liftPx(row.centerX(10), finger, row.pitchPx, peak) / DENSITY;
            assertTrue("lift " + lift, lift >= 6f && lift <= 8f);
        }
        // Its neighbours less, the far letters not at all.
        float finger = row.centerX(10);
        float under = AzBeadRow.liftPx(row.centerX(10), finger, row.pitchPx, peak);
        float next = AzBeadRow.liftPx(row.centerX(11), finger, row.pitchPx, peak);
        float second = AzBeadRow.liftPx(row.centerX(8), finger, row.pitchPx, peak);
        assertTrue(under > next && next > second && second > 0f);
        assertEquals(0f, AzBeadRow.liftPx(row.centerX(14), finger, row.pitchPx, peak), 0f);
        // No finger, or no peak: nothing rises.
        assertEquals(0f, AzBeadRow.liftPx(row.centerX(10), Float.NaN, row.pitchPx, peak), 0f);
        assertEquals(0f, AzBeadRow.liftPx(row.centerX(10), finger, row.pitchPx, 0f), 0f);
    }

    // ---------------------------------------------------------------- the spring

    @Test
    public void theSpringSetsOffWholeAndSettlesAtRest() {
        assertEquals(1f, AzBeadRow.springShare(0f), 0f);
        assertEquals(1f, AzBeadRow.springShare(-5f), 0f);
        assertEquals(0f, AzBeadRow.springShare(AzBeadRow.SPRING_MS), 0f);
        assertEquals(0f, AzBeadRow.springShare(5000f), 0f);
        // Continuous where it is held: under a hundredth is left just before the hold.
        assertTrue(Math.abs(AzBeadRow.springShare(AzBeadRow.SPRING_MS - 1f)) < 0.01f);
        // Half way home well inside the first 100 ms: it reads as a spring, not a fade.
        assertTrue(AzBeadRow.springShare(60f) < 0.5f);
    }

    @Test
    public void theSpringOvershootsSlightlyAndNeverRings() {
        float least = 1f;
        int crossings = 0;
        float previous = 1f;
        for (float t = 1f; t < AzBeadRow.SPRING_MS; t += 1f) {
            float s = AzBeadRow.springShare(t);
            least = Math.min(least, s);
            if (previous > 0f && s <= 0f || previous < 0f && s >= 0f) crossings++;
            previous = s;
        }
        // A slight dip past rest (a bead dips under a dp below its line), and back.
        assertTrue(least < 0f);
        assertTrue(least > -0.2f);
        assertTrue(crossings <= 2);
    }

    @Test
    public void springBackFromAnyLiftLandsOnRest() {
        // Released mid-rise: from where it is to 0.
        assertEquals(0.6f, AzBeadRow.spring(0.6f, 0f, 0f), 1e-6f);
        assertEquals(0f, AzBeadRow.spring(0.6f, 0f, AzBeadRow.SPRING_MS), 0f);
        // And the rise on a touch: from rest to held.
        assertEquals(0f, AzBeadRow.spring(0f, 1f, 0f), 1e-6f);
        assertEquals(1f, AzBeadRow.spring(0f, 1f, AzBeadRow.SPRING_MS), 0f);
    }
}
