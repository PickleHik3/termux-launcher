package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The meter's judgement: reading to ratio, readable or amber, and the dim that would read. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LegibilityMeterTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    @Test public void nothingMeasuredShowsNoMeter() {
        assertNull(LegibilityMeter.of(null));
        assertNull(LegibilityMeter.of(new LegibilityMeter.Reading(WHITE, new int[0], 0)));
    }

    @Test public void paleInkOnDarkFaceIsReadable() {
        LegibilityMeter.State state = LegibilityMeter.of(
            new LegibilityMeter.Reading(WHITE, new int[] {0xFF202020, 0xFF303030}, 20));
        assertNotNull(state);
        assertTrue(state.readable);
        assertFalse(state.offersDim());
        assertTrue(state.ratio >= LegibilityMeter.READABLE);
    }

    @Test public void theWorstFaceDecides() {
        LegibilityMeter.State state = LegibilityMeter.of(
            new LegibilityMeter.Reading(WHITE, new int[] {0xFF101010, 0xFF909090}, 0));
        assertFalse(state.readable);
        assertEquals(com.termux.app.chrome.OnGlass.ratio(WHITE, 0xFF909090), state.ratio, 1e-9);
    }

    @Test public void amberOffersTheSmallestDimThatReads() {
        int[] faces = {0xFF808080};
        LegibilityMeter.State state = LegibilityMeter.of(new LegibilityMeter.Reading(WHITE, faces, 0));
        assertFalse(state.readable);
        assertTrue(state.offersDim());
        int dim = state.dimTo;
        assertTrue(dim > 0 && dim <= 100);
        float scale = (100f - dim) / 100f;
        assertTrue(LegibilityMeter.worst(WHITE, new int[] {scaled(faces[0], scale)})
            >= LegibilityMeter.READABLE);
        float less = (100f - (dim - 1)) / 100f;
        assertTrue(LegibilityMeter.worst(WHITE, new int[] {scaled(faces[0], less)})
            < LegibilityMeter.READABLE);
    }

    @Test public void theDimStartsFromTheCurrentDim() {
        LegibilityMeter.State state = LegibilityMeter.of(
            new LegibilityMeter.Reading(WHITE, new int[] {0xFFB0B0B0}, 40));
        assertTrue(state.offersDim());
        assertTrue(state.dimTo > 40);
    }

    @Test public void darkInkOnAPaleFaceIsNeverOfferedDim() {
        // Darkening a face moves dark ink toward its own colour: dim cannot help.
        LegibilityMeter.State state = LegibilityMeter.of(
            new LegibilityMeter.Reading(Color.rgb(0x55, 0x55, 0x55), new int[] {0xFF777777}, 0));
        assertFalse(state.readable);
        assertFalse(state.offersDim());
    }

    @Test public void fullDimOffersNothing() {
        LegibilityMeter.State state = LegibilityMeter.of(
            new LegibilityMeter.Reading(BLACK, new int[] {0xFF404040}, 100));
        assertFalse(state.offersDim());
    }

    @Test public void showRuleHidesReadableAtLiftAndKeepsAmber() {
        LegibilityMeter.State ok = LegibilityMeter.of(
            new LegibilityMeter.Reading(WHITE, new int[] {0xFF202020}, 0));
        LegibilityMeter.State poor = LegibilityMeter.of(
            new LegibilityMeter.Reading(WHITE, new int[] {0xFF909090}, 0));
        assertTrue(LegibilityMeter.shows(ok, true));
        assertTrue(LegibilityMeter.shows(poor, true));
        assertFalse(LegibilityMeter.shows(ok, false));
        assertTrue(LegibilityMeter.shows(poor, false));
        assertFalse(LegibilityMeter.shows(null, true));
    }

    private static int scaled(int face, float scale) {
        return Color.rgb(Math.round(Color.red(face) * scale), Math.round(Color.green(face) * scale),
            Math.round(Color.blue(face) * scale));
    }
}
