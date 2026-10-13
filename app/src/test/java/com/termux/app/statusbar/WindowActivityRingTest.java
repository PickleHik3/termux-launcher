package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WindowActivityRingTest {

    @Test
    public void aReportedPercentageFillsTheRingAndIsClamped() {
        assertEquals(0f, WindowActivityRing.determinateSweepDeg(0), .001f);
        assertEquals(90f, WindowActivityRing.determinateSweepDeg(25), .001f);
        assertEquals(360f, WindowActivityRing.determinateSweepDeg(100), .001f);
        assertEquals(360f, WindowActivityRing.determinateSweepDeg(140), .001f);
        assertEquals(0f, WindowActivityRing.determinateSweepDeg(-5), .001f);
    }
}
