package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;

import com.termux.app.terminal.extrakeys.HomePillSpot.Area;
import com.termux.app.terminal.extrakeys.HomePillSpot.Rect;

import org.junit.Test;

public class HomePillSpotTest {

    private static final float D = 0.01f;
    private final float[] out = new float[2];

    /** 1000 x 2000 window; legal area is 16..984 wide, 100..1800 tall once the gutter is taken. */
    private static Area area(Rect az) {
        return HomePillSpot.area(1000f, 2000f, 0f, 84f, 0f, 0f, 16f, 100f, 1800f, az, 12f);
    }

    @Test
    public void theLegalAreaIsInsideTheInsetsGutterStatusBarAndStack() {
        Area a = HomePillSpot.area(1000f, 2000f, 10f, 40f, 20f, 50f, 16f, 100f, 1800f, null, 12f);
        assertEquals(26f, a.legal.left, D);
        assertEquals(964f, a.legal.right, D);
        assertEquals(100f, a.legal.top, D);   // the status bar is lower than inset + gutter
        assertEquals(1800f, a.legal.bottom, D); // the stack is higher than window - inset - gutter
    }

    @Test
    public void noStoredSpotRestsOnTheRightCentredVertically() {
        HomePillSpot.place(null, 200f, 46f, area(null), out);
        assertEquals(984f - 12f - 200f, out[0], D);
        assertEquals(950f - 23f, out[1], D);
    }

    @Test
    public void aStoredSpotIsTheCentreAsFractionsOfTheWindow() {
        HomePillSpot.place(new float[]{0.5f, 0.25f}, 200f, 46f, area(null), out);
        assertEquals(400f, out[0], D);
        assertEquals(500f - 23f, out[1], D);
    }

    @Test
    public void anIllegalStoredSpotIsClampedIntoTheArea() {
        Area a = area(null);
        HomePillSpot.place(new float[]{0f, 0f}, 200f, 46f, a, out);
        assertEquals(16f, out[0], D);
        assertEquals(100f, out[1], D);
        HomePillSpot.place(new float[]{1f, 1f}, 200f, 46f, a, out);
        assertEquals(984f - 200f, out[0], D);
        assertEquals(1800f - 46f, out[1], D);
    }

    @Test
    public void anAreaSmallerThanThePillCentresItInTheWindowOnThatAxis() {
        Area narrow = HomePillSpot.area(300f, 2000f, 0f, 84f, 0f, 0f, 16f, 100f, 1800f, null, 12f);
        HomePillSpot.place(new float[]{0.9f, 0.5f}, 400f, 46f, narrow, out);
        assertEquals(-50f, out[0], D);
        assertEquals(977f, out[1], D);
        Area flat = HomePillSpot.area(1000f, 2000f, 0f, 84f, 0f, 0f, 16f, 100f, 120f, null, 12f);
        HomePillSpot.place(new float[]{0.5f, 0.1f}, 200f, 46f, flat, out);
        assertEquals((2000f - 46f) * 0.5f, out[1], D);
    }

    @Test
    public void thePillStepsToTheNearerSideOfTheAzTab() {
        Rect az = new Rect(900f, 400f, 984f, 1400f);
        Area a = area(az);
        // Centre at x 880 puts the pill over the tab's left edge; the left side is the nearer one.
        HomePillSpot.settle(880f, 900f, 200f, 46f, a, out);
        assertEquals(az.left - 200f, out[0], D);
        // A pill that overlaps from the left stays left of it.
        HomePillSpot.settle(800f, 900f, 200f, 46f, a, out);
        assertEquals(az.left - 200f, out[0], D);
    }

    @Test
    public void thePillUsesTheOtherSideWhenTheNearerOneDoesNotFit() {
        Rect az = new Rect(100f, 400f, 200f, 1400f); // left side has only 84 px free
        Area a = area(az);
        HomePillSpot.settle(150f, 900f, 200f, 46f, a, out);
        assertEquals(200f, out[0], D);
    }

    @Test
    public void theAzTabIsIgnoredAboveOrBelowIt() {
        Rect az = new Rect(900f, 400f, 984f, 1400f);
        HomePillSpot.settle(880f, 200f, 200f, 46f, area(az), out);
        assertEquals(780f, out[0], D);
    }

    @Test
    public void fractionsAreTheCentreInTheUnitRange() {
        HomePillSpot.fractionsOf(400f, 477f, 200f, 46f, 1000f, 2000f, out);
        assertEquals(0.5f, out[0], D);
        assertEquals(0.25f, out[1], D);
        HomePillSpot.fractionsOf(-500f, 5000f, 200f, 46f, 1000f, 2000f, out);
        assertEquals(0f, out[0], D);
        assertEquals(1f, out[1], D);
    }

    @Test
    public void theChipSitsAboveCentredAndBelowWhenThereIsNoRoom() {
        Area a = area(null);
        float[] chip = new float[2];
        HomePillSpot.chipTopLeft(400f, 800f, 200f, 46f, 120f, 34f, 10f, a, chip);
        assertEquals(440f, chip[0], D);
        assertEquals(800f - 10f - 34f, chip[1], D);
        HomePillSpot.chipTopLeft(400f, 120f, 200f, 46f, 120f, 34f, 10f, a, chip);
        assertEquals(120f + 46f + 10f, chip[1], D);
    }

    @Test
    public void theChipIsClampedSidewaysIntoTheArea() {
        Area a = area(null);
        float[] chip = new float[2];
        HomePillSpot.chipTopLeft(20f, 800f, 60f, 46f, 200f, 34f, 10f, a, chip);
        assertEquals(16f, chip[0], D);
        HomePillSpot.chipTopLeft(900f, 800f, 80f, 46f, 200f, 34f, 10f, a, chip);
        assertEquals(984f - 200f, chip[0], D);
    }
}
