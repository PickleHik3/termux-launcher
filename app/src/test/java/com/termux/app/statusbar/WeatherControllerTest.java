package com.termux.app.statusbar;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeatherControllerTest {

    /** Every code Open-Meteo can report, so no branch of the table is left unasserted. */
    private static final int[] WMO_CODES = {
        0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77,
        80, 81, 82, 85, 86, 95, 96, 99};

    @Test
    public void anUnknownCodeHasNoWordsForTheSky() {
        assertEquals("\u2014", WeatherController.describe(-1));
        assertEquals("\u2014", WeatherController.describe(1234));
    }

    @Test
    public void describeSeparatesHailBearingThunderstorms() {
        assertEquals("Thunderstorm", WeatherController.describe(95));
        assertEquals("Thunderstorm with hail", WeatherController.describe(96));
        assertEquals("Thunderstorm with hail", WeatherController.describe(99));
    }

    @Test
    public void formatTemp_convertsAndRoundsConsistentlyInBothUnits() {
        // Shared by the compact status-bar widget and the detail card — same math, same rounding.
        assertEquals("22°", WeatherController.formatTemp(21.6, false));
        assertEquals("71°", WeatherController.formatTemp(21.6, true));   // 70.88 F rounds up
        assertEquals("0°", WeatherController.formatTemp(0.0, false));
        assertEquals("32°", WeatherController.formatTemp(0.0, true));
        assertEquals("-4°", WeatherController.formatTemp(-3.9, false));
        assertEquals("25°", WeatherController.formatTemp(-3.9, true));   // 24.98 F rounds up
        assertEquals("--°", WeatherController.formatTemp(Double.NaN, false));
        assertEquals("--°", WeatherController.formatTemp(Double.NaN, true));
    }

    @Test
    public void formatTempBare_sameRoundingWithoutTheDegreeGlyph() {
        // The side status bar's chip has no room for the glyph or the unit, but agrees with
        // formatTemp on the number itself.
        assertEquals("22", WeatherController.formatTempBare(21.6, false));
        assertEquals("71", WeatherController.formatTempBare(21.6, true));
        assertEquals("0", WeatherController.formatTempBare(0.0, false));
        assertEquals("32", WeatherController.formatTempBare(0.0, true));
        assertEquals("-4", WeatherController.formatTempBare(-3.9, false));
        assertEquals("25", WeatherController.formatTempBare(-3.9, true));
        assertEquals("--", WeatherController.formatTempBare(Double.NaN, false));
        assertEquals("--", WeatherController.formatTempBare(Double.NaN, true));
    }

    /** The label every code Open-Meteo reports resolved to before the cascades became one table. */
    private static final Object[][] WMO_TABLE = {
        {0, "Clear"},
        {1, "Mainly clear"},
        {2, "Partly cloudy"},
        {3, "Overcast"},
        {45, "Fog"},
        {48, "Fog"},
        {51, "Drizzle"},
        {53, "Drizzle"},
        {55, "Drizzle"},
        {56, "Drizzle"},
        {57, "Drizzle"},
        {61, "Rain"},
        {63, "Rain"},
        {65, "Rain"},
        {66, "Rain"},
        {67, "Rain"},
        {71, "Snow"},
        {73, "Snow"},
        {75, "Snow"},
        {77, "Snow"},
        {80, "Showers"},
        {81, "Showers"},
        {82, "Showers"},
        {85, "Snow showers"},
        {86, "Snow showers"},
        {95, "Thunderstorm"},
        {96, "Thunderstorm with hail"},
        {99, "Thunderstorm with hail"},
        {-1, "\u2014"},
    };

    @Test
    public void everyWmoCodeKeepsItsLabel() {
        Set<Integer> covered = new HashSet<>();
        for (Object[] row : WMO_TABLE) {
            int code = (Integer) row[0];
            covered.add(code);
            assertEquals("label " + code, row[1], WeatherController.describe(code));
        }
        for (int code : WMO_CODES) assertTrue("code " + code + " unpinned", covered.contains(code));
    }
}
