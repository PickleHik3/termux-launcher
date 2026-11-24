package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The grip's pure parts: drag to scale with clamp and snap at the default, and the readout. */
public class BarGripPolicyTest {

    private static final float DEFAULT = 1.0423018f;
    private static final float MIN = 0.5f;
    private static final float MAX = 1.6f;

    @Test public void growthScalesTheStoredScaleByTheBarsOwnHeight() {
        // A 200px bar pulled 100px taller is half as tall again.
        assertEquals(1.5f, BarGripPolicy.scaleFor(1f, 200f, 100f, DEFAULT, MIN, MAX), 1e-4f);
        assertEquals(0.75f, BarGripPolicy.scaleFor(1f, 200f, -50f, DEFAULT, MIN, MAX), 1e-4f);
    }

    @Test public void clampsToTheStoredRange() {
        assertEquals(MAX, BarGripPolicy.scaleFor(1f, 100f, 900f, DEFAULT, MIN, MAX), 0f);
        assertEquals(MIN, BarGripPolicy.scaleFor(1f, 100f, -900f, DEFAULT, MIN, MAX), 0f);
    }

    @Test public void snapsAtTheDefaultAndOnlyNearIt() {
        // 100px bar at 1.0 pulled 5px: 1.05, within the band of 1.0423.
        assertEquals(DEFAULT, BarGripPolicy.scaleFor(1f, 100f, 5f, DEFAULT, MIN, MAX), 0f);
        // 10px: 1.10, outside the band.
        assertEquals(1.1f, BarGripPolicy.scaleFor(1f, 100f, 10f, DEFAULT, MIN, MAX), 1e-4f);
    }

    @Test public void anUnmeasuredBarDoesNotMove() {
        assertEquals(1.2f, BarGripPolicy.scaleFor(1.2f, 0f, 40f, DEFAULT, MIN, MAX), 0f);
        assertEquals(1.2f, BarGripPolicy.scaleFor(1.2f, Float.NaN, 40f, DEFAULT, MIN, MAX), 0f);
    }

    @Test public void thePercentIsWhatTheHeightRowShows() {
        assertEquals(104, BarGripPolicy.percent(DEFAULT));
        assertEquals(50, BarGripPolicy.percent(0.5f));
        assertEquals(160, BarGripPolicy.percent(1.6f));
    }

    @Test public void terminalRowsLoseTheRowsTheKeyboardGrowthCovers() {
        // A 1000px pane of 40 rows: 25px a row. 60px more keyboard covers three rows.
        assertEquals(37, BarGripPolicy.terminalRows(40, 1000, 60));
        // Nothing changed: no rows move.
        assertEquals(40, BarGripPolicy.terminalRows(40, 1000, 0));
    }

    @Test public void terminalRowsGainRowsWhenTheKeyboardShrinks() {
        assertEquals(43, BarGripPolicy.terminalRows(40, 1000, -60));
    }

    @Test public void terminalRowsNeverReachZeroOrFollowAnUnmeasuredPane() {
        assertEquals(1, BarGripPolicy.terminalRows(5, 100, 10_000));
        assertEquals(40, BarGripPolicy.terminalRows(40, 0, 60));
        assertEquals(0, BarGripPolicy.terminalRows(0, 1000, 60));
    }
}
