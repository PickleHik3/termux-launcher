package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CalendarTodayDiscPolicyTest {
    private static final int NAVY = 0xFF1B2A5C;
    private static final int DARK_GLASS = 0xFF14161C;
    private static final int LIGHT_INK = 0xFFE6E8EE;

    @Test public void darkNavyPrimaryOnDarkGlassFallsBackToInk() {
        int[] c = CalendarTodayDiscPolicy.todayDiscColours(DARK_GLASS, NAVY, 0xFF1B2A5C, LIGHT_INK);
        assertTrue(CalendarTodayDiscPolicy.contrast(c[0], DARK_GLASS)
            >= CalendarTodayDiscPolicy.DISC_FLOOR);
        assertTrue(CalendarTodayDiscPolicy.contrast(c[1], c[0])
            >= CalendarTodayDiscPolicy.DIGIT_FLOOR);
    }

    @Test public void normalLightCaseKeepsPrimaryAndOnPrimary() {
        int primary = 0xFF3F5BD9;
        int[] c = CalendarTodayDiscPolicy.todayDiscColours(0xFFF4F5FA, primary, 0xFFFFFFFF,
            0xFF1B1D24);
        assertEquals(primary, c[0]);
        assertEquals(0xFFFFFFFF, c[1]);
    }

    @Test public void brightPrimaryOnDarkGlassIsKept() {
        int primary = 0xFFAFC6FF;
        int[] c = CalendarTodayDiscPolicy.todayDiscColours(DARK_GLASS, primary, 0xFF00296B,
            LIGHT_INK);
        assertEquals(primary, c[0]);
        assertEquals(0xFF00296B, c[1]);
    }
}
