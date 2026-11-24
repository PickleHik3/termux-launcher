package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DisplayScaleStepsTest {

    @Test
    public void nativeModeIsTheFirstStopWhateverTheScaleSays() {
        assertEquals(100, DisplayScaleSteps.fromPreferences("native", 200));
        assertEquals(100, DisplayScaleSteps.fromPreferences("exact", 150));
    }

    @Test
    public void scaledModeSnapsTheStoredScaleToTheNearestStop() {
        assertEquals(150, DisplayScaleSteps.fromPreferences("scaled", 150));
        assertEquals(125, DisplayScaleSteps.fromPreferences("scaled", 130));
        assertEquals(250, DisplayScaleSteps.fromPreferences("scaled", 300));
        assertEquals(100, DisplayScaleSteps.fromPreferences("scaled", 30));
    }

    @Test
    public void tiesGoToTheSmallerStop() {
        assertEquals(200, DisplayScaleSteps.nearest(225));
    }

    @Test
    public void onlyTheFirstStopMeansTheScreensOwnPixels() {
        assertEquals("native", DisplayScaleSteps.modeFor(100));
        assertEquals("scaled", DisplayScaleSteps.modeFor(125));
        assertEquals("scaled", DisplayScaleSteps.modeFor(250));
    }

    @Test
    public void fractionAndStepAtAreInverses() {
        for (int step : DisplayScaleSteps.STEPS) {
            assertEquals(step, DisplayScaleSteps.stepAt(DisplayScaleSteps.fraction(step)));
        }
        assertEquals(0f, DisplayScaleSteps.fraction(100), 0f);
        assertEquals(1f, DisplayScaleSteps.fraction(250), 0f);
    }

    @Test
    public void stepAtClampsAndRoundsToTheClosestStop() {
        assertEquals(100, DisplayScaleSteps.stepAt(-1f));
        assertEquals(250, DisplayScaleSteps.stepAt(2f));
        assertEquals(125, DisplayScaleSteps.stepAt(0.2f));
        assertEquals(150, DisplayScaleSteps.stepAt(0.45f));
    }

    @Test
    public void labelIsThePercent() {
        assertEquals("150%", DisplayScaleSteps.label(150));
    }

    @Test
    public void aStopIsAScaleValueAndBack() {
        assertEquals("1", DisplayScaleSteps.scaleValue(100));
        assertEquals("1.25", DisplayScaleSteps.scaleValue(125));
        assertEquals("1.75", DisplayScaleSteps.scaleValue(175));
        assertEquals("2", DisplayScaleSteps.scaleValue(200));
        assertEquals("2.5", DisplayScaleSteps.scaleValue(250));
        for (int step : DisplayScaleSteps.STEPS) {
            assertEquals(step, DisplayScaleSteps.stepOf(DisplayScaleSteps.scaleValue(step)));
        }
        assertEquals(0, DisplayScaleSteps.stepOf("3"));
        assertEquals(0, DisplayScaleSteps.stepOf("1.0"));
        assertEquals(0, DisplayScaleSteps.stepOf(null));
    }
}
