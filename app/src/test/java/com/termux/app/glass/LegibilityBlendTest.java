package com.termux.app.glass;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The look blend's legibility half: a value for page A, one for page B and a progress give the
 * value between, pale and dark ink drifting on the crossing's curve and never flipping.
 */
public class LegibilityBlendTest {

    private static final int DARK = 0xFF000000;
    private static final int PALE = 0xFFFFFFFF;

    @Test
    public void eachEndIsExactlyItself() {
        assertEquals(DARK, LegibilityBlend.color(DARK, PALE, 0f));
        assertEquals(PALE, LegibilityBlend.color(DARK, PALE, 1f));
    }

    @Test
    public void aLightToDarkCrossingDriftsFromDarkInkToPaleInk() {
        // A light page wears dark legends, a dark page pale ones: no step on the way.
        int previous = LegibilityBlend.color(DARK, PALE, 0f) & 0xFF;
        for (int step = 1; step <= 32; step++) {
            int channel = LegibilityBlend.color(DARK, PALE, step / 32f) & 0xFF;
            assertTrue("step " + step, channel >= previous);
            assertTrue("no flip at step " + step, channel - previous <= 12);
            previous = channel;
        }
        assertEquals(0xFF, previous);
    }

    @Test
    public void theMiddleIsBetweenTheEnds() {
        int middle = LegibilityBlend.color(DARK, PALE, 0.5f);
        assertEquals(0x80, middle & 0xFF);
        assertEquals(0x80, (middle >>> 16) & 0xFF);
    }

    @Test
    public void aProgressOutsideTheCrossingIsClampedAndNanIsTheLeavingEnd() {
        assertEquals(DARK, LegibilityBlend.color(DARK, PALE, -3f));
        assertEquals(PALE, LegibilityBlend.color(DARK, PALE, 4f));
        assertEquals(DARK, LegibilityBlend.color(DARK, PALE, Float.NaN));
    }

    @Test
    public void alphaBlendsToo() {
        assertEquals(0x80, LegibilityBlend.color(0xFF000000, 0x00000000, 0.5f) >>> 24);
    }

    @Test
    public void rowsBlendOneByOneIntoTheCallersArray() {
        LegibilityCorrection a = LegibilityCorrection.ofRows(new int[] {DARK, DARK}, 1);
        LegibilityCorrection b = LegibilityCorrection.ofRows(new int[] {PALE, DARK}, 2);
        int[] out = new int[2];
        int[] returned = LegibilityBlend.faces(a, b, 1f, out);
        assertEquals(out, returned);
        assertArrayEquals(new int[] {PALE, DARK}, out);
    }

    @Test
    public void aShorterCorrectionRepeatsItsLastRow() {
        LegibilityCorrection one = LegibilityCorrection.ofFace(DARK);
        LegibilityCorrection four = LegibilityCorrection.ofRows(new int[] {PALE, PALE, PALE, PALE}, 2);
        int[] out = new int[4];
        LegibilityBlend.faces(one, four, 0f, out);
        assertArrayEquals(new int[] {DARK, DARK, DARK, DARK}, out);
    }

    @Test
    public void aSideOnlyConsumerSwitchesAtTheMiddle() {
        LegibilityCorrection a = LegibilityCorrection.ofRows(new int[] {DARK}, LegibilityCorrection.TONE_DARK);
        LegibilityCorrection b = LegibilityCorrection.ofRows(new int[] {PALE}, LegibilityCorrection.TONE_PALE);
        assertEquals(LegibilityCorrection.TONE_DARK, LegibilityBlend.tone(a, b, 0.49f));
        assertEquals(LegibilityCorrection.TONE_PALE, LegibilityBlend.tone(a, b, 0.5f));
    }
}
